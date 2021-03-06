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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.File;
import java.util.Collections;

import org.jboss.provision.ProvisionEnvironment;
import org.jboss.provision.ProvisionException;
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
public class UnitVersionMismatchTestCase extends ApplicationTestBase {

    private File tmpDir;

    @Override
    public void doInit() {
        originalInstall.createFileWithRandomContent("a.txt")
        .createFileWithRandomContent("b/b.txt")
        .createFileWithRandomContent("c/c/c.txt");

        tmpDir = FSUtils.newTmpFile("uvm_testdir");
        if(!tmpDir.mkdirs()) {
            fail("failed to create " + tmpDir.getAbsolutePath());
        }
    }

    @Override
    public void doCleanUp() {
        IoUtils.recursiveDelete(tmpDir);
    }

    @Test
    public void testMain() throws Exception {

        ProvisionPackage.newBuilder()
            .setTargetInstallationDir(originalInstall.getHome())
            .setPackageOutputFile(archive)
            .buildInstall("unitA", "1.0");

        final ProvisionEnvironment env = ProvisionEnvironment.builder().setEnvironmentHome(testInstall.getHome()).build();
        env.apply(archive);

        assertEquals(Collections.singleton("unitA"), env.getUnitNames());
        assertEquals("1.0", env.getUnitEnvironment("unitA").getUnitInfo().getVersion());

        originalInstall.updateFileWithRandomContent("a.txt")
            .createFileWithRandomContent("d/d.txt");
        ProvisionPackage.newBuilder()
            .setCurrentInstallationDir(testInstall.getHome())
            .setTargetInstallationDir(originalInstall.getHome())
            .setPackageOutputFile(archive)
            .buildUpdate("unitA", "1.1", "1.2");

        AssertUtil.assertNotIdentical(originalInstall.getHome(), testInstall.getHome(), true);
        IoUtils.copyFile(testInstall.getHome(), tmpDir);

        try {
            env.apply(archive);
            fail("Cannot apply an update that targets version 1.1 to version 1.0");
        } catch(ProvisionException e) {
            // expected
        }
        AssertUtil.assertNotIdentical(originalInstall.getHome(), testInstall.getHome(), true);
        AssertUtil.assertIdentical(tmpDir, testInstall.getHome(), false);
    }
}
