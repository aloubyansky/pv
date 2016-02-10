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
import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

import org.jboss.provision.UnitInstructionHistory.UnitRecord;
import org.jboss.provision.audit.ProvisionEnvironmentJournal;
import org.jboss.provision.info.ContentPath;
import org.jboss.provision.info.ProvisionEnvironmentInfo;
import org.jboss.provision.info.ProvisionUnitInfo;
import org.jboss.provision.instruction.ProvisionEnvironmentInstruction;
import org.jboss.provision.io.FSImage;
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

/*    static ProvisionEnvironmentHistory getInstance(File historyHome) {
        return new ProvisionEnvironmentHistory(historyHome);
    }

    static ProvisionEnvironmentHistory forEnvironment(File envHome) {
        return new ProvisionEnvironmentHistory(new File(envHome, ProvisionEnvironment.DEF_HISTORY_DIR));
    }
*/
    static boolean storesHistory(File dir) throws ProvisionException {
        assert dir != null : ProvisionErrors.nullArgument("dir");
        if(!dir.exists()) {
            return false;
        }
        if(!dir.isDirectory()) {
            return false;
        }
        return EnvInstructionHistory.getInstance(dir).getLastAppliedId() != null;
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
        final EnvInstructionHistory.EnvRecord historyRecord = EnvInstructionHistory.getInstance(historyHome).createRecord(currentEnv, instruction);
        final ProvisionEnvironment updatedEnv = historyRecord.getUpdatedEnvironment();
        if(updatedEnv.getUnitNames().isEmpty()) { // delete the history when the environment is uninstalled
            IoUtils.recursiveDelete(historyHome);
        } else {
            final FSImage tasks = new FSImage();
            historyRecord.schedulePersistence(envJournal, tasks);
            try {
                tasks.commit();
            } catch (IOException e) {
                throw ProvisionErrors.failedToUpdateHistory(e);
            }
        }
        return updatedEnv;
    }

    protected ProvisionEnvironment rollbackLast(ProvisionEnvironment currentEnv) throws ProvisionException {
        final FSImage tasks = new FSImage();
        final EnvInstructionHistory.EnvRecord lastRecord = EnvInstructionHistory.getInstance(historyHome).loadLastApplied();
        lastRecord.scheduleDelete(tasks);
        final EnvInstructionHistory.EnvRecord prevRecord = lastRecord.getPrevious();
        if(tasks.isUntouched()) {
            return currentEnv;
        }
        try {
            tasks.commit();
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

    protected void rollbackPatches(ProvisionEnvironment currentEnv, String unitName) throws ProvisionException {

        ProvisionUnitEnvironment unitEnv = currentEnv.getUnitEnvironment(unitName);
        if(unitEnv == null) {
            throw ProvisionErrors.unitIsNotInstalled(unitName);
        }
        int patchesTotal = unitEnv.getUnitInfo().getPatches().size();
        if(patchesTotal == 0) {
            return;
        }

        final FSImage tasks = new FSImage();
        final EnvInstructionHistory envInstrHistory = EnvInstructionHistory.getInstance(historyHome);
        final UnitInstructionHistory unitHistory = UnitInstructionHistory.getInstance(envInstrHistory, unitName);
        UnitRecord unitRecord = unitHistory.loadLast();
        while(patchesTotal > 0 && unitRecord != null) {
            final EnvInstructionHistory.EnvRecord envRecord = envInstrHistory.loadRecord(unitRecord.getRecordDir().getName());
            assertRollbackForUnit(unitName, envRecord);
            envRecord.scheduleDelete(tasks);
            unitRecord = unitRecord.getPrevious();
            --patchesTotal;
        }

        if(!tasks.isUntouched()) {
            try {
                tasks.commit();
            } catch (IOException e) {
                throw ProvisionErrors.failedToUninstallUnit(unitEnv.getUnitInfo(), e);
            }
        }
    }

    protected void uninstall(ProvisionEnvironment currentEnv, String unitName) throws ProvisionException {
        final ProvisionUnitEnvironment unitEnv = currentEnv.getUnitEnvironment(unitName);
        if(unitEnv == null) {
            throw ProvisionErrors.unitIsNotInstalled(unitName);
        }
        final FSImage tasks = new FSImage();

        final EnvInstructionHistory envInstrHistory = EnvInstructionHistory.getInstance(historyHome);
        final UnitInstructionHistory unitHistory = UnitInstructionHistory.getInstance(envInstrHistory, unitName);
        for(String recordId : unitHistory.getRecordIds()) {
            final EnvInstructionHistory.EnvRecord envRecord = envInstrHistory.loadRecord(recordId);
            assertRollbackForUnit(unitName, envRecord);
            envRecord.scheduleDelete(tasks);
        }
        for(ContentPath path : unitEnv.getContentPaths()) {
            tasks.delete(unitEnv.resolvePath(path)); // TODO unless it is a shared path
        }
        tasks.delete(unitHistory.recordsDir);

        if(!tasks.isUntouched()) {
            try {
                tasks.commit();
            } catch (IOException e) {
                throw ProvisionErrors.failedToUninstallUnit(unitEnv.getUnitInfo(), e);
            }
        }
        currentEnv.removeUnit(unitName);
    }

    protected void assertRollbackForUnit(String unitName, final EnvInstructionHistory.EnvRecord envRecord)
            throws ProvisionException {
        final Set<String> affectedUnits = envRecord.getAppliedInstruction().getUnitNames();
        if(!Collections.singleton(unitName).equals(affectedUnits)) {
            throw ProvisionErrors.instructionTargetsOtherThanRequestedUnits(unitName, affectedUnits);
        }
    }

    ProvisionEnvironment getCurrentEnvironment() throws ProvisionException {
        final EnvInstructionHistory.EnvRecord appliedInstr = EnvInstructionHistory.getInstance(historyHome).loadLastApplied();
        return appliedInstr == null ? null : appliedInstr.getUpdatedEnvironment();
    }

    EnvInstructionHistory.EnvRecord getLastEnvironmentRecord() throws ProvisionException {
        return EnvInstructionHistory.getInstance(historyHome).loadLastApplied();
    }

    UnitInstructionHistory.UnitRecord getLastUnitRecord(String unitName) throws ProvisionException {
        return UnitInstructionHistory.getInstance(EnvInstructionHistory.getInstance(historyHome), unitName).loadLast();
    }

    Iterator<EnvInstructionHistory.EnvRecord> appliedInstructions() {
        return new Iterator<EnvInstructionHistory.EnvRecord>() {
            boolean doNext = true;
            EnvInstructionHistory.EnvRecord appliedInstr;
            @Override
            public boolean hasNext() {
                if(doNext) {
                    doNext();
                }
                return appliedInstr != null;
            }
            @Override
            public EnvInstructionHistory.EnvRecord next() {
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
                        appliedInstr = EnvInstructionHistory.getInstance(historyHome).loadLastApplied();
                    } else {
                        appliedInstr = appliedInstr.getPrevious();
                    }
                } catch(ProvisionException e) {
                    throw new IllegalStateException(e);
                }
                doNext = false;
            }};
    }

    Iterator<UnitRecord> unitBackupRecords(final String unitName) {
        assert unitName != null : ProvisionErrors.nullArgument("unitName");
        return new Iterator<UnitRecord>() {
            boolean doNext = true;
            UnitRecord record;
            @Override
            public boolean hasNext() {
                if(doNext) {
                    doNext();
                }
                return record != null;
            }
            @Override
            public UnitRecord next() {
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
                        record = UnitInstructionHistory.getInstance(EnvInstructionHistory.getInstance(historyHome), unitName).loadLast();
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
            final Iterator<EnvInstructionHistory.EnvRecord> delegate = appliedInstructions();
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
            final Iterator<UnitRecord> delegate = unitBackupRecords(unitName);
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
