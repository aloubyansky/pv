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
import java.util.Map;

import org.jboss.provision.info.ContentPath;
import org.jboss.provision.info.ProvisionUnitInfo;

/**
 *
 * @author Alexey Loubyansky
 */
public class ProvisionUnitEnvironment extends ProvisionEnvironmentBase {

    private final ContentPath unitHome;
    private final ProvisionUnitInfo unitInfo;

    ProvisionUnitEnvironment(ProvisionEnvironment parentEnv, ProvisionUnitInfo unitInfo, ContentPath unitHome,
            Map<String, ContentPath> namedLocations, UnitUpdatePolicy updatePolicy) {
        super(parentEnv, namedLocations, updatePolicy);
        assert parentEnv != null : ProvisionErrors.nullArgument("parentEnv");
        assert unitInfo != null : ProvisionErrors.nullArgument("unitInfo");
        this.unitHome = unitHome;
        this.unitInfo = unitInfo;
    }

    /* (non-Javadoc)
     * @see org.jboss.provision.ProvisionEnvironmentBase#getEnvironmentHome()
     */
    @Override
    public File getEnvironmentHome() throws ProvisionException {
        return unitHome == null ? getParentEnv().getEnvironmentHome() : getParentEnv().resolvePath(unitHome);
    }

    public ProvisionUnitInfo getUnitInfo() {
        return unitInfo;
    }

    public ContentPath getHomePath() {
        return unitHome;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((unitInfo == null) ? 0 : unitInfo.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        ProvisionUnitEnvironment other = (ProvisionUnitEnvironment) obj;
        if (unitInfo == null) {
            if (other.unitInfo != null)
                return false;
        } else if (!unitInfo.equals(other.unitInfo))
            return false;
        return true;
    }
}
