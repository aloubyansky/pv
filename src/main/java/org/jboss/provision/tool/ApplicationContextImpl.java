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
import org.jboss.provision.ProvisionUnitEnvironment;
import org.jboss.provision.UnitUpdatePolicy;
import org.jboss.provision.audit.AuditRecord;
import org.jboss.provision.audit.AuditSession;
import org.jboss.provision.audit.AuditSessionFactory;
import org.jboss.provision.info.ContentPath;
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
    private ProvisionUnitEnvironment unitEnv;
    private ProvisionUnitInstruction unitInstruction;
    private FSPaths fsPaths = new FSPaths();

    private ZipFile zip;

    ApplicationContextImpl(ProvisionEnvironment env) {
        assert env != null : ProvisionErrors.nullArgument("env");
        this.env = env;
    }

    void processPackage(File pkgFile) throws ProvisionException {
        assert pkgFile != null : ProvisionErrors.nullArgument("packageFile");

        if (!pkgFile.exists()) {
            throw ProvisionErrors.pathDoesNotExist(pkgFile);
        }
        final ProvisionPackageInstruction instructions = readInstructions(pkgFile);
        AuditSession auditSession = null;
        boolean discardBackup = true;
        try {
            assertCanApply(instructions);
            auditSession = AuditSessionFactory.newInstance().startSession(env);
            auditSession.record(env);
            fsPaths.copy(auditSession);
            fsPaths.delete(auditSession);
        } catch(ProvisionException|RuntimeException|Error e) {
            discardBackup = false;
            if(auditSession != null) {
                try {
                    revertPerformedInstructions(auditSession);
                    discardBackup = true;
                } catch(Throwable t) {
                    // ignore;
                }
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
            final File targetFile = unitEnv.resolvePath(record.getInstruction().getPath()); // TODO THIS IS BROKEN
            if(record.getBackupFile() == null) {
                if(!IoUtils.recursiveDelete(targetFile)) {
                    throw ProvisionErrors.failedToDelete(targetFile);
                }
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
            this.unitEnv = env.getUnitEnvironment(unitName);
            if(unitEnv == null) {
                throw ProvisionErrors.unknownUnit(unitName);
            }
            this.unitInstruction = instructions.getUnitInstruction(unitName);
            assertCanApplyUnit();
        }
    }

    private void assertCanApplyUnit() throws ProvisionException {

        final UnitUpdatePolicy updatePolicy = unitEnv.resolveUpdatePolicy();
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
    public ProvisionUnitEnvironment getUnitEnvironment() {
        return unitEnv;
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

        /** After a path from this map is deleted, its parent dir will be checked for containing other
         *  files or directories. If the parent dir is empty it will also be deleted
         *  and then its parent directory will checked, etc. This will trigger a cascading delete
         *  of empty parent directories up to the unit home directory */
        private Map<String, ScheduledInstruction> deleteWithDirs = Collections.emptyMap();

        /** Unlike the previous map, deleting paths from this map won't trigger deleting empty parent directories.
         *  The logic is that these paths could be outside of the unit home branch. */
        private Map<String, ScheduledInstruction> delete = Collections.emptyMap();

        private Map<String, ScheduledInstruction> copy = Collections.emptyMap();

        void scheduleDelete(ContentItemInstruction item) throws ProvisionException {

            final ContentPath path = item.getPath();
            final File resolvedPath = unitEnv.resolvePath(path);
            if(copy.containsKey(resolvedPath.getAbsolutePath())) {
                throw ProvisionErrors.pathCopiedAndDeleted(path);
            }

            final ContentPath homePath = unitEnv.getHomePath();
            if (homePath != null) {
                switch (deleteWithDirs.size()) {
                    case 0:
                        deleteWithDirs = Collections.singletonMap(resolvedPath.getAbsolutePath(),
                                new ScheduledInstruction(resolvedPath, item, unitEnv.getEnvironmentHome()));
                        break;
                    case 1:
                        deleteWithDirs = new HashMap<String, ScheduledInstruction>(deleteWithDirs);
                    default:
                        deleteWithDirs.put(resolvedPath.getAbsolutePath(),
                                new ScheduledInstruction(resolvedPath, item, unitEnv.getEnvironmentHome()));
                }
            } else {
                switch (delete.size()) {
                    case 0:
                        delete = Collections.singletonMap(resolvedPath.getAbsolutePath(), new ScheduledInstruction(resolvedPath, item, null));
                        break;
                    case 1:
                        delete = new HashMap<String, ScheduledInstruction>(delete);
                    default:
                        delete.put(resolvedPath.getAbsolutePath(), new ScheduledInstruction(resolvedPath, item, unitEnv.getEnvironmentHome()));
                }
            }
        }

        void scheduleCopy(ContentItemInstruction item) throws ProvisionException {

            final ContentPath path = item.getPath();
            final File resolvedPath = unitEnv.resolvePath(path);
            if(deleteWithDirs.containsKey(resolvedPath.getAbsolutePath())) {
                throw ProvisionErrors.pathCopiedAndDeleted(path);
            }
            if(delete.containsKey(resolvedPath.getAbsolutePath())) {
                throw ProvisionErrors.pathCopiedAndDeleted(path);
            }

            switch(copy.size()) {
                case 0:
                    copy = Collections.singletonMap(resolvedPath.getAbsolutePath(), new ScheduledInstruction(resolvedPath, item, unitEnv.getEnvironmentHome()));
                    break;
                case 1:
                    copy = new HashMap<String, ScheduledInstruction>(copy);
                default:
                    if(copy.put(resolvedPath.getAbsolutePath(), new ScheduledInstruction(resolvedPath, item, unitEnv.getEnvironmentHome())) != null) {
                        throw ProvisionErrors.pathCopiedMoreThanOnce(path);
                    }
            }
        }

        void delete(AuditSession session) throws ProvisionException {
            delete(session, deleteWithDirs, true);
            delete(session, delete, false);
        }

        private void delete(AuditSession session, Map<String, ScheduledInstruction> delete, boolean withDirs) throws ProvisionException {
            for (ScheduledInstruction scheduled : delete.values()) {
                File targetFile = scheduled.targetFile;
                session.record(scheduled.instruction, targetFile);
                if (!IoUtils.recursiveDelete(targetFile)) {
                    throw ProvisionErrors.deleteFailed(targetFile);
                }
                if (withDirs) {
                    targetFile = targetFile.getParentFile();
                    while (targetFile.list().length == 0) {
                        if(targetFile.getAbsolutePath().equals(scheduled.unitDir.getAbsolutePath())) {
                            IoUtils.recursiveDelete(targetFile);
                            break;
                        }
                        if (!IoUtils.recursiveDelete(targetFile)) {
                            break;
                        }
                        targetFile = targetFile.getParentFile();
                    }
                }
            }
        }

        void copy(AuditSession session) throws ProvisionException {
            for (ScheduledInstruction scheduled : copy.values()) {
                final File targetFile = scheduled.targetFile;
                session.record(scheduled.instruction, targetFile);
                if(!targetFile.getParentFile().exists()) {
                    if(!targetFile.getParentFile().mkdirs()) {
                        throw new ProvisionException(ProvisionErrors.couldNotCreateDir(targetFile.getParentFile()));
                    }
                }
                InputStream is = null;
                FileOutputStream os = null;
                try {
                    is = zip.getInputStream(new ZipEntry(scheduled.instruction.getPath().getRelativePath())); // TODO THIS NEEDS A BETTER PATH BINDING
                    os = new FileOutputStream(targetFile);
                    IoUtils.copyStream(is, os);
                } catch(IOException e) {
                    throw ProvisionErrors.failedToCopyContentFromZIP(scheduled.instruction.getPath(), targetFile, e);
                } finally {
                    IoUtils.safeClose(is);
                    IoUtils.safeClose(os);
                }
            }
        }
    }

    private static class ScheduledInstruction {

        final File targetFile;
        final ContentItemInstruction instruction;
        final File unitDir;

        ScheduledInstruction(File targetFile, ContentItemInstruction instruction, File rootDir) {
            this.targetFile = targetFile;
            this.instruction = instruction;
            this.unitDir = rootDir;
        }
    }
}
