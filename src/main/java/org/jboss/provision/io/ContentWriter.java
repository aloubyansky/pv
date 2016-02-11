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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.jboss.provision.ProvisionErrors;

/**
 *
 * @author Alexey Loubyansky
 */
public abstract class ContentWriter extends ContentTask {
    protected ContentWriter(File target) {
        super(target);
    }
    protected ContentWriter(File target, BackupPathFactory backupPathFactory, boolean cleanup) {
        super(target, backupPathFactory, cleanup);
    }

    @Override
    public void execute() throws IOException {
        if(!original.getParentFile().exists() && !original.getParentFile().mkdirs()) {
            throw new IOException(ProvisionErrors.couldNotCreateDir(original.getParentFile()));
        }
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(original));
            write(writer);
        } finally {
            IoUtils.safeClose(writer);
        }
    }

    public abstract void write(BufferedWriter writer) throws IOException;
}