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

package org.jboss.provision.instruction;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jboss.provision.ProvisionErrors;
import org.jboss.provision.ProvisionException;
import org.jboss.provision.info.ContentPath;
import org.jboss.provision.util.HashUtils;

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
            final String version = unitInstr.getRequiredVersion();
            final String resultingVersion = unitInstr.getResultingVersion();
            final ProvisionUnitInstruction.Builder unitInstBuilder;
            if(version == null) {
                unitInstBuilder = ProvisionUnitInstruction.uninstallUnit(unitName, resultingVersion);
            } else if(resultingVersion == null) {
                unitInstBuilder = ProvisionUnitInstruction.installUnit(unitName, version);
            } else if(version.equals(resultingVersion)) {
                unitInstBuilder = ProvisionUnitInstruction.patchUnit(unitName, version, "rollback-" + unitInstr.getId());
            } else {
                unitInstBuilder = ProvisionUnitInstruction.replaceUnit(unitName, resultingVersion, version);
            }
            for(ContentItemInstruction contentInst : unitInstr.getContentInstructions()) {
                unitInstBuilder.addContentInstruction(contentInst.getRollback());
            }
            builder.add(unitInstBuilder.build());
        }
        return builder.build();
    }

    public ProvisionEnvironmentInstruction mergeWith(ProvisionEnvironmentInstruction override) throws ProvisionException {
        final Builder builder = builder();
        final Set<String> addedNames = new HashSet<String>(override.getUnitNames());
        for(String unitName : getUnitNames()) {
            if(addedNames.remove(unitName)) {
                final ProvisionUnitInstruction unit = getUnitInstruction(unitName);
                final ProvisionUnitInstruction overrideUnit = override.getUnitInstruction(unitName);

                String requiredVersion = unit.getRequiredVersion();
                String resultingVersion = overrideUnit.getResultingVersion();
                if(requiredVersion == null && resultingVersion == null) {
                    continue;
                }

                final String originalResultingVersion = unit.getRequiredVersion();
                final String overrideRequiredVersion = overrideUnit.getRequiredVersion();
                if(originalResultingVersion == null) {
                    if(overrideRequiredVersion != null) {
                        throw ProvisionErrors.unitIsNotInstalled(unitName);
                    }
                } else if(!originalResultingVersion.equals(overrideRequiredVersion)) {
                    throw ProvisionErrors.unitVersionMismatch(unitName, originalResultingVersion, overrideRequiredVersion);
                }

                final ProvisionUnitInstruction.Builder unitInstBuilder;
                if(requiredVersion == null) {
                    // install
                    unitInstBuilder = ProvisionUnitInstruction.installUnit(unitName, resultingVersion);
                } else if(resultingVersion == null) {
                    // uninstall
                    unitInstBuilder = ProvisionUnitInstruction.installUnit(unitName, requiredVersion);
                } else if(resultingVersion.equals(requiredVersion)) {
                    if(!requiredVersion.equals(originalResultingVersion)) {
                        continue;
                    }
                    // two patches TODO indicate two patch IDs applied
                    unitInstBuilder = ProvisionUnitInstruction.patchUnit(unitName, requiredVersion, overrideUnit.getId());
                } else if(!requiredVersion.equals(originalResultingVersion)) {
                    if (overrideRequiredVersion.equals(resultingVersion)) {
                        // version update + patch TODO indicate version and applied patch
                        unitInstBuilder = ProvisionUnitInstruction.replaceUnit(unitName, requiredVersion, resultingVersion);
                    } else {
                        // version update
                        unitInstBuilder = ProvisionUnitInstruction.replaceUnit(unitName, requiredVersion, resultingVersion);
                    }
                } else {
                    // patch + version update, which shouldn't happen
                    if(!unit.getId().startsWith("rollback-")) {
                        throw ProvisionErrors.versionUpdateOverPatch(unitName, unit.getId(), overrideUnit.getResultingVersion());
                    }
                    // version update
                    unitInstBuilder = ProvisionUnitInstruction.replaceUnit(unitName, requiredVersion, resultingVersion);
                }

                final Map<ContentPath, ContentItemInstruction> overrideContent = new HashMap<ContentPath, ContentItemInstruction>(overrideUnit.getContentInstructions().size());
                for(ContentItemInstruction instr : overrideUnit.getContentInstructions()) {
                    overrideContent.put(instr.getPath(), instr);
                }
                for(ContentItemInstruction instr : unit.getContentInstructions()) {
                    final ContentItemInstruction overrideInstr = overrideContent.remove(instr.getPath());
                    if(overrideInstr == null) {
                        unitInstBuilder.addContentInstruction(instr);
                    } else {
                        if(instr.getReplacedHash() == null && overrideInstr.getContentHash() == null) {
                            continue;
                        }
                        if (instr.getContentHash() != null && overrideInstr.getReplacedHash() != null
                                && !Arrays.equals(instr.getContentHash(), overrideInstr.getReplacedHash())) {
                            throw ProvisionErrors.pathHashMismatch(instr.getPath(),
                                    HashUtils.bytesToHexString(overrideInstr.getReplacedHash()),
                                    HashUtils.bytesToHexString(instr.getContentHash()));
                        }
                        if(instr.getReplacedHash() == null) {
                            unitInstBuilder.addContentInstruction(ContentItemInstruction.Builder.addContent(instr.getPath(), overrideInstr.getContentHash()).build());
                            continue;
                        }
                        if(overrideInstr.getContentHash() == null) {
                            unitInstBuilder.addContentInstruction(ContentItemInstruction.Builder.removeContent(instr.getPath(), instr.getReplacedHash()).build());
                            continue;
                        }
                        if(Arrays.equals(instr.getReplacedHash(), overrideInstr.getContentHash())) {
                            continue;
                        }
                        unitInstBuilder.addContentInstruction(ContentItemInstruction.Builder.replaceContent(instr.getPath(), overrideInstr.getContentHash(), instr.getReplacedHash()).build());
                    }
                }
                if(!overrideContent.isEmpty()) {
                    for(ContentItemInstruction instr : overrideContent.values()) {
                        unitInstBuilder.addContentInstruction(instr);
                    }
                }
                builder.add(unitInstBuilder.build());
            } else {
                builder.add(getUnitInstruction(unitName));
            }
        }
        if(!addedNames.isEmpty()) {
            for (String unitName : addedNames) {
                builder.add(override.getUnitInstruction(unitName));
            }
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

        public Builder add(ProvisionUnitInstruction unitInstr) {
            assert unitInstr != null : ProvisionErrors.nullArgument("unit");
            switch(units.size()) {
                case 0:
                    units = Collections.singletonMap(unitInstr.getUnitName(), unitInstr);
                    break;
                case 1:
                    units = new HashMap<String, ProvisionUnitInstruction>(units);
                default:
                    units.put(unitInstr.getUnitName(), unitInstr);
            }
            return this;
        }
    }
}
