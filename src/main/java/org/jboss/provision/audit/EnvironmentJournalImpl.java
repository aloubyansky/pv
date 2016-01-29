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

package org.jboss.provision.audit;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.provision.ProvisionEnvironment;
import org.jboss.provision.ProvisionErrors;
import org.jboss.provision.ProvisionException;
import org.jboss.provision.ProvisionUnitEnvironment;
import org.jboss.provision.instruction.ContentItemInstruction;
import org.jboss.provision.io.IoUtils;

/**
 *
 * @author Alexey Loubyansky
 */
class EnvironmentJournalImpl implements ProvisionEnvironmentJournal {

    private static final String AUDIT_DIR = ".pvaudit";
    private static final String CONTENT_DIR = "content";
    private static final String ENV_PROPS = "env.properties";
    private static final String INSTR_DIR = "instructions";
    private static final String INSTR_FILE_SUFFIX = ".properties";

    //private final ProvisionEnvironment env;
    private final File auditHome;
    private Map<String, ProvisionUnitJournal> unitJournals = Collections.emptyMap();

    private boolean recording;

    static EnvironmentJournalImpl start(ProvisionEnvironment env) throws ProvisionException {
        final EnvironmentJournalImpl session = new EnvironmentJournalImpl(env);
        session.start();
        return session;
    }

    static EnvironmentJournalImpl load(ProvisionEnvironment env) throws ProvisionException {
        final EnvironmentJournalImpl session = new EnvironmentJournalImpl(env);
        session.load();
        return session;
    }

    private static void assertDirToLoad(final File dir) throws ProvisionException {
        if(!dir.exists()) {
            throw ProvisionErrors.pathDoesNotExist(dir);
        }
        if(!dir.isDirectory()) {
            throw ProvisionErrors.failedToLoadAuditSession(ProvisionErrors.notADir(dir));
        }
    }

    private EnvironmentJournalImpl(ProvisionEnvironment env) throws ProvisionException {
        assert env != null : ProvisionErrors.nullArgument("env");
        auditHome = new File(env.getEnvironmentHome(), AUDIT_DIR);
    }

    void start() throws ProvisionException {
        if(auditHome.exists()) {
            throw ProvisionErrors.cantStartNewAuditSessionOverExistingOne();
        } else if (!auditHome.mkdirs()) {
            throw ProvisionErrors.auditSessionInitFailed(ProvisionErrors.couldNotCreateDir(auditHome));
        }
        recording = true;
    }

    void load() throws ProvisionException {
        assertDirToLoad(auditHome);
        recording = false;
        final ProvisionEnvironment prevEnv = AuditUtil.loadEnv(new File(auditHome, ENV_PROPS));
        for(String unitName : prevEnv.getUnitNames()) {
            final ProvisionUnitJournal unitJournal = getUnitJournal(prevEnv.getUnitEnvironment(unitName));
            unitJournal.load();
        }
    }

    /* (non-Javadoc)
     * @see org.jboss.provision.backup.BackupSession#isActive()
     */
    @Override
    public boolean isRecording() {
        return recording;
    }

    @Override
    public void record(ProvisionEnvironment env) throws ProvisionException {
        AuditUtil.record(env, new File(auditHome, ENV_PROPS));
    }

    @Override
    public void discardBackup() throws ProvisionException {
        IoUtils.recursiveDelete(auditHome);
    }

    /* (non-Javadoc)
     * @see org.jboss.provision.backup.BackupSession#close()
     */
    @Override
    public void close() throws ProvisionException {
        recording = false;
    }

    @Override
    public ProvisionUnitJournal getUnitJournal(ProvisionUnitEnvironment unitEnv) throws ProvisionException {
        assert unitEnv != null : ProvisionErrors.nullArgument("unitEnv");
        ProvisionUnitJournal unitJournal = unitJournals.get(unitEnv.getUnitInfo().getName());
        if(unitJournal != null) {
            return unitJournal;
        }
        final File unitAuditDir = new File(auditHome, unitEnv.getUnitInfo().getName());
        if(!unitAuditDir.exists() && !unitAuditDir.mkdirs()) {
            throw new ProvisionException(ProvisionErrors.couldNotCreateDir(unitAuditDir));
        }
        unitJournal = new UnitAuditJournalImpl(unitEnv, unitAuditDir);
        switch(unitJournals.size()) {
            case 0:
                unitJournals = Collections.singletonMap(unitEnv.getUnitInfo().getName(), unitJournal);
            case 1:
                unitJournals = new HashMap<String, ProvisionUnitJournal>(unitJournals);
            default:
                unitJournals.put(unitEnv.getUnitInfo().getName(), unitJournal);
        }
        return unitJournal;
    }

