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

    public static ProvisionException failedMarshalXml(String xmlName, Exception e) {
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
}
