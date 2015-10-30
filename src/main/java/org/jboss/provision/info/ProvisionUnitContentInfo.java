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

package org.jboss.provision.info;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.jboss.provision.ProvisionErrors;

/**
 *
 * @author Alexey Loubyansky
 */
public interface ProvisionUnitContentInfo extends ProvisionUnitInfo {

    class Builder {

        public static Builder forUnit(String name, String version) {
            return new Builder(name, version);
        }

        private final String name;
        private final String version;
        private Map<ContentPath, ContentItemInfo> content = Collections.emptyMap();

        private Builder(String name, String version) {
            assert name != null : "name is null";
            assert version != null : "version is null";
            this.name = name;
            this.version = version;
        };

        public Builder add(ContentItemInfo item) {
            assert item != null : "item is null";
            switch(content.size()) {
                case 0:
                    content = Collections.singletonMap(item.getPath(), item);
                    break;
                case 1:
                    content = new HashMap<ContentPath, ContentItemInfo>(content);
                default:
                    content.put(item.getPath(), item);
            }
            return this;
        }

        public ProvisionUnitContentInfo build() {
            return new ProvisionUnitContentInfo() {
                public String getName() {
                    return name;
                }

                public String getVersion() {
                    return version;
                }

                public Set<ContentPath> getPaths() {
                    return content.keySet();
                }

                public ContentItemInfo getContentInfo(ContentPath path) {
                    assert path != null : ProvisionErrors.nullArgument("path");
                    return content.get(path);
                }

                public Collection<ContentItemInfo> getContentInfo() {
                    return content.values();
                }
            };
        }
    };

    Set<ContentPath> getPaths();

    Collection<ContentItemInfo> getContentInfo();

    ContentItemInfo getContentInfo(ContentPath path);
}
