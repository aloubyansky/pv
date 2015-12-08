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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author Alexey Loubyansky
 */
public class FileTaskList {
    private List<FileTask> tasks = Collections.emptyList();
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
