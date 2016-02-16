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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.jboss.provision.ProvisionErrors;
import org.jboss.provision.info.ContentItemInfo;
import org.jboss.provision.info.ContentPath;

/**
 *
 * @author Alexey Loubyansky
 */
public class ContentItemInstruction extends ContentItemInfo {

    protected final byte[] replacedHash;
    protected boolean required = true;

    protected final List<InstructionCondition> conditions;

    protected ContentItemInstruction(ContentPath path, byte[] hash, byte[] replacedHash, boolean required,
            List<InstructionCondition> conditions) {
        super(path, hash);
        this.replacedHash = replacedHash;
        this.required = required;
        this.conditions = conditions;
    }

    public byte[] getReplacedHash() {
        return replacedHash;
    }

    public List<InstructionCondition> getConditions() {
        return conditions;
    }

    public boolean isRequired() {
        return required;
    }

    public ContentItemInstruction getRollback() {
        Builder builder;
        if(replacedHash == null) {
            builder = Builder.removeContent(path, hash);
        } else if(hash == null) {
            builder = Builder.addContent(path, replacedHash);
        } else {
            builder = Builder.replaceContent(path, replacedHash, hash);
        }
        return builder.setRequired(required).build();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((conditions == null) ? 0 : conditions.hashCode());
        result = prime * result + Arrays.hashCode(hash);
        result = prime * result + ((path == null) ? 0 : path.hashCode());
        result = prime * result + Arrays.hashCode(replacedHash);
        result = prime * result + (required ? 1231 : 1237);
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
        ContentItemInstruction other = (ContentItemInstruction) obj;
        if (conditions == null) {
            if (other.conditions != null)
                return false;
        } else if (!conditions.equals(other.conditions))
            return false;
        if (!Arrays.equals(hash, other.hash))
            return false;
        if (path == null) {
            if (other.path != null)
                return false;
        } else if (!path.equals(other.path))
            return false;
        if (!Arrays.equals(replacedHash, other.replacedHash))
            return false;
        if (required != other.required)
            return false;
        return true;
    }

    public static class Builder {

        private final ContentPath path;
        private final byte[] hash;
        private final byte[] replacedHash;
        private boolean required = true;

        private List<InstructionCondition> conditions = Collections.emptyList();

        private Builder(ContentPath path, byte[] hash, byte[] replacedHash) {
            assert path != null : ProvisionErrors.nullArgument("path");
            this.path = path;
            this.hash = hash;
            this.replacedHash = replacedHash;
            conditions = Collections.<InstructionCondition>singletonList(new ContentHashCondition(path, replacedHash, hash));
        }

        public static Builder addContent(ContentPath path, byte[] hash) {
            return new Builder(path, hash, null);
        }

        public static Builder removeContent(ContentPath path, byte[] replacedHash) {
            return new Builder(path, null, replacedHash);
        }

        public static Builder replaceContent(ContentPath path, byte[] hash, byte[] replacedHash) {
            return new Builder(path, hash, replacedHash);
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

        public ContentItemInstruction build() {
            return new ContentItemInstruction(path, hash, replacedHash, required, conditions);
        }
    }
}
