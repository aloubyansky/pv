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
import java.io.IOException;

import org.jboss.provision.io.FileTask;
import org.jboss.provision.io.FileTaskList;
import org.jboss.provision.io.FileUtils;
import org.jboss.provision.io.IoUtils;

/**
 *
 * @author Alexey Loubyansky
 */
abstract class InstructionHistory {

    protected static final String LAST_INSTR_TXT = "last.txt";
    protected static final String NEXT_INSTR_TXT = "next.txt";
    protected static final String PREV_INSTR_TXT = "prev.txt";

    protected static File getFileToPersist(final File instrDir, String name) throws ProvisionException {
        final File f = new File(instrDir, name);
        if(f.exists()) {
            throw ProvisionErrors.pathAlreadyExists(f);
        }
        return f;
    }

    protected static File getFileToLoad(final File instrDir, String name) throws ProvisionException {
        final File f = new File(instrDir, name);
        if(!f.exists()) {
            throw ProvisionErrors.pathDoesNotExist(f);
        }
        return f;
    }

    protected final File recordsDir;

    protected InstructionHistory(File recordsDir) {
        assert recordsDir != null : ProvisionErrors.nullArgument("recordsDir");
        this.recordsDir = recordsDir;
    }

    File getLastAppliedDir() throws ProvisionException {
        final String lastId = getLastAppliedId();
        if(lastId == null) {
            return null;
        }
        return new File(recordsDir, lastId);
    }

    String getLastAppliedId() throws ProvisionException {
        final File lastTxt = IoUtils.newFile(recordsDir, LAST_INSTR_TXT);
        if(!lastTxt.exists()) {
            return null;
        }
        try {
            return FileUtils.readFile(lastTxt);
        } catch (IOException e) {
            throw ProvisionErrors.readError(IoUtils.newFile(recordsDir, LAST_INSTR_TXT), e);
        }
    }

    String getPreviousRecordId(File recordDir) throws ProvisionException {
        final File prevInstrTxt = new File(recordDir, PREV_INSTR_TXT);
        if(!prevInstrTxt.exists()) {
            return null;
        }
        try {
            return FileUtils.readFile(prevInstrTxt);
        } catch (IOException e) {
            throw ProvisionErrors.readError(prevInstrTxt, e);
        }
    }

    String getNextRecordId(File recordDir) throws ProvisionException {
        final File nextInstrTxt = new File(recordDir, NEXT_INSTR_TXT);
        if(!nextInstrTxt.exists()) {
            return null;
        }
        try {
            return FileUtils.readFile(nextInstrTxt);
        } catch (IOException e) {
            throw ProvisionErrors.readError(nextInstrTxt, e);
        }
    }

    abstract class Record {

        abstract File getRecordDir() throws ProvisionException;

        String getPreviousRecordId() throws ProvisionException {
            return InstructionHistory.this.getPreviousRecordId(getRecordDir());
        }

        String getNextRecordId() throws ProvisionException {
            return InstructionHistory.this.getNextRecordId(getRecordDir());
        }

        File schedulePersistence(String recordId, FileTaskList tasks) throws ProvisionException {

            final File recordDir = new File(recordsDir, recordId);
            if (recordDir.exists()) {
                if (!recordDir.isDirectory()) {
                    throw new ProvisionException(ProvisionErrors.notADir(recordDir));
                }
            } else if (!recordDir.mkdirs()) {
                throw new ProvisionException(ProvisionErrors.couldNotCreateDir(recordDir));
            }

            final File prevRecordTxt = getFileToPersist(recordDir, PREV_INSTR_TXT);
            final File lastRecordTxt = new File(recordsDir, LAST_INSTR_TXT);
            final File lastAppliedRecordDir = getLastAppliedDir();

            if (lastAppliedRecordDir != null && lastAppliedRecordDir.exists()) {
                tasks.add(FileTask.write(prevRecordTxt, lastAppliedRecordDir.getName()));
                final File nextInstrTxt = getFileToPersist(lastAppliedRecordDir, NEXT_INSTR_TXT);
                tasks.add(FileTask.write(nextInstrTxt, recordId));
            }
            try {
                if (lastRecordTxt.exists()) {
                    tasks.add(FileTask.override(lastRecordTxt, recordId));
                } else {
                    tasks.add(FileTask.write(lastRecordTxt, recordId));
                }
            } catch (IOException e) {
                throw ProvisionErrors.failedToUpdateHistory(e);
            }
            return recordDir;
        }

        void scheduleDelete(String recordId, FileTaskList tasks) throws ProvisionException {
            final File recordDir = new File(recordsDir, recordId);
            if (!recordDir.exists()) {
                return;
            }
            final String prevRecordId = InstructionHistory.this.getPreviousRecordId(recordDir);
            final String nextRecordId = InstructionHistory.this.getNextRecordId(recordDir);
            if (prevRecordId != null) {
                final File nextRecordTxt = IoUtils.newFile(recordsDir, prevRecordId, NEXT_INSTR_TXT);
                if (nextRecordId != null) {
                    if (nextRecordTxt.exists()) {
                        try {
                            tasks.add(FileTask.override(nextRecordTxt, nextRecordId));
                        } catch (IOException e) {
                            throw ProvisionErrors.failedToUpdateHistory(e);
                        }
                    } else {
                        throw new IllegalStateException("next record must exist");
                    }
                } else {
                    tasks.add(FileTask.delete(nextRecordTxt));
                }
            }
            if (nextRecordId != null) {
                final File prevRecordTxt = IoUtils.newFile(recordsDir, nextRecordId, PREV_INSTR_TXT);
                if (prevRecordId != null) {
                    if (prevRecordTxt.exists()) {
                        try {
                            tasks.add(FileTask.override(prevRecordTxt, prevRecordId));
                        } catch (IOException e) {
                            throw ProvisionErrors.failedToUpdateHistory(e);
                        }
                    } else {
                        throw new IllegalStateException("previous record must exist");
                    }
                } else {
                    tasks.add(FileTask.delete(prevRecordTxt));
                }
            }

            final String lastRecordId = getLastAppliedId();
            if (lastRecordId != null && lastRecordId.equals(recordId)) {
                if (nextRecordId != null) {
                    try {
                        tasks.add(FileTask.override(new File(recordsDir, LAST_INSTR_TXT), nextRecordId));
                    } catch (IOException e) {
                        throw ProvisionErrors.failedToUpdateHistory(e);
                    }
                } else if (prevRecordId != null) {
                    try {
                        tasks.add(FileTask.override(new File(recordsDir, LAST_INSTR_TXT), prevRecordId));
                    } catch (IOException e) {
                        throw ProvisionErrors.failedToUpdateHistory(e);
                    }
                } else {
                    tasks.add(FileTask.delete(new File(recordsDir, LAST_INSTR_TXT)));
                }
            }
            tasks.add(FileTask.delete(recordDir));
        }
    }
}