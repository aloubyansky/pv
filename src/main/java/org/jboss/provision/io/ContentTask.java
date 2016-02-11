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
import java.util.Properties;

import org.jboss.provision.ProvisionErrors;

/**
 *
 * @author Alexey Loubyansky
 */
public abstract class ContentTask {

    public interface BackupPathFactory {
        File getBackupFile(File original);
    }

    public static final BackupPathFactory DEFAULT_BACKUP_FACTORY = new BackupPathFactory() {
        @Override
        public File getBackupFile(File original) {
            return new File(original.getParentFile(), original.getName() + FSImage.BACKUP_SUFFIX);
        }
    };

    public static PropertiesContentWriter forProperties(Properties props, File target) {
        return new PropertiesContentWriter(props, target);
    }
    protected final File original;
    private File backup;
    private final BackupPathFactory backupPathFactory;
    private final boolean cleanup;

    ContentTask(File target) {
        this(target, DEFAULT_BACKUP_FACTORY, true);
    }

    protected ContentTask(File target, BackupPathFactory backupPathFactory, boolean cleanup) {
        this.original = target;
        this.backupPathFactory = backupPathFactory;
        this.cleanup = cleanup;
    }

    public File getTarget() {
        return original;
    }

    public boolean isDelete() {
        return false;
    }

    public String getContentString() {
        throw new UnsupportedOperationException();
    }

    public File getContentFile() {
        throw new UnsupportedOperationException();
    }

    public void backup() throws IOException {
        if (!original.exists()) {
            return;
        }
        backup = backupPathFactory.getBackupFile(original);
        if (backup.exists()) {
            backup = null;
            throw new IOException(ProvisionErrors.pathAlreadyExists(backup).getLocalizedMessage());
        }
        IoUtils.copyFile(original, backup);
    }

    public void revert() throws IOException {
        if (backup == null) {
            return;
        }
        if (backup.isDirectory()) {
            IoUtils.recursiveDelete(original);
        }
        IoUtils.copyFile(backup, original);
        IoUtils.recursiveDelete(backup);
        backup = null;
    }

    public void cleanup() throws IOException {
        if(!cleanup) {
            return;
        }
        if (backup == null) {
            return;
        }
        IoUtils.recursiveDelete(backup);
        backup = null;
    }

    public abstract void execute() throws IOException;

    @Override
    public String toString() {
        return "ContentTask for " + original.getAbsolutePath();
    }
}