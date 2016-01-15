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

import org.jboss.provision.tool.instruction.ProvisionEnvironmentInstruction;

/**
 *
 * @author Alexey Loubyansky
 */
public abstract class FileTask {

    public static FileTask copy(File src, File target) {
        return new CopyFileTask(src, target);
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

    abstract void execute() throws IOException;

    abstract void rollback() throws IOException;

    void safeRollback() {
        try {
            rollback();
        } catch(RuntimeException | Error | IOException e) {
            // ignore
        }
    }

    public void cleanup() throws IOException {
    }
}
