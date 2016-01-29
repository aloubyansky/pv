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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.xml.stream.XMLStreamException;

import org.jboss.provision.audit.AuditUtil;
import org.jboss.provision.audit.ProvisionEnvironmentJournal;
import org.jboss.provision.audit.ProvisionUnitJournal;
import org.jboss.provision.audit.UnitJournalRecord;
import org.jboss.provision.info.ContentPath;
import org.jboss.provision.info.ProvisionUnitInfo;
import org.jboss.provision.instruction.ProvisionEnvironmentInstruction;
import org.jboss.provision.instruction.ProvisionUnitInstruction;
import org.jboss.provision.io.FileTask;
import org.jboss.provision.io.FileTaskList;
import org.jboss.provision.io.FileUtils;
import org.jboss.provision.io.IoUtils;
import org.jboss.provision.xml.ProvisionXml;

/**
 *
 * @author Alexey Loubyansky
 */
class EnvironmentHistoryRecord {

    static final String UNITS = "units";
    static final String BACKUP = "backup";

    private static final String ENV_FILE = "env.properties";
    private static final String LAST_INSTR_TXT = "last.txt";
    private static final String PREV_INSTR_TXT = "prev.txt";
    private static final String UNIT_PATHS = "paths.txt";

    static EnvironmentHistoryRecord create(ProvisionEnvironment currentEnv, ProvisionEnvironmentInstruction instruction) throws ProvisionException {
        final ProvisionEnvironmentBuilder envBuilder = ProvisionEnvironment.builder();

        //env home
        envBuilder.setEnvironmentHome(currentEnv.getEnvironmentHome());

        // named locations
        for(String locationName : currentEnv.getLocationNames(false)) {
            envBuilder.nameLocation(locationName, currentEnv.getNamedLocation(locationName));
        }

        // default update policy
        envBuilder.setDefaultUnitUpdatePolicy(currentEnv.getUpdatePolicy());

        // units
        final Set<String> updatedUnits = new HashSet<String>(instruction.getUnitNames());
        for(String unitName : currentEnv.getUnitNames()) {
            if(updatedUnits.remove(unitName)) {
                final ProvisionUnitInstruction unitInstr = instruction.getUnitInstruction(unitName);
                // WARN this assumes the unit instruction conditions have been satisfied!!!
                final String newVersion = unitInstr.getResultingVersion();
                if(newVersion == null) {
                    // unit removed
                } else {
                    final ProvisionUnitEnvironment unitEnv = currentEnv.getUnitEnvironment(unitName);
                    if(newVersion.equals(unitEnv.getUnitInfo().getVersion())) {
                        if (unitInstr.getId() != null) {
                            envBuilder.addPatchedUnit(unitEnv, unitInstr.getId());
                        } else {
                            envBuilder.copyUnit(unitEnv);
                        }
                    } else {
                        envBuilder.addUpdatedUnit(unitEnv, newVersion);
                    }
                }
            } else {
                envBuilder.copyUnit(currentEnv.getUnitEnvironment(unitName));
            }
        }
        if(!updatedUnits.isEmpty()) {
            for(String newUnit : updatedUnits) {
                final ProvisionUnitInstruction unitInstr = instruction.getUnitInstruction(newUnit);
                if(unitInstr.getRequiredVersion() != null) {
                    throw ProvisionErrors.unitIsNotInstalled(newUnit);
                }
                envBuilder.addUnit(ProvisionUnitInfo.createInfo(unitInstr.getUnitName(), unitInstr.getResultingVersion()));
            }
        }
        return new EnvironmentHistoryRecord(envBuilder.build(), instruction);
    }

    static File getLastAppliedInstrDir(final File historyDir) throws ProvisionException {
        assert historyDir != null : ProvisionErrors.nullArgument("historyDir");
        final File lastTxt = IoUtils.newFile(historyDir, LAST_INSTR_TXT);
        if(!lastTxt.exists()) {
            return null;
        }
        String dirName;
        try {
            dirName = FileUtils.readFile(lastTxt);
        } catch (IOException e) {
            throw ProvisionErrors.readError(IoUtils.newFile(historyDir, LAST_INSTR_TXT), e);
        }
        return new File(historyDir, dirName);
    }

