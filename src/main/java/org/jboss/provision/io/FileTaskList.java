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

package org.jboss.provision.io;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Alexey Loubyansky
 */
public class FileTaskList {

    private enum FsOp {
        DELETE,
        OVERRIDE
    }

    private static class TaskDescription {
        static TaskDescription delete(int index) {
            return new TaskDescription(index, FsOp.DELETE, null);
        }
        static TaskDescription override(int index, String content) {
            return new TaskDescription(index, FsOp.OVERRIDE, content);
        }
        private final int index;
        private FsOp op;
        private String content;
        TaskDescription(int index, FsOp op, String content) {
            this.index = index;
            this.op = op;
            this.content = content;
        }
        void override(String content) {
            op = FsOp.OVERRIDE;
            this.content = content;
        }
        void delete() {
            op = FsOp.DELETE;
            content = null;
        }
    }

    private List<FileTask> tasks = Collections.emptyList();
    private Map<String, TaskDescription> addedDescr = Collections.<String, TaskDescription>emptyMap();

    public FileTaskList delete(File f) {
        final String path = f.getAbsolutePath();
        TaskDescription descr = addedDescr.get(path);
        if(descr != null) {
            if(descr.op == FsOp.DELETE) {
                return this;
            }
            replace(descr.index, FileTask.delete(f));
            descr.delete();
            return this;
        }
        descr = TaskDescription.delete(tasks.size());
        switch(addedDescr.size()) {
            case 0:
                addedDescr = Collections.singletonMap(path, descr);
                break;
            case 1:
                addedDescr = new HashMap<String, TaskDescription>(addedDescr);
            default:
                addedDescr.put(path, descr);
        }
        return add(FileTask.delete(f));
    }

    public FileTaskList override(File f, String content) throws IOException {
        final String path = f.getAbsolutePath();
        TaskDescription descr = addedDescr.get(path);
        if(descr != null) {
            if(descr.op == FsOp.OVERRIDE) {
                descr.override(content);
            }
            replace(descr.index, FileTask.override(f, content));
            return this;
        }
        descr = TaskDescription.override(tasks.size(), content);
        switch(addedDescr.size()) {
            case 0:
                addedDescr = Collections.singletonMap(path, descr);
                break;
            case 1:
                addedDescr = new HashMap<String, TaskDescription>(addedDescr);
            default:
                addedDescr.put(path, descr);
        }
        return add(FileTask.override(f, content));
    }

    private void replace(int index, FileTask task) {
        if(tasks.size() == 1) {
            tasks = Collections.singletonList(task);
        } else {
            tasks.set(index, task);
        }
    }

    public boolean isDeleted(File f) {
        final TaskDescription descr = addedDescr.get(f.getAbsolutePath());
        return descr != null && descr.op == FsOp.DELETE;
    }

    public String getWrittenContent(File f) {
        final TaskDescription descr = addedDescr.get(f.getAbsolutePath());
        return descr == null ? null : descr.content;
    }

    public FileTaskList add(FileTask task) {
        switch(tasks.size()) {
            case 0:
                tasks = Collections.singletonList(task);
                break;
            case 1:
                tasks = new ArrayList<FileTask>(tasks);
            default:
                tasks.add(task);
        }
        return this;
    }

    public void safeExecute() throws IOException {
        safeExecute(true);
    }

    public void safeExecute(boolean cleanupOnSuccess) throws IOException {
        int i = 0;
        while(i < tasks.size()) {
            try {
                tasks.get(i++).execute();
            } catch (RuntimeException | Error | IOException t) {
                --i;
                while(i >= 0) {
                    tasks.get(i--).safeRollback();
                }
                throw t;
            }
        }
        if(cleanupOnSuccess) {
            for(FileTask task : tasks) {
                task.cleanup();
            }
        }
    }

    public void safeRollback() {
        for(FileTask task : tasks) {
            task.safeRollback();
        }
    }

    public boolean isEmpty() {
        return tasks.isEmpty();
    }

    public void clear() {
        tasks = Collections.emptyList();
    }

    @Override
    public String toString() {
        return "FileTaskList [tasks=" + tasks + "]";
    }
}
