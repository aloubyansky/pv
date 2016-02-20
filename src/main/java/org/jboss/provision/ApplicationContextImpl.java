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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.stream.XMLStreamException;

import org.jboss.provision.ApplicationContext;
import org.jboss.provision.EnvInstructionHistory.EnvRecord;
import org.jboss.provision.ProvisionEnvironment;
import org.jboss.provision.ProvisionErrors;
import org.jboss.provision.ProvisionException;
import org.jboss.provision.ProvisionUnitEnvironment;
import org.jboss.provision.UnitInstructionHistory.UnitRecord;
import org.jboss.provision.UnitUpdatePolicy;
import org.jboss.provision.info.ContentPath;
import org.jboss.provision.info.ProvisionUnitInfo;
import org.jboss.provision.instruction.ContentItemInstruction;
import org.jboss.provision.instruction.InstructionCondition;
import org.jboss.provision.instruction.ProvisionEnvironmentInstruction;
import org.jboss.provision.instruction.ProvisionUnitInstruction;
import org.jboss.provision.instruction.UpdatePolicy;
import org.jboss.provision.io.FSImage;
import org.jboss.provision.io.IoUtils;
import org.jboss.provision.util.HashUtils;
import org.jboss.provision.xml.ProvisionXml;

/**
 *
 * @author Alexey Loubyansky
 */
class ApplicationContextImpl implements ApplicationContext {

    interface CommitCallback {

        EnvRecord getEnvRecord() throws ProvisionException;

        void schedule(ProvisionEnvironmentInstruction instruction) throws ProvisionException;

        ProvisionEnvironment commit() throws ProvisionException;

        void scheduleWrite(File target, ContentPath path, UnitRecord unitRecord) throws ProvisionException;

        void scheduleDelete(File target, ContentPath path, UnitRecord unitRecord) throws ProvisionException;
    }

    static class Journal {
        List<ContentPath> added = Collections.emptyList();
        List<ContentPath> deleted = Collections.emptyList();
        void add(ContentPath path) {
            switch(added.size()) {
                case 0:
                    added = Collections.singletonList(path);
                    break;
                case 1:
                    added = new ArrayList<ContentPath>(added);
                default:
                    added.add(path);
            }
        }
        void delete(ContentPath path) {
            switch(deleted.size()) {
                case 0:
                    deleted = Collections.singletonList(path);
                    break;
                case 1:
                    deleted = new ArrayList<ContentPath>(deleted);
                default:
                    deleted.add(path);
            }
        }
    }

    private ProvisionEnvironment env;
    private ProvisionUnitEnvironment unitEnv;

    private final FSImage fsImage = new FSImage();
    private Map<String, Journal> journal = Collections.emptyMap();
    private final CommitCallback callback;

    ApplicationContextImpl(ProvisionEnvironment env) throws ProvisionException {
        this(env, true);
    }

    ApplicationContextImpl(final ProvisionEnvironment env, boolean apply) {
        assert env != null : ProvisionErrors.nullArgument("env");
        this.env = env;
        if(apply) {
            callback = new CommitCallback() {
                private EnvRecord envRecord;
                @Override
                public EnvRecord getEnvRecord() throws ProvisionException {
                    if(envRecord != null) {
                        return envRecord;
                    }
                    final EnvInstructionHistory envHistory = env.getHistory().getEnvInstructionHistory();
                    envRecord = envHistory.createRecord(env);
                    return envRecord;
                }

                @Override
                public void schedule(ProvisionEnvironmentInstruction instruction) throws ProvisionException {
                    envRecord.schedulePersistence(fsImage, instruction, journal);
                }

                @Override
                public ProvisionEnvironment commit() throws ProvisionException {
                    try {
                        fsImage.commit();
                    } catch (IOException e) {
                        throw ProvisionErrors.failedToUpdateHistory(e);
                    }
                    final ProvisionEnvironment updatedEnv = envRecord.getUpdatedEnvironment();
                    // TODO this below may be not a good idea, only the actual uninstall probably should erase history,
                    // not a usual delete instruction
                    if(updatedEnv.getUnitNames().isEmpty()) { // delete the history when the environment is uninstalled
                        IoUtils.recursiveDelete(env.getHistory().getHistoryHome());
                    }
                    return updatedEnv;
                }

                @Override
                public void scheduleWrite(File target, ContentPath path, UnitRecord unitRecord) throws ProvisionException {
                    fsImage.write(target, unitEnv.resolvePath(path), unitRecord.createBackupPathFactory(path), false);
                }

                @Override
                public void scheduleDelete(File target, ContentPath path, UnitRecord unitRecord) throws ProvisionException {
                    fsImage.delete(unitEnv.resolvePath(path), unitRecord.createBackupPathFactory(path), false);
                }
            };
        } else {
            callback = new CommitCallback() {
                private EnvRecord envRecord;
                @Override
                public EnvRecord getEnvRecord() throws ProvisionException {
                    if(envRecord != null) {
                        return envRecord;
                    }
                    envRecord = env.getHistory().getEnvInstructionHistory().loadLastApplied();
                    return envRecord;
                }

                @Override
                public void schedule(ProvisionEnvironmentInstruction instruction) throws ProvisionException {
                    envRecord.scheduleDelete(fsImage);
                }

                @Override
                public ProvisionEnvironment commit() throws ProvisionException {
                    final EnvInstructionHistory.EnvRecord prevRecord = envRecord.getPrevious();
                    try {
                        fsImage.commit();
                    } catch (IOException e) {
                        throw ProvisionErrors.failedToUpdateHistory(e);
                    }
                    if(prevRecord == null) {
                        IoUtils.recursiveDelete(env.getHistory().getHistoryHome());
                        return ProvisionEnvironment.builder().setEnvironmentHome(env.getEnvironmentHome()).build();
                    }
                    final ProvisionEnvironment updatedEnv = prevRecord.getUpdatedEnvironment();
                    if(updatedEnv.getUnitNames().isEmpty()) { // delete the history when the environment is uninstalled
                        IoUtils.recursiveDelete(env.getHistory().getHistoryHome());
                    }
                    return updatedEnv;

                }

                @Override
                public void scheduleWrite(File target, ContentPath path, UnitRecord unitRecord) throws ProvisionException {
                    fsImage.write(target, unitEnv.resolvePath(path));
                }

                @Override
                public void scheduleDelete(File target, ContentPath path, UnitRecord unitRecord) throws ProvisionException {
                    fsImage.delete(unitEnv.resolvePath(path));
                }
            };
        }
    }