    static String loadPreviousInstructionId(File instrDir) throws ProvisionException {
        final File prevInstrTxt = new File(instrDir, PREV_INSTR_TXT);
        if(!prevInstrTxt.exists()) {
            return null;
        }
        try {
            return FileUtils.readFile(prevInstrTxt);
        } catch (IOException e) {
            throw ProvisionErrors.readError(prevInstrTxt, e);
        }
    }

    static EnvironmentHistoryRecord loadLast(File historyDir) throws ProvisionException {
        final File instrDir = getLastAppliedInstrDir(historyDir);
        if(instrDir == null) {
            return null;
        }
        return loadInstruction(instrDir);
    }

    static EnvironmentHistoryRecord scheduleDeleteLast(File historyDir, FileTaskList tasks) throws ProvisionException {
        final EnvironmentHistoryRecord lastRecord = loadLast(historyDir);
        if(lastRecord == null) {
            return null;
        }
        final File lastInstrTxt = new File(historyDir, LAST_INSTR_TXT);
        final String prevRecordDir = lastRecord.getPreviousInstructionDirName();
        if(prevRecordDir != null) {
            try {
                tasks.add(FileTask.override(lastInstrTxt, prevRecordDir));
            } catch (IOException e) {
                throw ProvisionErrors.failedToUpdateHistory(e);
            }
        } else {
            tasks.add(FileTask.delete(lastInstrTxt));
        }
        final File instrDir = lastRecord.getInstructionDirectory();
        tasks.add(FileTask.delete(instrDir));

        final File unitsDir = new File(historyDir, UNITS);
        for(String unitName : lastRecord.getAppliedInstruction().getUnitNames()) {
            final File unitDir = new File(unitsDir, unitName);
            final File unitInstrDir = new File(unitDir, instrDir.getName());
            final File unitLastInstrTxt = new File(unitDir, LAST_INSTR_TXT);
            final String prevUnitInstrId = loadPreviousInstructionId(unitInstrDir);
            if(prevUnitInstrId != null) {
                try {
                    tasks.add(FileTask.override(unitLastInstrTxt, prevUnitInstrId));
                } catch (IOException e) {
                    throw ProvisionErrors.failedToUpdateHistory(e);
                }
                tasks.add(FileTask.delete(unitInstrDir));
            } else {
                tasks.add(FileTask.delete(unitDir));
            }
        }

        return lastRecord.getPrevious();
    }

    private static EnvironmentHistoryRecord loadInstruction(final File instrDir) throws ProvisionException {
        if(instrDir.exists()) {
            if(!instrDir.isDirectory()) {
                throw new ProvisionException(ProvisionErrors.notADir(instrDir));
            }
        } else {
            throw ProvisionErrors.pathDoesNotExist(instrDir);
        }
        return new EnvironmentHistoryRecord(getFileToLoad(instrDir, ENV_FILE), getFileToLoad(instrDir, ProvisionXml.PROVISION_XML)){};
    }

    private static File getFileToPersist(final File instrDir, String name) throws ProvisionException {
        final File f = new File(instrDir, name);
        if(f.exists()) {
            throw ProvisionErrors.pathAlreadyExists(f);
        }
        return f;
    }

    private static File getFileToLoad(final File instrDir, String name) throws ProvisionException {
        final File f = new File(instrDir, name);
        if(!f.exists()) {
            throw ProvisionErrors.pathDoesNotExist(f);
        }
        return f;
    }

    protected File envFile;
    protected File instrXml;
    protected ProvisionEnvironment updatedEnv;
    protected ProvisionEnvironmentInstruction appliedInstruction;

    protected EnvironmentHistoryRecord(File envFile, File instrFile) {
        assert envFile != null : ProvisionErrors.nullArgument("envFile");
        assert instrFile != null : ProvisionErrors.nullArgument("instrFile");
        this.envFile = envFile;
        this.instrXml = instrFile;
    }

