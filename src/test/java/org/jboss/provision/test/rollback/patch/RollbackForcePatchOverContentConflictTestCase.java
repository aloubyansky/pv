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
import org.jboss.provision.ProvisionException;
import org.jboss.provision.UnitUpdatePolicy;
import org.jboss.provision.io.IoUtils;
import org.jboss.provision.test.application.ApplicationTestBase;
import org.jboss.provision.test.util.AssertUtil;
import org.jboss.provision.test.util.FSUtils;
import org.jboss.provision.test.util.InstallationBuilder;
import org.jboss.provision.tool.ProvisionPackage;
import org.jboss.provision.tool.ProvisionTool;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author Alexey Loubyansky
 */
public class RollbackForcePatchOverContentConflictTestCase extends ApplicationTestBase {

    private InstallationBuilder nextOriginal;
    private File tempDir;

    @Override
    protected void doInit() {
        nextOriginal = InstallationBuilder.create();
        tempDir = FSUtils.createTmpDir("pvrollbackforcedpatch");
    }

    @Override
    protected void doCleanUp() {
        IoUtils.recursiveDelete(nextOriginal.getHome());
        IoUtils.recursiveDelete(testInstall.getHome());
        IoUtils.recursiveDelete(tempDir);
    }

    @Test
    public void testMain() throws Exception {

        originalInstall.createFileWithRandomContent("a.txt")
            .createFileWithRandomContent("b/b.txt")
            .createFileWithRandomContent("c/c/c.txt")
            .createDir("d/e/f");

        IoUtils.copyFile(originalInstall.getHome(), nextOriginal.getHome());
        nextOriginal.updateFileWithRandomContent("a.txt")
            .delete("b/b.txt")
            .createFileWithRandomContent("b/bb.txt")
            .createFileWithRandomContent("d/d/d/d.txt")
            .createDir("g/h/i");

        ProvisionPackage.newBuilder()
            .setCurrentInstallationDir(originalInstall.getHome())
            .setTargetInstallationDir(nextOriginal.getHome())
            .setPackageOutputFile(archive)
            .buildPatch("patch1");

        IoUtils.copyFile(originalInstall.getHome(), testInstall.getHome());
        testInstall.createFileWithRandomContent("aa.txt")
            .createFileWithRandomContent("b/bbb.txt")
            .createFileWithRandomContent("c/c/cc.txt")
            .createFileWithRandomContent("d/e/f.txt")
            .updateFileWithRandomContent("b/b.txt");
        IoUtils.copyFile(testInstall.getHome(), tempDir);

        AssertUtil.assertIdentical(tempDir, testInstall.getHome());
        AssertUtil.assertExpectedFilesNotInTarget(originalInstall.getHome(), testInstall.getHome(), false);
        AssertUtil.assertExpectedFilesNotInTarget(nextOriginal.getHome(), testInstall.getHome(), false);

        ProvisionEnvironment env = ProvisionEnvironment.forUndefinedUnit()
                .setEnvironmentHome(testInstall.getHome()).build();
        try {
            ProvisionTool.apply(env, archive);
            Assert.fail("Modified content replaced");
        } catch(ProvisionException e) {
            // expected
        }

        AssertUtil.assertIdentical(tempDir, testInstall.getHome());
        AssertUtil.assertExpectedFilesNotInTarget(originalInstall.getHome(), testInstall.getHome(), false);
        AssertUtil.assertExpectedFilesNotInTarget(nextOriginal.getHome(), testInstall.getHome(), false);

        env = ProvisionEnvironment.forUndefinedUnit()
                .setEnvironmentHome(testInstall.getHome())
                .setDefaultUnitUpdatePolicy(UnitUpdatePolicy.FORCED).build();
        env = ProvisionTool.apply(env, archive);

        AssertUtil.assertNotIdentical(tempDir, testInstall.getHome(), true);
        AssertUtil.assertExpectedFilesNotInTarget(originalInstall.getHome(), testInstall.getHome(), false);
        AssertUtil.assertExpectedContentInTarget(nextOriginal.getHome(), testInstall.getHome(), true);

        env = ProvisionTool.rollbackLast(env);

        AssertUtil.assertExpectedFilesNotInTarget(nextOriginal.getHome(), testInstall.getHome(), false);
        AssertUtil.assertExpectedFilesNotInTarget(originalInstall.getHome(), testInstall.getHome(), false);
        AssertUtil.assertIdentical(tempDir, testInstall.getHome(), true);

        assertHistoryEmpty(env);
        assertCantRollback(env);
    }
}
