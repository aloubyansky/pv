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
import java.util.UUID;

/**
 *
 * @author Alexey Loubyansky
 */
class DeleteTask extends ContentTask {

    static ContentTask DELETE_FLAG = deleteFlag(new File(UUID.randomUUID().toString()));

    private static ContentTask deleteFlag(final File target) {
        return new ContentTask(target) {
            @Override
            public void execute() throws IOException {
            }
            @Override
            public boolean isDelete() {
                return true;
            }
            @Override
            public void backup() throws IOException {
            }
            @Override
            public void revert() throws IOException {
            }
            @Override
            public void cleanup() throws IOException {
            }
            @Override
            public String toString() {
                return "DeleteFlag for " + target.getAbsolutePath();
            }
        };
    };

    protected DeleteTask(File target) {
        super(target);
    }
    protected DeleteTask(File target, BackupPathFactory backupPathStrategy, boolean cleanup) {
        super(target, backupPathStrategy, cleanup);
    }
    @Override
    public boolean isDelete() {
        return true;
    }
    @Override
    public void execute() throws IOException {
        IoUtils.recursiveDelete(original);
    }
    @Override
    public String toString() {
        return "DeleteTask for " + original.getAbsolutePath();
    }
}