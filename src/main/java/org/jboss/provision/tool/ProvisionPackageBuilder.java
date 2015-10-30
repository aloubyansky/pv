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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.xml.stream.XMLStreamException;

import org.jboss.provision.ProvisionErrors;
import org.jboss.provision.ProvisionException;
import org.jboss.provision.tool.instruction.ContentItemInstruction;
import org.jboss.provision.tool.instruction.ProvisionPackageInstruction;
import org.jboss.provision.tool.instruction.ProvisionUnitInstruction;
import org.jboss.provision.util.HashUtils;
import org.jboss.provision.util.IoUtils;
import org.jboss.provision.xml.ProvisionXml;

/**
 *
 * @author Alexey Loubyansky
 */
public class ProvisionPackageBuilder {

    private ProvisionPackageBuilder() {
    }

    public static void build(ProvisionPackageInstruction instructions, File src, File packageFile) throws ProvisionException {


        ZipOutputStream zos = null;
        try {
            zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(packageFile)));

            zos.putNextEntry(new ZipEntry(ProvisionXml.PROVISION_XML));
            ProvisionXml.marshal(zos, instructions);
            zos.closeEntry();

            for(String unitName : instructions.getUnitNames()) {
                final ProvisionUnitInstruction unitInfo = instructions.getUnitInstruction(unitName);
                for (ContentItemInstruction itemInfo : unitInfo.getContentInstructions()) {
                    if (itemInfo.getContentHash() == null) {
                        continue;
                    }
                    String relativePath = itemInfo.getPath().getRelativePath();
                    if (File.separatorChar == '\\') {
                        relativePath = relativePath.replace('/', '\\');
                    }
                    final File itemFile = new File(src, relativePath);
                    if (!itemFile.exists()) {
                        throw ProvisionErrors.pathDoesNotExist(itemFile);
                    }
                    final byte[] actualHash;
                    try {
                        actualHash = HashUtils.hashFile(itemFile);
                    } catch (IOException e) {
                        throw ProvisionErrors.hashCalculationFailed(itemFile, e);
                    }
                    if (!Arrays.equals(itemInfo.getContentHash(), actualHash)) {
                        throw ProvisionErrors.unexpectedContentHash(itemFile, itemInfo.getContentHash(), actualHash);
                    }

                    addFileToZip(itemFile, itemInfo.getPath().getRelativePath(), zos);
                }
            }
        } catch (IOException ioe) {
            throw ProvisionErrors.failedToZip(src, ioe);
        } catch (XMLStreamException xmlE) {
            throw ProvisionErrors.failedMarshalXml(ProvisionXml.PROVISION_XML, xmlE);
        } finally {
            IoUtils.safeClose(zos);
        }
    }

    private static void addFileToZip(File file, String entryName, ZipOutputStream zos) throws IOException {
        final FileInputStream is = new FileInputStream(file);
        try {
            zos.putNextEntry(new ZipEntry(entryName));
            final BufferedInputStream bis = new BufferedInputStream(is);
            try {
                IoUtils.copyStream(bis, zos);
            } finally {
                IoUtils.safeClose(bis);
            }
            zos.closeEntry();
        } finally {
            IoUtils.safeClose(is);
        }
    }
}
