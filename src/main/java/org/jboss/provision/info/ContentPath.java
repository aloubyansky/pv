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

    public static ContentPath create(final String relativePath) {
        return create(null, relativePath);
    }

    public static ContentPath create(final String namedLocation, final String relativePath) {
        return new ContentPath(namedLocation, relativePath){};
    }

    private final String namedLocation;
    private final String relativePath;

    protected ContentPath(String namedLocation, String relativePath) {
        assert relativePath != null : ProvisionErrors.nullArgument("relativePath");
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
        return buf.append('$').append(namedLocation).append('/').append(relativePath).toString();
    }
}
