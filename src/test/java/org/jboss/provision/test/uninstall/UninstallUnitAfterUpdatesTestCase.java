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

package org.jboss.provision.test.uninstall;

import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.jboss.provision.ProvisionEnvironment;
import org.jboss.provision.instruction.ProvisionPackage;
import org.jboss.provision.test.application.ApplicationTestBase;
import org.jboss.provision.test.util.AssertUtil;
import org.junit.Test;

/**
 *
 * @author Alexey Loubyansky
 */
public class UninstallUnitAfterUpdatesTestCase extends ApplicationTestBase {

    @Test
    public void testMain() throws Exception {

        originalInstall.createFileWithRandomContent("shared.txt")
            .createFileWithRandomContent("unit_a/a.txt");

        final ProvisionEnvironment env = ProvisionEnvironment.builder().setEnvironmentHome(testInstall.getHome()).build();
        AssertUtil.assertHistoryEmpty(env);

        ProvisionPackage.newBuilder()
            .setTargetInstallationDir(originalInstall.getHome())
            .setPackageOutputFile(archive)
            .buildInstall("unitA", "1.0");
        env.apply(archive);

        originalInstall.updateFileWithRandomContent("unit_a/a.txt");
        ProvisionPackage.newBuilder()
            .setTargetInstallationDir(originalInstall.getHome())
            .setCurrentInstallationDir(testInstall.getHome())
            .setPackageOutputFile(archive)
            .buildUpdate("unitA", "1.0", "1.1");
        env.apply(archive);

        originalInstall.updateFileWithRandomContent("unit_a/a.txt");
        ProvisionPackage.newBuilder()
            .setTargetInstallationDir(originalInstall.getHome())
            .setCurrentInstallationDir(testInstall.getHome())
            .setPackageOutputFile(archive)
            .buildPatch("patch1", "unitA", "1.1");
        env.apply(archive);

        originalInstall.updateFileWithRandomContent("unit_a/a.txt");
        ProvisionPackage.newBuilder()
            .setTargetInstallationDir(originalInstall.getHome())
            .setCurrentInstallationDir(testInstall.getHome())
            .setPackageOutputFile(archive)
            .buildPatch("patch2", "unitA", "1.1");
        env.apply(archive);

        AssertUtil.assertUnitInfo(env.getEnvironmentInfo(), "unitA", "1.1", Arrays.asList("patch1", "patch2"));

        env.uninstall("unitA");

        assertTrue(env.getEnvironmentInfo().getUnitNames().isEmpty());
        AssertUtil.assertHistoryEmpty(env);
    }
}