    void schedule(ProvisionEnvironmentInstruction instruction, ContentSource contentSrc) throws ProvisionException {
        final EnvRecord envRecord = callback.getEnvRecord();
        envRecord.updateEnvironment(instruction);
        scheduleTasks(instruction, envRecord, contentSrc);
        unitEnv = null;
        env = envRecord.getUpdatedEnvironment();
    }

    void scheduleUninstall(String unitName) throws ProvisionException {
        final EnvRecord activeRecord = callback.getEnvRecord();
        final ProvisionUnitEnvironment unitEnv = env.getUnitEnvironment(unitName);
        if(unitEnv == null) {
            throw ProvisionErrors.unitIsNotInstalled(unitName);
        }

        final EnvInstructionHistory envInstrHistory = env.getHistory().getEnvInstructionHistory();
        final UnitInstructionHistory unitHistory = UnitInstructionHistory.getInstance(envInstrHistory, unitName);
        for(String recordId : unitHistory.getRecordIds()) {
            final EnvInstructionHistory.EnvRecord envRecord = envInstrHistory.loadRecord(recordId);
            envRecord.assertRollbackForUnit(unitName);
            envRecord.scheduleDelete(fsImage);
        }
        final PathsOwnership pathsOwnership = env.getPathsOwnership();
        for(ContentPath path : unitEnv.getContentPaths()) {
            if(!pathsOwnership.removeOwner(unitEnv.resolvePath(path).getAbsolutePath(), unitName)) {
                fsImage.delete(unitEnv.resolvePath(path));
            }
        }
        fsImage.delete(unitHistory.recordsDir);
        activeRecord.uninstallUnit(unitName);
        env = activeRecord.getUpdatedEnvironment();
    }

    ProvisionEnvironment commit() throws ProvisionException {
        final ProvisionEnvironmentInstruction appliedInstr = callback.getEnvRecord().getAppliedInstruction();
        if(appliedInstr != null) {
            callback.schedule(appliedInstr);
        }
        return callback.commit();
    }

