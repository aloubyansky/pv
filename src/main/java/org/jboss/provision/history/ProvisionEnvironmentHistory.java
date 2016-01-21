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

package org.jboss.provision.history;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import org.jboss.provision.ProvisionEnvironment;
import org.jboss.provision.ProvisionErrors;
import org.jboss.provision.ProvisionException;
import org.jboss.provision.ProvisionUnitEnvironment;
import org.jboss.provision.audit.ProvisionEnvironmentJournal;
import org.jboss.provision.audit.ProvisionUnitJournal;
import org.jboss.provision.io.FileTask;
import org.jboss.provision.io.FileTaskList;
import org.jboss.provision.io.IoUtils;
import org.jboss.provision.tool.ContentSource;
import org.jboss.provision.tool.instruction.ProvisionEnvironmentInstruction;

/**
 *
 * @author Alexey Loubyansky
 */
public class ProvisionEnvironmentHistory {

    public static final String DEF_HISTORY_DIR = ".pvh";
    private static final String UNITS = "units";

    public static ProvisionEnvironmentHistory getInstance(ProvisionEnvironment env) {
        assert env != null : ProvisionErrors.nullArgument("env");
        return new ProvisionEnvironmentHistory(new File(env.getEnvironmentHome(), DEF_HISTORY_DIR));
    }

    public static ProvisionEnvironmentHistory getInstance(File historyHome) {
        return new ProvisionEnvironmentHistory(historyHome);
    }

    public static ProvisionEnvironmentHistory forEnvironment(File envHome) {
        return new ProvisionEnvironmentHistory(new File(envHome, DEF_HISTORY_DIR));
    }

    public static boolean storesHistory(File dir) throws ProvisionException {
        assert dir != null : ProvisionErrors.nullArgument("dir");
        if(!dir.exists()) {
            return false;
        }
        if(!dir.isDirectory()) {
            return false;
        }
        return EnvironmentHistoryRecord.getLastAppliedInstrDir(dir) != null;
    }

    private final File historyHome;

    protected ProvisionEnvironmentHistory(File historyHome) {
        assert historyHome != null : ProvisionErrors.nullArgument("historyHome");
        this.historyHome = historyHome;
    }

    public File getHistoryHome() {
        return historyHome;
    }

    protected ProvisionEnvironment record(ProvisionEnvironment currentEnv, ProvisionEnvironmentInstruction instruction, ProvisionEnvironmentJournal envJournal) throws ProvisionException {
        final EnvironmentHistoryRecord historyRecord = EnvironmentHistoryRecord.create(currentEnv, instruction);
        final ProvisionEnvironment updatedEnv = historyRecord.getUpdatedEnvironment();
        if(updatedEnv.getUnitNames().isEmpty()) { // delete the history when the environment is uninstalled
            IoUtils.recursiveDelete(historyHome);
        } else {
            final FileTaskList tasks = new FileTaskList();
            historyRecord.schedulePersistence(historyHome, tasks);

            final String instrId = historyRecord.getInstructionDirectory().getName();
            final File unitsDir = new File(historyHome, UNITS);
            if(!unitsDir.exists() && !unitsDir.mkdirs()) {
                throw new ProvisionException(ProvisionErrors.couldNotCreateDir(unitsDir));
            }
            for(ProvisionUnitJournal unitJournal : envJournal.getUnitJournals()) {
                final String unitName = unitJournal.getUnitEnvironment().getUnitInfo().getName();
                final File unitBackupDir = IoUtils.newFile(unitsDir, unitName, instrId);
                if(!unitJournal.getContentBackupDir().exists()) {
                    tasks.add(FileTask.mkdirs(unitBackupDir));
                } else {
                    if (unitBackupDir.exists()) {
                        throw ProvisionErrors.pathAlreadyExists(unitBackupDir);
                    }
                    tasks.add(FileTask.copy(unitJournal.getContentBackupDir(), unitBackupDir));
                }
            }

            try {
                tasks.safeExecute();
            } catch (IOException e) {
                throw ProvisionErrors.failedToUpdateHistory(e);
            }
        }
        return updatedEnv;
    }

