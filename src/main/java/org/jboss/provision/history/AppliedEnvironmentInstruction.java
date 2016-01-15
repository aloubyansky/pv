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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.xml.stream.XMLStreamException;

import org.jboss.provision.ProvisionEnvironment;
import org.jboss.provision.ProvisionErrors;
import org.jboss.provision.ProvisionEnvironment.Builder;
import org.jboss.provision.ProvisionException;
import org.jboss.provision.audit.AuditUtil;
import org.jboss.provision.info.ProvisionUnitInfo;
import org.jboss.provision.io.FileTask;
import org.jboss.provision.io.FileTaskList;
import org.jboss.provision.io.FileUtils;
import org.jboss.provision.io.IoUtils;
import org.jboss.provision.tool.instruction.ProvisionEnvironmentInstruction;
import org.jboss.provision.tool.instruction.ProvisionUnitInstruction;
import org.jboss.provision.xml.ProvisionXml;

/**
 *
 * @author Alexey Loubyansky
 */
class AppliedEnvironmentInstruction {

    private static final String ENV_FILE = "env.properties";
    private static final String LAST_INSTR_TXT = "last.txt";
    private static final String PREV_INSTR_TXT = "prev.txt";

    static AppliedEnvironmentInstruction create(ProvisionEnvironment currentEnv, ProvisionEnvironmentInstruction instruction) throws ProvisionException {
        final Builder envBuilder = ProvisionEnvironment.builder();

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
                final String newVersion = unitInstr.getVersion();
                if(newVersion == null) {
                    // unit removed
                } else {
                    envBuilder.copyUnit(currentEnv.getUnitEnvironment(unitName));
                    envBuilder.addUnit(unitName, newVersion);
                }
            } else {
                envBuilder.copyUnit(currentEnv.getUnitEnvironment(unitName));
            }
        }
        if(!updatedUnits.isEmpty()) {
            for(String newUnit : updatedUnits) {
                final ProvisionUnitInstruction unitInstr = instruction.getUnitInstruction(newUnit);
                if(unitInstr.getReplacedVersion() != null) {
                    throw ProvisionErrors.unitIsNotInstalled(newUnit);
                }
                envBuilder.addUnit(ProvisionUnitInfo.createInfo(unitInstr.getName(), unitInstr.getVersion()));
            }
        }
        return new AppliedEnvironmentInstruction(envBuilder.build(), instruction);
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

    static AppliedEnvironmentInstruction loadLast(File historyDir) throws ProvisionException {
        final File instrDir = getLastAppliedInstrDir(historyDir);
        if(instrDir == null) {
            return null;
        }
        return loadInstruction(instrDir);
    }

    static AppliedEnvironmentInstruction loadPrevious(AppliedEnvironmentInstruction instr) throws ProvisionException {
        if(instr == null) {
            return null;
        }
        final String prevDir = instr.getPreviousInstructionDirName();
        if(prevDir == null) {
            return null;
        }
        return loadInstruction(new File(instr.envFile.getParentFile().getParentFile(), prevDir));
    }

    private static AppliedEnvironmentInstruction loadInstruction(final File instrDir) throws ProvisionException {
        if(instrDir.exists()) {
            if(!instrDir.isDirectory()) {
                throw new ProvisionException(ProvisionErrors.notADir(instrDir));
            }
        } else {
            throw ProvisionErrors.pathDoesNotExist(instrDir);
        }
        return new AppliedEnvironmentInstruction(getFileToLoad(instrDir, ENV_FILE), getFileToLoad(instrDir, ProvisionXml.PROVISION_XML)){};
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

    protected AppliedEnvironmentInstruction(File envFile, File instrFile) {
        assert envFile != null : ProvisionErrors.nullArgument("envFile");
        assert instrFile != null : ProvisionErrors.nullArgument("instrFile");
        this.envFile = envFile;
        this.instrXml = instrFile;
    }

    protected AppliedEnvironmentInstruction(ProvisionEnvironment updatedEnv, ProvisionEnvironmentInstruction appliedInstruction) {
        assert updatedEnv != null : ProvisionErrors.nullArgument("updatedEnv");
        assert appliedInstruction != null : ProvisionErrors.nullArgument("appliedInstruction");
        envFile = null;
        instrXml = null;
        this.updatedEnv = updatedEnv;
        this.appliedInstruction = appliedInstruction;
    }

    public ProvisionEnvironment getUpdatedEnvironment() throws ProvisionException {
        if(updatedEnv == null) {
            updatedEnv = AuditUtil.loadEnv(envFile);
        }
        return updatedEnv;
    }

    public ProvisionEnvironmentInstruction getAppliedInstruction() throws ProvisionException {
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

    public File getInstructionDirectory() throws ProvisionException {
        if(instrXml == null) {
            throw ProvisionErrors.fileIsNotAssociatedWithInstruction();
        }
        return instrXml.getParentFile();
    }

    public String getPreviousInstructionDirName() throws ProvisionException {
        if(instrXml == null) {
            throw ProvisionErrors.fileIsNotAssociatedWithInstruction();
        }
        final File prevInstrTxt = new File(instrXml.getParentFile(), PREV_INSTR_TXT);
        if(!prevInstrTxt.exists()) {
            return null;
        }
        try {
            return FileUtils.readFile(prevInstrTxt);
        } catch (IOException e) {
            throw ProvisionErrors.readError(prevInstrTxt, e);
        }
    }

    void schedulePersistence(File historyDir, FileTaskList tasks) throws ProvisionException {
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
    }
}
