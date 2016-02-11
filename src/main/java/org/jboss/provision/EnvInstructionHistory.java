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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.xml.stream.XMLStreamException;

import org.jboss.provision.audit.AuditUtil;
import org.jboss.provision.info.ProvisionUnitInfo;
import org.jboss.provision.instruction.ProvisionEnvironmentInstruction;
import org.jboss.provision.instruction.ProvisionUnitInstruction;
import org.jboss.provision.io.FSImage;
import org.jboss.provision.io.IoUtils;
import org.jboss.provision.xml.ProvisionXml;

/**
 * @author Alexey Loubyansy
 *
 */
class EnvInstructionHistory extends InstructionHistory {

    private static final String ENV_FILE = "env.properties";

    static EnvInstructionHistory getInstance(File historyDir) {
        return new EnvInstructionHistory(historyDir);
    }

    private EnvInstructionHistory(File historyDir) {
        super(historyDir);
    }

    EnvRecord createRecord(ProvisionEnvironment currentEnv, ProvisionEnvironmentInstruction instruction) throws ProvisionException {
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
        return new EnvRecord(envBuilder.build(), instruction);
    }

    EnvRecord loadRecord(final String recordId) throws ProvisionException {
        return loadRecord(new File(recordsDir, recordId));
    }

    EnvRecord loadRecord(final File recordDir) throws ProvisionException {
        if(recordDir.exists()) {
            if(!recordDir.isDirectory()) {
                throw new ProvisionException(ProvisionErrors.notADir(recordDir));
            }
        } else {
            throw ProvisionErrors.pathDoesNotExist(recordDir);
        }
        return new EnvRecord(getFileToLoad(recordDir, ENV_FILE), getFileToLoad(recordDir, ProvisionXml.PROVISION_XML)){};
    }

    EnvRecord loadLastApplied() throws ProvisionException {
        final File instrDir = getLastAppliedDir();
        if(instrDir == null) {
            return null;
        }
        return loadRecord(instrDir);
    }

    class EnvRecord extends Record {

        private final String recordId;
        protected File envFile;
        protected File instrXml;
        protected ProvisionEnvironment updatedEnv;
        protected ProvisionEnvironmentInstruction appliedInstruction;

        protected EnvRecord(File envFile, File instrFile) {
            assert envFile != null : ProvisionErrors.nullArgument("envFile");
            assert instrFile != null : ProvisionErrors.nullArgument("instrFile");
            this.envFile = envFile;
            this.instrXml = instrFile;
            this.recordId = envFile.getParentFile().getName();
        }

        protected EnvRecord(ProvisionEnvironment updatedEnv, ProvisionEnvironmentInstruction appliedInstruction) {
            assert updatedEnv != null : ProvisionErrors.nullArgument("updatedEnv");
            assert appliedInstruction != null : ProvisionErrors.nullArgument("appliedInstruction");
            this.updatedEnv = updatedEnv;
            this.appliedInstruction = appliedInstruction;

            recordId = UUID.randomUUID().toString();
        }

        String getRecordId() {
            return recordId;
        }

        EnvInstructionHistory getEnvironmentHistory() {
            return EnvInstructionHistory.this;
        }

        ProvisionEnvironment getUpdatedEnvironment() throws ProvisionException {
            if (updatedEnv == null) {
                final ProvisionEnvironmentBuilder envBuilder = ProvisionEnvironment.builder();
                AuditUtil.loadEnv(envBuilder, envFile);
                UnitInstructionHistory.loadUnitEnvs(EnvInstructionHistory.this, envBuilder, recordId);
                updatedEnv = envBuilder.build();
            }
            return updatedEnv;
        }

        ProvisionEnvironmentInstruction getAppliedInstruction() throws ProvisionException {
            if (appliedInstruction == null) {
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
            return ContentSource.forBackup(this);
        }

        @Override
        File getRecordDir() throws ProvisionException {
            if (instrXml == null) {
                throw ProvisionErrors.instructionIsNotAssociatedWithFile();
            }
            return instrXml.getParentFile();
        }

        void schedulePersistence(FSImage tasks, ProvisionEnvironmentInstruction instruction, Map<String, ApplicationContextImpl.Journal> journal)
                throws ProvisionException {
            final File recordDir = super.schedulePersistence(recordId, tasks);
            envFile = getFileToPersist(recordDir, ENV_FILE);
            tasks.write(AuditUtil.createWriter(updatedEnv, envFile));
            instrXml = getFileToPersist(recordDir, ProvisionXml.PROVISION_XML);
            tasks.write(appliedInstruction, instrXml);
            Set<String> notAffectedUnits = new HashSet<String>(updatedEnv.getUnitNames());
            for(String unitName : instruction.getUnitNames()) {
                UnitInstructionHistory.getInstance(EnvInstructionHistory.this, unitName)
                    .schedulePersistence(recordId, journal.get(unitName), updatedEnv.getUnitEnvironment(unitName), tasks);
                notAffectedUnits.remove(unitName);
            }
            if(!notAffectedUnits.isEmpty()) {
                for(String unitName : notAffectedUnits) {
                    UnitInstructionHistory.getInstance(EnvInstructionHistory.this, unitName).schedulePersistence(recordId, tasks);
                }
            }
        }

        void scheduleDelete(FSImage tasks) throws ProvisionException {
            UnitInstructionHistory.scheduleDelete(EnvInstructionHistory.this, tasks, recordId);
            super.scheduleDelete(recordId, tasks);
        }

        EnvRecord getPrevious() throws ProvisionException {
            final String prevId = getPreviousRecordId();
            if(prevId == null) {
                return null;
            }
            return loadRecord(prevId);
        }
    }
}
