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
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.jboss.provision.EnvironmentHistoryRecord.UnitBackupRecord;
import org.jboss.provision.audit.ProvisionEnvironmentJournal;
import org.jboss.provision.info.ProvisionEnvironmentInfo;
import org.jboss.provision.info.ProvisionUnitInfo;
import org.jboss.provision.instruction.ProvisionEnvironmentInstruction;
import org.jboss.provision.io.FileTaskList;
import org.jboss.provision.io.IoUtils;

/**
 *
 * @author Alexey Loubyansky
 */
class ProvisionEnvironmentHistory {

    static ProvisionEnvironmentHistory getInstance(ProvisionEnvironment env) {
        assert env != null : ProvisionErrors.nullArgument("env");
        return new ProvisionEnvironmentHistory(new File(env.getEnvironmentHome(), ProvisionEnvironment.DEF_HISTORY_DIR));
    }

    static ProvisionEnvironmentHistory getInstance(File historyHome) {
        return new ProvisionEnvironmentHistory(historyHome);
    }

    static ProvisionEnvironmentHistory forEnvironment(File envHome) {
        return new ProvisionEnvironmentHistory(new File(envHome, ProvisionEnvironment.DEF_HISTORY_DIR));
    }

    static boolean storesHistory(File dir) throws ProvisionException {
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

    File getHistoryHome() {
        return historyHome;
    }

    protected ProvisionEnvironment record(ProvisionEnvironment currentEnv, ProvisionEnvironmentInstruction instruction, ProvisionEnvironmentJournal envJournal) throws ProvisionException {
        final EnvironmentHistoryRecord historyRecord = EnvironmentHistoryRecord.create(currentEnv, instruction);
        final ProvisionEnvironment updatedEnv = historyRecord.getUpdatedEnvironment();
        if(updatedEnv.getUnitNames().isEmpty()) { // delete the history when the environment is uninstalled
            IoUtils.recursiveDelete(historyHome);
        } else {
            final FileTaskList tasks = new FileTaskList();
            historyRecord.schedulePersistence(historyHome, envJournal, tasks);
            try {
                tasks.safeExecute();
            } catch (IOException e) {
                throw ProvisionErrors.failedToUpdateHistory(e);
            }
        }
        return updatedEnv;
    }

    protected ProvisionEnvironment rollbackLast(ProvisionEnvironment currentEnv) throws ProvisionException {
        final FileTaskList tasks = new FileTaskList();
        final EnvironmentHistoryRecord prevRecord = EnvironmentHistoryRecord.scheduleDeleteLast(historyHome, tasks);
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

    ProvisionEnvironment getCurrentEnvironment() throws ProvisionException {
        final EnvironmentHistoryRecord appliedInstr = EnvironmentHistoryRecord.loadLast(historyHome);
        return appliedInstr == null ? null : appliedInstr.getUpdatedEnvironment();
    }

    EnvironmentHistoryRecord getLastRecord() throws ProvisionException {
        return EnvironmentHistoryRecord.loadLast(historyHome);
    }

    UnitBackupRecord getLastUnitRecord(String unitName) throws ProvisionException {
        return UnitBackupRecord.loadLast(historyHome, unitName);
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
                        appliedInstr = appliedInstr.getPrevious();
                    }
                } catch(ProvisionException e) {
                    throw new IllegalStateException(e);
                }
                doNext = false;
            }};
    }

    Iterator<UnitBackupRecord> unitBackupRecords(final String unitName) {
        assert unitName != null : ProvisionErrors.nullArgument("unitName");
        return new Iterator<UnitBackupRecord>() {
            boolean doNext = true;
            UnitBackupRecord record;
            @Override
            public boolean hasNext() {
                if(doNext) {
                    doNext();
                }
                return record != null;
            }
            @Override
            public UnitBackupRecord next() {
                if(hasNext()) {
                    doNext = true;
                        return record;
                }
                throw new NoSuchElementException();
            }
            protected void doNext() {
                if(!doNext) {
                    return;
                }
                try {
                    if (record == null) {
                        record = UnitBackupRecord.loadLast(historyHome, unitName);
                    } else {
                        record = record.getPrevious();
                    }
                } catch(ProvisionException e) {
                    throw new IllegalStateException(e);
                }
                doNext = false;
            }};
    }

    Iterator<ProvisionEnvironmentInfo> environmentIterator() {
        return new Iterator<ProvisionEnvironmentInfo>() {
            final Iterator<EnvironmentHistoryRecord> delegate = appliedInstructions();
            @Override
            public boolean hasNext() {
                return delegate.hasNext();
            }
            @Override
            public ProvisionEnvironmentInfo next() {
                try {
                    return delegate.next().getUpdatedEnvironment().getEnvironmentInfo();
                } catch (ProvisionException e) {
                    throw new IllegalStateException(e);
                }
            }};
    }

    Iterator<ProvisionUnitInfo> unitIterator(final String unitName) {
        return new Iterator<ProvisionUnitInfo>() {
            final Iterator<UnitBackupRecord> delegate = unitBackupRecords(unitName);
            @Override
            public boolean hasNext() {
                return delegate.hasNext();
            }
            @Override
            public ProvisionUnitInfo next() {
                try {
                    return delegate.next().getUpdatedUnitInfo();
                } catch (ProvisionException e) {
                    throw new IllegalStateException(e);
                }
            }};
    }
}
