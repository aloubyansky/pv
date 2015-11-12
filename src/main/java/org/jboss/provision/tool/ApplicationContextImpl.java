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
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import javax.xml.stream.XMLStreamException;

import org.jboss.provision.ApplicationContext;
import org.jboss.provision.ProvisionEnvironment;
import org.jboss.provision.ProvisionErrors;
import org.jboss.provision.ProvisionException;
import org.jboss.provision.UnitUpdatePolicy;
import org.jboss.provision.info.ContentPath;
import org.jboss.provision.info.ProvisionUnitInfo;
import org.jboss.provision.tool.instruction.ContentItemInstruction;
import org.jboss.provision.tool.instruction.InstructionCondition;
import org.jboss.provision.tool.instruction.ProvisionPackageInstruction;
import org.jboss.provision.tool.instruction.ProvisionUnitInstruction;
import org.jboss.provision.tool.instruction.UpdatePolicy;
import org.jboss.provision.util.IoUtils;
import org.jboss.provision.xml.ProvisionXml;

/**
 *
 * @author Alexey Loubyansky
 */
class ApplicationContextImpl implements ApplicationContext {

    private final ProvisionEnvironment env;
    private String unitName;
    private File unitHome;
    private ProvisionUnitInstruction unitInstruction;
    private FSPaths fsPaths = new FSPaths();

    private ZipFile zip;

    ApplicationContextImpl(ProvisionEnvironment env) {
        assert env != null : ProvisionErrors.nullArgument("env");
        this.env = env;
    }

    void processPackage() throws ProvisionException {
        if (!env.getPackageFile().exists()) {
            throw ProvisionErrors.pathDoesNotExist(env.getPackageFile());
        }
        final ProvisionPackageInstruction instructions = readInstructions(env.getPackageFile());
        try {
            assertCanApply(instructions);
            fsPaths.delete();
            fsPaths.copy();
        } finally {
            IoUtils.safeClose(zip);
        }
    }

    void assertCanApply(ProvisionPackageInstruction instructions) throws ProvisionException {

        for (String unitName : instructions.getUnitNames()) {
            this.unitName = unitName;
            this.unitHome = env.getInstallationHome();
            this.unitInstruction = instructions.getUnitInstruction(unitName);
            assertCanApplyUnit();
        }
    }

    void assertCanApplyUnit() throws ProvisionException {

        final UnitUpdatePolicy updatePolicy = env.getUnitUpdatePolicy(this.unitName);
        if (updatePolicy.getUnitPolicy() == UpdatePolicy.IGNORED) {
            return;
        }

        if (updatePolicy.getUnitPolicy() == UpdatePolicy.CONDITIONED) {
            for (InstructionCondition condition : unitInstruction.getConditions()) {
                if(!condition.isSatisfied(this)) {
                    return;
                }
            }
        }

        for(ContentItemInstruction item : unitInstruction.getContentInstructions()) {

            final UpdatePolicy contentPolicy = updatePolicy.getContentPolicy(item.getPath().getRelativePath());
            if(contentPolicy == UpdatePolicy.IGNORED) {
                continue;
            }
            if (contentPolicy == UpdatePolicy.CONDITIONED) {
                for (InstructionCondition condition : item.getConditions()) {
                    if(!condition.isSatisfied(this)) {
                        continue;
                    }
                }
            }

            if(item.getContentHash() == null) {
                fsPaths.scheduleDelete(item.getPath());
            } else {
                fsPaths.scheduleCopy(item.getPath());
            }
        }
    }

/*    void processUnit() throws ProvisionException {

        final UnitUpdatePolicy updatePolicy = env.getUnitUpdatePolicy(this.unitName);
        if (updatePolicy.getUnitPolicy() == UpdatePolicy.IGNORED) {
            return;
        }

        if (updatePolicy.getUnitPolicy() == UpdatePolicy.CONDITIONED) {
            for (InstructionCondition condition : unitInstruction.getConditions()) {
                if(!condition.isSatisfied(this)) {
                    return;
                }
            }
        }

        for(ContentItemInstruction item : unitInstruction.getContentInstructions()) {

            final UpdatePolicy contentPolicy = updatePolicy.getContentPolicy(item.getPath().getRelativePath());
            if(contentPolicy == UpdatePolicy.IGNORED) {
                continue;
            }
            if (contentPolicy == UpdatePolicy.CONDITIONED) {
                for (InstructionCondition condition : item.getConditions()) {
                    if(!condition.isSatisfied(this)) {
                        continue;
                    }
                }
            }

            final File targetFile = resolvePath(item.getPath());
            if(item.getContentHash() == null) {
                if(!IoUtils.recursiveDelete(targetFile)) {
                    throw ProvisionErrors.deleteFailed(targetFile);
                }
            } else {
                InputStream is = null;
                try {
                    is = zip.getInputStream(new ZipEntry(item.getPath().getRelativePath()));
                    IoUtils.copy(is, targetFile);
                } catch(IOException e) {
                    IoUtils.safeClose(is);
                }
            }
        }
    }
*/
    /* (non-Javadoc)
     * @see org.jboss.provision.tool.instruction.ProvisionEnvironment#getUnitContentInfo(java.lang.String)
     */
    @Override
    public ProvisionUnitInfo getUnitInfo() {
        return unitInstruction;
    }

