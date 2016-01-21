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

package org.jboss.provision.test.rollback.install;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.io.File;

import org.jboss.provision.ProvisionEnvironment;
import org.jboss.provision.ProvisionException;
import org.jboss.provision.UnitUpdatePolicy;
import org.jboss.provision.history.ProvisionEnvironmentHistory;
import org.jboss.provision.io.IoUtils;
import org.jboss.provision.test.application.ApplicationTestBase;
import org.jboss.provision.test.util.AssertUtil;
import org.jboss.provision.test.util.FSUtils;
import org.jboss.provision.tool.ProvisionPackage;
import org.jboss.provision.tool.ProvisionTool;
import org.junit.Test;

/**
 *
 * @author Alexey Loubyansky
 */
public class RollbackInstallOverConflictingContentTestCase extends ApplicationTestBase {

    private File tmpDir;

    @Override
    public void doInit() {
        tmpDir = FSUtils.createTmpDir("pvrollbacktest");
    }

    @Override
    public void doCleanUp() {
        IoUtils.recursiveDelete(tmpDir);
    }

    @Test
    public void testMain() throws Exception {

        originalInstall.createFileWithRandomContent("a.txt")
            .createFileWithRandomContent("b/b.txt")
            .createFileWithRandomContent("c/c/c.txt")
            .createDir("d/e/f");

        ProvisionPackage.newBuilder()
            .setTargetInstallationDir(originalInstall.getHome())
            .setPackageOutputFile(archive)
            .buildInstall();

        testInstall.createFileWithRandomContent("b/b.txt");
        IoUtils.copyFile(testInstall.getHome(), tmpDir);

        ProvisionEnvironment env = ProvisionEnvironment.builder().setEnvironmentHome(testInstall.getHome()).build();
        try {
            ProvisionTool.apply(env, archive);
            fail("install didn't fail");
        } catch(ProvisionException e) {
            // expected
        }

        AssertUtil.assertExpectedFilesNotInTarget(originalInstall.getHome(), testInstall.getHome(), true);

        env = ProvisionEnvironment.builder()
                .setEnvironmentHome(testInstall.getHome())
                .setDefaultUnitUpdatePolicy(UnitUpdatePolicy.FORCED).build();
        env = ProvisionTool.apply(env, archive);

        AssertUtil.assertIdentical(originalInstall.getHome(), testInstall.getHome(), true);

        env = ProvisionTool.rollbackLast(env);

        AssertUtil.assertIdentical(tmpDir, testInstall.getHome(), true);
        assertNull(ProvisionEnvironmentHistory.getInstance(env).getCurrentEnvironment());
    }
}
