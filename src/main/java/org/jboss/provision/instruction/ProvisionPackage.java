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
import org.jboss.provision.info.ProvisionInfoReader;
import org.jboss.provision.info.ProvisionUnitContentInfo;
import org.jboss.provision.info.ProvisionUnitInfo;
import org.jboss.provision.io.IoUtils;
import org.jboss.provision.util.HashUtils;
import org.jboss.provision.xml.ProvisionXml;

/**
 *
 * @author Alexey Loubyansky
 */
public class ProvisionPackage {

    private ProvisionPackage() {
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder {

        private File curDir;
        private File targetDir;
        private File pkgFile;
        private String patchId;

        private Builder() {
        }

        public Builder setCurrentInstallationDir(File curInstall) {
            this.curDir = curInstall;
            return this;
        }

        public Builder setTargetInstallationDir(File targetInstall) {
            this.targetDir = targetInstall;
            return this;
        }

        public Builder setPackageOutputFile(File pkgFile) {
            this.pkgFile = pkgFile;
            return this;
        }

        public void buildPatch(String patchId) throws ProvisionException {
            buildPatch(patchId, ProvisionUnitInfo.UNDEFINED_NAME, ProvisionUnitInfo.UNDEFINED_VERSION);
        }

        public void buildPatch(String patchId, String unitName, String unitVersion) throws ProvisionException {
            this.patchId = patchId;
            if(patchId == null) {
                throw new ProvisionException(ProvisionErrors.nullArgument("patchId"));
            }
            buildUpdate(unitName, unitVersion, unitVersion);
        }

        public void buildUpdate(String unitName, String replacedVersion, String targetVersion) throws ProvisionException {
            assertExists(curDir, "currentInstallationDir");
            assertExists(targetDir, "targetInstallationDir");
            final ProvisionUnitContentInfo currentContent = ProvisionInfoReader.readContentInfo(unitName, replacedVersion, curDir);
            final ProvisionUnitContentInfo targetContent = ProvisionInfoReader.readContentInfo(unitName, targetVersion, targetDir);
            final ProvisionUnitInstruction updateInstruction;
            if(patchId == null) {
                updateInstruction = ProvisionInstructionBuilder.replace(currentContent, targetContent);
            } else {
                updateInstruction = ProvisionInstructionBuilder.patch(patchId, currentContent, targetContent);
            }
            ProvisionPackage.build(ProvisionEnvironmentInstruction.builder().add(updateInstruction).build(), targetDir, pkgFile);
        }

        public void buildInstall() throws ProvisionException {
            buildInstall(ProvisionUnitInfo.UNDEFINED_NAME, ProvisionUnitInfo.UNDEFINED_VERSION);
        }

        public void buildInstall(String unitName, String targetVersion) throws ProvisionException {
            if(patchId != null) {
                throw ProvisionErrors.patchCantChangeVersion();
            }
            assertExists(targetDir, "targetInstallationDir");
            final ProvisionUnitContentInfo contentInfo = ProvisionInfoReader.readContentInfo(unitName, targetVersion, targetDir);
            final ProvisionUnitInstruction installInstruction = ProvisionInstructionBuilder.install(contentInfo);
            ProvisionPackage.build(ProvisionEnvironmentInstruction.builder().add(installInstruction).build(), targetDir, pkgFile);
        }

        public void buildUninstall() throws ProvisionException {
            buildUninstall(ProvisionUnitInfo.UNDEFINED_NAME, ProvisionUnitInfo.UNDEFINED_VERSION);
        }

        public void buildUninstall(String unitName, String unitVersion) throws ProvisionException {
            if(patchId != null) {
                throw ProvisionErrors.patchCantChangeVersion();
            }
            assertExists(curDir, "currentInstallationDir");
            final ProvisionUnitContentInfo contentInfo = ProvisionInfoReader.readContentInfo(unitName, unitVersion, curDir);
            final ProvisionUnitInstruction uninstallInstruction = ProvisionInstructionBuilder.uninstall(contentInfo);
            ProvisionPackage.build(ProvisionEnvironmentInstruction.builder().add(uninstallInstruction).build(), curDir, pkgFile);
        }

        private void assertExists(File f, String argName) throws ProvisionException {
            if(f == null) {
                throw new ProvisionException(ProvisionErrors.nullArgument(argName));
            }
            if(!f.exists()) {
                throw ProvisionErrors.pathDoesNotExist(f);
            }
        }
    }

    public static void build(ProvisionEnvironmentInstruction instructions, File src, File packageFile) throws ProvisionException {

        if(packageFile == null) {
            throw new ProvisionException(ProvisionErrors.nullArgument("packageFile"));
        }
        if(src == null) {
            throw new ProvisionException(ProvisionErrors.nullArgument("src"));
        }
        if(instructions == null) {
            throw new ProvisionException(ProvisionErrors.nullArgument("instructions"));
        }

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
                    final File itemFile = new File(src, itemInfo.getPath().getFSRelativePath());
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
            throw ProvisionErrors.xmlMarshallingFailed(ProvisionXml.PROVISION_XML, xmlE);
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
