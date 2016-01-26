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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.stream.XMLStreamException;

import org.jboss.provision.ApplicationContext;
import org.jboss.provision.ProvisionEnvironment;
import org.jboss.provision.ProvisionErrors;
import org.jboss.provision.ProvisionException;
import org.jboss.provision.ProvisionUnitEnvironment;
import org.jboss.provision.UnitUpdatePolicy;
import org.jboss.provision.audit.ProvisionUnitJournal;
import org.jboss.provision.audit.UnitJournalRecord;
import org.jboss.provision.audit.ProvisionEnvironmentJournal;
import org.jboss.provision.info.ContentPath;
import org.jboss.provision.info.ProvisionUnitInfo;
import org.jboss.provision.instruction.ContentItemInstruction;
import org.jboss.provision.instruction.InstructionCondition;
import org.jboss.provision.instruction.ProvisionEnvironmentInstruction;
import org.jboss.provision.instruction.ProvisionUnitInstruction;
import org.jboss.provision.instruction.UpdatePolicy;
import org.jboss.provision.io.IoUtils;
import org.jboss.provision.xml.ProvisionXml;

/**
 *
 * @author Alexey Loubyansky
 */
class ApplicationContextImpl implements ApplicationContext {

    interface CommitCallback {
        CommitCallback APPLY = new CommitCallback() {
            @Override
            public ProvisionEnvironment commit(ProvisionEnvironment currentEnv, ProvisionEnvironmentInstruction instruction,
                    ProvisionEnvironmentJournal envJournal) throws ProvisionException {
                return MutableEnvironmentHistory.newInstance(ProvisionEnvironmentHistory.getInstance(currentEnv).getHistoryHome())
                        .doRecord(currentEnv, instruction, envJournal);
            }
        };

        CommitCallback ROLLBACK = new CommitCallback() {
            @Override
            public ProvisionEnvironment commit(ProvisionEnvironment currentEnv, ProvisionEnvironmentInstruction instruction,
                    ProvisionEnvironmentJournal envJournal) throws ProvisionException {
                return MutableEnvironmentHistory.newInstance(ProvisionEnvironmentHistory.getInstance(currentEnv).getHistoryHome())
                        .doRollbackLast(currentEnv);
            }
        };

        ProvisionEnvironment commit(ProvisionEnvironment currentEnv, ProvisionEnvironmentInstruction instruction, ProvisionEnvironmentJournal envJournal) throws ProvisionException;
    }

    private ProvisionEnvironment env;
    private ProvisionUnitEnvironment unitEnv;
    private EnvironmentTasks envTasks = new EnvironmentTasks();

    private final ContentSource contentSrc;

    ApplicationContextImpl(ProvisionEnvironment env, ContentSource contentSource) {
        assert env != null : ProvisionErrors.nullArgument("env");
        assert contentSource != null : ProvisionErrors.nullArgument("contentSource");
        this.env = env;
        this.contentSrc = contentSource;
    }

    ProvisionEnvironment processPackage(File pkgFile) throws ProvisionException {
        assert pkgFile != null : ProvisionErrors.nullArgument("packageFile");
        if (!pkgFile.exists()) {
            throw ProvisionErrors.pathDoesNotExist(pkgFile);
        }
        return apply(readInstruction(pkgFile), CommitCallback.APPLY);
    }

    protected ProvisionEnvironment apply(final ProvisionEnvironmentInstruction instruction, CommitCallback callback) throws ProvisionException {
        ProvisionEnvironmentJournal envJournal = null;
        boolean discardBackup = true;
        try {
            scheduleTasks(instruction);
            unitEnv = null;
            envJournal = ProvisionEnvironmentJournal.Factory.startSession(env);
            envJournal.record(env);
            envTasks.execute(envJournal);
            env = callback.commit(env, instruction, envJournal);
        } catch(ProvisionException|RuntimeException|Error e) {
            discardBackup = false;
            if(envJournal != null) {
                try {
                    revertPerformedInstructions(envJournal);
                    discardBackup = true;
                } catch(Throwable t) {
                    // ignore;
                }
            }
            throw e;
        } finally {
            IoUtils.safeClose(contentSrc);
            if(envJournal != null) {
                if(discardBackup) {
                    envJournal.discardBackup();
                }
                envJournal.close();
            }
        }
        return env;
    }

