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

package org.jboss.provision.tool.instruction;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import org.jboss.provision.ApplicationContext;
import org.jboss.provision.ProvisionErrors;
import org.jboss.provision.ProvisionException;
import org.jboss.provision.info.ContentPath;
import org.jboss.provision.util.HashUtils;

/**
 *
 * @author Alexey Loubyansky
 */
class ContentHashCondition implements InstructionCondition {

    private final ContentPath path;
    private final byte[] expectedHash;
    private final byte[] newHash;

    ContentHashCondition(ContentPath path, byte[] expectedHash, byte[] newHash) {
        this.path = path;
        this.expectedHash = expectedHash;
        this.newHash = newHash;
    }

    @Override
    public boolean isSatisfied(ApplicationContext ctx) throws ProvisionException {
        final File targetFile = ctx.getUnitEnvironment().resolvePath(path);
        if(expectedHash == null) {
            // the path is not expected to exist
            if(targetFile.exists()) {
                throw ProvisionErrors.pathAlreadyExists(targetFile);
            }
        } else {
            if(!targetFile.exists()) {
                if(newHash == null) {
                    return false;
                }
                throw ProvisionErrors.pathDoesNotExist(targetFile);
            }
            byte[] actualHash;
            try {
                actualHash = HashUtils.hashFile(targetFile);
            } catch (IOException e) {
                throw ProvisionErrors.hashCalculationFailed(targetFile, e);
            }
            if(Arrays.equals(newHash, actualHash)) {
                // the existing content matches the new one
                return false;
            }
            if(!Arrays.equals(expectedHash, actualHash)) {
                throw ProvisionErrors.pathHashMismatch(targetFile, HashUtils.bytesToHexString(expectedHash), HashUtils.bytesToHexString(actualHash));
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode(expectedHash);
        result = prime * result + ((path == null) ? 0 : path.hashCode());
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
        ContentHashCondition other = (ContentHashCondition) obj;
        if (!Arrays.equals(expectedHash, other.expectedHash))
            return false;
        if (path == null) {
            if (other.path != null)
                return false;
        } else if (!path.equals(other.path))
            return false;
        return true;
    }
}
