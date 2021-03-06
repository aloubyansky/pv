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

package org.jboss.provision.test.application.uninstall;

import java.io.File;

import org.jboss.provision.ProvisionEnvironment;
import org.jboss.provision.ProvisionException;
import org.jboss.provision.UnitUpdatePolicy;
import org.jboss.provision.instruction.ProvisionPackage;
import org.jboss.provision.io.IoUtils;
import org.jboss.provision.test.application.ApplicationTestBase;
import org.jboss.provision.test.util.AssertUtil;
import org.jboss.provision.test.util.FSUtils;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author Alexey Loubyansky
 */
public class UninstallConflictingContentTestCase extends ApplicationTestBase {

    private File temp;

    @Override
    public void doInit() {
        temp = FSUtils.createTmpDir("installtemptest");
    }

    @Override
    protected void doCleanUp() {
        IoUtils.recursiveDelete(temp);
    }

    @Test
    public void testMain() throws Exception {

        originalInstall.createFileWithRandomContent("a.txt")
            .createFileWithRandomContent("b/b.txt")
            .createFileWithRandomContent("c/c/c.txt")
            .createDir("d/e/f");

        ProvisionPackage.newBuilder()
            .setCurrentInstallationDir(originalInstall.getHome())
            .setPackageOutputFile(archive)
            .buildUninstall();

        testInstall.createFileWithRandomContent("aa.txt");
        testInstall.createFileWithRandomContent("b/bb.txt");
        testInstall.createFileWithRandomContent("c/cc.txt");
        testInstall.createFileWithRandomContent("d/dd.txt");
        testInstall.createDir("e");

        IoUtils.copyFile(testInstall.getHome(), temp);
        IoUtils.copyFile(originalInstall.getHome(), testInstall.getHome());

        testInstall.updateFileWithRandomContent("b/b.txt");

        AssertUtil.assertExpectedContentInTarget(temp, testInstall.getHome(), false);
        AssertUtil.assertExpectedFilesNotInTarget(originalInstall.getHome(), testInstall.getHome(), false);

        ProvisionEnvironment env = ProvisionEnvironment.forUndefinedUnit().setEnvironmentHome(testInstall.getHome()).build();
        try {
            env.apply(archive);
            Assert.fail("Modified content uninstalled");
        } catch(ProvisionException e) {
            // expected
        }

        AssertUtil.assertExpectedContentInTarget(temp, testInstall.getHome(), false);
        AssertUtil.assertExpectedFilesNotInTarget(originalInstall.getHome(), testInstall.getHome(), false);

        env = ProvisionEnvironment.forUndefinedUnit()
                .setEnvironmentHome(testInstall.getHome())
                .setDefaultUnitUpdatePolicy(UnitUpdatePolicy.FORCED).build();
        env.apply(archive);

        AssertUtil.assertExpectedContentInTarget(temp, testInstall.getHome());
        AssertUtil.assertExpectedFilesNotInTarget(originalInstall.getHome(), testInstall.getHome(), true);
    }
}
