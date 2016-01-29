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
class OverrideFileTask extends CopyFileTask {
    private static final String BACKUP_SUFFIX = ".tmpbkp";
    private final File backup;
    OverrideFileTask(File src, File trg) throws IOException {
        this(src, trg, false);
    }
    OverrideFileTask(File src, File trg, boolean withBackup) throws IOException {
        this(src, trg, withBackup ? BACKUP_SUFFIX : null);
    }
    OverrideFileTask(File src, File trg, String backupSuffix) throws IOException {
        super(src, trg);
        if(backupSuffix == null) {
            backup = null;
        } else {
            backup = new File(trg.getParentFile(), trg.getName() + backupSuffix);
            if(backup.exists()) {
                throw new IOException(ProvisionErrors.pathAlreadyExists(backup));
            }
        }
    }
    @Override
    protected void execute() throws IOException {
        if(backup != null) {
            IoUtils.copy(trg, backup);
        }
        super.execute();
    }
    @Override
    protected
    void rollback() throws IOException {
        IoUtils.copy(backup, trg);
        IoUtils.recursiveDelete(backup);
    }
}
