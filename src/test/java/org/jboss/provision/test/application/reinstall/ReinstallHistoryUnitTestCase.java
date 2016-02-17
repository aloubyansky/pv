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

package org.jboss.provision.test.application.reinstall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;

import org.jboss.provision.ProvisionEnvironment;
import org.jboss.provision.ProvisionException;
import org.jboss.provision.info.ProvisionEnvironmentInfo;
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
public class ReinstallHistoryUnitTestCase extends ApplicationTestBase {

    private File tmp;

    @Override
    public void doInit() {
        tmp = FSUtils.createTmpDir("pvreinstalltest");
    }

    @Override
    public void doCleanUp() {
        IoUtils.recursiveDelete(tmp);
    }

    @Test
    public void testMain() throws Exception {

        originalInstall.createFileWithRandomContent("a.txt")
            .createFileWithRandomContent("b/b.txt")
            .createFileWithRandomContent("c/c/c.txt")
            .createDir("d/e/f");
        IoUtils.copyFile(originalInstall.getHome(), tmp);

        ProvisionPackage.newBuilder()
            .setTargetInstallationDir(originalInstall.getHome())
            .setPackageOutputFile(archive)
            .buildInstall("unitA", "1.0");

        AssertUtil.assertEmptyDirBranch(testInstall.getHome());

        final ProvisionEnvironment env = ProvisionEnvironment.builder().setEnvironmentHome(testInstall.getHome()).build();

        env.apply(archive);
        final ProvisionUnitInfo unitA_1_0 = ProvisionUnitInfo.createInfo("unitA", "1.0");

        originalInstall.updateFileWithRandomContent("a.txt");
        ProvisionPackage.newBuilder()
            .setCurrentInstallationDir(tmp)
            .setTargetInstallationDir(originalInstall.getHome())
            .setPackageOutputFile(archive)
            .buildUpdate("unitA", "1.0", "1.1");
        env.apply(archive);
        final ProvisionUnitInfo unitA_1_1 = ProvisionUnitInfo.createInfo("unitA", "1.1");

        IoUtils.recursiveDelete(tmp);
        IoUtils.copyFile(originalInstall.getHome(), tmp);
        originalInstall.updateFileWithRandomContent("a.txt");
        ProvisionPackage.newBuilder()
            .setCurrentInstallationDir(tmp)
            .setTargetInstallationDir(originalInstall.getHome())
            .setPackageOutputFile(archive)
            .buildPatch("patch1", "unitA", "1.1");
        env.apply(archive);
        final ProvisionUnitInfo unitA_patch1 = ProvisionUnitInfo.createInfo("unitA", "1.1", Collections.singletonList("patch1"));

        IoUtils.recursiveDelete(tmp);
        IoUtils.copyFile(originalInstall.getHome(), tmp);
        originalInstall.updateFileWithRandomContent("a.txt");
        ProvisionPackage.newBuilder()
            .setCurrentInstallationDir(tmp)
            .setTargetInstallationDir(originalInstall.getHome())
            .setPackageOutputFile(archive)
            .buildPatch("patch2", "unitA", "1.1");
        env.apply(archive);
        final ProvisionUnitInfo unitA_patch2 = ProvisionUnitInfo.createInfo("unitA", "1.1", Arrays.asList("patch1", "patch2"));

        AssertUtil.assertIdentical(originalInstall.getHome(), testInstall.getHome(), true);
        AssertUtil.assertEnvInfo(env.getEnvironmentInfo(), unitA_patch2);
        Iterator<ProvisionEnvironmentInfo> envHistory = env.environmentHistory();
        assertTrue(envHistory.hasNext());
        AssertUtil.assertEnvInfo(envHistory.next(), unitA_patch2);
        assertTrue(envHistory.hasNext());
        AssertUtil.assertEnvInfo(envHistory.next(), unitA_patch1);
        assertTrue(envHistory.hasNext());
        AssertUtil.assertEnvInfo(envHistory.next(), unitA_1_1);
        assertTrue(envHistory.hasNext());
        AssertUtil.assertEnvInfo(envHistory.next(), unitA_1_0);
        assertFalse(envHistory.hasNext());

        Iterator<ProvisionUnitInfo> unitHistory = env.unitHistory("unitA");
        assertTrue(unitHistory.hasNext());
        assertEquals(unitHistory.next(), unitA_patch2);
        assertTrue(unitHistory.hasNext());
        assertEquals(unitHistory.next(), unitA_patch1);
        assertTrue(unitHistory.hasNext());
        assertEquals(unitHistory.next(), unitA_1_1);
        assertTrue(unitHistory.hasNext());
        assertEquals(unitHistory.next(), unitA_1_0);
        assertFalse(unitHistory.hasNext());

        IoUtils.recursiveDelete(tmp);
        IoUtils.copyFile(originalInstall.getHome(), tmp);
        originalInstall.updateFileWithRandomContent("a.txt");
        ProvisionPackage.newBuilder()
            .setCurrentInstallationDir(tmp)
            .setTargetInstallationDir(originalInstall.getHome())
            .setPackageOutputFile(archive)
            .buildInstall("unitA", "2.0");
        env.apply(archive);
        assertInstalled(env, ProvisionUnitInfo.createInfo("unitA", "2.0"));
    }

    protected void assertInstalled(final ProvisionEnvironment env, ProvisionUnitInfo unitInfo) throws ProvisionException {
        AssertUtil.assertIdentical(originalInstall.getHome(), testInstall.getHome(), true);
        AssertUtil.assertEnvInfo(env.getEnvironmentInfo(), unitInfo);
        Iterator<ProvisionEnvironmentInfo> envHistory = env.environmentHistory();
        assertTrue(envHistory.hasNext());
        AssertUtil.assertEnvInfo(envHistory.next(), unitInfo);
        assertFalse(envHistory.hasNext());
        Iterator<ProvisionUnitInfo> unitHistory = env.unitHistory("unitA");
        assertTrue(unitHistory.hasNext());
        assertEquals(unitHistory.next(), unitInfo);
        assertFalse(unitHistory.hasNext());
    }
}