    /* (non-Javadoc)
     * @see org.jboss.provision.tool.instruction.ApplicationContext#getUnitHome()
     */
    @Override
    public File getUnitHome() {
        return unitHome;
    }

    /* (non-Javadoc)
     * @see org.jboss.provision.tool.instruction.ApplicationContext#getEnvironment()
     */
    @Override
    public ProvisionEnvironment getEnvironment() {
        return env;
    }

    /* (non-Javadoc)
     * @see org.jboss.provision.tool.instruction.ApplicationContext#resolvePath(org.jboss.provision.info.ContentPath)
     */
    @Override
    public File resolvePath(ContentPath path) throws ProvisionException {

        File f;
        final String namedLocation = path.getNamedLocation();
        if(namedLocation != null) {
            f = env.resolveNamedLocation(namedLocation);
        } else {
            f = unitHome;
        }
        String relativePath = path.getRelativePath();
        if(File.separatorChar == '\\') {
            relativePath = relativePath.replace('/', '\\');
        }
        return new File(f, relativePath);
    }

    private ProvisionPackageInstruction readInstructions(File pvnPackage) throws ProvisionException {
        InputStream is = null;
        try {
            if (pvnPackage.isDirectory()) {
                is = new FileInputStream(new File(pvnPackage, ProvisionXml.PROVISION_XML));
            } else {
                zip = new ZipFile(pvnPackage);
                is = zip.getInputStream(new ZipEntry(ProvisionXml.PROVISION_XML));
            }
            return ProvisionXml.parse(is);
        } catch (ZipException e) {
            throw ProvisionErrors.zipFormatError(pvnPackage, e);
        } catch (FileNotFoundException e) {
            throw ProvisionErrors.pathDoesNotExist(new File(pvnPackage, ProvisionXml.PROVISION_XML).getAbsoluteFile());
        } catch (IOException e) {
            throw ProvisionErrors.readError(pvnPackage, e);
        } catch (XMLStreamException e) {
            throw ProvisionErrors.failedToParse(ProvisionXml.PROVISION_XML, e);
        } finally {
            IoUtils.safeClose(is);
        }
    }

    private class FSPaths {

        private Set<ContentPath> deleted = Collections.emptySet();
        private Set<ContentPath> copied = Collections.emptySet();

        void scheduleDelete(ContentPath path) throws ProvisionException {

            switch(deleted.size()) {
                case 0:
                    deleted = Collections.singleton(path);
                    break;
                case 1:
                    deleted = new HashSet<ContentPath>(deleted);
                default:
                    deleted.add(path);
            }

            if(copied.contains(path)) {
                throw ProvisionErrors.pathCopiedAndDeleted(path);
            }
        }

        void scheduleCopy(ContentPath path) throws ProvisionException {

            switch(copied.size()) {
                case 0:
                    copied = Collections.singleton(path);
                    break;
                case 1:
                    copied = new HashSet<ContentPath>(copied);
                default:
                    if(!copied.add(path)) {
                        throw ProvisionErrors.pathCopiedMoreThanOnce(path);
                    }
            }

            if(deleted.contains(path)) {
                throw ProvisionErrors.pathCopiedAndDeleted(path);
            }
        }

        void delete() throws ProvisionException {
            for (ContentPath path : deleted) {
                final File targetFile = resolvePath(path);
                if (!IoUtils.recursiveDelete(targetFile)) {
                    throw ProvisionErrors.deleteFailed(targetFile);
                }
            }
        }

        void copy() throws ProvisionException {
            for (ContentPath path : copied) {
                final File targetFile = resolvePath(path);
                InputStream is = null;
                try {
                    is = zip.getInputStream(new ZipEntry(path.getRelativePath()));
                    IoUtils.copy(is, targetFile);
                } catch(IOException e) {
                    IoUtils.safeClose(is);
                }
            }
        }
    }
}
