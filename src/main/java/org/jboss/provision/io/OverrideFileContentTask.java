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

import org.jboss.provision.ProvisionErrors;

/**
 *
 * @author Alexey Loubyansky
 */
class OverrideFileContentTask extends WriteFileTask {
    private static final String BACKUP_SUFFIX = ".tmpbkp";
    private final File backup;
    OverrideFileContentTask(File src, String content) throws IOException {
        this(src, content, false);
    }
    OverrideFileContentTask(File src, String content, boolean withBackup) throws IOException {
        this(src, content, withBackup ? BACKUP_SUFFIX : null);
    }
    OverrideFileContentTask(File src, String content, String backupSuffix) throws IOException {
        super(src, content);
        if(backupSuffix == null) {
            backup = null;
        } else {
            backup = new File(src.getParentFile(), src.getName() + backupSuffix);
            if(backup.exists()) {
                throw new IOException(ProvisionErrors.pathAlreadyExists(backup));
            }
        }
    }
    @Override
    protected void execute() throws IOException {
        if(backup != null) {
            IoUtils.copy(trgFile, backup);
        }
        super.execute();
    }
    @Override
    protected
    void rollback() throws IOException {
        IoUtils.copy(backup, trgFile);
        IoUtils.recursiveDelete(backup);
    }
    @Override
    public void cleanup() throws IOException {
        IoUtils.recursiveDelete(backup);
    }
    @Override
    public String toString() {
        return "OverrideFileContentTask [trgFile=" + trgFile.getAbsolutePath() + ", content='" + content + "']";
    }

}
