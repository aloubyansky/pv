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

import org.jboss.provision.info.ProvisionUnitInfo;

/**
 *
 * @author Alexey Loubyansky
 */
public abstract class ProvisionEnvironment {

    public static Builder create() {
        return new Builder();
    }

    public static class Builder {

        private File installationHome;
        private Map<String, File> paths = Collections.emptyMap();
        private Map<String, File> unitHomes = Collections.emptyMap();
        private UnitUpdatePolicy defaultUnitUpdatePolicy = UnitUpdatePolicy.UNIT_FORCED_CONTENT_CONDITIONED;
        private Map<String, UnitUpdatePolicy> unitUpdatePolicies = Collections.emptyMap();
        private Map<String, ProvisionUnitInfo> unitInfos = Collections.emptyMap();

        private Builder() {
        }

        public Builder setInstallationHome(File installationHome) {
            this.installationHome = installationHome;
            return this;
        }

        public Builder nameLocation(String name, File path) {
            switch(paths.size()) {
                case 0:
                    paths = Collections.singletonMap(name, path);
                    break;
                case 1:
                    paths = new HashMap<String, File>(paths);
                default:
                    paths.put(name, path);
            }
            return this;
        }

        public Builder addUnit(final String name, final String version) {
            assert name != null : ProvisionErrors.nullArgument("name");
            assert version != null : ProvisionErrors.nullArgument("version");
            final ProvisionUnitInfo info = ProvisionUnitInfo.createInfo(name, version);
            switch(unitInfos.size()) {
                case 0:
                    unitInfos = Collections.singletonMap(name, info);
                    break;
                case 1:
                    unitInfos = new HashMap<String, ProvisionUnitInfo>(unitInfos);
                default:
                    unitInfos.put(name, info);
            }
            return this;
        }

        public Builder setUnitHome(String unitName, File homeDir) throws ProvisionException {
            if(!unitInfos.containsKey(unitName)) {
                throw ProvisionErrors.unknownUnit(unitName);
            }
            switch(unitHomes.size()) {
                case 0:
                    unitHomes = Collections.singletonMap(unitName, homeDir);
                    break;
                case 1:
                    unitHomes = new HashMap<String, File>(unitHomes);
                default:
                    unitHomes.put(unitName, homeDir);
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

        public ProvisionEnvironment build() {
            return new ProvisionEnvironment(this){};
        }
    }

    protected ProvisionEnvironment(Builder builder) {
        assert builder.installationHome != null : ProvisionErrors.nullArgument("home");
        assert builder.paths != null : ProvisionErrors.nullArgument("paths");
        assert builder.unitHomes != null : ProvisionErrors.nullArgument("unitHome");
        assert builder.defaultUnitUpdatePolicy != null : ProvisionErrors.nullArgument("defaultUnitUpdatePolicy");
        assert builder.unitUpdatePolicies != null : ProvisionErrors.nullArgument("unitUpdatePolicies");
        this.installHome = builder.installationHome;
        this.namedLocations = builder.paths;
        this.unitHomes = builder.unitHomes;
        this.defaultUnitPolicy = builder.defaultUnitUpdatePolicy;
        this.unitUpdatePolicies = builder.unitUpdatePolicies;
        this.unitInfos = builder.unitInfos;
    }

    private final File installHome;
    private final Map<String, File> namedLocations;
    private final Map<String, File> unitHomes;
    private final UnitUpdatePolicy defaultUnitPolicy;
    private final Map<String, UnitUpdatePolicy> unitUpdatePolicies;
    private final Map<String, ProvisionUnitInfo> unitInfos;

    //java.util.Date getLastModifiedDate();

    public File getInstallationHome() {
        return installHome;
    }

    public Set<String> getUnitNames() {
        return unitInfos.keySet();
    }

    public ProvisionUnitInfo getUnitInfo(String unitName) {
        assert unitName != null : ProvisionErrors.nullArgument("unitName");
        return unitInfos.get(unitName);
    }

    public File getUnitHome(String unitName) {
        assert unitName != null : ProvisionErrors.nullArgument("unitName");
        return unitHomes.get(unitName);
    }

    public Set<String> getLocationNames() {
        return namedLocations.keySet();
    }

    public File resolveNamedLocation(String namedLocation) throws ProvisionException {
        final File f = namedLocations.get(namedLocation);
        if(f == null) {
            throw ProvisionErrors.undefinedNamedLocation(namedLocation);
        }
        return f;
    }

    public UnitUpdatePolicy getDefaultUnitPolicy() {
        return defaultUnitPolicy;
    }

    public UnitUpdatePolicy getUnitPolicy(String unitName) {
        assert unitName != null : ProvisionErrors.nullArgument("unitName");
        return unitUpdatePolicies.get(unitName);
    }

    public UnitUpdatePolicy resolveUnitPolicy(String unitName) {
        final UnitUpdatePolicy unitPolicy = getUnitPolicy(unitName);
        return unitPolicy == null ? defaultUnitPolicy : unitPolicy;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((defaultUnitPolicy == null) ? 0 : defaultUnitPolicy.hashCode());
        result = prime * result + ((installHome == null) ? 0 : installHome.hashCode());
        result = prime * result + ((namedLocations == null) ? 0 : namedLocations.hashCode());
        result = prime * result + ((unitHomes == null) ? 0 : unitHomes.hashCode());
        result = prime * result + ((unitInfos == null) ? 0 : unitInfos.hashCode());
        result = prime * result + ((unitUpdatePolicies == null) ? 0 : unitUpdatePolicies.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ProvisionEnvironment other = (ProvisionEnvironment) obj;
        if (defaultUnitPolicy == null) {
            if (other.defaultUnitPolicy != null)
                return false;
        } else if (!defaultUnitPolicy.equals(other.defaultUnitPolicy))
            return false;
        if (installHome == null) {
            if (other.installHome != null)
                return false;
        } else if (!installHome.getAbsolutePath().equals(other.installHome.getAbsolutePath())) {
            return false;
        }
        if (namedLocations == null) {
            if (other.namedLocations != null)
                return false;
        } else if (!namedLocations.keySet().equals(other.namedLocations.keySet())) {
            return false;
        } else {
            for(String name : namedLocations.keySet()) {
                if(!namedLocations.get(name).getAbsolutePath().equals(other.namedLocations.get(name).getAbsolutePath())) {
                    return false;
                }
            }
        }
        if (unitHomes == null) {
            if (other.unitHomes != null)
                return false;
        } else if(!unitHomes.keySet().equals(other.unitHomes.keySet())) {
            return false;
        } else {
            for(String name : unitHomes.keySet()) {
                if(!unitHomes.get(name).getAbsolutePath().equals(other.unitHomes.get(name).getAbsolutePath())) {
                    return false;
                }
            }
        }
        if (unitInfos == null) {
            if (other.unitInfos != null)
                return false;
        } else if (!unitInfos.equals(other.unitInfos))
            return false;
        if (unitUpdatePolicies == null) {
            if (other.unitUpdatePolicies != null)
                return false;
        } else if (!unitUpdatePolicies.equals(other.unitUpdatePolicies)) {
            return false;
        }
        return true;
    }
}
