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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author Alexey Loubyansky
 */
public interface ProvisionEnvironment {

    //java.util.Date getLastModifiedDate();

    File getInstallationHome();

    Set<String> getUnitNames();

    File getUnitHome(String unitName);

    File resolveNamedLocation(String namedLocation) throws ProvisionException;

    UnitUpdatePolicy getDefaultUnitUpdatePolicy();

    UnitUpdatePolicy getUnitUpdatePolicy(String unitName);

    class Builder {

        public static Builder create() {
            return new Builder();
        }

        private File installationHome;
        private Map<String, File> paths = Collections.emptyMap();
        private Map<String, File> unitHomes = Collections.emptyMap();
        private UnitUpdatePolicy defaultUnitUpdatePolicy = UnitUpdatePolicy.UNIT_FORCED_CONTENT_CONDITIONED;
        private Map<String, UnitUpdatePolicy> unitUpdatePolicies = Collections.emptyMap();
        private Set<String> unitNames = Collections.emptySet();

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

        public Builder setUnitHome(String unitName, File homeDir) {
            switch(unitHomes.size()) {
                case 0:
                    unitHomes = Collections.singletonMap(unitName, homeDir);
                    break;
                case 1:
                    unitHomes = new HashMap<String, File>(unitHomes);
                default:
                    unitHomes.put(unitName, homeDir);
            }
            addUnitName(unitName);
            return this;
        }

        public Builder setDefaultUnitUpdatePolicy(UnitUpdatePolicy unitPolicy) {
            this.defaultUnitUpdatePolicy = unitPolicy;
            return this;
        }

        public Builder setUnitUpdatePolicy(String unitName, UnitUpdatePolicy updatePolicy) {
            switch(unitUpdatePolicies.size()) {
                case 0:
                    unitUpdatePolicies = Collections.singletonMap(unitName, updatePolicy);
                    break;
                case 1:
                    unitUpdatePolicies = new HashMap<String, UnitUpdatePolicy>(unitUpdatePolicies);
                default:
                    unitUpdatePolicies.put(unitName, updatePolicy);
            }
            addUnitName(unitName);
            return this;
        }

        private void addUnitName(String unitName) {
            if(unitNames.contains(unitName)) {
                return;
            }
            switch(unitNames.size()) {
                case 0:
                    unitNames = Collections.singleton(unitName);
                    break;
                case 1:
                    unitNames = new HashSet<String>(unitNames);
                default:
                    unitNames.add(unitName);
            }
        }

        public ProvisionEnvironment build() {
            return new ProvisionEnvImpl(this);
        }

        class ProvisionEnvImpl implements ProvisionEnvironment {

            private final File installHome;
            private final Map<String, File> paths;
            private final Map<String, File> unitHomes;
            private final UnitUpdatePolicy defaultUnitUpdatePolicy;
            private final Map<String, UnitUpdatePolicy> unitUpdatePolicies;
            private final Set<String> unitNames;

            public ProvisionEnvImpl(Builder builder) {
                assert builder.installationHome != null : ProvisionErrors.nullArgument("home");
                assert builder.paths != null : ProvisionErrors.nullArgument("paths");
                assert builder.unitHomes != null : ProvisionErrors.nullArgument("unitHome");
                assert builder.defaultUnitUpdatePolicy != null : ProvisionErrors.nullArgument("defaultUnitUpdatePolicy");
                assert builder.unitUpdatePolicies != null : ProvisionErrors.nullArgument("unitUpdatePolicies");
                this.installHome = builder.installationHome;
                this.paths = builder.paths;
                this.unitHomes = builder.unitHomes;
                this.defaultUnitUpdatePolicy = builder.defaultUnitUpdatePolicy;
                this.unitUpdatePolicies = builder.unitUpdatePolicies;
                this.unitNames = builder.unitNames;
            }

            @Override
            public File resolveNamedLocation(String namedLocation) throws ProvisionException {
                final File f = paths.get(namedLocation);
                if(f == null) {
                    throw ProvisionErrors.undefinedNamedLocation(namedLocation);
                }
                return f;
            }

            @Override
            public File getInstallationHome() {
                return installHome;
            }

            @Override
            public File getUnitHome(String unitName) {
                assert unitName != null : ProvisionErrors.nullArgument("unitName");
                return unitHomes.get(unitName);
            }

            @Override
            public UnitUpdatePolicy getDefaultUnitUpdatePolicy() {
                return defaultUnitUpdatePolicy;
            }

            @Override
            public UnitUpdatePolicy getUnitUpdatePolicy(String unitName) {
                assert unitName != null : ProvisionErrors.nullArgument("unitName");
                final UnitUpdatePolicy updatePolicy = unitUpdatePolicies.get(unitName);
                return updatePolicy == null ? defaultUnitUpdatePolicy : updatePolicy;
            }

            @Override
            public Set<String> getUnitNames() {
                return unitNames;
            }
        }
    }
}