    void revertPerformedInstructions() throws ProvisionException {
        final ProvisionEnvironmentJournal auditSession = ProvisionEnvironmentJournal.Factory.loadCrushedSession(env);
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

    private void revertPerformedInstructions(ProvisionEnvironmentJournal envJournal) throws ProvisionException {
        for(ProvisionUnitJournal unitJournal : envJournal.getUnitJournals()) {
            for(UnitJournalRecord record : unitJournal.getRecorded()) {
                final File targetFile = unitJournal.getUnitEnvironment().resolvePath(record.getInstruction().getPath());
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
    }

    private void scheduleTasks(ProvisionEnvironmentInstruction instructions) throws ProvisionException {
        for (String unitName : instructions.getUnitNames()) {
            final ProvisionUnitInstruction unitInstr = instructions.getUnitInstruction(unitName);
            this.unitEnv = env.getUnitEnvironment(unitName);
            if(unitEnv == null) {
                if(unitInstr.getRequiredVersion() != null) {
                    throw ProvisionErrors.unknownUnit(unitName);
                }
                unitEnv = ProvisionUnitEnvironment.builder()
                        .setParentEnv(env)
                        .setUnitInfo(ProvisionUnitInfo.createInfo(unitInstr.getUnitName(), unitInstr.getRequiredVersion()))
                        .build();
            }
            scheduleTasks(unitInstr);
        }
    }

    private void scheduleTasks(ProvisionUnitInstruction instructions) throws ProvisionException {

        final UnitUpdatePolicy updatePolicy = unitEnv.resolveUpdatePolicy();
        if (updatePolicy.getUnitPolicy() == UpdatePolicy.IGNORED) {
            return;
        }
        if (updatePolicy.getUnitPolicy() == UpdatePolicy.CONDITIONED) {
            for (InstructionCondition condition : instructions.getConditions()) {
                if(!condition.isSatisfied(this)) {
                    return;
                }
            }
        }

        final EnvironmentTasks.UnitTasks unitTasks = envTasks.createUnitTasks(unitEnv);
        for(ContentItemInstruction item : instructions.getContentInstructions()) {
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
                // TODO this check here is for rolling back a forced add of an item over a conflicting existing one which was backed up
                if(contentSrc.isAvailable(unitEnv, item.getPath())) {
                    unitTasks.scheduleCopy(item);
                } else {
                    unitTasks.scheduleDelete(item);
                }
            } else
                // TODO this check here is for rolling back a delete of an item which has already been deleted
                if(contentSrc.isAvailable(unitEnv, item.getPath())) {
                    unitTasks.scheduleCopy(item);
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

    private ProvisionEnvironmentInstruction readInstruction(File pvnPackage) throws ProvisionException {
        InputStream is = null;
        try {
            is = contentSrc.getInputStream(env, ContentPath.forPath(ProvisionXml.PROVISION_XML));
            return ProvisionXml.parse(is);
        } catch (XMLStreamException e) {
            throw ProvisionErrors.failedToParse(ProvisionXml.PROVISION_XML, e);
        } finally {
            IoUtils.safeClose(is);
        }
    }

    private class EnvironmentTasks {

        private Set<String> deletedPaths = Collections.emptySet();
        private Set<String> copiedPaths = Collections.emptySet();

        private List<UnitTasks> unitTasks = Collections.emptyList();

        UnitTasks createUnitTasks(ProvisionUnitEnvironment unitEnv) {
            UnitTasks unitTasks = new UnitTasks(unitEnv);
            switch(this.unitTasks.size()) {
                case 0:
                    this.unitTasks = Collections.singletonList(unitTasks);
                    break;
                case 1:
                    this.unitTasks = new ArrayList<UnitTasks>(this.unitTasks);
                default:
                    this.unitTasks.add(unitTasks);
            }
            return unitTasks;
        }

        void execute(ProvisionEnvironmentJournal envJournal) throws ProvisionException {
            for(UnitTasks unitTasks : this.unitTasks) {
                final ProvisionUnitJournal unitJournal = envJournal.getUnitJournal(unitTasks.unitEnv);
                unitTasks.delete(unitJournal);
                unitTasks.copy(unitJournal);
            }
        }

        private class UnitTasks {

            final ProvisionUnitEnvironment unitEnv;

            /**
             * After a path from this map is deleted, its parent dir will be checked for containing other files or directories.
             * If the parent dir is empty it will also be deleted and then its parent directory will checked, etc. This will
             * trigger a cascading delete of empty parent directories up to the unit home directory
             */
            private List<ScheduledInstruction> deleteWithDirs = Collections.emptyList();

            /**
             * Unlike the previous map, deleting paths from this map won't trigger deleting empty parent directories. The logic
             * is that these paths could be outside of the unit home branch.
             */
            private List<ScheduledInstruction> delete = Collections.emptyList();

            private List<ScheduledInstruction> copy = Collections.emptyList();

            UnitTasks(ProvisionUnitEnvironment unitEnv) {
                this.unitEnv = unitEnv;
            }

            void scheduleDelete(ContentItemInstruction item) throws ProvisionException {

                final ContentPath path = item.getPath();
                final File resolvedPath = unitEnv.resolvePath(path);
                if (copiedPaths.contains(resolvedPath.getAbsolutePath())) {
                    throw ProvisionErrors.pathCopiedAndDeleted(path);
                }

                final ContentPath homePath = unitEnv.getHomePath();
                if (homePath != null) {
                    switch (deleteWithDirs.size()) {
                        case 0:
                            deleteWithDirs = Collections.singletonList(new ScheduledInstruction(resolvedPath, item));
                            break;
                        case 1:
                            deleteWithDirs = new ArrayList<ScheduledInstruction>(deleteWithDirs);
                        default:
                            deleteWithDirs.add(new ScheduledInstruction(resolvedPath, item));
                    }
                } else {
                    switch (delete.size()) {
                        case 0:
                            delete = Collections.singletonList(new ScheduledInstruction(resolvedPath, item));
                            break;
                        case 1:
                            delete = new ArrayList<ScheduledInstruction>(delete);
                        default:
                            delete.add(new ScheduledInstruction(resolvedPath, item));
                    }
                }
                switch (deletedPaths.size()) {
                    case 0:
                        deletedPaths = Collections.singleton(resolvedPath.getAbsolutePath());
                        break;
                    case 1:
                        deletedPaths = new HashSet<String>(deletedPaths);
                    default:
                        deletedPaths.add(resolvedPath.getAbsolutePath());
                }
            }

            void scheduleCopy(ContentItemInstruction item) throws ProvisionException {

                final ContentPath path = item.getPath();
                final File resolvedPath = unitEnv.resolvePath(path);
                if (deletedPaths.contains(resolvedPath.getAbsolutePath())) {
                    throw ProvisionErrors.pathCopiedAndDeleted(path);
                }

                switch (copy.size()) {
                    case 0:
                        copy = Collections.singletonList(new ScheduledInstruction(resolvedPath, item));
                        break;
                    case 1:
                        copy = new ArrayList<ScheduledInstruction>(copy);
                    default:
                        if (copiedPaths.contains(resolvedPath.getAbsolutePath())) {
                            throw ProvisionErrors.pathCopiedMoreThanOnce(path);
                        }
                        copy.add(new ScheduledInstruction(resolvedPath, item));
                }
                switch (copiedPaths.size()) {
                    case 0:
                        copiedPaths = Collections.singleton(resolvedPath.getAbsolutePath());
                        break;
                    case 1:
                        copiedPaths = new HashSet<String>(copiedPaths);
                    default:
                        copiedPaths.add(resolvedPath.getAbsolutePath());
                }
            }

            void delete(ProvisionUnitJournal unitJournal) throws ProvisionException {
                delete(unitJournal, deleteWithDirs, true);
                delete(unitJournal, delete, false);
            }

            private void delete(ProvisionUnitJournal unitJournal, List<ScheduledInstruction> delete, boolean withDirs)
                    throws ProvisionException {
                final String unitHomePath = withDirs ? unitEnv.getEnvironmentHome().getAbsolutePath() : null;
                for (ScheduledInstruction scheduled : delete) {
                    File targetFile = scheduled.targetFile;
                    unitJournal.record(scheduled.instruction, targetFile);
                    if (!IoUtils.recursiveDelete(targetFile)) {
                        throw ProvisionErrors.deleteFailed(targetFile);
                    }
                    if (unitHomePath != null) {
                        targetFile = targetFile.getParentFile();
                        while (targetFile.list().length == 0) {
                            if (targetFile.getAbsolutePath().equals(unitHomePath)) {
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

            void copy(ProvisionUnitJournal unitJournal) throws ProvisionException {
                for (ScheduledInstruction scheduled : copy) {
                    final File targetFile = scheduled.targetFile;
                    unitJournal.record(scheduled.instruction, targetFile);
                    if (!targetFile.getParentFile().exists()) {
                        if (!targetFile.getParentFile().mkdirs()) {
                            throw new ProvisionException(ProvisionErrors.couldNotCreateDir(targetFile.getParentFile()));
                        }
                    }
                    InputStream is = null;
                    FileOutputStream os = null;
                    try {
                        is = contentSrc.getInputStream(unitJournal.getUnitEnvironment(), scheduled.instruction.getPath());
                        os = new FileOutputStream(targetFile);
                        IoUtils.copyStream(is, os);
                    } catch (IOException e) {
                        throw ProvisionErrors.failedToCopyContentFromZIP(scheduled.instruction.getPath(), targetFile, e);
                    } finally {
                        IoUtils.safeClose(is);
                        IoUtils.safeClose(os);
                    }
                }
            }
        }
    }

    private static class ScheduledInstruction {
        final File targetFile;
        final ContentItemInstruction instruction;
        ScheduledInstruction(File targetFile, ContentItemInstruction instruction) {
            this.targetFile = targetFile;
            this.instruction = instruction;
        }
    }

    static class MutableEnvironmentHistory extends ProvisionEnvironmentHistory {

        static MutableEnvironmentHistory newInstance(File historyHome) {
            return new MutableEnvironmentHistory(historyHome);
        }

        private MutableEnvironmentHistory(File historyHome) {
            super(historyHome);
        }

        ProvisionEnvironment doRecord(ProvisionEnvironment currentEnv, ProvisionEnvironmentInstruction instruction, ProvisionEnvironmentJournal envJournal) throws ProvisionException {
            return record(currentEnv, instruction, envJournal);
        }

        ProvisionEnvironment doRollbackLast(ProvisionEnvironment currentEnv) throws ProvisionException {
            return rollbackLast(currentEnv);
        }
    }
}
