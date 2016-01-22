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

package org.jboss.provision.test.rollback.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Collections;

import org.jboss.provision.ProvisionEnvironment;
import org.jboss.provision.io.IoUtils;
import org.jboss.provision.test.application.ApplicationTestBase;
import org.jboss.provision.test.util.AssertUtil;
import org.jboss.provision.test.util.FSUtils;
import org.jboss.provision.tool.ProvisionPackage;
import org.jboss.provision.tool.ProvisionTool;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author Alexey Loubyansky
 */
public class RollbackUnitVersionUpdateTestCase extends ApplicationTestBase {

    private File tempDir;

    @Override
    public void doInit() {
        originalInstall.createFileWithRandomContent("a.txt")
        .createFileWithRandomContent("b/b.txt")
        .createFileWithRandomContent("c/c/c.txt");

        tempDir = FSUtils.createTmpDir("pvrollbackversion");
    }

    @Override
    public void doCleanUp() {
        IoUtils.recursiveDelete(tempDir);
    }

    @Test
    public void testMain() throws Exception {

        ProvisionPackage.newBuilder()
            .setTargetInstallationDir(originalInstall.getHome())
            .setPackageOutputFile(archive)
            .buildInstall("unitA", "1.0");

        final ProvisionEnvironment env = ProvisionEnvironment.builder().setEnvironmentHome(testInstall.getHome()).build();
        final ProvisionEnvironment originalEnv = ProvisionTool.apply(env, archive);

        assertEquals(Collections.singleton("unitA"), originalEnv.getUnitNames());
        assertEquals("1.0", originalEnv.getUnitEnvironment("unitA").getUnitInfo().getVersion());

        IoUtils.copyFile(originalInstall.getHome(), tempDir);

        originalInstall.updateFileWithRandomContent("a.txt")
            .createFileWithRandomContent("d/d.txt");
        ProvisionPackage.newBuilder()
            .setCurrentInstallationDir(testInstall.getHome())
            .setTargetInstallationDir(originalInstall.getHome())
            .setPackageOutputFile(archive)
            .buildUpdate("unitA", "1.0", "1.1");

        AssertUtil.assertIdentical(tempDir, testInstall.getHome());
        AssertUtil.assertNotIdentical(originalInstall.getHome(), testInstall.getHome(), true);
        final ProvisionEnvironment patchedEnv = ProvisionTool.apply(originalEnv, archive);

        AssertUtil.assertNotIdentical(tempDir, testInstall.getHome(), true);
        AssertUtil.assertIdentical(originalInstall.getHome(), testInstall.getHome(), true);

        Assert.assertNotEquals(originalEnv, patchedEnv);
        assertEquals(Collections.singleton("unitA"), patchedEnv.getUnitNames());
        assertEquals("1.1", patchedEnv.getUnitEnvironment("unitA").getUnitInfo().getVersion());

        ProvisionEnvironment rolledbackEnv = ProvisionTool.rollbackLast(patchedEnv);
        AssertUtil.assertNotIdentical(originalInstall.getHome(), testInstall.getHome(), true);
        AssertUtil.assertIdentical(tempDir, testInstall.getHome(), true);

        assertEquals(Collections.singleton("unitA"), rolledbackEnv.getUnitNames());
        assertEquals("1.0", rolledbackEnv.getUnitEnvironment("unitA").getUnitInfo().getVersion());

        Assert.assertNotEquals(patchedEnv, rolledbackEnv);
        Assert.assertEquals(originalEnv, rolledbackEnv);

        assertHistoryNotEmpty(rolledbackEnv);

        rolledbackEnv = ProvisionTool.rollbackLast(rolledbackEnv);
        AssertUtil.assertEmptyDirBranch(testInstall.getHome());
        assertTrue(rolledbackEnv.getUnitNames().isEmpty());
        assertHistoryEmpty(rolledbackEnv);
        assertCantRollback(rolledbackEnv);
    }
}
