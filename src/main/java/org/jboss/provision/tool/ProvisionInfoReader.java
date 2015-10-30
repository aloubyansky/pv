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

package org.jboss.provision.tool;

import java.io.File;
import java.io.IOException;

import org.jboss.provision.ProvisionErrors;
import org.jboss.provision.ProvisionException;
import org.jboss.provision.info.ContentItemInfo;
import org.jboss.provision.info.ProvisionUnitContentInfo;
import org.jboss.provision.util.HashUtils;

/**
 *
 * @author Alexey Loubyansky
 */
public class ProvisionInfoReader {

    private ProvisionInfoReader() {
    }

    public static ProvisionUnitContentInfo readContentInfo(String name, String version, File root) throws ProvisionException {

        final ProvisionUnitContentInfo.Builder builder = ProvisionUnitContentInfo.Builder.forUnit(name, version);
        if(root.exists()) {
            readContentInfo(builder, root, root.getAbsolutePath().length() + 1);
        }
        return builder.build();
    }

    private static void readContentInfo(final ProvisionUnitContentInfo.Builder builder, File file, final int rootPathOffset)
            throws ProvisionException {
        if(file.isDirectory()) {
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
            if(File.separatorChar == '\\' && !relativePath.isEmpty()) {
                relativePath = relativePath.replace('\\', '/');
            }
            builder.add(ContentItemInfo.BUILDER.build(relativePath, fileHash));
        }
    }

    public static void main(String[] args) throws Exception {

        System.out.println("hello");
        readContentInfo("test", "x.x.x", new File("/home/olubyans/git/patch-unit/"));
    }
}
