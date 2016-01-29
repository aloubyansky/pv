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
import java.util.Properties;

import org.jboss.provision.instruction.ProvisionEnvironmentInstruction;

/**
 *
 * @author Alexey Loubyansky
 */
public abstract class FileTask {

    public static FileTask copy(File src, File target) {
        return new CopyFileTask(src, target);
    }

    public static FileTask delete(File f) {
        return new DeleteFileTask(f);
    }

    public static FileTask mkdirs(File dir) {
        return new MkDirsTask(dir);
    }
    public static FileTask override(File target, String content) throws IOException {
        return new OverrideFileContentTask(target, content, true);
    }

    public static FileTask write(File target, String content) {
        return new WriteFileTask(target, content);
    }

    public static FileTask write(File target, Properties props) {
        return new WritePropertiesFileTask(target, props);
    }

    public static FileTask writeProvisionXml(File target, ProvisionEnvironmentInstruction instruction) {
        return new WriteProvisionXmlTask(target, instruction);
    }

    protected abstract void execute() throws IOException;

    protected abstract void rollback() throws IOException;

    protected void safeRollback() {
        try {
            rollback();
        } catch(RuntimeException | Error | IOException e) {
            // ignore
        }
    }

    public void cleanup() throws IOException {
    }
}
