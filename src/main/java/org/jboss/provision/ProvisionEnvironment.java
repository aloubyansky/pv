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
import java.util.Map;
import java.util.Set;

import org.jboss.provision.info.ContentPath;
import org.jboss.provision.info.ProvisionUnitInfo;

/**
 *
 * @author Alexey Loubyansky
 */
public abstract class ProvisionEnvironment extends ProvisionEnvironmentBase {

    public static Builder newBuilder() {
        return new Builder();
    }

    public static Builder forUndefinedUnit() {
        return new Builder().addUnit(ProvisionUnitInfo.UNDEFINED_INFO);
    }

    public static Builder forUnit(String name, String version) {
        return new Builder().addUnit(name, version);
    }

    public static class Builder {

        private File envHome;
        private Map<String, ContentPath> namedLocations = Collections.emptyMap();
        private Map<String, ContentPath> unitHomes = Collections.emptyMap();
        private UnitUpdatePolicy defaultUnitUpdatePolicy = UnitUpdatePolicy.UNIT_FORCED_CONTENT_CONDITIONED;
        private Map<String, UnitUpdatePolicy> unitUpdatePolicies = Collections.emptyMap();
        private Map<String, ProvisionUnitInfo> unitInfos = Collections.emptyMap();

        private Builder() {
        }

        public Builder setEnvironmentHome(File envHome) {
            this.envHome = envHome;
            return this;
        }

        public Builder nameLocation(String name, String relativePath) {
            return nameLocation(name, ContentPath.forPath(relativePath));
        }

        public Builder nameLocation(String name, ContentPath relativePath) {
            switch(namedLocations.size()) {
                case 0:
                    namedLocations = Collections.singletonMap(name, relativePath);
                    break;
                case 1:
                    namedLocations = new HashMap<String, ContentPath>(namedLocations);
                default:
                    namedLocations.put(name, relativePath);
            }
            return this;
        }

        public Builder addUnit(final String name, final String version) {
            assert name != null : ProvisionErrors.nullArgument("name");
            assert version != null : ProvisionErrors.nullArgument("version");
            return addUnit(ProvisionUnitInfo.createInfo(name, version));
        }

        public Builder addUnit(ProvisionUnitInfo unitInfo) {
            switch(unitInfos.size()) {
                case 0:
                    unitInfos = Collections.singletonMap(unitInfo.getName(), unitInfo);
                    break;
                case 1:
                    unitInfos = new HashMap<String, ProvisionUnitInfo>(unitInfos);
                default:
                    unitInfos.put(unitInfo.getName(), unitInfo);
            }
            return this;
        }

        public Builder copyUnit(ProvisionUnitEnvironment unitEnv) throws ProvisionException {
            addUnit(unitEnv.getUnitInfo());
            final String unitName = unitEnv.getUnitInfo().getName();
            if(unitEnv.getHomePath() != null) {
                setUnitHome(unitName, unitEnv.getHomePath());
            }
            if(unitEnv.getUpdatePolicy() != null) {
                setUnitUpdatePolicy(unitName, unitEnv.getUpdatePolicy());
            }
            return this;
        }

        public Builder setUnitHome(String unitName, ContentPath unitHome) throws ProvisionException {
            if(!unitInfos.containsKey(unitName)) {
                throw ProvisionErrors.unknownUnit(unitName);
            }
            switch(unitHomes.size()) {
                case 0:
                    unitHomes = Collections.singletonMap(unitName, unitHome);
                    break;
                case 1:
                    unitHomes = new HashMap<String, ContentPath>(unitHomes);
                default:
                    unitHomes.put(unitName, unitHome);
            }
            return this;
        }

        public Builder setDefaultUnitUpdatePolicy(UnitUpdatePolicy unitPolicy) {
            this.defaultUnitUpdatePolicy = unitPolicy;
            return this;
        }

        public Builder setUnitUpdatePolicy(String unitName, UnitUpdatePolicy updatePolicy) throws ProvisionException {
            if(!unitInfos.containsKey(unitName)) {
                throw ProvisionErrors.unknownUnit(unitName);
            }
            switch(unitUpdatePolicies.size()) {
                case 0:
                    unitUpdatePolicies = Collections.singletonMap(unitName, updatePolicy);
                    break;
                case 1:
                    unitUpdatePolicies = new HashMap<String, UnitUpdatePolicy>(unitUpdatePolicies);
                default:
                    unitUpdatePolicies.put(unitName, updatePolicy);
            }
            return this;
        }

        public ProvisionEnvironment build() throws ProvisionException {
            return new ProvisionEnvironment(this){};
        }
    }

    protected ProvisionEnvironment(Builder builder) throws ProvisionException {
        super(builder.namedLocations, builder.defaultUnitUpdatePolicy);
        assert builder.envHome != null : ProvisionErrors.nullArgument("envHome");
        assert builder.defaultUnitUpdatePolicy != null : ProvisionErrors.nullArgument("updatePolicy");
        assert builder.unitHomes != null : ProvisionErrors.nullArgument("unitHome");
        assert builder.unitUpdatePolicies != null : ProvisionErrors.nullArgument("unitUpdatePolicies");
        this.envHome = builder.envHome;

        if(builder.unitInfos.isEmpty()) {
            throw ProvisionErrors.environmentHasNoUnits();
        }

        if(builder.unitInfos.size() == 1) {
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
    private final Map<String, ProvisionUnitEnvironment> unitEnvs;

    @Override
    public File getEnvironmentHome() {
        return envHome;
    }

    public Set<String> getUnitNames() {
        return unitEnvs.keySet();
    }

    public ProvisionUnitEnvironment getUnitEnvironment(String unitName) {
        assert unitName != null : ProvisionErrors.nullArgument("unitName");
        return unitEnvs.get(unitName);
    }

    public UnitUpdatePolicy resolveUnitPolicy(String unitName) {
        assert unitName != null : ProvisionErrors.nullArgument("unitName");
        final ProvisionUnitEnvironment unitEnv = unitEnvs.get(unitName);
        return unitEnv == null ? null : unitEnv.resolveUpdatePolicy();
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