    private void scheduleTasks(ProvisionEnvironmentInstruction instructions, EnvRecord envRecord, ContentSource contentSrc) throws ProvisionException {

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
            scheduleTasks(unitInstr, envRecord, contentSrc);
        }
    }

    private void scheduleTasks(ProvisionUnitInstruction instructions, EnvRecord envRecord, ContentSource contentSrc) throws ProvisionException {

        final UnitUpdatePolicy updatePolicy = unitEnv.resolveUpdatePolicy();
        if (updatePolicy.getUnitPolicy() == UpdatePolicy.IGNORED) {
            return;
        }
        if (updatePolicy.getUnitPolicy() == UpdatePolicy.CONDITIONED) {
            for (InstructionCondition condition : instructions.getConditions()) {
                if (!condition.isSatisfied(this)) {
                    return;
                }
            }
        }

        String unitName = unitEnv.getUnitInfo().getName();
        final UnitRecord unitRecord = UnitInstructionHistory.getInstance(envRecord.getEnvironmentHistory(), unitName).createRecord(envRecord.getRecordId());
        final Journal unitJournal = getUnitJournal(unitName);
        final PathsOwnership pathsOwnership = env.getPathsOwnership();
        for (ContentItemInstruction item : instructions.getContentInstructions()) {
            final ContentPath path = item.getPath();
            final UpdatePolicy contentPolicy = updatePolicy.getContentPolicy(path.getRelativePath());
            if (contentPolicy == UpdatePolicy.IGNORED) {
                continue;
            }
            if (contentPolicy == UpdatePolicy.CONDITIONED) {
                final byte[] expectedHash = item.getReplacedHash();
                final File targetFile = unitEnv.resolvePath(item.getPath());
                final byte[] actualHash = getHash(targetFile);
                if(expectedHash == null) {
                    // the path is not expected to exist
                    if(actualHash != null) {
                        if(Arrays.equals(item.getContentHash(), actualHash)) {
                            // the existing content matches the new one
                            unitJournal.add(path);
                            pathsOwnership.addOwner(unitEnv.resolvePath(path).getAbsolutePath(), unitName);
                            continue;
                        }
                        throw ProvisionErrors.pathAlreadyExists(targetFile);
                    }
                } else {
                    if(item.getContentHash() == null) {
                        // delete
                        if(actualHash == null) {
                            // the target does not exist
                            pathsOwnership.removeOwner(unitEnv.resolvePath(path).getAbsolutePath(), unitName);
                            continue;
                        }
                        if(!Arrays.equals(expectedHash, actualHash)) {
                            // - it was modified by the user manually or using an external tool
                            // - it was overridden by another unit
                            //if (!pathsOwnership.isOnlyOwner(unitEnv.resolvePath(item.getPath()).getAbsolutePath(), unitName)) {
                            //    pathsOwnership.removeOwner(unitEnv.resolvePath(path).getAbsolutePath(), unitName);
                            //    continue;
                            //}
                            throw ProvisionErrors.pathHashMismatch(targetFile, HashUtils.bytesToHexString(expectedHash), HashUtils.bytesToHexString(actualHash));
                        }
                    } else {
                        if (actualHash == null) {
                            throw ProvisionErrors.pathDoesNotExist(targetFile);
                        }
                        if (Arrays.equals(item.getContentHash(), actualHash)) {
                            // the existing content matches the new one
                            continue;
                        }
                        if (!Arrays.equals(expectedHash, actualHash)) {
                            throw ProvisionErrors.pathHashMismatch(targetFile, HashUtils.bytesToHexString(expectedHash),
                                    HashUtils.bytesToHexString(actualHash));
                        }
                    }
                }

                int i = 0;
                while(i < item.getConditions().size()) {
                    InstructionCondition condition = item.getConditions().get(i++);
                    if (!condition.isSatisfied(this)) {
                        continue;
                    }
                }
            }

            final File f = contentSrc.getFile(unitEnv, path);
            if (item.getContentHash() == null) {
                // this check here is for rolling back a forced add of an item over a conflicting existing one which was
                // backed up
                if (f.exists()) {
                    callback.scheduleWrite(f, path, unitRecord);
                    pathsOwnership.addOwner(unitEnv.resolvePath(path).getAbsolutePath(), unitName);
                } else {
                    if(!pathsOwnership.removeOwner(unitEnv.resolvePath(path).getAbsolutePath(), unitName)) {
                        callback.scheduleDelete(f, path, unitRecord);
                    }
                    unitJournal.delete(path);
                }
            } else
            // this check here is for rolling back a delete of an item which has already been deleted
            if (f.exists()) {
                final File target = unitEnv.resolvePath(path);
                callback.scheduleWrite(f, path, unitRecord);
                pathsOwnership.addOwner(target.getAbsolutePath(), unitName);
                if(!target.exists()) { // TODO everything should be added to the journal
                    unitJournal.add(path);
                }
            }
        }
    }

    @Override
    public byte[] getHash(File target) throws ProvisionException {
        try {
            return fsImage.getHash(target);
        } catch (IOException e) {
            throw ProvisionErrors.hashCalculationFailed(target, e);
        }
    }

    private Journal getUnitJournal(String unitName) {
        Journal unitJournal = journal.get(unitName);
        if(unitJournal != null) {
            return unitJournal;
        }
        unitJournal = new Journal();
        switch(journal.size()) {
            case 0:
                journal = Collections.singletonMap(unitName, unitJournal);
                break;
            case 1:
                journal = new HashMap<String, Journal>(journal);
            default:
                journal.put(unitName, unitJournal);
        }
        return unitJournal;
    }
    /* (non-Javadoc)
     * @see org.jboss.provision.tool.instruction.ProvisionEnvironment#getUnitContentInfo(java.lang.String)
     */
    @Override
    public ProvisionUnitEnvironment getUnitEnvironment() {
        return unitEnv;
    }

    static ProvisionEnvironmentInstruction readInstruction(ProvisionEnvironment env, ContentSource contentSrc, File pkgFile) throws ProvisionException {
        assert pkgFile != null : ProvisionErrors.nullArgument("packageFile");
        if (!pkgFile.exists()) {
            throw ProvisionErrors.pathDoesNotExist(pkgFile);
        }
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
}
