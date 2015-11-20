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
public abstract class ProvisionEnvironment {

    public static Builder create() {
        return new Builder();
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

        public ProvisionEnvironment build() {
            return new ProvisionEnvironment(this){};
        }
    }

    protected ProvisionEnvironment(Builder builder) {
        assert builder.envHome != null : ProvisionErrors.nullArgument("envHome");
        assert builder.namedLocations != null : ProvisionErrors.nullArgument("namedLocations");
        assert builder.unitHomes != null : ProvisionErrors.nullArgument("unitHome");
        assert builder.defaultUnitUpdatePolicy != null : ProvisionErrors.nullArgument("defaultUnitUpdatePolicy");
        assert builder.unitUpdatePolicies != null : ProvisionErrors.nullArgument("unitUpdatePolicies");
        this.envHome = builder.envHome;
        this.namedLocations = builder.namedLocations;
        this.unitHomes = builder.unitHomes;
        this.defaultUnitPolicy = builder.defaultUnitUpdatePolicy;
        this.unitUpdatePolicies = builder.unitUpdatePolicies;
        this.unitInfos = builder.unitInfos;
    }

    private final File envHome;
    private final Map<String, ContentPath> namedLocations;
    private final Map<String, ContentPath> unitHomes;
    private final UnitUpdatePolicy defaultUnitPolicy;
    private final Map<String, UnitUpdatePolicy> unitUpdatePolicies;
    private final Map<String, ProvisionUnitInfo> unitInfos;

    //java.util.Date getLastModifiedDate();

    public File getEnvironmentHome() {
        return envHome;
    }

    public Set<String> getUnitNames() {
        return unitInfos.keySet();
    }

    public ProvisionUnitInfo getUnitInfo(String unitName) {
        assert unitName != null : ProvisionErrors.nullArgument("unitName");
        return unitInfos.get(unitName);
    }

    public ContentPath getUnitHome(String unitName) {
        assert unitName != null : ProvisionErrors.nullArgument("unitName");
        return unitHomes.get(unitName);
    }

    public Set<String> getLocationNames() {
        return namedLocations.keySet();
    }

    public ContentPath getNamedLocation(String namedLocation) {
        return namedLocations.get(namedLocation);
    }

    public File resolveNamedLocation(String namedLocation) throws ProvisionException {
        ContentPath path = namedLocations.get(namedLocation);
        if(path == null) {
            throw ProvisionErrors.undefinedNamedLocation(namedLocation);
        }

        File f = envHome;
        if(path.getNamedLocation() != null) {
            f = resolveNamedLocation(path.getNamedLocation());
        }

        String relativePath = path.getRelativePath();
        if(relativePath == null) {
            return f;
        }
        if(File.separatorChar == '\\') {
            relativePath = relativePath.replace('/', '\\');
        }

        return new File(f, relativePath);
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
        result = prime * result + ((envHome == null) ? 0 : envHome.hashCode());
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
        if (!(obj instanceof ProvisionEnvironment))
            return false;
        ProvisionEnvironment other = (ProvisionEnvironment) obj;
        if (defaultUnitPolicy == null) {
            if (other.defaultUnitPolicy != null)
                return false;
        } else if (!defaultUnitPolicy.equals(other.defaultUnitPolicy))
            return false;
        if (envHome == null) {
            if (other.envHome != null)
                return false;
        } else if (!envHome.getAbsolutePath().equals(other.envHome.getAbsolutePath())) {
            return false;
        }
        if (namedLocations == null) {
            if (other.namedLocations != null)
                return false;
        } else if (!namedLocations.equals(other.namedLocations)) {
            return false;
        }
        if (unitHomes == null) {
            if (other.unitHomes != null)
                return false;
        } else if(!unitHomes.equals(other.unitHomes)) {
            return false;
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
