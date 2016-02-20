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

import org.jboss.provision.UnitInstructionHistory.UnitRecord;
import org.jboss.provision.info.ContentPath;
import org.jboss.provision.info.ProvisionEnvironmentInfo;
import org.jboss.provision.info.ProvisionUnitInfo;
import org.jboss.provision.instruction.ProvisionEnvironmentInstruction;
import org.jboss.provision.instruction.ProvisionUnitInstruction;
import org.jboss.provision.io.IoUtils;

/**
 *
 * @author Alexey Loubyansky
 */
public class ProvisionEnvironment extends ProvisionEnvironmentBase {

    public static final String DEF_HISTORY_DIR = ".pvh";

    public static ProvisionEnvironment load(File home) throws ProvisionException {
        final File historyDir = ProvisionEnvironmentHistory.getDefaultHistoryDir(home);
        if(!ProvisionEnvironmentHistory.storesHistory(historyDir)) {
            throw ProvisionErrors.noHistoryRecordedUntilThisPoint();
        }
        return new ProvisionEnvironmentHistory(historyDir).getCurrentEnvironment();
    }

    public static ProvisionEnvironmentBuilder builder() {
        return new ProvisionEnvironmentBuilder();
    }

    public static ProvisionEnvironmentBuilder forUndefinedUnit() {
        return new ProvisionEnvironmentBuilder().addUnit(ProvisionUnitInfo.UNDEFINED_INFO);
    }

    public static ProvisionEnvironmentBuilder forUnit(String name, String version) {
        return new ProvisionEnvironmentBuilder().addUnit(name, version);
    }

    private final File envHome;
    private Map<String, ProvisionUnitEnvironment> unitEnvs;
    private PathsOwnership pathsOwnership;

    ProvisionEnvironment(ProvisionEnvironmentBuilder builder) throws ProvisionException {
        super(builder.namedLocations, builder.defaultUnitUpdatePolicy);
        assert builder.envHome != null : ProvisionErrors.nullArgument("envHome");
        assert builder.defaultUnitUpdatePolicy != null : ProvisionErrors.nullArgument("updatePolicy");
        assert builder.unitHomes != null : ProvisionErrors.nullArgument("unitHome");
        assert builder.unitUpdatePolicies != null : ProvisionErrors.nullArgument("unitUpdatePolicies");
        this.envHome = builder.envHome;

        if(builder.unitInfos.isEmpty()) {
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

    ProvisionEnvironment(ProvisionEnvironment env) throws ProvisionException {
        super(env.getNamedLocations(), env.getUpdatePolicy());
        this.envHome = env.envHome;
        this.unitEnvs = new HashMap<String, ProvisionUnitEnvironment>(env.unitEnvs);
    }

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
        ContentSource expandedZip = null;
        try {
            expandedZip = ContentSource.expandedZip(packageFile);
            final ProvisionEnvironmentInstruction instruction = ApplicationContextImpl.readInstruction(this, expandedZip, packageFile);
            final ApplicationContextImpl appCtx = new ApplicationContextImpl(this);

            for(String unitName : instruction.getUnitNames()) {
                final ProvisionUnitInstruction unitInstr = instruction.getUnitInstruction(unitName);
                if(unitInstr.isVersionUpdate()) {
                    final ProvisionUnitEnvironment unitEnv = getUnitEnvironment(unitName);
                    if(unitEnv == null) {
                        throw ProvisionErrors.unitIsNotInstalled(unitName);
                    }
                    int patchesTotal = unitEnv.getUnitInfo().getPatches().size();
                    if(patchesTotal == 0) {
                        continue;
                    }
                    final ProvisionEnvironmentHistory history = getHistory();
                    final EnvInstructionHistory envInstrHistory = history.getEnvInstructionHistory();
                    final UnitInstructionHistory unitHistory = UnitInstructionHistory.getInstance(envInstrHistory, unitName);
                    UnitRecord unitRecord = unitHistory.loadLast();
                    while(patchesTotal > 0 && unitRecord != null) {
                        final EnvInstructionHistory.EnvRecord envRecord = envInstrHistory.loadRecord(unitRecord.getRecordDir().getName());
                        envRecord.assertRollbackForUnit(unitName);
                        appCtx.schedule(envRecord.getRollbackInstruction(), envRecord.getBackup());
                        unitRecord = unitRecord.getPrevious();
                        --patchesTotal;
                    }
                } else if(unitInstr.getRequiredVersion() == null && this.unitEnvs.containsKey(unitName)) {
                    appCtx.scheduleUninstall(unitName);
                }
            }

            appCtx.schedule(instruction, expandedZip);
            reset(appCtx.commit());
        } finally {
            IoUtils.safeClose(expandedZip);
        }
    }

    public void rollbackLast() throws ProvisionException {
        final EnvInstructionHistory.EnvRecord record = getHistory().getLastEnvironmentRecord();
        if(record == null) {
            throw ProvisionErrors.noHistoryRecordedUntilThisPoint();
        }
        final ProvisionEnvironmentInstruction rollback = record.getRollbackInstruction();
        final ApplicationContextImpl appCtx = new ApplicationContextImpl(this, false);
        appCtx.schedule(rollback, record.getBackup());
        reset(appCtx.commit());
    }

    public void uninstall(String unitName) throws ProvisionException {
        final ApplicationContextImpl appCtx = new ApplicationContextImpl(this, true);
        appCtx.scheduleUninstall(unitName);
        reset(appCtx.commit());
    }

    ProvisionEnvironmentHistory getHistory() {
        return ProvisionEnvironmentHistory.getInstance(this);
    }

    PathsOwnership getPathsOwnership() throws ProvisionException {
        if(pathsOwnership != null) {
            return pathsOwnership;
        }
        final PathsOwnership pathsOwnership = new PathsOwnership();
        for(ProvisionUnitEnvironment unitEnv : unitEnvs.values()) {
            final String unitName = unitEnv.getUnitInfo().getName();
            for(ContentPath path : unitEnv.getContentPaths()) {
                pathsOwnership.addOwner(unitEnv.resolvePath(path).getAbsolutePath(), unitName);
            }
        }
        this.pathsOwnership = pathsOwnership;
        return pathsOwnership;
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
