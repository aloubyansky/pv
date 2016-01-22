/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
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

package org.jboss.provision.info;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.jboss.provision.ProvisionErrors;
import org.jboss.provision.ProvisionException;

/**
 *
 * @author Alexey Loubyansky
 */
public class ProvisionEnvironmentInfo {

    public static ProvisionEnvironmentInfo undefinedUnitEnvironment() {
        return new ProvisionEnvironmentInfo(Collections.singletonMap(ProvisionUnitInfo.UNDEFINED_INFO.getName(), ProvisionUnitInfo.UNDEFINED_INFO));
    }

    public static ProvisionEnvironmentInfo emptyEnvironment() {
        return new ProvisionEnvironmentInfo(Collections.<String, ProvisionUnitInfo>emptyMap());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Map<String, ProvisionUnitInfo> units = Collections.emptyMap();
        private Builder() {}
        public Builder addUnit(ProvisionUnitInfo unitInfo) throws ProvisionException {
            assert unitInfo != null : ProvisionErrors.nullArgument("unitInfo");
            if(units.isEmpty()) {
                units = Collections.singletonMap(unitInfo.getName(), unitInfo);
            } else {
                if(units.containsKey(unitInfo.getName())) {
                    throw ProvisionErrors.unitAlreadyInstalled(unitInfo.getName(), units.get(unitInfo.getName()).getVersion());
                }
                if(units.size() == 1) {
                    units = new HashMap<String, ProvisionUnitInfo>(units);
                }
                units.put(unitInfo.getName(), unitInfo);
            }
            return this;
        }
        public Builder addUnit(String name, String version) throws ProvisionException {
            return addUnit(ProvisionUnitInfo.createInfo(name, version));
        }
        public ProvisionEnvironmentInfo build() {
            return new ProvisionEnvironmentInfo(units);
        }
    }

    private Map<String, ProvisionUnitInfo> units;

    private ProvisionEnvironmentInfo(Map<String, ProvisionUnitInfo> units) {
        assert units != null : ProvisionErrors.nullArgument("units");
        this.units = units;
    }

    public Collection<String> getUnitNames() {
        return units.keySet();
    }

    public ProvisionUnitInfo getUnitInfo(String unitName) {
        assert unitName != null : ProvisionErrors.nullArgument("unitName");
        return units.get(unitName);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((units == null) ? 0 : units.hashCode());
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
        ProvisionEnvironmentInfo other = (ProvisionEnvironmentInfo) obj;
        if (units == null) {
            if (other.units != null)
                return false;
        } else if (!units.equals(other.units))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "[units=" + units + "]";
    }
}
