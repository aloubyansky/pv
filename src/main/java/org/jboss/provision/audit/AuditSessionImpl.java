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

import org.jboss.provision.ProvisionErrors;
import org.jboss.provision.ProvisionException;
import org.jboss.provision.tool.instruction.ContentItemInstruction;
import org.jboss.provision.util.IoUtils;

/**
 *
 * @author Alexey Loubyansky
 */
class AuditSessionImpl implements AuditSession {

    private static final String INSTR_DIR = "instructions";
    private static final String CONTENT_DIR = "content";
    private static final String INSTR_FILE_SUFFIX = ".properties";

    //private final ProvisionEnvironment env;
    private final File auditHome;
    private final File instructionsDir;
    private final File contentDir;

    private List<AuditRecord> recorded = Collections.emptyList();

    private boolean active;

    static AuditSessionImpl start(File auditHome) throws ProvisionException {

        assert auditHome != null : ProvisionErrors.nullArgument("auditHome");

        if(auditHome.exists()) {
            if(!auditHome.isDirectory()) {
                throw ProvisionErrors.auditSessionInitFailed(ProvisionErrors.notADir(auditHome));
            }
            if(auditHome.list().length != 0) {
                throw ProvisionErrors.dirIsNotEmpty(auditHome);
            }
        } else {
            if(!auditHome.mkdirs()) {
                throw ProvisionErrors.auditSessionInitFailed(ProvisionErrors.couldNotCreateDir(auditHome));
            }
        }

        final File instructionsDir = new File(auditHome, INSTR_DIR);
        if(!instructionsDir.mkdir()) {
            throw ProvisionErrors.auditSessionInitFailed(ProvisionErrors.couldNotCreateDir(instructionsDir));
        }
        final File contentDir = new File(auditHome, CONTENT_DIR);
        if(!contentDir.mkdir()) {
            throw ProvisionErrors.auditSessionInitFailed(ProvisionErrors.couldNotCreateDir(contentDir));
        }
        return new AuditSessionImpl(auditHome, instructionsDir, contentDir);
    }

    static AuditSessionImpl load(File auditHome) throws ProvisionException {

        assert auditHome != null : ProvisionErrors.nullArgument("auditHome");

        assertDirToLoad(auditHome);

        final File instructionsDir = new File(auditHome, INSTR_DIR);
        assertDirToLoad(instructionsDir);

        final File contentDir = new File(auditHome, CONTENT_DIR);
        assertDirToLoad(contentDir);

        final List<String> names = Arrays.asList(instructionsDir.list());
        Collections.sort(names);

        List<AuditRecord> recorded = new ArrayList<AuditRecord>(names.size());
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
        return new AuditSessionImpl(auditHome, instructionsDir, contentDir, recorded);
    }

    private static void assertDirToLoad(final File dir) throws ProvisionException {
        if(!dir.exists()) {
            throw ProvisionErrors.pathDoesNotExist(dir);
        }
        if(!dir.isDirectory()) {
            throw ProvisionErrors.failedToLoadAuditSession(ProvisionErrors.notADir(dir));
        }
    }

    private AuditSessionImpl(File backupDir, File instructionsDir, File contentDir) throws ProvisionException {

        this.auditHome = backupDir;
        this.instructionsDir = instructionsDir;
        this.contentDir = contentDir;
        active = true;
    }

    private AuditSessionImpl(File backupDir, File instructionsDir, File contentDir, List<AuditRecord> recorded) throws ProvisionException {

        this.auditHome = backupDir;
        this.instructionsDir = instructionsDir;
        this.contentDir = contentDir;
        this.recorded = recorded;
        active = false;
    }

    /* (non-Javadoc)
     * @see org.jboss.provision.backup.BackupSession#isActive()
     */
    @Override
    public boolean isActive() {
        return active;
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

    /* (non-Javadoc)
     * @see org.jboss.provision.backup.BackupSession#close()
     */
    @Override
    public void close() throws ProvisionException {
        active = false;
        IoUtils.recursiveDelete(auditHome);
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
