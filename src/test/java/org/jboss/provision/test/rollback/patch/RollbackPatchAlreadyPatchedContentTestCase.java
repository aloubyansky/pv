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

package org.jboss.provision.test.rollback.patch;

import java.io.File;

import org.jboss.provision.ProvisionEnvironment;
import org.jboss.provision.instruction.ProvisionPackage;
import org.jboss.provision.io.IoUtils;
import org.jboss.provision.test.application.ApplicationTestBase;
import org.jboss.provision.test.util.AssertUtil;
import org.jboss.provision.test.util.FSUtils;
import org.jboss.provision.test.util.InstallationBuilder;
import org.junit.Test;

/**
 *
 * @author Alexey Loubyansky
 */
public class RollbackPatchAlreadyPatchedContentTestCase extends ApplicationTestBase {

    private InstallationBuilder nextOriginal;
    private File tempDir;

    @Override
    protected void doInit() {
        nextOriginal = InstallationBuilder.create();
        tempDir = FSUtils.createTmpDir("pvrollbackpatch");
    }

    @Override
    protected void doCleanUp() {
        IoUtils.recursiveDelete(nextOriginal.getHome());
        IoUtils.recursiveDelete(tempDir);
    }

    @Test
    public void testMain() throws Exception {

        originalInstall.createFileWithRandomContent("a.txt")
            .createFileWithRandomContent("a/b/c.txt")
            .createFileWithRandomContent("b/b.txt")
            .createFileWithRandomContent("b/b/c.txt")
            .createFileWithRandomContent("c/c/c.txt")
            .createDir("d/e/f");

        IoUtils.copyFile(originalInstall.getHome(), nextOriginal.getHome());

        nextOriginal.updateFileWithRandomContent("a.txt")
            .updateFileWithRandomContent("a/b/c.txt")
            .delete("b/b.txt")
            .delete("b/b/c.txt")
            .createFileWithRandomContent("b/bb.txt")
            .createFileWithRandomContent("d/d/d/d.txt")
            .createDir("g/h/i");

        ProvisionPackage.newBuilder()
            .setCurrentInstallationDir(originalInstall.getHome())
            .setTargetInstallationDir(nextOriginal.getHome())
            .setPackageOutputFile(archive)
            .buildPatch("patch1");

        IoUtils.copyFile(originalInstall.getHome(), testInstall.getHome());
        // update content of a path in the target installation
        IoUtils.copyFile(nextOriginal.resolvePath("a/b/c.txt"), testInstall.resolvePath("a/b/c.txt"));
        // delete content that does not exist in the target
        testInstall.delete("b/b.txt");

        IoUtils.copyFile(testInstall.getHome(), tempDir);

        AssertUtil.assertIdentical(testInstall.getHome(), tempDir);
        AssertUtil.assertNotIdentical(originalInstall.getHome(), testInstall.getHome(), true);
        AssertUtil.assertNotIdentical(nextOriginal.getHome(), testInstall.getHome(), true);

        ProvisionEnvironment env = ProvisionEnvironment.forUndefinedUnit().setEnvironmentHome(testInstall.getHome()).build();
        env.apply(archive);

        AssertUtil.assertNotIdentical(testInstall.getHome(), tempDir, true);
        AssertUtil.assertNotIdentical(originalInstall.getHome(), testInstall.getHome(), true);
        AssertUtil.assertIdentical(nextOriginal.getHome(), testInstall.getHome(), true);

        env.rollbackLast();

        AssertUtil.assertNotIdentical(nextOriginal.getHome(), testInstall.getHome(), true);
        AssertUtil.assertNotIdentical(originalInstall.getHome(), testInstall.getHome(), true);
        AssertUtil.assertIdentical(testInstall.getHome(), tempDir, true);

        AssertUtil.assertCantRollback(env);
        AssertUtil.assertHistoryEmpty(env);
    }
}
