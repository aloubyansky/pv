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

package org.jboss.provision.io;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import org.jboss.provision.instruction.ProvisionEnvironmentInstruction;
import org.jboss.provision.io.ContentTask.BackupPathFactory;
import org.jboss.provision.util.HashUtils;

/**
 *
 * @author Alexey Loubyansky
 */
public class FSImage {

    static final String BACKUP_SUFFIX = ".fsimagebkp";

    public enum PathStatus {
        DELETE_SCHEDULED,
        NOT_SCHEDULED,
        WRITE_SCHEDULED
    }

    private static class OpDescr {
        static OpDescr newTask(ContentTask contentTask) {
            return new OpDescr(contentTask);
        }
        ContentTask contentTask;
        OpDescr(ContentTask contentTask) {
            this.contentTask = contentTask;
        }
        void setTask(ContentTask contentTask) {
            this.contentTask = contentTask;
        }
    }

    public FSImage() {}

    private Map<String, OpDescr> updates = new LinkedHashMap<String, OpDescr>();

    public void write(ContentWriter contentWriter) {
        final OpDescr descr = updates.get(contentWriter.original.getAbsolutePath());
        if(descr != null) {
            if(descr.contentTask.isDelete()) {
                return;
            }
            descr.setTask(contentWriter);
            return;
        }
        updates.put(contentWriter.original.getAbsolutePath(), OpDescr.newTask(contentWriter));
    }

    public void delete(File target) {
        scheduleDelete(target, new DeleteTask(target));
    }

    public void delete(File target, BackupPathFactory backupPathFactory, boolean cleanup) {
        scheduleDelete(target, new DeleteTask(target, backupPathFactory, cleanup));
    }

    protected void scheduleDelete(File target, ContentTask task) {
        if(target.isDirectory()) {
            for(File f : target.listFiles()) {
                scheduleDelete(f, DeleteTask.DELETE_FLAG);
            }
        }
        final OpDescr descr = updates.get(target.getAbsolutePath());
        if (descr != null) {
            if (!descr.contentTask.isDelete()) {
                descr.setTask(task);
            }
            return;
        }
        updates.put(target.getAbsolutePath(), OpDescr.newTask(task));
    }

    public void write(String content, File target) {
        write(new StringContentWriter(content, target));
    }

    public void write(File content, File target) {
        write(new FileContentWriter(content, target));
    }

    public void write(File content, File target, BackupPathFactory backupPathFactory, boolean cleanup) {
        write(new FileContentWriter(content, target, backupPathFactory, cleanup));
    }

    public void write(Properties content, File target) {
        write(ContentTask.forProperties(content, target));
    }

    public void write(ProvisionEnvironmentInstruction content, File target) {
        write(new ProvisionXmlWriter(content, target));
    }

    public void mkdirs(File target) {
        write(new MkDirsWriter(target));
    }

    public void copy(File src, File target) {
        write(new CopyContentWriter(src, target));
    }

    public String readContent(File target) throws IOException {
        final OpDescr opDescr = updates.get(target.getAbsolutePath());
        if(opDescr == null) {
            if(!target.exists()) {
                return null;
            }
            return FileUtils.readFile(target);
        }
        if(opDescr.contentTask.isDelete()) {
            return null;
        }
        return opDescr.contentTask.getContentString();
    }

    public boolean exists(File target) {
        final OpDescr opDescr = updates.get(target.getAbsolutePath());
        if(opDescr == null) {
            return target.exists();
        }
        return !opDescr.contentTask.isDelete();
    }

    public boolean isDeleted(File target) {
        final OpDescr opDescr = updates.get(target.getAbsolutePath());
        if(opDescr == null) {
            return false;
        }
        return opDescr.contentTask.isDelete();
    }

    public byte[] getHash(File target) throws IOException {
        final OpDescr opDescr = updates.get(target.getAbsolutePath());
        if(opDescr == null) {
            if(!target.exists()) {
                return null;
            }
            return HashUtils.hashFile(target);
        }
        if(opDescr.contentTask.isDelete()) {
            return null;
        }
        if(opDescr.contentTask.getContentFile() != null) {
            return HashUtils.hashFile(opDescr.contentTask.getContentFile());
        }
        if(opDescr.contentTask.getContentString() != null) {
            return HashUtils.hashBytes(opDescr.contentTask.getContentString().getBytes());
        }
        if(!opDescr.contentTask.getTarget().exists()) {
            return null;
        }
        return HashUtils.hashFile(opDescr.contentTask.getTarget());
    }

    public PathStatus getStatus(File target) {
        final OpDescr opDescr = updates.get(target.getAbsolutePath());
        if(opDescr == null) {
            return PathStatus.NOT_SCHEDULED;
        }
        return opDescr.contentTask.isDelete() ? PathStatus.DELETE_SCHEDULED : PathStatus.WRITE_SCHEDULED;
    }

    public void commit() throws IOException {

        final OpDescr[] ops = new OpDescr[updates.size()];
        int i = 0;

        // backup
        try {
            for (OpDescr op : updates.values()) {
                ops[i++] = op;
                op.contentTask.backup();
            }
        } catch (IOException | RuntimeException | Error e) {
            while(i > 0) {
                ops[--i].contentTask.cleanup();
            }
            throw e;
        }

        // execute
        try {
            i = 0;
            while(i < ops.length) {
                ops[i++].contentTask.execute();
            }
        } catch (IOException | RuntimeException | Error e) {
            while (i > 0) {
                try {
                    ops[--i].contentTask.revert();
                } catch(Throwable t) {
                    t.printStackTrace();
                }
            }
            throw e;
        }

        // cleanup
        while(i > 0) {
            try {
                ops[--i].contentTask.cleanup();
            } catch (IOException | RuntimeException | Error e) {
                e.printStackTrace();
            }
        }

        updates.clear();
    }

    public boolean isUntouched() {
        return updates.isEmpty();
    }

    public void logUpdates(PrintStream out) {
        for(OpDescr op : updates.values()) {
            out.println(op.contentTask);
        }
    }
}
