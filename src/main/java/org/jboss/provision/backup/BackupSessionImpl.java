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

package org.jboss.provision.backup;

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
class BackupSessionImpl implements BackupSession {

    private static final String INSTR_DIR = "instructions";
    private static final String CONTENT_DIR = "content";
    private static final String INSTR_FILE_SUFFIX = ".properties";

    //private final ProvisionEnvironment env;
    private final File instructionsDir;
    private final File contentDir;

    private List<InstructionBackup> recorded = Collections.emptyList();

    private boolean active;

    static BackupSessionImpl start(File backupDir) throws ProvisionException {

        assert backupDir != null : ProvisionErrors.nullArgument("backupDir");

        if(backupDir.exists()) {
            if(!backupDir.isDirectory()) {
                throw ProvisionErrors.backupSessionInitFailed(ProvisionErrors.notADir(backupDir));
            }
            if(backupDir.list().length != 0) {
                throw ProvisionErrors.backupSessionInitFailed(ProvisionErrors.dirIsNotEmpty(backupDir));
            }
        } else {
            if(!backupDir.mkdirs()) {
                throw ProvisionErrors.backupSessionInitFailed(ProvisionErrors.couldNotCreateDir(backupDir));
            }
        }

        final File instructionsDir = new File(backupDir, INSTR_DIR);
        if(!instructionsDir.mkdir()) {
            throw ProvisionErrors.backupSessionInitFailed(ProvisionErrors.couldNotCreateDir(instructionsDir));
        }
        final File contentDir = new File(backupDir, CONTENT_DIR);
        if(!contentDir.mkdir()) {
            throw ProvisionErrors.backupSessionInitFailed(ProvisionErrors.couldNotCreateDir(contentDir));
        }
        return new BackupSessionImpl(instructionsDir, contentDir);
    }

    static BackupSessionImpl load(File backupDir) throws ProvisionException {

        assert backupDir != null : ProvisionErrors.nullArgument("backupDir");

        assertDirToLoad(backupDir);

        final File instructionsDir = new File(backupDir, INSTR_DIR);
        assertDirToLoad(instructionsDir);

        final File contentDir = new File(backupDir, CONTENT_DIR);
        assertDirToLoad(contentDir);

        final List<String> names = Arrays.asList(instructionsDir.list());
        Collections.sort(names);

        List<InstructionBackup> recorded = new ArrayList<InstructionBackup>(names.size());
        for(String name : names) {
            if(!name.endsWith(INSTR_FILE_SUFFIX)) {
                continue;
            }
            final File f = new File(instructionsDir, name);
            final ContentItemInstruction instruction = BackupUtil.load(f);

            String relativePath = instruction.getPath().getRelativePath();
            if(File.separatorChar == '\\') {
                relativePath = relativePath.replace('/', '\\');
            }
            File contentFile = new File(contentDir, relativePath);
            if(!contentFile.exists()) {
                contentFile = null;
            }

            recorded.add(new InstructionBackupImpl(instruction, contentFile));
        }
        return new BackupSessionImpl(instructionsDir, contentDir, recorded);
    }

    private static void assertDirToLoad(final File dir) throws ProvisionException {
        if(!dir.exists()) {
            throw ProvisionErrors.backupSessionLoadFailed(ProvisionErrors.pathDoesNotExist(dir));
        }
        if(!dir.isDirectory()) {
            throw ProvisionErrors.backupSessionLoadFailed(ProvisionErrors.notADir(dir));
        }
    }

    private BackupSessionImpl(File instructionsDir, File contentDir) throws ProvisionException {

        this.instructionsDir = instructionsDir;
        this.contentDir = contentDir;
        active = true;
    }

    private BackupSessionImpl(File instructionsDir, File contentDir, List<InstructionBackup> recorded) throws ProvisionException {

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
    public void backup(ContentItemInstruction instruction, File replacedFile) throws ProvisionException {

        if(!active) {
            throw ProvisionErrors.backupSessionNotActive();
        }

        final InstructionBackup record = new InstructionBackupImpl(instruction, replacedFile);
        switch(recorded.size()) {
            case 0:
                recorded = Collections.singletonList(record);
                break;
            case 1:
                recorded = new ArrayList<InstructionBackup>(recorded);
            default:
                recorded.add(record);
        }

        String relativePath = instruction.getPath().getRelativePath();
        if(File.separatorChar == '\\') {
            relativePath = relativePath.replace('/', '\\');
        }
        File backup = new File(contentDir, relativePath);
        if(backup.mkdirs()) {
            throw ProvisionErrors.failedToBackupInstruction(instruction, ProvisionErrors.couldNotCreateDir(backup));
        }
        backup = new File(backup, replacedFile.getName());
        try {
            IoUtils.copy(replacedFile, backup);
        } catch (IOException e) {
            throw ProvisionErrors.failedToBackupInstruction(instruction, e);
        }

        BackupUtil.record(instruction, new File(instructionsDir, recorded.size() + INSTR_FILE_SUFFIX));
    }

    /* (non-Javadoc)
     * @see org.jboss.provision.backup.BackupSession#getRecorded()
     */
    @Override
    public List<InstructionBackup> getRecorded() {
        return recorded;
    }

    /* (non-Javadoc)
     * @see org.jboss.provision.backup.BackupSession#close()
     */
    @Override
    public void close() throws ProvisionException {
        active = false;
    }

    private static class InstructionBackupImpl implements InstructionBackup {

        private final ContentItemInstruction item;
        private final File f;

        private InstructionBackupImpl(ContentItemInstruction item, File f) throws ProvisionException {
            assert item != null : ProvisionErrors.nullArgument("item");
            assert f != null : ProvisionErrors.nullArgument("file");
            this.item = item;
            this.f = f;
            if(!f.exists()) {
                throw ProvisionErrors.pathDoesNotExist(f);
            }
        }

        @Override
        public ContentItemInstruction getInstruction() {
            return item;
        }

        @Override
        public File getReplacedFile() {
            return f;
        }
    }
}
