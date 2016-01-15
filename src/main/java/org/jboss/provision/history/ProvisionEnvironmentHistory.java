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
        return AppliedEnvironmentInstruction.getLastAppliedInstrDir(dir) != null;
    }

    private final File historyHome;

    private ProvisionEnvironmentHistory(File historyHome) {
        assert historyHome != null : ProvisionErrors.nullArgument("historyHome");
        this.historyHome = historyHome;
    }

    public ProvisionEnvironment update(ProvisionEnvironment currentEnv, ProvisionEnvironmentInstruction instruction, ProvisionEnvironmentJournal envJournal) throws ProvisionException {
        final AppliedEnvironmentInstruction appliedInstr = AppliedEnvironmentInstruction.create(currentEnv, instruction);
        final ProvisionEnvironment updatedEnv = appliedInstr.getUpdatedEnvironment();
        if(updatedEnv.getUnitNames().isEmpty()) { // delete the history when the environment is uninstalled
            IoUtils.recursiveDelete(historyHome);
        } else {
            final FileTaskList tasks = new FileTaskList();

            appliedInstr.schedulePersistence(historyHome, tasks);

            final String instrId = appliedInstr.getInstructionDirectory().getName();
            final File unitsDir = new File(historyHome, UNITS);
            if(!unitsDir.exists() && !unitsDir.mkdirs()) {
                throw new ProvisionException(ProvisionErrors.couldNotCreateDir(unitsDir));
            }
            for(ProvisionUnitJournal unitJournal : envJournal.getUnitJournals()) {
                final String unitName = unitJournal.getUnitEnvironment().getUnitInfo().getName();
                final File unitBackupDir = IoUtils.newFile(unitsDir, unitName, instrId);
                if(unitBackupDir.exists()) {
                    throw ProvisionErrors.pathAlreadyExists(unitBackupDir);
                }
                FileTask.copy(unitJournal.getContentBackupDir(), unitBackupDir);
            }

            try {
                tasks.safeExecute();
            } catch (IOException e) {
                throw ProvisionErrors.failedToUpdateHistory(e);
            }

        }
        return updatedEnv;
    }

    public ProvisionEnvironment getCurrentEnvironment() throws ProvisionException {
        final AppliedEnvironmentInstruction appliedInstr = AppliedEnvironmentInstruction.loadLast(historyHome);
        return appliedInstr == null ? null : appliedInstr.getUpdatedEnvironment();
    }

    public Iterator<ProvisionEnvironment> environmentIterator() {
        return new Iterator<ProvisionEnvironment>() {

            boolean doNext = true;
            AppliedEnvironmentInstruction appliedInstr;

            @Override
            public boolean hasNext() {
                if(doNext) {
                    doNext();
                }
                return appliedInstr != null;
            }
            @Override
            public ProvisionEnvironment next() {
                if(hasNext()) {
                    try {
                        final ProvisionEnvironment next = appliedInstr.getUpdatedEnvironment();
                        doNext = true;
                        return next;
                    } catch (ProvisionException e) {
                        throw new IllegalStateException(e);
                    }
                }
                throw new NoSuchElementException();
            }
            protected void doNext() {
                if(!doNext) {
                    return;
                }
                try {
                    if (appliedInstr == null) {
                        appliedInstr = AppliedEnvironmentInstruction.loadLast(historyHome);
                    } else {
                        appliedInstr = AppliedEnvironmentInstruction.loadPrevious(appliedInstr);
                    }
                } catch(ProvisionException e) {
                    throw new IllegalStateException(e);
                }
                doNext = false;
            }};
    }

    // TODO
    public Iterator<ProvisionUnitEnvironment> unitHistory(String unitName) {
        return null;
    }
}
