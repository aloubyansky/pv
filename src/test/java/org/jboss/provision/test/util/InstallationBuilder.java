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

package org.jboss.provision.test.util;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import org.jboss.provision.util.HashUtils;
import org.jboss.provision.util.IoUtils;

/**
 *
 * @author Alexey Loubyansky
 */
public class InstallationBuilder {

    private static final String TMP_DIR = "jbosspvtest";

    private static File nextTmpDir() {
        return FSUtils.nextTmpDir(TMP_DIR);
    }

    public static InstallationBuilder create() {
        return new InstallationBuilder();
    }

    private final File home;

    public InstallationBuilder() {
        this(nextTmpDir());
    }

    public InstallationBuilder(File home) {
        this.home = home;
        createDir(home);
    }

    public File getHome() {
        return home;
    }

    public InstallationBuilder createDir(String relativePath) {
        createDir(resolvePath(relativePath));
        return this;
    }

    public InstallationBuilder createFileWithRandomContent(String relativePath) {
        return createFile(relativePath, randomString());
    }

    public InstallationBuilder createFile(String relativePath, String content) {

        File f = resolvePath(relativePath);
        if(f.exists()) {
            throw pathAlreadyExists(f);
        }
        final File parent = f.getParentFile();
        if(!parent.exists()) {
            createDir(parent);
        }
        FSUtils.writeFile(f, content);
        return this;
    }

    public InstallationBuilder updateFileWithRandomContent(String relativePath) {
        return updateFile(relativePath, randomString());
    }

    public InstallationBuilder updateFile(String relativePath, String newContent) {
        delete(relativePath);
        createFile(relativePath, newContent);
        return this;
    }

    public InstallationBuilder delete(String relativePath) {
        IoUtils.recursiveDelete(resolvePath(relativePath));
        return this;
    }

    private static String randomString() {
        return UUID.randomUUID().toString();
    }

    private static void createDir(File dir) {
        if (!dir.exists() && !dir.mkdirs()) {
            throw dirNotCreated(dir);
        }
    }

    public File resolvePath(String relativePath) {
        return new File(home, toNativePath(relativePath));
    }

    public byte[] hashOf(String relativePath) throws IOException {
        return HashUtils.hashFile(resolvePath(relativePath));
    }

    private static String toNativePath(String path) {
        if(File.separatorChar == '\\') {
            return path.replace('/', '\\');
        }
        return path;
    }

    static IllegalStateException fileWriteFailed(File f, IOException e) {
        return error("Failed to write to " + f.getAbsolutePath(), e);
    }

    private static IllegalStateException pathAlreadyExists(File f) {
        return error("Path already exists " + f.getAbsolutePath());
    }

    private static IllegalStateException dirNotCreated(File dir) {
        return error("Failed to create dir " + dir.getAbsolutePath());
    }

    private static IllegalStateException error(String msg) {
        return new IllegalStateException(msg);
    }

    private static IllegalStateException error(String msg, Throwable t) {
        return new IllegalStateException(msg, t);
    }
}
