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

/**
 *
 * @author Alexey Loubyansky
 */
public interface ApplicationContext {

    /**
     * Returns the currently being processed unit environment.
     *
     * @return  unit environment currently being processed
     */
    ProvisionUnitEnvironment getUnitEnvironment();

    /**
     * Calculates a hash for the target file taking into account scheduled overriding original content on or delete from the disk tasks.
     * If the target file (taking into account scheduled tasks) does not exist, the method will return null.
     *
     * @param target  target file
     * @return  calculated hash or null, if the target file does not exist
     * @throws ProvisionException  in case calculation failed for any reason
     */
    byte[] getHash(File target) throws ProvisionException;
}
