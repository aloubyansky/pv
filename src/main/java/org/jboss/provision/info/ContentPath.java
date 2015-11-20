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

import org.jboss.provision.ProvisionErrors;

/**
 *
 * @author Alexey Loubyansky
 */
public abstract class ContentPath {

    public static ContentPath forPath(final String relativePath) {
        return create(null, relativePath);
    }

    public static ContentPath forName(final String name) {
        return create(name, null);
    }

    public static ContentPath create(final String namedLocation, final String relativePath) {
        return new ContentPath(namedLocation, relativePath){};
    }

    private final String namedLocation;
    private final String relativePath;

    protected ContentPath(String namedLocation, String relativePath) {
        assert relativePath != null || namedLocation != null : ProvisionErrors.nullArgument("relativePath && namedLocation");
        this.namedLocation = namedLocation;
        this.relativePath = relativePath;
    }

    public String getNamedLocation() {
        return namedLocation;
    }

    public String getRelativePath() {
        return relativePath;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((namedLocation == null) ? 0 : namedLocation.hashCode());
        result = prime * result + ((relativePath == null) ? 0 : relativePath.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (!(obj instanceof ContentPath))
            return false;
        ContentPath other = (ContentPath) obj;
        if (namedLocation == null) {
            if (other.namedLocation != null)
                return false;
        } else if (!namedLocation.equals(other.namedLocation))
            return false;
        if (relativePath == null) {
            if (other.relativePath != null)
                return false;
        } else if (!relativePath.equals(other.relativePath))
            return false;
        return true;
    }

    @Override
    public String toString() {
        if(namedLocation == null) {
            return relativePath;
        }
        final StringBuilder buf = new StringBuilder();
        buf.append('$').append(namedLocation);
        if(relativePath == null) {
            return buf.toString();
        }
        return buf.append('/').append(relativePath).toString();
    }

    public static ContentPath fromString(String str) {
        if(str == null) {
            throw new IllegalArgumentException(ProvisionErrors.nullArgument("str"));
        }
        if(str.isEmpty()) {
            throw new IllegalArgumentException("string is empty");
        }
        if(str.charAt(0) != '$') {
            return ContentPath.forPath(str);
        }
        final int i = str.indexOf('/');
        if(i < 0) {
            return ContentPath.forName(str.substring(1));
        }
        final String name = str.substring(1, i);
        if(name.isEmpty()) {
            throw new IllegalStateException("The name is empty in '" + str + "'");
        }
        final String path = str.substring(i + 1);
        if(path.isEmpty()) {
            throw new IllegalStateException("The path is empty in '" + str + "'");
        }
        return ContentPath.create(name, path);
    }
}
