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

import java.io.File;
import java.io.IOException;

import org.jboss.provision.ProvisionEnvironment;
import org.jboss.provision.ProvisionErrors;
import org.jboss.provision.ProvisionException;
import org.jboss.provision.util.HashUtils;

/**
 *
 * @author Alexey Loubyansky
 */
public class ProvisionInfoReader {

    private ProvisionInfoReader() {
    }

    public static ProvisionUnitContentInfo readContentInfo(File root) throws ProvisionException {
        return readContentInfo(ProvisionUnitInfo.UNDEFINED_INFO.getName(), ProvisionUnitInfo.UNDEFINED_INFO.getVersion(), root);
    }

    public static ProvisionUnitContentInfo readContentInfo(String name, String version, File root) throws ProvisionException {

        final ProvisionUnitContentInfo.Builder builder = ProvisionUnitContentInfo.forUnit(name, version);
        if(root.exists()) {
            readContentInfo(builder, root, root.getAbsolutePath().length() + 1);
        }
        return builder.build();
    }

    private static void readContentInfo(final ProvisionUnitContentInfo.Builder builder, File file, final int rootPathOffset)
            throws ProvisionException {
        if(file.isDirectory()) {
            if(file.getName().equals(ProvisionEnvironment.DEF_HISTORY_DIR)) {
                return;
            }
            for(File f : file.listFiles()) {
                readContentInfo(builder, f, rootPathOffset);
            }
        } else {
            final byte[] fileHash;
            try {
                fileHash = HashUtils.hashFile(file);
            } catch (IOException e) {
                throw ProvisionErrors.hashCalculationFailed(file, e);
            }
            String relativePath = file.getAbsolutePath().substring(rootPathOffset);
            builder.add(ContentItemInfo.create(ContentPath.forFSPath(relativePath), fileHash));
        }
    }
}
