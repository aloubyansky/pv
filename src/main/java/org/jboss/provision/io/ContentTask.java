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
import org.jboss.provision.instruction.ProvisionEnvironmentInstruction;

/**
 *
 * @author Alexey Loubyansky
 */
public abstract class ContentTask {

    public static DeleteTask delete(File target) {
        return new DeleteTask(target);
    }
    public static StringContentWriter forString(String content, File target) {
        return new StringContentWriter(content, target);
    }
    public static FileContentWriter forFile(File f, File target) {
        return new FileContentWriter(f, target);
    }
    public static PropertiesContentWriter forProperties(Properties props, File target) {
        return new PropertiesContentWriter(props, target);
    }
    public static ProvisionXmlWriter forProvisionXml(ProvisionEnvironmentInstruction instr, File target) {
        return new ProvisionXmlWriter(instr, target);
    }

    protected final File target;
    private File backupFile;

    protected ContentTask(File target) {
        this.target = target;
    }

    public File getTarget() {
        return target;
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

    void backup() throws IOException {
        if(!target.exists()) {
            return;
        }
        backupFile = new File(target.getParentFile(), target.getName() + FSImage.BACKUP_SUFFIX);
        if(backupFile.exists()) {
            backupFile = null;
            throw new IOException(ProvisionErrors.pathAlreadyExists(backupFile).getLocalizedMessage());
        }
        IoUtils.copyFile(target, backupFile);
    }
    void revert() throws IOException {
        if(backupFile == null) {
            return;
        }
        if(backupFile.isDirectory()) {
            IoUtils.recursiveDelete(target);
        }
        IoUtils.copyFile(backupFile, target);
        IoUtils.recursiveDelete(backupFile);
        backupFile = null;
    }
    void cleanup() throws IOException {
        if(backupFile == null) {
            return;
        }
        IoUtils.recursiveDelete(backupFile);
        backupFile = null;
    }
    public abstract void execute() throws IOException;

    @Override
    public String toString() {
        return "ContentTask for " + target.getAbsolutePath();
    }
}