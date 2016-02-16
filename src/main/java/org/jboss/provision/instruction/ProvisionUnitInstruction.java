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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jboss.provision.ProvisionErrors;

/**
 *
 * @author Alexey Loubyansky
 */
public abstract class ProvisionUnitInstruction {

    public static Builder installUnit(String name, String version) {
        assert name != null : ProvisionErrors.nullArgument("name");
        assert version != null : ProvisionErrors.nullArgument("version");
        return new Builder(name, null, version, null);
    }

    public static Builder uninstallUnit(String name, String version) {
        assert name != null : ProvisionErrors.nullArgument("name");
        assert version != null : ProvisionErrors.nullArgument("version");
        return new Builder(name, version, null, null);
    }

    public static Builder replaceUnit(String name, String version, String resultingVersion) {
        assert name != null : ProvisionErrors.nullArgument("name");
        assert version != null : ProvisionErrors.nullArgument("version");
        assert resultingVersion != null : ProvisionErrors.nullArgument("resultingVersion");
        return new Builder(name, version, resultingVersion, null);
    }

    public static Builder patchUnit(String name, String version, String patchId) {
        assert name != null : ProvisionErrors.nullArgument("name");
        assert version != null : ProvisionErrors.nullArgument("version");
        assert patchId != null : ProvisionErrors.nullArgument("patchId");
        return new Builder(name, version, version, patchId);
    }

    public static class Builder {

        private final String id;
        private final String name;
        private final String resultingVersion;
        private final String requiredVersion;
        private boolean required = true;

        private List<InstructionCondition> conditions;
        private List<ContentItemInstruction> contentInstructions = Collections.emptyList();
        private List<IntegrationTask> integrationTasks = Collections.emptyList();

        private Builder(String name, String requiredVersion, String resultingVersion, String id) {
            assert name != null : ProvisionErrors.nullArgument("name");
            this.id = id;
            this.name = name;
            this.requiredVersion = requiredVersion;
            this.resultingVersion = resultingVersion;
            conditions = Collections.<InstructionCondition>singletonList(new UnitVersionCondition(name, requiredVersion));
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
            return new ProvisionUnitInstruction(this) {};
        }
    }

    protected ProvisionUnitInstruction(Builder builder) {
        this.id = builder.id;
        this.unitName = builder.name;
        this.requiredVersion = builder.requiredVersion;
        this.resultingVersion = builder.resultingVersion;
        this.required = builder.required;
        this.conditions = builder.conditions;
        this.contentInstructions = builder.contentInstructions;
        this.integrationTasks = builder.integrationTasks;
    }

    private final String id;
    private final String unitName;
    private final String requiredVersion;
    private final String resultingVersion;
    private boolean required = true;
    private final List<InstructionCondition> conditions;
    private final List<ContentItemInstruction> contentInstructions;
    private final List<IntegrationTask> integrationTasks;

    public String getId() {
        return id;
    }

    public String getUnitName() {
        return unitName;
    }

    public String getRequiredVersion() {
        return requiredVersion;
    }

    public String getResultingVersion() {
        return resultingVersion;
    }

    public boolean isVersionUpdate() {
        return resultingVersion != null && requiredVersion != null && !resultingVersion.equals(requiredVersion);
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

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((conditions == null) ? 0 : conditions.hashCode());
        result = prime * result + ((contentInstructions == null) ? 0 : contentInstructions.hashCode());
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        result = prime * result + ((integrationTasks == null) ? 0 : integrationTasks.hashCode());
        result = prime * result + (required ? 1231 : 1237);
        result = prime * result + ((resultingVersion == null) ? 0 : resultingVersion.hashCode());
        result = prime * result + ((unitName == null) ? 0 : unitName.hashCode());
        result = prime * result + ((requiredVersion == null) ? 0 : requiredVersion.hashCode());
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
        if (required != other.required)
            return false;
        if (resultingVersion == null) {
            if (other.resultingVersion != null)
                return false;
        } else if (!resultingVersion.equals(other.resultingVersion))
            return false;
        if (unitName == null) {
            if (other.unitName != null)
                return false;
        } else if (!unitName.equals(other.unitName))
            return false;
        if (requiredVersion == null) {
            if (other.requiredVersion != null)
                return false;
        } else if (!requiredVersion.equals(other.requiredVersion))
            return false;
        return true;
    }
}
