/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jboss.provision.EnvInstructionHistory.EnvRecord;
import org.jboss.provision.audit.ProvisionUnitJournal;
import org.jboss.provision.audit.UnitJournalRecord;
import org.jboss.provision.info.ContentPath;
import org.jboss.provision.info.ProvisionUnitInfo;
import org.jboss.provision.io.FileTask;
import org.jboss.provision.io.FileTaskList;
import org.jboss.provision.io.IoUtils;

/**
 * @author Alexey Loubyansky
 *
 */
public class UnitInstructionHistory extends InstructionHistory {

    private static final String UNITS = "units";
    private static final String BACKUP = "backup";
    private static final String UNIT_PATHS = "paths.txt";

    static File getBackupDir(EnvInstructionHistory envHistory, String unitName, String recordId) {
        return IoUtils.newFile(IoUtils.newFile(envHistory.recordsDir, UNITS, unitName), recordId, BACKUP);
    }

    static UnitInstructionHistory getInstance(EnvInstructionHistory envHistory, String unitName) {
        return new UnitInstructionHistory(envHistory, unitName);
    }

    private final EnvInstructionHistory envHistory;
    private final String unitName;

    private UnitInstructionHistory(EnvInstructionHistory envHistory, String unitName) {
        super(IoUtils.newFile(envHistory.recordsDir, UNITS, unitName));
        this.envHistory = envHistory;
        this.unitName = unitName;
    }

    List<String> getRecordIds() {
        return Arrays.asList(IoUtils.newFile(envHistory.recordsDir, UNITS, unitName).list(new FilenameFilter(){
            @Override
            public boolean accept(File dir, String name) {
                return new File(dir, name).isDirectory();
            }}));
    }

    UnitRecord createRecord(String id) {
        return createRecord(new File(recordsDir, id));
    }

    UnitRecord createRecord(File recordDir) {
        return new UnitRecord(recordDir);
    }

    void schedulePersistence(String id, ProvisionUnitJournal unitJournal, FileTaskList tasks) throws ProvisionException {
        createRecord(id).schedulePersistence(unitJournal, tasks);
    }

    void scheduleDelete(String id, FileTaskList tasks) throws ProvisionException {
        createRecord(id).scheduleDelete(tasks);
    }

    protected static Set<ContentPath> loadUnitCurrentPaths(File recordDir) throws ProvisionException {
        Set<ContentPath> paths = new HashSet<ContentPath>();
        final File pathsFile = new File(recordDir, UNIT_PATHS);
        if(pathsFile.exists()) {
            BufferedReader reader = null;
            try {
                reader = new BufferedReader(new FileReader(pathsFile));
                String line = reader.readLine();
                while(line != null) {
                    paths.add(ContentPath.forPath(line));
                    line = reader.readLine();
                }
                return paths;
            } catch (FileNotFoundException e) {
                throw ProvisionErrors.pathDoesNotExist(pathsFile);
            } catch (IOException e) {
                throw ProvisionErrors.readError(pathsFile, e);
            } finally {
                IoUtils.safeClose(reader);
            }
        }
        return paths;
    }

    UnitRecord loadLast() throws ProvisionException {
        final File recordDir = getLastAppliedDir();
        if(recordDir == null) {
            return null;
        }
        return createRecord(recordDir);
    }

    class UnitRecord extends Record {

        private final File recordDir;

        protected UnitRecord(File recordDir) {
            assert unitName != null : ProvisionErrors.nullArgument("unitName");
            this.recordDir = recordDir;
        }

        /*
         * (non-Javadoc)
         *
         * @see org.jboss.provision.InstructionHistoryRecord#getRecordDir()
         */
        @Override
        File getRecordDir() throws ProvisionException {
            return recordDir;
        }

        Set<ContentPath> loadPaths() throws ProvisionException {
            Set<ContentPath> paths = new HashSet<ContentPath>();
            final File pathsFile = new File(recordDir, UNIT_PATHS);
            if(pathsFile.exists()) {
                BufferedReader reader = null;
                try {
                    reader = new BufferedReader(new FileReader(pathsFile));
                    String line = reader.readLine();
                    while(line != null) {
                        paths.add(ContentPath.forPath(line));
                        line = reader.readLine();
                    }
                    return paths;
                } catch (FileNotFoundException e) {
                    throw ProvisionErrors.pathDoesNotExist(pathsFile);
                } catch (IOException e) {
                    throw ProvisionErrors.readError(pathsFile, e);
                } finally {
                    IoUtils.safeClose(reader);
                }
            }
            return paths;
        }

        UnitRecord getPrevious() throws ProvisionException {
            final String prevDir = getPreviousRecordId();
            if(prevDir == null) {
                return null;
            }
            return new UnitRecord(new File(recordDir.getParentFile(), prevDir));
        }

        UnitRecord getNext() throws ProvisionException {
            final String nextDir = getNextRecordId();
            if(nextDir == null) {
                return null;
            }
            return new UnitRecord(new File(recordDir.getParentFile(), nextDir));
        }

        ProvisionUnitInfo getUpdatedUnitInfo() throws ProvisionException {
            final EnvRecord envRecord = envHistory.loadRecord(recordDir.getName());
            return envRecord.getUpdatedEnvironment().getUnitEnvironment(unitName).getUnitInfo();
        }

        void schedulePersistence(ProvisionUnitJournal unitJournal, FileTaskList tasks) throws ProvisionException {
            super.schedulePersistence(recordDir.getName(), tasks);
            final File unitBackupDir = getFileToPersist(recordDir, BACKUP);
            if(!unitJournal.getContentBackupDir().exists()) {
                tasks.add(FileTask.mkdirs(unitBackupDir));
            } else {
                if (unitBackupDir.exists()) {
                    throw ProvisionErrors.pathAlreadyExists(unitBackupDir);
                }
                tasks.add(FileTask.copy(unitJournal.getContentBackupDir(), unitBackupDir));
            }

            final Set<ContentPath> unitPaths = loadUnitCurrentPaths(getLastAppliedDir());
            if(unitPaths.isEmpty()) {
                for(UnitJournalRecord record : unitJournal.getAdds()) {
                    unitPaths.add(record.getInstruction().getPath());
                }
            } else {
                if(!unitJournal.getDeletes().isEmpty()) {
                    for(UnitJournalRecord record : unitJournal.getDeletes()) {
                        unitPaths.remove(record.getInstruction().getPath());
                    }
                }
                if(!unitJournal.getAdds().isEmpty()) {
                    for(UnitJournalRecord record : unitJournal.getAdds()) {
                        unitPaths.add(record.getInstruction().getPath());
                    }
                }
            }
            final File pathsFile = getFileToPersist(recordDir, UNIT_PATHS);
            tasks.add(new FileTask() {
                @Override
                protected void execute() throws IOException {
                    BufferedWriter writer = null;
                    try {
                        writer = new BufferedWriter(new FileWriter(pathsFile));
                        for(ContentPath path : unitPaths) {
                            writer.write(path.toString());
                            writer.newLine();
                        }
                    } finally {
                        IoUtils.safeClose(writer);
                    }
                }
                @Override
                protected void rollback() throws IOException {
                    IoUtils.recursiveDelete(pathsFile);
                }
            });
        }

        void scheduleDelete(FileTaskList tasks) throws ProvisionException {
            super.scheduleDelete(recordDir.getName(), tasks);
        }
    }
}
