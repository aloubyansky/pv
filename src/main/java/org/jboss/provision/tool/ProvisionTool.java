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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import javax.xml.stream.XMLStreamException;

import org.jboss.provision.ProvisionErrors;
import org.jboss.provision.ProvisionException;
import org.jboss.provision.tool.instruction.ProvisionPackageInstruction;
import org.jboss.provision.util.IoUtils;
import org.jboss.provision.xml.ProvisionXml;

/**
 *
 * @author Alexey Loubyansky
 */
public class ProvisionTool {

    private ProvisionTool() {
    }

    public static void apply(File pvnPackage) throws ProvisionException {

        if (!pvnPackage.exists()) {
            throw ProvisionErrors.pathDoesNotExist(pvnPackage);
        }
        final ProvisionPackageInstruction instructions = readInstructions(pvnPackage);
    }

    private static ProvisionPackageInstruction readInstructions(File pvnPackage) throws ProvisionException {
        ZipFile zipFile = null;
        InputStream is = null;
        try {
            if (pvnPackage.isDirectory()) {
                is = new FileInputStream(new File(pvnPackage, ProvisionXml.PROVISION_XML));
            } else {
                zipFile = new ZipFile(pvnPackage);
                is = zipFile.getInputStream(new ZipEntry(ProvisionXml.PROVISION_XML));
            }
            return ProvisionXml.parse(is);
        } catch (ZipException e) {
            throw ProvisionErrors.zipFormatError(pvnPackage, e);
        } catch (FileNotFoundException e) {
            throw ProvisionErrors.pathDoesNotExist(new File(pvnPackage, ProvisionXml.PROVISION_XML).getAbsoluteFile());
        } catch (IOException e) {
            throw ProvisionErrors.fileReadError(pvnPackage, e);
        } catch (XMLStreamException e) {
            throw ProvisionErrors.failedToParse(ProvisionXml.PROVISION_XML, e);
        } finally {
            IoUtils.safeClose(is);
            IoUtils.safeClose(zipFile);
        }
    }
}