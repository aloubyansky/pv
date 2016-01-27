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

package org.jboss.provision.test.history;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.jboss.provision.ProvisionEnvironment;
import org.jboss.provision.info.ProvisionEnvironmentInfo;
import org.jboss.provision.info.ProvisionUnitInfo;
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
public class UnitHistoryIteratorTestCase extends ApplicationTestBase {

    private File tmp;
    protected InstallationBuilder unitBInstall;

    @Override
    public void doInit() {
        unitBInstall = InstallationBuilder.create();
        tmp = FSUtils.createTmpDir("pvunithistorytest");
    }

    @Override
    public void doCleanUp() {
        IoUtils.recursiveDelete(unitBInstall.getHome());
        IoUtils.recursiveDelete(tmp);
    }

    @Test
    public void testEmptyHistory() throws Exception {
        final ProvisionEnvironment env = ProvisionEnvironment.builder().setEnvironmentHome(testInstall.getHome()).build();
        Iterator<ProvisionUnitInfo> i = env.unitHistory("unitA");
        assertFalse(i.hasNext());
        try {
            i.next();
            fail("NoSuchElementException expected");
        } catch(NoSuchElementException e) {
            // expected
        }
    }

    @Test
    public void testMain() throws Exception {

        originalInstall.createFileWithRandomContent("a.txt")
            .createFileWithRandomContent("b/b.txt")
            .createFileWithRandomContent("c/c/c.txt");
        unitBInstall.createFileWithRandomContent("unitB-a.txt")
            .createFileWithRandomContent("b/unitB-b.txt")
            .createFileWithRandomContent("c/c/unotB-c.txt");

        final ProvisionEnvironment env = ProvisionEnvironment.builder().setEnvironmentHome(testInstall.getHome()).build();
        final ProvisionEnvironmentInfo infoEmpty = env.getEnvironmentInfo();
        assertTrue(infoEmpty.getUnitNames().isEmpty());

        ProvisionPackage.newBuilder()
            .setTargetInstallationDir(originalInstall.getHome())
            .setPackageOutputFile(archive)
            .buildInstall("unitA", "1.0");
        env.apply(archive);
        ProvisionEnvironmentInfo envInfo = env.getEnvironmentInfo();
        final ProvisionUnitInfo unitA_1_0 = ProvisionUnitInfo.createInfo("unitA", "1.0");
        AssertUtil.assertUnitInfo(envInfo, unitA_1_0);

        IoUtils.copyFile(originalInstall.getHome(), tmp);
        originalInstall.createFileWithRandomContent("d/d/d/d.txt");
        ProvisionPackage.newBuilder()
            .setCurrentInstallationDir(tmp)
            .setTargetInstallationDir(originalInstall.getHome())
            .setPackageOutputFile(archive)
            .buildUpdate("unitA", "1.0", "1.1");
        env.apply(archive);
        envInfo = env.getEnvironmentInfo();
        final ProvisionUnitInfo unitA_1_1 = ProvisionUnitInfo.createInfo("unitA", "1.1");
        AssertUtil.assertUnitInfo(envInfo, unitA_1_1);

        ProvisionPackage.newBuilder()
            .setTargetInstallationDir(unitBInstall.getHome())
            .setPackageOutputFile(archive)
            .buildInstall("unitB", "1.0");
        env.apply(archive);
        envInfo = env.getEnvironmentInfo();
        final ProvisionUnitInfo unitB_1_0 = ProvisionUnitInfo.createInfo("unitB", "1.0");
        AssertUtil.assertEnvInfo(envInfo, unitA_1_1, unitB_1_0);

        IoUtils.recursiveDelete(tmp);
        IoUtils.copyFile(originalInstall.getHome(), tmp);
        originalInstall.updateFileWithRandomContent("d/d/d/d.txt");
        ProvisionPackage.newBuilder()
            .setCurrentInstallationDir(tmp)
            .setTargetInstallationDir(originalInstall.getHome())
            .setPackageOutputFile(archive)
            .buildPatch("patch1", "unitA", "1.1");
        env.apply(archive);
        envInfo = env.getEnvironmentInfo();
        final ProvisionUnitInfo unitA_patch1 = ProvisionUnitInfo.createInfo("unitA", "1.1", Collections.singletonList("patch1"));
        AssertUtil.assertEnvInfo(envInfo, unitA_patch1, unitB_1_0);

        IoUtils.recursiveDelete(tmp);
        IoUtils.copyFile(unitBInstall.getHome(), tmp);
        unitBInstall.createFileWithRandomContent("d/d/d/unitB_d.txt");
        ProvisionPackage.newBuilder()
            .setCurrentInstallationDir(tmp)
            .setTargetInstallationDir(unitBInstall.getHome())
            .setPackageOutputFile(archive)
            .buildUpdate("unitB", "1.0", "1.1");
        env.apply(archive);
        envInfo = env.getEnvironmentInfo();
        final ProvisionUnitInfo unitB_1_1 = ProvisionUnitInfo.createInfo("unitB", "1.1");
        AssertUtil.assertEnvInfo(envInfo, unitA_patch1, unitB_1_1);

        IoUtils.recursiveDelete(tmp);
        IoUtils.copyFile(unitBInstall.getHome(), tmp);
        unitBInstall.updateFileWithRandomContent("d/d/d/unitB_d.txt");
        ProvisionPackage.newBuilder()
            .setCurrentInstallationDir(tmp)
            .setTargetInstallationDir(unitBInstall.getHome())
            .setPackageOutputFile(archive)
            .buildPatch("patch1", "unitB", "1.1");
        env.apply(archive);
        envInfo = env.getEnvironmentInfo();
        final ProvisionUnitInfo unitB_patch1 = ProvisionUnitInfo.createInfo("unitB", "1.1", Collections.singletonList("patch1"));
        AssertUtil.assertEnvInfo(envInfo, unitA_patch1, unitB_patch1);

        IoUtils.recursiveDelete(tmp);
        IoUtils.copyFile(originalInstall.getHome(), tmp);
        originalInstall.updateFileWithRandomContent("d/d/d/d.txt");
        ProvisionPackage.newBuilder()
            .setCurrentInstallationDir(tmp)
            .setTargetInstallationDir(originalInstall.getHome())
            .setPackageOutputFile(archive)
            .buildPatch("patch2", "unitA", "1.1");
        env.apply(archive);
        envInfo = env.getEnvironmentInfo();
        final ProvisionUnitInfo unitA_patch2 = ProvisionUnitInfo.createInfo("unitA", "1.1", Arrays.asList("patch1", "patch2"));
        AssertUtil.assertEnvInfo(envInfo, unitA_patch2, unitB_patch1);

        IoUtils.recursiveDelete(tmp);
        IoUtils.copyFile(unitBInstall.getHome(), tmp);
        unitBInstall.updateFileWithRandomContent("d/d/d/unitB_d.txt");
        ProvisionPackage.newBuilder()
            .setCurrentInstallationDir(tmp)
            .setTargetInstallationDir(unitBInstall.getHome())
            .setPackageOutputFile(archive)
            .buildPatch("patch2", "unitB", "1.1");
        env.apply(archive);
        envInfo = env.getEnvironmentInfo();
        final ProvisionUnitInfo unitB_patch2 = ProvisionUnitInfo.createInfo("unitB", "1.1", Arrays.asList("patch1", "patch2"));
        AssertUtil.assertEnvInfo(envInfo, unitA_patch2, unitB_patch2);

        assertHistory(env.unitHistory("unitA"), unitA_patch2, unitA_patch1, unitA_1_1, unitA_1_0);
        assertHistory(env.unitHistory("unitB"), unitB_patch2, unitB_patch1, unitB_1_1, unitB_1_0);
        assertFalse(env.unitHistory("unitC").hasNext());

        env.rollbackLast();
        assertHistory(env.unitHistory("unitA"), unitA_patch2, unitA_patch1, unitA_1_1, unitA_1_0);
        assertHistory(env.unitHistory("unitB"), unitB_patch1, unitB_1_1, unitB_1_0);

        env.rollbackLast();
        assertHistory(env.unitHistory("unitA"), unitA_patch1, unitA_1_1, unitA_1_0);
        assertHistory(env.unitHistory("unitB"), unitB_patch1, unitB_1_1, unitB_1_0);

        env.rollbackLast();
        assertHistory(env.unitHistory("unitA"), unitA_patch1, unitA_1_1, unitA_1_0);
        assertHistory(env.unitHistory("unitB"), unitB_1_1, unitB_1_0);

        env.rollbackLast();
        assertHistory(env.unitHistory("unitA"), unitA_patch1, unitA_1_1, unitA_1_0);
        assertHistory(env.unitHistory("unitB"), unitB_1_0);

        env.rollbackLast();
        assertHistory(env.unitHistory("unitA"), unitA_1_1, unitA_1_0);
        assertHistory(env.unitHistory("unitB"), unitB_1_0);
        Collection<String> envUnitNames = env.getEnvironmentInfo().getUnitNames();
        assertTrue(envUnitNames.contains("unitA"));
        assertTrue(envUnitNames.contains("unitB"));

        env.rollbackLast();
        assertHistory(env.unitHistory("unitA"), unitA_1_1, unitA_1_0);
        assertFalse(env.unitHistory("unitB").hasNext());
        assertEquals(env.getEnvironmentInfo().getUnitNames(), Collections.singleton("unitA"));

        env.rollbackLast();
        assertHistory(env.unitHistory("unitA"), unitA_1_0);
        assertFalse(env.unitHistory("unitB").hasNext());

        env.rollbackLast();
        assertFalse(env.unitHistory("unitA").hasNext());
        assertFalse(env.unitHistory("unitB").hasNext());
        assertTrue(env.getEnvironmentInfo().getUnitNames().isEmpty());
    }

    protected void assertHistory(Iterator<ProvisionUnitInfo> i, ProvisionUnitInfo... info) {
        for(int j = 0; j < info.length; ++j) {
            assertTrue("" + j, i.hasNext());
            ProvisionUnitInfo unitInfo = i.next();
            assertEquals(info[j], unitInfo);
        }
        assertFalse(i.hasNext());
        try {
            i.next();
            fail("NoSuchElementException expected");
        } catch(NoSuchElementException e) {
            // expected
        }
    }
}
