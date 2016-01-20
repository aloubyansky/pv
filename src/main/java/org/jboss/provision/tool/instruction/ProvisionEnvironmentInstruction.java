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

package org.jboss.provision.tool.instruction;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.jboss.provision.ProvisionErrors;
import org.jboss.provision.ProvisionException;

/**
 *
 * @author Alexey Loubyansky
 */
public class ProvisionEnvironmentInstruction {

    public static Builder builder() {
        return new Builder();
    }

    protected final Map<String, ProvisionUnitInstruction> units;

    protected ProvisionEnvironmentInstruction(Map<String, ProvisionUnitInstruction> units) {
        assert units != null : ProvisionErrors.nullArgument("units");
        this.units = units;
    }

    public Set<String> getUnitNames() {
        return units.keySet();
    }

    public ProvisionUnitInstruction getUnitInstruction(String unitName) {
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
        ProvisionEnvironmentInstruction other = (ProvisionEnvironmentInstruction) obj;
        if (units == null) {
            if (other.units != null)
                return false;
        } else if (!units.equals(other.units))
            return false;
        return true;
    }

    public ProvisionEnvironmentInstruction getRollback() throws ProvisionException {
        final Builder builder = builder();
        for(String unitName : getUnitNames()) {
            final ProvisionUnitInstruction unitInstr = getUnitInstruction(unitName);
            final String replacedVersion = unitInstr.getReplacedVersion();
            final String version = unitInstr.getVersion();
            final ProvisionUnitInstruction.Builder unitInstBuilder;
            if(replacedVersion == null) {
                unitInstBuilder = ProvisionUnitInstruction.uninstallUnit(unitName, unitInstr.getVersion());
            } else if(version == null) {
                unitInstBuilder = ProvisionUnitInstruction.installUnit(unitName, unitInstr.getVersion());
            } else if(replacedVersion.equals(version)) {
                unitInstBuilder = ProvisionUnitInstruction.patchUnit(unitName, unitInstr.getVersion(), "rollback-" + unitInstr.getId());
            } else {
                unitInstBuilder = ProvisionUnitInstruction.replaceUnit(unitName, replacedVersion, unitInstr.getVersion());
            }
            for(ContentItemInstruction contentInst : unitInstr.getContentInstructions()) {
                unitInstBuilder.addContentInstruction(contentInst.getRollback());
            }
            builder.add(unitInstBuilder.build());
        }
        return builder.build();
    }

    public static class Builder {

        private Map<String, ProvisionUnitInstruction> units = Collections.<String, ProvisionUnitInstruction>emptyMap();

        private Builder() {
        }

        public ProvisionEnvironmentInstruction build() {
            return new ProvisionEnvironmentInstruction(units);
        }

        public Builder add(ProvisionUnitInstruction unit) {
            assert unit != null : ProvisionErrors.nullArgument("unit");
            switch(units.size()) {
                case 0:
                    units = Collections.singletonMap(unit.getName(), unit);
                    break;
                case 1:
                    units = new HashMap<String, ProvisionUnitInstruction>(units);
                default:
                    units.put(unit.getName(), unit);
            }
            return this;
        }
    }
}
