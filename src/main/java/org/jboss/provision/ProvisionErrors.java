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
import java.io.IOException;
import java.util.zip.ZipException;

import javax.xml.stream.XMLStreamException;

import org.jboss.provision.info.ContentPath;
import org.jboss.provision.tool.instruction.ContentItemInstruction;
import org.jboss.provision.util.HashUtils;

/**
 *
 * @author Alexey Loubyansky
 */
public class ProvisionErrors {

    public static String nullArgument(String name) {
        return name + " is null";
    }

    public static IOException couldNotCreateDir(File dir) {
        return new IOException("Could not create directory " + dir.getAbsolutePath());
    }

    public static IOException notADir(File f) {
        return new IOException("Not a directory: " + f.getAbsolutePath());
    }

    public static IOException dirIsNotEmpty(File f) {
        return new IOException("Directory " + f.getAbsolutePath() + " is not empty");
    }

    public static ProvisionException hashCalculationFailed(File f, IOException e) {
        return new ProvisionException("Failed to calculate hash for " + f.getAbsolutePath(), e);
    }

    public static ProvisionException pathDoesNotExist(File f) {
        return new ProvisionException("Content path " + f.getAbsolutePath() + " does not exist.");
    }

    public static ProvisionException unexpectedContentHash(File f, byte[] expectedHash, byte[] actualHash) {
        return new ProvisionException("Expected hash for " + f.getAbsolutePath() + " is " + HashUtils.bytesToHexString(expectedHash) +
                " but was " + HashUtils.bytesToHexString(actualHash));
    }

    public static ProvisionException failedToZip(File f, IOException e) {
        return new ProvisionException("Failed to zip content of " + f.getAbsolutePath(), e);
    }

    public static ProvisionException unitNamesDoNotMatch(String expected, String actual) {
        return new ProvisionException("Expected unit name " + expected + " but the target is " + actual);
    }

    public static ProvisionException xmlMarshallingFailed(String xmlName, Exception e) {
        return new ProvisionException("Failed to marshal " + xmlName, e);
    }

    public static ProvisionException zipFormatError(File zipFile, ZipException e) {
        return new ProvisionException("Failed to read ZIP archive " + zipFile.getAbsolutePath(), e);
    }

    public static ProvisionException readError(File f, IOException e) {
        return new ProvisionException("Failed to read " + f.getAbsolutePath(), e);
    }

    public static ProvisionException failedToParse(String xml, XMLStreamException e) {
        return new ProvisionException("Failed to parse " + xml, e);
    }

    public static ProvisionException unitAlreadyInstalled(String name, String version) {
        return new ProvisionException("Unit already installed " + name + "-" + version);
    }

    public static ProvisionException unitIsNotInstalled(String name) {
        return new ProvisionException("Unit is not installed " + name);
    }

    public static ProvisionException unitVersionMismatch(String name, String expectedVersion, String actualVersion) {
        return new ProvisionException("Installed unit " + name + " is at version " + actualVersion + " but expected to be at " + expectedVersion);
    }

    public static ProvisionException pathAlreadyExists(File path) {
        return new ProvisionException("Content path " + path + " already exists.");
    }

    public static ProvisionException pathHashMismatch(File path, String expectedHash, String actualHash) {
        return new ProvisionException("The hash of " + path + " is " + actualHash + " but expected to be " + expectedHash);
    }

    public static ProvisionException unitHomeNotDefined(String unitName) {
        return new ProvisionException("Unit home path not defined for " + unitName);
    }

    public static ProvisionException unfinedNamedLocation(String namedLocation) {
        return new ProvisionException("Undefined named location: " + namedLocation);
    }

    public static ProvisionException deleteFailed(File f) {
        return new ProvisionException("Could not delete " + f.getAbsolutePath());
    }

    public static ProvisionException patchCantChangeVersion() {
        return new ProvisionException("Patch is not allowed to install new or change the version of the installed software.");
    }

    public static ProvisionException patchIdMissing() {
        return new ProvisionException("Patch ID is required to produce a patch");
    }

    public static ProvisionException pathCopiedAndDeleted(ContentPath path) {
        return new ProvisionException(path + " is copied and deleted during the same file system update.");
    }

    public static ProvisionException pathCopiedMoreThanOnce(ContentPath path) {
        return new ProvisionException(path + " is copied more than once during the same file system update.");
    }

    public static ProvisionException failedToBackupInstruction(ContentItemInstruction instruction, Throwable t) {
        return new ProvisionException("Failed to back up instruction for " + instruction.getPath(), t);
    }

    public static ProvisionException backupSessionNotActive() {
        return new ProvisionException("Backup session is not active");
    }

    public static ProvisionException backupSessionInitFailed(Throwable t) {
        return new ProvisionException("Backup session initialization failed", t);
    }

    public static ProvisionException backupSessionLoadFailed(Throwable t) {
        return new ProvisionException("Failed to load the backup session", t);
    }

    public static ProvisionException backupInstructionFailed(ContentItemInstruction instruction, Throwable t) {
        return new ProvisionException("Failed to backup instruction for " + instruction.getPath(), t);
    }

    public static ProvisionException backedUpInstructionLoadFailed(Throwable t) {
        return new ProvisionException("Failed to load backed up instruction", t);
    }

    public static ProvisionException backedUpInstructionLoadFailed(String msg) {
        return new ProvisionException("Failed to load backed up instruction: " + msg);
    }

    public static String relativePathMissing() {
        return "relative path is missing";
    }

    public static String contentHashMissing() {
        return "content hash is missing";
    }
}
