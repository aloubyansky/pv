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
import java.util.Set;
import java.util.UUID;

import org.jboss.provision.ProvisionEnvironment;
import org.jboss.provision.ProvisionErrors;
import org.jboss.provision.ProvisionEnvironment.Builder;
import org.jboss.provision.ProvisionException;
import org.jboss.provision.audit.AuditUtil;
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
public abstract class AppliedEnvironmentInstruction {

    private static final String ENV_FILE = "env.properties";
    private static final String LAST_INSTR_TXT = "last.txt";
    private static final String PREV_INSTR_TXT = "prev.txt";

    public static AppliedEnvironmentInstruction create(ProvisionEnvironment previousEnv, ProvisionEnvironmentInstruction instruction) throws ProvisionException {
        final Builder envBuilder = ProvisionEnvironment.newBuilder();

        //env home
        envBuilder.setEnvironmentHome(previousEnv.getEnvironmentHome());

        // named locations
        for(String locationName : previousEnv.getLocationNames(false)) {
            envBuilder.nameLocation(locationName, previousEnv.getNamedLocation(locationName));
        }

        // default update policy
        envBuilder.setDefaultUnitUpdatePolicy(previousEnv.getUpdatePolicy());

        // units
        final Set<String> updatedUnits = instruction.getUnitNames();
        for(String unitName : previousEnv.getUnitNames()) {
            if(updatedUnits.contains(unitName)) {
                final ProvisionUnitInstruction unitInstr = instruction.getUnitInstruction(unitName);
                // WARN this assumes the unit instruction conditions have been satisfied!!!
                final String newVersion = unitInstr.getVersion();
                if(newVersion == null) {
                    // unit removed
                } else {
                    envBuilder.copyUnit(previousEnv.getUnitEnvironment(unitName));
                    envBuilder.addUnit(unitName, newVersion);
                }
            } else {
                envBuilder.copyUnit(previousEnv.getUnitEnvironment(unitName));
            }
        }

        return new AppliedEnvironmentInstruction(envBuilder.build(), instruction){};
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

    static void persist(AppliedEnvironmentInstruction appliedInstr, File historyDir) throws ProvisionException {
        assert appliedInstr != null : ProvisionErrors.nullArgument("appliedInstruction");
        assert historyDir != null : ProvisionErrors.nullArgument("historyDir");
        final File instrDir = new File(historyDir, UUID.randomUUID().toString());
        if(instrDir.exists()) {
            if(!instrDir.isDirectory()) {
                throw new ProvisionException(ProvisionErrors.notADir(instrDir));
            }
        } else if(!instrDir.mkdirs()) {
            throw new ProvisionException(ProvisionErrors.couldNotCreateDir(instrDir));
        }

        final File envFile = getFileToPersist(instrDir, ENV_FILE);
        final File instrXml = getFileToPersist(instrDir, ProvisionXml.PROVISION_XML);
        final File prevInstrTxt = getFileToPersist(instrDir, PREV_INSTR_TXT);
        final File lastInstrTxt = new File(historyDir, LAST_INSTR_TXT);
        final File lastAppliedInstrDir = getLastAppliedInstrDir(historyDir);

        final FileTaskList tasks = new FileTaskList();
        tasks.add(AuditUtil.createRecordTask(appliedInstr.updatedEnv, envFile));
        tasks.add(FileTask.writeProvisionXml(instrXml, appliedInstr.appliedInstruction));
        if(lastAppliedInstrDir != null && lastAppliedInstrDir.exists()) {
            tasks.add(FileTask.write(prevInstrTxt, lastAppliedInstrDir.getName()));
        }
        try {
            tasks.add(FileTask.override(lastInstrTxt, instrDir.getName()));
            tasks.safeExecute();
        } catch (IOException e) {
            ProvisionErrors.failedToUpdateHistory(e);
        }
    }

    protected static File getFileToPersist(final File instrDir, String name) throws ProvisionException {
        final File f = new File(instrDir, name);
        if(f.exists()) {
            throw ProvisionErrors.pathAlreadyExists(f);
        }
        return f;
    }

    protected final ProvisionEnvironment updatedEnv;
    protected final ProvisionEnvironmentInstruction appliedInstruction;

    protected AppliedEnvironmentInstruction(ProvisionEnvironment updatedEnv, ProvisionEnvironmentInstruction appliedInstruction) {
        assert updatedEnv != null : ProvisionErrors.nullArgument("updatedEnv");
        assert appliedInstruction != null : ProvisionErrors.nullArgument("appliedInstruction");
        this.updatedEnv = updatedEnv;
        this.appliedInstruction = appliedInstruction;
    }

    public ProvisionEnvironment getUpdatedEnvironment() {
        return updatedEnv;
    }

    public ProvisionEnvironmentInstruction getAppliedInstruction() {
        return appliedInstruction;
    }
}