    protected EnvironmentHistoryRecord(ProvisionEnvironment updatedEnv, ProvisionEnvironmentInstruction appliedInstruction) {
        assert updatedEnv != null : ProvisionErrors.nullArgument("updatedEnv");
        assert appliedInstruction != null : ProvisionErrors.nullArgument("appliedInstruction");
        envFile = null;
        instrXml = null;
        this.updatedEnv = updatedEnv;
        this.appliedInstruction = appliedInstruction;
    }

    ProvisionEnvironment getUpdatedEnvironment() throws ProvisionException {
        if(updatedEnv == null) {
            updatedEnv = AuditUtil.loadEnv(envFile);
        }
        return updatedEnv;
    }

    ProvisionEnvironmentInstruction getAppliedInstruction() throws ProvisionException {
        if(appliedInstruction == null) {
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(instrXml);
                appliedInstruction = ProvisionXml.parse(fis);
            } catch (FileNotFoundException e) {
                throw ProvisionErrors.pathDoesNotExist(instrXml);
            } catch (XMLStreamException e) {
                throw ProvisionErrors.failedToParse(instrXml.getAbsolutePath(), e);
            } finally {
                IoUtils.safeClose(fis);
            }
        }
        return appliedInstruction;
    }

    ContentSource getBackup() throws ProvisionException {
        final File instrDir = getInstructionDirectory();
        return ContentSource.forBackup(instrDir);
    }

    File getInstructionDirectory() throws ProvisionException {
        if(instrXml == null) {
            throw ProvisionErrors.instructionIsNotAssociatedWithFile();
        }
        return instrXml.getParentFile();
    }

    String getPreviousInstructionDirName() throws ProvisionException {
        return loadPreviousInstructionId(getInstructionDirectory());
    }

    EnvironmentHistoryRecord getPrevious() throws ProvisionException {
        final String prevDir = getPreviousInstructionDirName();
        if(prevDir == null) {
            return null;
        }
        return loadInstruction(new File(envFile.getParentFile().getParentFile(), prevDir));
    }

    void schedulePersistence(File historyDir, ProvisionEnvironmentJournal envJournal, FileTaskList tasks) throws ProvisionException {
        assert historyDir != null : ProvisionErrors.nullArgument("historyDir");
        final File instrDir = new File(historyDir, UUID.randomUUID().toString());
        if(instrDir.exists()) {
            if(!instrDir.isDirectory()) {
                throw new ProvisionException(ProvisionErrors.notADir(instrDir));
            }
        } else if(!instrDir.mkdirs()) {
            throw new ProvisionException(ProvisionErrors.couldNotCreateDir(instrDir));
        }

        envFile = getFileToPersist(instrDir, ENV_FILE);
        instrXml = getFileToPersist(instrDir, ProvisionXml.PROVISION_XML);
        final File prevInstrTxt = getFileToPersist(instrDir, PREV_INSTR_TXT);
        final File lastInstrTxt = new File(historyDir, LAST_INSTR_TXT);
        final File lastAppliedInstrDir = getLastAppliedInstrDir(historyDir);

        tasks.add(AuditUtil.createRecordTask(updatedEnv, envFile));
        tasks.add(FileTask.writeProvisionXml(instrXml, appliedInstruction));
        if(lastAppliedInstrDir != null && lastAppliedInstrDir.exists()) {
            tasks.add(FileTask.write(prevInstrTxt, lastAppliedInstrDir.getName()));
        }
        try {
            if(lastInstrTxt.exists()) {
                tasks.add(FileTask.override(lastInstrTxt, instrDir.getName()));
            } else {
                tasks.add(FileTask.write(lastInstrTxt, instrDir.getName()));
            }
        } catch (IOException e) {
            throw ProvisionErrors.failedToUpdateHistory(e);
        }

        final String instrId = instrDir.getName();
        final File unitsDir = new File(historyDir, UNITS);
        if(!unitsDir.exists() && !unitsDir.mkdirs()) {
            throw new ProvisionException(ProvisionErrors.couldNotCreateDir(unitsDir));
        }
        for(ProvisionUnitJournal unitJournal : envJournal.getUnitJournals()) {
            final String unitName = unitJournal.getUnitEnvironment().getUnitInfo().getName();
            final File unitDir = new File(unitsDir, unitName);
            final File unitInstrDir = getFileToPersist(unitDir, instrId);
            final File unitBackupDir = getFileToPersist(unitInstrDir, BACKUP);
            if(!unitJournal.getContentBackupDir().exists()) {
                tasks.add(FileTask.mkdirs(unitBackupDir));
            } else {
                if (unitBackupDir.exists()) {
                    throw ProvisionErrors.pathAlreadyExists(unitBackupDir);
                }
                tasks.add(FileTask.copy(unitJournal.getContentBackupDir(), unitBackupDir));
            }

            final File unitPrevInstrTxt = getFileToPersist(unitInstrDir, PREV_INSTR_TXT);
            final File unitLastInstrTxt = new File(unitDir, LAST_INSTR_TXT);
            final File unitLastAppliedInstrDir = getLastAppliedInstrDir(unitDir);
            if(unitLastAppliedInstrDir != null && unitLastAppliedInstrDir.exists()) {
                tasks.add(FileTask.write(unitPrevInstrTxt, unitLastAppliedInstrDir.getName()));
            }

            final Set<ContentPath> unitPaths = loadUnitCurrentPaths(unitLastAppliedInstrDir);
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
            final File pathsFile = getFileToPersist(unitInstrDir, UNIT_PATHS);
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

            try {
                if(unitLastInstrTxt.exists()) {
                    tasks.add(FileTask.override(unitLastInstrTxt, unitInstrDir.getName()));
                } else {
                    tasks.add(FileTask.write(unitLastInstrTxt, unitInstrDir.getName()));
                }
            } catch (IOException e) {
                throw ProvisionErrors.failedToUpdateHistory(e);
            }
        }
    }

    static Set<ContentPath> loadUnitCurrentPaths(File unitInstrDir) throws ProvisionException {
        Set<ContentPath> paths = new HashSet<ContentPath>();
        final File pathsFile = new File(unitInstrDir, UNIT_PATHS);
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

    static class UnitBackupRecord {

        private static File getLastUnitRecordDir(File historyDir, String unitName) throws ProvisionException {
            final File unitDir = IoUtils.newFile(historyDir, UNITS, unitName);
            if(!unitDir.exists()) {
                return null;
            }
            return getLastAppliedInstrDir(unitDir);
        }

        static UnitBackupRecord loadLast(File historyDir, String unitName) throws ProvisionException {
            final File instrDir = getLastUnitRecordDir(historyDir, unitName);
            if(instrDir == null) {
                return null;
            }
            return new UnitBackupRecord(unitName, instrDir);
        }

        final String unitName;
        final File recordDir;

        protected UnitBackupRecord(String unitName, File recordDir) {
            assert unitName != null : ProvisionErrors.nullArgument("unitName");
            assert recordDir != null : ProvisionErrors.nullArgument("recordDir");
            this.unitName = unitName;
            this.recordDir = recordDir;
        }

        Collection<ContentPath> loadPaths() throws ProvisionException {
            return loadUnitCurrentPaths(recordDir);
        }

        EnvironmentHistoryRecord getEnvironmentRecord() throws ProvisionException {
            return loadInstruction(new File(recordDir.getParentFile().getParentFile().getParentFile(), recordDir.getName()));
        }

        ProvisionUnitInfo getUpdatedUnitInfo() throws ProvisionException {
            return getEnvironmentRecord().getUpdatedEnvironment().getUnitEnvironment(unitName).getUnitInfo();
        }

        String getPreviousRecordDirName() throws ProvisionException {
            return loadPreviousInstructionId(recordDir);
        }

        UnitBackupRecord getPrevious() throws ProvisionException {
            final String prevDir = getPreviousRecordDirName();
            if(prevDir == null) {
                return null;
            }
            return new UnitBackupRecord(unitName, new File(recordDir.getParentFile(), prevDir));
        }
    }
}