    @Override
    public Collection<ProvisionUnitJournal> getUnitJournals() {
        return unitJournals.values();
    }

    private class UnitAuditJournalImpl implements ProvisionUnitJournal {

        private final ProvisionUnitEnvironment unitEnv;
        private final File unitAuditDir;
        private final File contentBackupDir;
        private final File instrDir;
        private List<UnitJournalRecord> adds = Collections.emptyList();
        private List<UnitJournalRecord> deletes = Collections.emptyList();
        private List<UnitJournalRecord> updates = Collections.emptyList();
        private int recordCount = 1;

        protected UnitAuditJournalImpl(ProvisionUnitEnvironment unitEnv, File unitAuditDir) {
            assert unitEnv != null : ProvisionErrors.nullArgument("unitEnv");
            assert unitAuditDir != null : ProvisionErrors.nullArgument("unitAuditDir");
            this.unitEnv = unitEnv;
            this.unitAuditDir = unitAuditDir;
            contentBackupDir = new File(unitAuditDir, CONTENT_DIR);
            instrDir = new File(unitAuditDir, INSTR_DIR);
        }

        @Override
        public ProvisionUnitEnvironment getUnitEnvironment() {
            return unitEnv;
        }

        @Override
        public void record(ContentItemInstruction instruction, File replacedFile) throws ProvisionException {
            if(!recording) {
                throw ProvisionErrors.auditJournalIsNotRecording();
            }

            File backup = null;
            if (replacedFile.exists()) {
                backup = IoUtils.newFile(contentBackupDir, instruction.getPath().getFSRelativePath());
                if (!backup.getParentFile().exists() && !backup.getParentFile().mkdirs()) {
                    throw new ProvisionException(ProvisionErrors.couldNotCreateDir(backup.getParentFile()));
                }
                try {
                    IoUtils.copy(replacedFile, backup);
                } catch (IOException e) {
                    throw ProvisionErrors.failedToAuditInstruction(instruction, e);
                }
            }

            final UnitJournalRecord record = new AuditRecordImpl(instruction, backup);

            if(instruction.getContentHash() == null) {
                switch(deletes.size()) {
                    case 0:
                        deletes = Collections.singletonList(record);
                        break;
                    case 1:
                        deletes = new ArrayList<UnitJournalRecord>(deletes);
                    default:
                        deletes.add(record);
                }
            } else if(instruction.getReplacedHash() == null) {
                switch(adds.size()) {
                    case 0:
                        adds = Collections.singletonList(record);
                        break;
                    case 1:
                        adds = new ArrayList<UnitJournalRecord>(adds);
                    default:
                        adds.add(record);
                }
            } else {
                switch(updates.size()) {
                    case 0:
                        updates = Collections.singletonList(record);
                        break;
                    case 1:
                        updates = new ArrayList<UnitJournalRecord>(updates);
                    default:
                        updates.add(record);
                }
            }
            AuditUtil.record(instruction, IoUtils.newFile(instrDir, recordCount++ + INSTR_FILE_SUFFIX));
        }

        @Override
        public List<UnitJournalRecord> getAdds() {
            return adds;
        }

        @Override
        public List<UnitJournalRecord> getDeletes() {
            return deletes;
        }

        @Override
        public List<UnitJournalRecord> getUpdates() {
            return updates;
        }

        @Override
        public void load() throws ProvisionException {
            if(recording) {
                throw ProvisionErrors.auditJournalIsRecording();
            }
            assertDirToLoad(unitAuditDir);
            adds = new ArrayList<UnitJournalRecord>();
            final List<String> names = Arrays.asList(instrDir.list());
            Collections.sort(names);
            for (String name : names) {
                if (!name.endsWith(INSTR_FILE_SUFFIX)) {
                    continue;
                }
                final File f = new File(instrDir, name);
                final ContentItemInstruction instruction = AuditUtil.load(f);
                File backupFile = new File(contentBackupDir, instruction.getPath().getFSRelativePath());
                if (!backupFile.exists()) {
                    backupFile = null;
                }
                adds.add(new AuditRecordImpl(instruction, backupFile));
            }
        }

        @Override
        public File getContentBackupDir() {
            return contentBackupDir;
        }
    }

    private static class AuditRecordImpl implements UnitJournalRecord {
        private final ContentItemInstruction item;
        private final File backup;
        private AuditRecordImpl(ContentItemInstruction item, File backup) throws ProvisionException {
            assert item != null : ProvisionErrors.nullArgument("item");
            this.item = item;
            this.backup = backup;
        }
        @Override
        public ContentItemInstruction getInstruction() {
            return item;
        }
        @Override
        public File getBackupFile() {
            return backup;
        }
    }
}
