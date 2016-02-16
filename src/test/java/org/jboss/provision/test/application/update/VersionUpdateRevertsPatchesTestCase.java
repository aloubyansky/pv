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

package org.jboss.provision.test.application.update;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;

import org.jboss.provision.ProvisionEnvironment;
import org.jboss.provision.info.ProvisionUnitInfo;
import org.jboss.provision.instruction.ProvisionPackage;
import org.jboss.provision.io.IoUtils;
import org.jboss.provision.test.application.ApplicationTestBase;
import org.jboss.provision.test.util.AssertUtil;
import org.jboss.provision.test.util.FSUtils;
import org.junit.Test;

/**
 *
 * @author Alexey Loubyansky
 */
public class VersionUpdateRevertsPatchesTestCase extends ApplicationTestBase {

    private File updatePatch;
    private File unitA_1_0_State;
    private File unitA_patch1_State;
    private File unitA_patch2_State;
    private File unitA_1_1_State;

    @Override
    public void doInit() {
        originalInstall.createFileWithRandomContent("a.txt")
            .createFileWithRandomContent("b/b.txt")
            .createFileWithRandomContent("c/c/c.txt");

        updatePatch = FSUtils.newTmpFile("testupdatepatch.pv");
        unitA_1_0_State = FSUtils.createTmpDir("pvupdpatchrb");
        unitA_patch1_State = FSUtils.createTmpDir("pvupdpatchr1");
        unitA_patch2_State = FSUtils.createTmpDir("pvupdpatchr2");
        unitA_1_1_State = FSUtils.createTmpDir("pvupdpatchrb3");
    }

    @Override
    public void doCleanUp() {
        IoUtils.recursiveDelete(unitA_1_0_State);
        IoUtils.recursiveDelete(unitA_patch1_State);
        IoUtils.recursiveDelete(unitA_patch2_State);
        IoUtils.recursiveDelete(unitA_1_1_State);
        IoUtils.recursiveDelete(updatePatch);
    }

    @Test
    public void testMain() throws Exception {

        ProvisionPackage.newBuilder()
            .setTargetInstallationDir(originalInstall.getHome())
            .setPackageOutputFile(archive)
            .buildInstall("unitA", "1.0");

        ProvisionEnvironment env = ProvisionEnvironment.builder().setEnvironmentHome(testInstall.getHome()).build();
        env.apply(archive);

        final ProvisionUnitInfo unitA_1_0 = ProvisionUnitInfo.createInfo("unitA", "1.0");
        AssertUtil.assertEnvInfo(env.getEnvironmentInfo(), unitA_1_0);
        IoUtils.copyFile(testInstall.getHome(), unitA_1_0_State);

        originalInstall.updateFileWithRandomContent("a.txt")
            .createFileWithRandomContent("d/d.txt");
        ProvisionPackage.newBuilder()
            .setCurrentInstallationDir(testInstall.getHome())
            .setTargetInstallationDir(originalInstall.getHome())
            .setPackageOutputFile(updatePatch)
            .buildUpdate("unitA", "1.0", "1.1");

        AssertUtil.assertNotIdentical(originalInstall.getHome(), testInstall.getHome(), true);
        env.apply(updatePatch);
        AssertUtil.assertIdentical(originalInstall.getHome(), testInstall.getHome(), true);
        final ProvisionUnitInfo unitA_1_1 = ProvisionUnitInfo.createInfo("unitA", "1.1");
        AssertUtil.assertEnvInfo(env.getEnvironmentInfo(), unitA_1_1);
        IoUtils.copyFile(originalInstall.getHome(), unitA_1_1_State);

        IoUtils.recursiveDelete(testInstall.getHome());
        IoUtils.recursiveDelete(originalInstall.getHome());
        IoUtils.copyFile(unitA_1_0_State, testInstall.getHome());
        IoUtils.copyFile(unitA_1_0_State, originalInstall.getHome());
        env = ProvisionEnvironment.load(testInstall.getHome());
        AssertUtil.assertEnvInfo(env.getEnvironmentInfo(), unitA_1_0);

        originalInstall.updateFileWithRandomContent("a.txt")
            .createFileWithRandomContent("patch1.txt");
        ProvisionPackage.newBuilder()
            .setCurrentInstallationDir(testInstall.getHome())
            .setTargetInstallationDir(originalInstall.getHome())
            .setPackageOutputFile(archive)
            .buildPatch("patch1", "unitA", "1.0");
        env.apply(archive);
        final ProvisionUnitInfo unitA_1_0_patch1 = ProvisionUnitInfo.createInfo("unitA", "1.0", Collections.singletonList("patch1"));
        AssertUtil.assertEnvInfo(env.getEnvironmentInfo(), unitA_1_0_patch1);
        IoUtils.copyFile(originalInstall.getHome(), unitA_patch1_State);

        originalInstall.updateFileWithRandomContent("a.txt")
            .createFileWithRandomContent("patch2.txt");
        ProvisionPackage.newBuilder()
            .setCurrentInstallationDir(testInstall.getHome())
            .setTargetInstallationDir(originalInstall.getHome())
            .setPackageOutputFile(archive)
            .buildPatch("patch2", "unitA", "1.0");
        env.apply(archive);
        final ProvisionUnitInfo unitA_1_0_patch2 = ProvisionUnitInfo.createInfo("unitA", "1.0", Arrays.asList("patch1", "patch2"));
        AssertUtil.assertEnvInfo(env.getEnvironmentInfo(), unitA_1_0_patch2);
        IoUtils.copyFile(originalInstall.getHome(), unitA_patch2_State);

        env.apply(updatePatch);
        AssertUtil.assertEnvInfo(env.getEnvironmentInfo(), unitA_1_1);
        AssertUtil.assertIdentical(unitA_1_1_State, testInstall.getHome());
        AssertUtil.assertNotIdentical(unitA_patch2_State, testInstall.getHome(), true);

        env.rollbackLast();
        AssertUtil.assertEnvInfo(env.getEnvironmentInfo(), unitA_1_0_patch2);
        AssertUtil.assertIdentical(unitA_patch2_State, testInstall.getHome(), true);

        env.rollbackLast();
        AssertUtil.assertEnvInfo(env.getEnvironmentInfo(), unitA_1_0_patch1);
        AssertUtil.assertIdentical(unitA_patch1_State, testInstall.getHome(), true);

        env.rollbackLast();
        AssertUtil.assertEnvInfo(env.getEnvironmentInfo(), unitA_1_0);
        AssertUtil.assertIdentical(unitA_1_0_State, testInstall.getHome(), true);

        env.rollbackLast();
        AssertUtil.assertHistoryEmpty(env);
        AssertUtil.assertEmptyDirBranch(testInstall.getHome());
    }
}
