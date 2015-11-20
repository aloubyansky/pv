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
import java.util.Collections;
import java.util.List;

import org.jboss.provision.ProvisionEnvironment;
import org.jboss.provision.ProvisionErrors;
import org.jboss.provision.ProvisionException;
import org.jboss.provision.tool.instruction.ContentItemInstruction;
import org.jboss.provision.util.IoUtils;

/**
 *
 * @author Alexey Loubyansky
 */
class AuditSessionImpl implements AuditSession {

    private static final String AUDIT_DIR = ".pvaudit";
    private static final String CONTENT_DIR = "content";
    private static final String ENV_PROPS = "env.properties";
    private static final String INSTR_DIR = "instructions";
    private static final String INSTR_FILE_SUFFIX = ".properties";

    //private final ProvisionEnvironment env;
    private final File auditHome;
    private final File instructionsDir;
    private final File contentDir;

    private List<AuditRecord> recorded = Collections.emptyList();

    private boolean active;

    static AuditSessionImpl start(ProvisionEnvironment env) throws ProvisionException {
        final AuditSessionImpl session = new AuditSessionImpl(env);
        session.start();
        return session;
    }

    static AuditSessionImpl load(ProvisionEnvironment env) throws ProvisionException {
        final AuditSessionImpl session = new AuditSessionImpl(env);
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

    private AuditSessionImpl(ProvisionEnvironment env) throws ProvisionException {

        assert env != null : ProvisionErrors.nullArgument("env");

        auditHome = new File(env.getEnvironmentHome(), AUDIT_DIR);
        instructionsDir = new File(auditHome, INSTR_DIR);
        contentDir = new File(auditHome, CONTENT_DIR);
    }

    void start() throws ProvisionException {
        if(auditHome.exists()) {
            throw ProvisionErrors.cantStartNewAuditSessionOverExistingOne();
        } else if (!auditHome.mkdirs()) {
            throw ProvisionErrors.auditSessionInitFailed(ProvisionErrors.couldNotCreateDir(auditHome));
        }

        if(!instructionsDir.mkdir()) {
            throw ProvisionErrors.auditSessionInitFailed(ProvisionErrors.couldNotCreateDir(instructionsDir));
        }
        if(!contentDir.mkdir()) {
            throw ProvisionErrors.auditSessionInitFailed(ProvisionErrors.couldNotCreateDir(contentDir));
        }
        active = true;
    }

    void load() throws ProvisionException {

        assertDirToLoad(auditHome);
        assertDirToLoad(instructionsDir);
        assertDirToLoad(contentDir);

        final ProvisionEnvironment prevEnv = AuditUtil.loadEnv(new File(auditHome, ENV_PROPS));

        final List<String> names = Arrays.asList(instructionsDir.list());
        Collections.sort(names);

        recorded = new ArrayList<AuditRecord>(names.size());
        for(String name : names) {
            if(!name.endsWith(INSTR_FILE_SUFFIX)) {
                continue;
            }
            final File f = new File(instructionsDir, name);
            final ContentItemInstruction instruction = AuditUtil.load(f);

            String relativePath = instruction.getPath().getRelativePath();
            if(File.separatorChar == '\\') {
                relativePath = relativePath.replace('/', '\\');
            }
            File backupFile = new File(contentDir, relativePath);
            if(!backupFile.exists()) {
                backupFile = null;
            }

            recorded.add(new AuditRecordImpl(instruction, backupFile));
        }

        active = false;
    }

    /* (non-Javadoc)
     * @see org.jboss.provision.backup.BackupSession#isActive()
     */
    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void record(ProvisionEnvironment env) throws ProvisionException {
        AuditUtil.record(env, new File(auditHome, ENV_PROPS));
    }

    /* (non-Javadoc)
     * @see org.jboss.provision.backup.BackupSession#record(org.jboss.provision.tool.instruction.ContentItemInstruction, java.io.File)
     */
    @Override
    public void record(ContentItemInstruction instruction, File replacedFile) throws ProvisionException {

        if(!active) {
            throw ProvisionErrors.auditSessionNotActive();
        }

        File backup = null;
        if (replacedFile.exists()) {
            String relativePath = instruction.getPath().getRelativePath();
            if (File.separatorChar == '\\') {
                relativePath = relativePath.replace('/', '\\');
            }
            backup = new File(contentDir, relativePath);
            if (!backup.getParentFile().exists() && !backup.getParentFile().mkdirs()) {
                throw new ProvisionException(ProvisionErrors.couldNotCreateDir(backup.getParentFile()));
            }
            try {
                IoUtils.copy(replacedFile, backup);
            } catch (IOException e) {
                throw ProvisionErrors.failedToAuditInstruction(instruction, e);
            }
        }

        final AuditRecord record = new AuditRecordImpl(instruction, backup);
        switch(recorded.size()) {
            case 0:
                recorded = Collections.singletonList(record);
                break;
            case 1:
                recorded = new ArrayList<AuditRecord>(recorded);
            default:
                recorded.add(record);
        }

        AuditUtil.record(instruction, new File(instructionsDir, recorded.size() + INSTR_FILE_SUFFIX));
    }

    /* (non-Javadoc)
     * @see org.jboss.provision.backup.BackupSession#getRecorded()
     */
    @Override
    public List<AuditRecord> getRecorded() {
        return recorded;
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
        active = false;
    }

    private static class AuditRecordImpl implements AuditRecord {

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
