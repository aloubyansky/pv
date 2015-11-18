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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import javax.xml.stream.XMLStreamException;

import org.jboss.provision.ApplicationContext;
import org.jboss.provision.ProvisionEnvironment;
import org.jboss.provision.ProvisionErrors;
import org.jboss.provision.ProvisionException;
import org.jboss.provision.UnitUpdatePolicy;
import org.jboss.provision.audit.AuditRecord;
import org.jboss.provision.audit.AuditSession;
import org.jboss.provision.audit.AuditSessionFactory;
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
        AuditSession auditSession = null;
        boolean discardBackup = true;
        try {
            assertCanApply(instructions);
            auditSession = AuditSessionFactory.newInstance().startSession(env);
            auditSession.record(env);
            fsPaths.delete(auditSession);
            fsPaths.copy(auditSession);
        } catch(ProvisionException|RuntimeException|Error e) {
            discardBackup = false;
            if(auditSession != null) {
                revertPerformedInstructions(auditSession);
                discardBackup = true;
            }
            throw e;
        } finally {
            IoUtils.safeClose(zip);
            if(auditSession != null) {
                if(discardBackup) {
                    auditSession.discardBackup();
                }
                auditSession.close();
            }
        }
    }

    void revertPerformedInstructions() throws ProvisionException {
        final AuditSession auditSession = AuditSessionFactory.newInstance().loadCrushedSession(env);
        boolean discardBackup = true;
        try {
            revertPerformedInstructions(auditSession);
        } catch(ProvisionException|RuntimeException|Error e) {
            discardBackup = false;
            throw e;
        } finally {
            if(discardBackup) {
                auditSession.discardBackup();
            }
            auditSession.close();
        }
    }

    private void revertPerformedInstructions(AuditSession session) throws ProvisionException {
        for(AuditRecord record : session.getRecorded()) {
            final File targetFile = resolvePath(record.getInstruction().getPath());
            if(record.getBackupFile() == null) {
                IoUtils.recursiveDelete(targetFile);
            } else {
                try {
                    IoUtils.copyFile(record.getBackupFile(), targetFile);
                } catch (IOException e) {
                    throw ProvisionErrors.failedToCopy(record.getInstruction().getPath(), targetFile);
                }
            }
        }
    }

    private void assertCanApply(ProvisionPackageInstruction instructions) throws ProvisionException {

        for (String unitName : instructions.getUnitNames()) {
            this.unitName = unitName;
            this.unitHome = env.getInstallationHome();
            this.unitInstruction = instructions.getUnitInstruction(unitName);
            assertCanApplyUnit();
        }
    }

    private void assertCanApplyUnit() throws ProvisionException {

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
                fsPaths.scheduleDelete(item);
            } else {
                fsPaths.scheduleCopy(item);
            }
        }
    }

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

        private Map<ContentPath, ContentItemInstruction> deleted = Collections.emptyMap();
        private Map<ContentPath, ContentItemInstruction> copied = Collections.emptyMap();

        void scheduleDelete(ContentItemInstruction item) throws ProvisionException {

            switch(deleted.size()) {
                case 0:
                    deleted = Collections.singletonMap(item.getPath(), item);
                    break;
                case 1:
                    deleted = new HashMap<ContentPath, ContentItemInstruction>(deleted);
                default:
                    deleted.put(item.getPath(), item);
            }

            if(copied.containsKey(item.getPath())) {
                throw ProvisionErrors.pathCopiedAndDeleted(item.getPath());
            }
        }

        void scheduleCopy(ContentItemInstruction item) throws ProvisionException {

            switch(copied.size()) {
                case 0:
                    copied = Collections.singletonMap(item.getPath(), item);
                    break;
                case 1:
                    copied = new HashMap<ContentPath, ContentItemInstruction>(copied);
                default:
                    if(copied.put(item.getPath(), item) != null) {
                        throw ProvisionErrors.pathCopiedMoreThanOnce(item.getPath());
                    }
            }

            if(deleted.containsKey(item.getPath())) {
                throw ProvisionErrors.pathCopiedAndDeleted(item.getPath());
            }
        }

        void delete(AuditSession session) throws ProvisionException {
            for (ContentItemInstruction item : deleted.values()) {
                final File targetFile = resolvePath(item.getPath());
                session.record(item, targetFile);
                if (!IoUtils.recursiveDelete(targetFile)) {
                    throw ProvisionErrors.deleteFailed(targetFile);
                }
            }
        }

        void copy(AuditSession session) throws ProvisionException {
            for (ContentItemInstruction item : copied.values()) {
                final ContentPath path = item.getPath();
                final File targetFile = resolvePath(path);
                session.record(item, targetFile);
                if(!targetFile.getParentFile().exists()) {
                    if(!targetFile.getParentFile().mkdirs()) {
                        throw new ProvisionException(ProvisionErrors.couldNotCreateDir(targetFile.getParentFile()));
                    }
                }
                InputStream is = null;
                FileOutputStream os = null;
                try {
                    is = zip.getInputStream(new ZipEntry(path.getRelativePath()));
                    os = new FileOutputStream(targetFile);
                    IoUtils.copyStream(is, os);
                } catch(IOException e) {
                    throw ProvisionErrors.failedToCopyContentFromZIP(path, targetFile, e);
                } finally {
                    IoUtils.safeClose(is);
                    IoUtils.safeClose(os);
                }
            }
        }
    }
}
