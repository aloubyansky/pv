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
package org.jboss.provision.test.packaging;

import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

import org.jboss.provision.test.util.InstallationBuilder;
import org.jboss.provision.util.HashUtils;
import org.jboss.provision.util.IoUtils;
import org.jboss.provision.util.Utils;
import org.jboss.provision.util.ZipUtils;
import org.junit.After;
import org.junit.Before;

/**
 *
 * @author Alexey Loubyansky
 */
public class PackagingTestBase {

    protected static final byte[] NA = new byte[0];
    protected InstallationBuilder home;
    protected File archiveDir;
    protected File archive;

    public PackagingTestBase() {
        super();
    }

    @Before
    public void init() throws Exception {
        home = InstallationBuilder.create();
        final File tmpDir = new File(Utils.getSystemProperty("java.io.tmpdir"));
        archiveDir = new File(tmpDir, "archivetestdir");
        archiveDir.mkdirs();
        archive = new File(archiveDir, "archive.zip");
        doInit();
    }

    @After
    public void cleanup() throws Exception {
        IoUtils.recursiveDelete(home.getHome());
        IoUtils.recursiveDelete(archiveDir);
        doCleanUp();
    }

    protected void doInit() {
    }

    protected void doCleanUp() {
    }

    protected File unzipPackage() throws IOException {
        final File unzipDir = new File(archiveDir, "unzip");
        ZipUtils.unzip(archive, unzipDir);
        return unzipDir;
    }

    protected void assertFilesContent(File dir, Map<String, byte[]> expectedPaths) throws IOException {
        for(File c : dir.listFiles()) {
            matchFiles(c, null, expectedPaths);
        }
        if(!expectedPaths.isEmpty()) {
            fail("Expected paths missing " + expectedPaths);
        }
    }

    private void matchFiles(File f, String relativePath, Map<String, byte[]> expectedPaths) throws IOException {
        relativePath = relativePath == null ? f.getName() : relativePath + '/' + f.getName();
        if(f.isFile()) {
            final byte[] hash = expectedPaths.remove(relativePath);
            if(hash == null) {
                fail("unexpected path " + relativePath);
            }
            if(hash != NA) {
                final byte[] actualHash = HashUtils.hashFile(f);
                if(!Arrays.equals(hash, actualHash)) {
                    fail("The hash of " + relativePath + " expected to be " + HashUtils.bytesToHexString(hash)
                            + " but was " + HashUtils.bytesToHexString(actualHash));
                }
            }
        } else {
            for(File c : f.listFiles()) {
                matchFiles(c, relativePath, expectedPaths);
            }
        }
    }

}