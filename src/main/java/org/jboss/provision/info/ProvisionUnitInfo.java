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

import java.util.Collections;
import java.util.List;

import org.jboss.provision.ProvisionErrors;


/**
 * Represents target unit installation.
 *
 * @author Alexey Loubyansky
 */
public class ProvisionUnitInfo {

    public static final String UNDEFINED_NAME = "UNDEFINED";
    public static final String UNDEFINED_VERSION = UNDEFINED_NAME;

    public static final ProvisionUnitInfo UNDEFINED_INFO = createInfo(UNDEFINED_NAME, UNDEFINED_VERSION);

    public static ProvisionUnitInfo createInfo(String name, String version) {
        return new ProvisionUnitInfo(name, version, Collections.<String>emptyList());
    }

    public static ProvisionUnitInfo createInfo(String name, String version, List<String> patches) {
        return new ProvisionUnitInfo(name, version, patches);
    }

    protected final String name;
    protected final String version;
    protected final List<String> patches;

    protected ProvisionUnitInfo(String name, String version, List<String> patches) {
        assert name != null : ProvisionErrors.nullArgument("name");
        assert patches != null : ProvisionErrors.nullArgument("patches");
        this.name = name;
        this.version = version;
        this.patches = patches;
    }
    /**
     * Name of the unit.
     *
     * @return  unit name
     */
    public String getName() {
        return name;
    }

    /**
     * Version of the unit.
     *
     * @return  unit version
     */
    public String getVersion() {
        return version;
    }

    public List<String> getPatches() {
        return patches;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + name.hashCode();
        result = prime * result + ((version == null) ? 0 : version.hashCode());
        result = prime * result + patches.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (!(obj instanceof ProvisionUnitInfo))
            return false;
        ProvisionUnitInfo other = (ProvisionUnitInfo) obj;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        if (version == null) {
            if (other.version != null)
                return false;
        } else if (!version.equals(other.version)) {
            return false;
        }
        if (!patches.equals(other.patches)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "ProvisionUnitInfo [name=" + name + ", version=" + version + ", patches=" + patches + "]";
    }
}
