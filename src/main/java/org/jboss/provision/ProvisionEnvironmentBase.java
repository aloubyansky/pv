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

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jboss.provision.info.ContentPath;

/**
 *
 * @author Alexey Loubyansky
 */
abstract class ProvisionEnvironmentBase {

    private final ProvisionEnvironmentBase parentEnv;
    private final Map<String, ContentPath> namedLocations;
    private final UnitUpdatePolicy updatePolicy;

    ProvisionEnvironmentBase(Map<String, ContentPath> namedLocations, UnitUpdatePolicy updatePolicy) {
        this(null, namedLocations, updatePolicy);
    }

    ProvisionEnvironmentBase(ProvisionEnvironmentBase parentEnv, Map<String, ContentPath> namedLocations, UnitUpdatePolicy updatePolicy) {
        assert namedLocations != null : ProvisionErrors.nullArgument("namedLocations");
        this.parentEnv = parentEnv;
        this.namedLocations = namedLocations;
        this.updatePolicy = updatePolicy;
    }

    protected ProvisionEnvironmentBase getParentEnv() {
        return parentEnv;
    }

    public abstract File getEnvironmentHome() throws ProvisionException;

    public Set<String> getLocationNames() {
        return getLocationNames(true);
    }

    public Set<String> getLocationNames(boolean inherited) {
        if(!inherited || parentEnv == null) {
            return namedLocations.keySet();
        }
        final Set<String> parentNames = parentEnv.getLocationNames(inherited);
        if(parentNames.isEmpty()) {
            return namedLocations.keySet();
        }
        final Set<String> localNames = namedLocations.keySet();
        if(localNames.isEmpty()) {
            return parentNames;
        }
        final Set<String> all = new HashSet<String>(parentNames.size() + localNames.size());
        all.addAll(parentNames);
        all.addAll(localNames);
        return all;
    }

    public ContentPath getNamedLocation(String locationName) {
        assert locationName != null : ProvisionErrors.nullArgument("locationName");
        final ContentPath path = namedLocations.get(locationName);
        if(path != null) {
            return path;
        }
        return parentEnv == null ? null : parentEnv.getNamedLocation(locationName);
    }

    public File resolveNamedLocation(String namedLocation) throws ProvisionException {
        ContentPath path = getNamedLocation(namedLocation);
        if(path == null) {
            throw ProvisionErrors.undefinedNamedLocation(namedLocation);
        }
        File f = getEnvironmentHome();
        if(path.getLocationName() != null) {
            f = resolveNamedLocation(path.getLocationName());
        }
        final String relativePath = path.getFSRelativePath();
        if(relativePath == null) {
            return f;
        }
        return new File(f, relativePath);
    }

    public File resolvePath(ContentPath path) throws ProvisionException {
        final String locationName = path.getLocationName();
        if(locationName == null) {
            return new File(getEnvironmentHome(), path.getFSRelativePath());
        }
        final ContentPath namedPath = getNamedLocation(locationName);
        if(namedPath == null) {
            throw ProvisionErrors.undefinedNamedLocation(locationName);
        }
        final File f = resolvePath(namedPath);
        String relativePath = path.getFSRelativePath();
        if(relativePath == null) {
            return f;
        }
        return new File(f, relativePath);
    }

    public UnitUpdatePolicy getUpdatePolicy() {
        return updatePolicy;
    }

    public UnitUpdatePolicy resolveUpdatePolicy() {
        if(updatePolicy != null) {
            return updatePolicy;
        }
        return parentEnv == null ? null : parentEnv.resolveUpdatePolicy();
    }

    @Override
    public int hashCode() {
        File envHome;
        try {
            envHome = getEnvironmentHome();
        } catch (ProvisionException e) {
            throw new IllegalStateException("Failed to calculate hash", e);
        }
        final UnitUpdatePolicy updatePolicy = resolveUpdatePolicy();
        final Map<String, ContentPath> namedLocations = getNamedLocations();

        final int prime = 31;
        int result = 1;
        result = prime * result + ((envHome == null) ? 0 : envHome.hashCode());
        result = prime * result + namedLocations.hashCode();
        result = prime * result + ((updatePolicy == null) ? 0 : updatePolicy.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof ProvisionEnvironmentBase)) {
            return false;
        }
        ProvisionEnvironmentBase other = (ProvisionEnvironmentBase) obj;

        try {
            final File envHome = getEnvironmentHome();
            if (envHome == null) {
                if (other.getEnvironmentHome() != null) {
                    return false;
                }
            }
            final File otherHome = other.getEnvironmentHome();
            if(otherHome == null) {
                return false;
            }
            if (!envHome.getAbsolutePath().equals(otherHome.getAbsolutePath())) {
                return false;
            }
        } catch (ProvisionException e) {
            throw new IllegalStateException("Failed to resolve environment home dir", e);
        }

        if (!getNamedLocations().equals(other.getNamedLocations())) {
            return false;
        }

        final UnitUpdatePolicy updatePolicy = resolveUpdatePolicy();
        final UnitUpdatePolicy otherUpdatePolicy = other.resolveUpdatePolicy();
        if (updatePolicy == null) {
            if (otherUpdatePolicy != null) {
                return false;
            }
        } else if (!updatePolicy.equals(otherUpdatePolicy)) {
            return false;
        }
        return true;
    }

    protected Map<String, ContentPath> getNamedLocations() {
        Map<String, ContentPath> namedLocations;
        final Set<String> locationNames = getLocationNames(true);
        switch(locationNames.size()) {
            case 0:
                namedLocations = Collections.emptyMap();
                break;
            case 1:
                final String locationName = locationNames.iterator().next();
                namedLocations = Collections.singletonMap(locationName, getNamedLocation(locationName));
                break;
            default:
                namedLocations = new HashMap<String, ContentPath>(locationNames.size());
                for(String name : locationNames) {
                    namedLocations.put(name, getNamedLocation(name));
                }
        }
        return namedLocations;
    }
}
