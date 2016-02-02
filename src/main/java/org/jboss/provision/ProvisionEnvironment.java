/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.provision;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.jboss.provision.info.ContentPath;
import org.jboss.provision.info.ProvisionEnvironmentInfo;
import org.jboss.provision.info.ProvisionUnitInfo;
import org.jboss.provision.instruction.ProvisionEnvironmentInstruction;

/**
 *
 * @author Alexey Loubyansky
 */
public class ProvisionEnvironment extends ProvisionEnvironmentBase {

    public static final String DEF_HISTORY_DIR = ".pvh";

    public static ProvisionEnvironmentBuilder builder() {
        return new ProvisionEnvironmentBuilder();
    }

    public static ProvisionEnvironmentBuilder forUndefinedUnit() {
        return new ProvisionEnvironmentBuilder().addUnit(ProvisionUnitInfo.UNDEFINED_INFO);
    }

    public static ProvisionEnvironmentBuilder forUnit(String name, String version) {
        return new ProvisionEnvironmentBuilder().addUnit(name, version);
    }

    ProvisionEnvironment(ProvisionEnvironmentBuilder builder) throws ProvisionException {
        super(builder.namedLocations, builder.defaultUnitUpdatePolicy);
        assert builder.envHome != null : ProvisionErrors.nullArgument("envHome");
        assert builder.defaultUnitUpdatePolicy != null : ProvisionErrors.nullArgument("updatePolicy");
        assert builder.unitHomes != null : ProvisionErrors.nullArgument("unitHome");
        assert builder.unitUpdatePolicies != null : ProvisionErrors.nullArgument("unitUpdatePolicies");
        this.envHome = builder.envHome;

        if(builder.unitInfos.isEmpty()) {
            //throw ProvisionErrors.environmentHasNoUnits();
            unitEnvs = Collections.emptyMap();
        } else if(builder.unitInfos.size() == 1) {
            final String unitName = builder.unitInfos.keySet().iterator().next();
            unitEnvs = Collections.singletonMap(unitName, new ProvisionUnitEnvironment(this,
                    builder.unitInfos.get(unitName), builder.unitHomes.get(unitName),
                    Collections.<String, ContentPath>emptyMap(), builder.unitUpdatePolicies.get(unitName)));
        } else {
            unitEnvs = new HashMap<String, ProvisionUnitEnvironment>(builder.unitInfos.size());
            for(String unitName : builder.unitInfos.keySet()) {
                unitEnvs.put(unitName, new ProvisionUnitEnvironment(this,
                        builder.unitInfos.get(unitName), builder.unitHomes.get(unitName),
                        Collections.<String, ContentPath>emptyMap(), builder.unitUpdatePolicies.get(unitName)));
            }
        }
    }

    private final File envHome;
    private Map<String, ProvisionUnitEnvironment> unitEnvs;

    @Override
    public File getEnvironmentHome() {
        return envHome;
    }

    public Set<String> getUnitNames() {
        return unitEnvs.keySet();
    }

    public ProvisionEnvironmentInfo getEnvironmentInfo() {
        if(unitEnvs.isEmpty()) {
            return ProvisionEnvironmentInfo.emptyEnvironment();
        }
        final ProvisionEnvironmentInfo.Builder builder = ProvisionEnvironmentInfo.builder();
        for(ProvisionUnitEnvironment unitEnv : unitEnvs.values()) {
            try {
                builder.addUnit(unitEnv.getUnitInfo());
            } catch (ProvisionException e) {
                throw new IllegalStateException(e);
            }
        }
        return builder.build();
    }

    public ProvisionUnitEnvironment getUnitEnvironment(String unitName) {
        assert unitName != null : ProvisionErrors.nullArgument("unitName");
        return unitEnvs.get(unitName);
    }

    void removeUnit(String unitName) throws ProvisionException {
        assert unitName != null : ProvisionErrors.nullArgument("untiName");
        if(!unitEnvs.containsKey(unitName)) {
            throw ProvisionErrors.unitIsNotInstalled(unitName);
        }
        if(unitEnvs.size() == 1) {
            unitEnvs = Collections.<String, ProvisionUnitEnvironment>emptyMap();
        } else {
            unitEnvs.remove(unitName);
        }
    }

    public UnitUpdatePolicy resolveUnitPolicy(String unitName) {
        assert unitName != null : ProvisionErrors.nullArgument("unitName");
        final ProvisionUnitEnvironment unitEnv = unitEnvs.get(unitName);
        return unitEnv == null ? null : unitEnv.resolveUpdatePolicy();
    }

    public Iterator<ProvisionEnvironmentInfo> environmentHistory() throws ProvisionException {
        return getHistory().environmentIterator();
    }

    public Iterator<ProvisionUnitInfo> unitHistory(String unitName) throws ProvisionException {
        return getHistory().unitIterator(unitName);
    }

    public void apply(File packageFile) throws ProvisionException {
        assert packageFile != null : ProvisionErrors.nullArgument("packageFile");
        final ApplicationContextImpl appCtx = new ApplicationContextImpl(this, ContentSource.forZip(packageFile));
        reset(appCtx.processPackage(packageFile));
    }

    public void rollbackLast() throws ProvisionException {
        final EnvInstructionHistory.EnvRecord record = getHistory().getLastEnvironmentRecord();
        if(record == null) {
            throw ProvisionErrors.noHistoryRecordedUntilThisPoint();
        }
        final ApplicationContextImpl appCtx = new ApplicationContextImpl(this, record.getBackup());
        ProvisionEnvironmentInstruction rollback = record.getAppliedInstruction().getRollback();
        reset(appCtx.apply(rollback, ApplicationContextImpl.CommitCallback.ROLLBACK));
    }

    public void uninstall(String unitName) throws ProvisionException {
        getHistory().uninstall(this, unitName);
    }

    ProvisionEnvironmentHistory getHistory() {
        return ProvisionEnvironmentHistory.getInstance(this);
    }

    protected void reset(ProvisionEnvironment env) {
        super.reset(env);
        this.unitEnvs = env.unitEnvs;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((unitEnvs == null) ? 0 : unitEnvs.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!super.equals(obj)) {
            return false;
        }
        if (!(obj instanceof ProvisionEnvironment)) {
            return false;
        }
        ProvisionEnvironment other = (ProvisionEnvironment) obj;
        if (unitEnvs == null) {
            if (other.unitEnvs != null) {
                return false;
            }
        } else if (!unitEnvs.equals(other.unitEnvs)) {
            return false;
        }
        return true;
    }
}
