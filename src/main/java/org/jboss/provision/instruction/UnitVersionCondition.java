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

import org.jboss.provision.ApplicationContext;
import org.jboss.provision.ProvisionErrors;
import org.jboss.provision.ProvisionException;
import org.jboss.provision.info.ProvisionUnitInfo;

/**
 *
 * @author Alexey Loubyansky
 */
class UnitVersionCondition implements InstructionCondition {

    private final String name;
    private final String version;

    UnitVersionCondition(String name, String version) {
        this.name = name;
        this.version = version;
    }

    @Override
    public boolean isSatisfied(ApplicationContext ctx) throws ProvisionException {
        final ProvisionUnitInfo unitInfo = ctx.getUnitEnvironment().getUnitInfo();
        if(version == null) {
            if(unitInfo.getVersion() != null) {
                throw ProvisionErrors.unitAlreadyInstalled(unitInfo.getName(), unitInfo.getVersion());
            }
        } else if(!unitInfo.getVersion().equals(version)) {
            throw ProvisionErrors.unitVersionMismatch(unitInfo.getName(), version, unitInfo.getVersion());
        }
        return true;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((version == null) ? 0 : version.hashCode());
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
        UnitVersionCondition other = (UnitVersionCondition) obj;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        if (version == null) {
            if (other.version != null)
                return false;
        } else if (!version.equals(other.version))
            return false;
        return true;
    }
}
