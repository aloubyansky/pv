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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jboss.provision.ProvisionErrors;
import org.jboss.provision.info.ProvisionUnitInfo;

/**
 *
 * @author Alexey Loubyansky
 */
public abstract class ProvisionUnitInstruction extends ProvisionUnitInfo {

    public static Builder installUnit(String name, String version) {
        return new Builder(name, version, null, null);
    }

    public static Builder uninstallUnit(String name, String version) {
        return new Builder(name, null, version, null);
    }

    public static Builder replaceUnit(String name, String version, String replacedVersion) {
        return new Builder(name, version, replacedVersion, null);
    }

    public static Builder patchUnit(String name, String version, String patchId) {
        assert patchId != null : ProvisionErrors.nullArgument("patchId");
        return new Builder(name, version, version, patchId);
    }

    public static class Builder {

        private final String id;
        private final String name;
        private final String version;
        private final String replacedVersion;
        private boolean required = true;

        private List<InstructionCondition> conditions;
        private List<ContentItemInstruction> contentInstructions = Collections.emptyList();
        private List<IntegrationTask> integrationTasks = Collections.emptyList();

        private Builder(String name, String version, String replacedVersion, String id) {
            assert name != null : ProvisionErrors.nullArgument("name");
            this.id = id;
            this.name = name;
            this.version = version;
            this.replacedVersion = replacedVersion;
            conditions = Collections.<InstructionCondition>singletonList(new UnitVersionCondition(name, replacedVersion));
        }

        public Builder setRequired(boolean required) {
            this.required = required;
            return this;
        }

        public Builder addCondition(InstructionCondition condition) {
            assert condition != null : ProvisionErrors.nullArgument("condition");
            if(conditions.size() == 1) {
                conditions = new ArrayList<InstructionCondition>(conditions);
            }
            conditions.add(condition);
            return this;
        }

        public Builder addContentInstruction(ContentItemInstruction contentInstruction) {
            assert contentInstruction != null : ProvisionErrors.nullArgument("contentInstruction");
            switch(contentInstructions.size()) {
                case 0:
                    contentInstructions = Collections.singletonList(contentInstruction);
                    break;
                case 1:
                    contentInstructions = new ArrayList<ContentItemInstruction>(contentInstructions);
                default:
                    contentInstructions.add(contentInstruction);
            }
            return this;
        }

        public Builder addIntegrationTask(IntegrationTask integrationTask) {
            assert integrationTask != null : ProvisionErrors.nullArgument("integrationTask");
            switch(integrationTasks.size()) {
                case 0:
                    integrationTasks = Collections.singletonList(integrationTask);
                    break;
                case 1:
                    integrationTasks = new ArrayList<IntegrationTask>(integrationTasks);
                default:
                    integrationTasks.add(integrationTask);
            }
            return this;
        }

        public ProvisionUnitInstruction build() {
            return new ProvisionUnitInstruction(id, name, version, replacedVersion, required, conditions, contentInstructions, integrationTasks) {};
        }
    }

    protected ProvisionUnitInstruction(String id, String name, String version, String replacedVersion,
            boolean required, List<InstructionCondition> conditions, List<ContentItemInstruction> contentInstructions,
            List<IntegrationTask> integrationTasks) {
        super(name, version);
        this.id = id;
        this.replacedVersion = replacedVersion;
        this.required = required;
        this.conditions = conditions;
        this.contentInstructions = contentInstructions;
        this.integrationTasks = integrationTasks;
    }

    private final String id;
    private final String replacedVersion;
    private boolean required = true;
    private final List<InstructionCondition> conditions;
    private final List<ContentItemInstruction> contentInstructions;
    private final List<IntegrationTask> integrationTasks;

    public String getId() {
        return id;
    }

    public String getReplacedVersion() {
        return replacedVersion;
    }

    public List<InstructionCondition> getConditions() {
        return conditions;
    }

    public boolean isRequired() {
        return required;
    }

    public List<IntegrationTask> getIntegrationTasks() {
        return integrationTasks;
    }

    public List<ContentItemInstruction> getContentInstructions() {
        return contentInstructions;
    }

    public ProvisionUnitInfo getReplacedUnitInfo() {
        return ProvisionUnitInfo.createInfo(name, replacedVersion);
    }

    public UnitInstructionType getType() {
        if(this.replacedVersion == null) {
            return UnitInstructionType.INSTALL;
        }
        if(this.version == null) {
            return UnitInstructionType.UNINSTALL;
        }
        if(replacedVersion.equals(version)) {
            return UnitInstructionType.PATCH;
        }
        return UnitInstructionType.REPLACE;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((conditions == null) ? 0 : conditions.hashCode());
        result = prime * result + ((contentInstructions == null) ? 0 : contentInstructions.hashCode());
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        result = prime * result + ((integrationTasks == null) ? 0 : integrationTasks.hashCode());
        result = prime * result + ((replacedVersion == null) ? 0 : replacedVersion.hashCode());
        result = prime * result + (required ? 1231 : 1237);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (!(obj instanceof ProvisionUnitInstruction))
            return false;
        ProvisionUnitInstruction other = (ProvisionUnitInstruction) obj;
        if (conditions == null) {
            if (other.conditions != null)
                return false;
        } else if (!conditions.equals(other.conditions))
            return false;
        if (contentInstructions == null) {
            if (other.contentInstructions != null)
                return false;
        } else if (!contentInstructions.equals(other.contentInstructions))
            return false;
        if (id == null) {
            if (other.id != null)
                return false;
        } else if (!id.equals(other.id))
            return false;
        if (integrationTasks == null) {
            if (other.integrationTasks != null)
                return false;
        } else if (!integrationTasks.equals(other.integrationTasks))
            return false;
        if (replacedVersion == null) {
            if (other.replacedVersion != null)
                return false;
        } else if (!replacedVersion.equals(other.replacedVersion))
            return false;
        if (required != other.required)
            return false;
        return true;
    }
}
