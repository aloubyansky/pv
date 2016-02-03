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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author Alexey Loubyansky
 */
public class FileTaskList {

    private List<FileTask> tasks = Collections.emptyList();
    private Set<String> deletedPaths = Collections.<String>emptySet();
    private Map<String,String> written = Collections.<String, String>emptyMap();

    public FileTaskList delete(File f) {
        switch(deletedPaths.size()) {
            case 0:
                deletedPaths = Collections.singleton(f.getAbsolutePath());
                break;
            case 1:
                deletedPaths = new HashSet<String>(deletedPaths);
            default:
                deletedPaths.add(f.getAbsolutePath());
        }
        return add(FileTask.delete(f));
    }

    public FileTaskList override(File f, String content) throws IOException {
        switch(written.size()) {
            case 0:
                written = Collections.singletonMap(f.getAbsolutePath(), content);
                break;
            case 1:
                written = new HashMap<String, String>(written);
            default:
                written.put(f.getAbsolutePath(), content);
        }
        return add(FileTask.override(f, content));
    }

    public boolean isDeleted(File f) {
        return deletedPaths.contains(f.getAbsolutePath());
    }

    public String getWrittenContent(File f) {
        return written.get(f.getAbsolutePath());
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
}
