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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.jboss.provision.tool.instruction.UpdatePolicy;

/**
 *
 * @author Alexey Loubyansky
 */
public abstract class UnitUpdatePolicy {

    public static final UnitUpdatePolicy UNIT_FORCED_CONTENT_CONDITIONED = UnitUpdatePolicy.newBuilder()
            .setUnitPolicy(UpdatePolicy.FORCED)
            .setDefaultContentPolicy(UpdatePolicy.CONDITIONED)
            .build();

    public static final UnitUpdatePolicy FORCED = UnitUpdatePolicy.newBuilder()
            .setUnitPolicy(UpdatePolicy.FORCED)
            .setDefaultContentPolicy(UpdatePolicy.FORCED)
            .build();

    public static final UnitUpdatePolicy CONDITIONED = UnitUpdatePolicy.newBuilder()
            .setUnitPolicy(UpdatePolicy.CONDITIONED)
            .setDefaultContentPolicy(UpdatePolicy.CONDITIONED)
            .build();

    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder {

        private UpdatePolicy unitPolicy;
        private UpdatePolicy defaultContentPolicy;
        private Map<String, UpdatePolicy> pathPolicy = Collections.emptyMap();

        private Builder() {
        }

        public Builder setUnitPolicy(UpdatePolicy unitPolicy) {
            this.unitPolicy = unitPolicy;
            return this;
        }

        public Builder setDefaultContentPolicy(UpdatePolicy policy) {
            this.defaultContentPolicy = policy;
            return this;
        }

        public Builder setPolicy(String path, UpdatePolicy policy) {
            switch(pathPolicy.size()) {
                case 0:
                    pathPolicy = Collections.singletonMap(path, policy);
                    break;
                case 1:
                    pathPolicy = new HashMap<String, UpdatePolicy>(pathPolicy);
                default:
                    pathPolicy.put(path, policy);
            }
            return this;
        }

        public UnitUpdatePolicy build() {
            return new UnitUpdatePolicy(unitPolicy, defaultContentPolicy, pathPolicy) {};
        }
    }

    protected final UpdatePolicy unitPolicy;
    protected final UpdatePolicy defaultContentPolicy;
    protected final Map<String, UpdatePolicy> pathPolicy;

    protected UnitUpdatePolicy(UpdatePolicy unitPolicy, UpdatePolicy defaultContentPolicy, Map<String, UpdatePolicy> pathPolicy) {
        assert unitPolicy != null : ProvisionErrors.nullArgument("unitPolicy");
        assert defaultContentPolicy != null : ProvisionErrors.nullArgument("defaultContentPolicy");
        this.unitPolicy = unitPolicy;
        this.defaultContentPolicy = defaultContentPolicy;
        this.pathPolicy = pathPolicy;
    }

    public UpdatePolicy getUnitPolicy() {
        return unitPolicy;
    }

    public UpdatePolicy getDefaultContentPolicy() {
        return defaultContentPolicy;
    }

    public Set<String> getPaths() {
        return pathPolicy == null ? Collections.<String>emptySet() : pathPolicy.keySet();
    }

    public UpdatePolicy getContentPolicy(String path) {
        if(pathPolicy == null) {
            return defaultContentPolicy;
        }
        final UpdatePolicy policy = pathPolicy.get(path);
        return policy == null ? defaultContentPolicy : policy;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((defaultContentPolicy == null) ? 0 : defaultContentPolicy.hashCode());
        result = prime * result + ((pathPolicy == null) ? 0 : pathPolicy.hashCode());
        result = prime * result + ((unitPolicy == null) ? 0 : unitPolicy.hashCode());
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
        UnitUpdatePolicy other = (UnitUpdatePolicy) obj;
        if (defaultContentPolicy != other.defaultContentPolicy)
            return false;
        if (pathPolicy == null) {
            if (other.pathPolicy != null)
                return false;
        } else if (!pathPolicy.equals(other.pathPolicy))
            return false;
        if (unitPolicy != other.unitPolicy) {
            return false;
        }
        return true;
    }
}