    protected ProvisionEnvironment rollbackLast(ProvisionEnvironment currentEnv) throws ProvisionException {
        final ProvisionEnvironmentHistory history = ProvisionEnvironmentHistory.getInstance(currentEnv);
        final FileTaskList tasks = new FileTaskList();
        final EnvironmentHistoryRecord prevRecord = EnvironmentHistoryRecord.scheduleDeleteLast(history.getHistoryHome(), tasks);
        if(tasks.isEmpty()) {
            return currentEnv;
        }
        try {
            tasks.safeExecute();
        } catch (IOException e) {
            throw ProvisionErrors.failedToUpdateHistory(e);
        }
        if(prevRecord == null) {
            IoUtils.recursiveDelete(historyHome);
            return ProvisionEnvironment.builder().setEnvironmentHome(currentEnv.getEnvironmentHome()).build();
        }
        if(prevRecord.getUpdatedEnvironment().getUnitNames().isEmpty()) { // delete the history when the environment is uninstalled
            IoUtils.recursiveDelete(historyHome);
        }
        return prevRecord.getUpdatedEnvironment();
    }

    public ProvisionEnvironment getCurrentEnvironment() throws ProvisionException {
        final EnvironmentHistoryRecord appliedInstr = EnvironmentHistoryRecord.loadLast(historyHome);
        return appliedInstr == null ? null : appliedInstr.getUpdatedEnvironment();
    }

    public EnvironmentHistoryRecord getLastRecord() throws ProvisionException {
        return EnvironmentHistoryRecord.loadLast(historyHome);
    }

    // TODO
    public ProvisionEnvironment rollbackToPrevious() throws ProvisionException {
        final EnvironmentHistoryRecord appliedInstr = EnvironmentHistoryRecord.loadLast(historyHome);
        final String prevInstrDir = appliedInstr.getPreviousInstructionDirName();
        if(prevInstrDir == null) {
            throw ProvisionErrors.noHistoryRecordedUntilThisPoint();
        }

        ContentSource.forBackup(appliedInstr.getInstructionDirectory());
        final ProvisionEnvironmentInstruction rollbackInstr = appliedInstr.getAppliedInstruction().getRollback();
        return null;
    }

    Iterator<EnvironmentHistoryRecord> appliedInstructions() {
        return new Iterator<EnvironmentHistoryRecord>() {
            boolean doNext = true;
            EnvironmentHistoryRecord appliedInstr;
            @Override
            public boolean hasNext() {
                if(doNext) {
                    doNext();
                }
                return appliedInstr != null;
            }
            @Override
            public EnvironmentHistoryRecord next() {
                if(hasNext()) {
                    doNext = true;
                        return appliedInstr;
                }
                throw new NoSuchElementException();
            }
            protected void doNext() {
                if(!doNext) {
                    return;
                }
                try {
                    if (appliedInstr == null) {
                        appliedInstr = EnvironmentHistoryRecord.loadLast(historyHome);
                    } else {
                        appliedInstr = EnvironmentHistoryRecord.loadPrevious(appliedInstr);
                    }
                } catch(ProvisionException e) {
                    throw new IllegalStateException(e);
                }
                doNext = false;
            }};
    }

    public Iterator<ProvisionEnvironment> environmentIterator() {
        return new Iterator<ProvisionEnvironment>() {
            final Iterator<EnvironmentHistoryRecord> delegate = appliedInstructions();
            @Override
            public boolean hasNext() {
                return delegate.hasNext();
            }
            @Override
            public ProvisionEnvironment next() {
                try {
                    return delegate.next().getUpdatedEnvironment();
                } catch (ProvisionException e) {
                    throw new IllegalStateException(e);
                }
            }};
    }

    // TODO
    public Iterator<ProvisionUnitEnvironment> unitHistory(String unitName) {
        return null;
    }
}
