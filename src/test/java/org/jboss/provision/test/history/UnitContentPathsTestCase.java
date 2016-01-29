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

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import org.jboss.provision.ProvisionEnvironment;
import org.jboss.provision.info.ContentPath;
import org.jboss.provision.info.ProvisionEnvironmentInfo;
import org.jboss.provision.instruction.ProvisionPackage;
import org.jboss.provision.io.IoUtils;
import org.jboss.provision.test.application.ApplicationTestBase;
import org.jboss.provision.test.util.FSUtils;
import org.jboss.provision.test.util.InstallationBuilder;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author Alexey Loubyansky
 */
public class UnitContentPathsTestCase extends ApplicationTestBase {

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
    public void testMain() throws Exception {

        originalInstall.createFileWithRandomContent("shared.txt")
            .createFileWithRandomContent("unit_a/a.txt");
        unitBInstall.createFileWithRandomContent("shared.txt")
            .createFileWithRandomContent("unit_b/b.txt");
        IoUtils.copyFile(originalInstall.resolvePath("shared.txt"), unitBInstall.resolvePath("shared.txt"));

        final ProvisionEnvironment env = ProvisionEnvironment.builder().setEnvironmentHome(testInstall.getHome()).build();
        final ProvisionEnvironmentInfo infoEmpty = env.getEnvironmentInfo();
        assertTrue(infoEmpty.getUnitNames().isEmpty());

        ProvisionPackage.newBuilder()
            .setTargetInstallationDir(originalInstall.getHome())
            .setPackageOutputFile(archive)
            .buildInstall("unitA", "1.0");
        env.apply(archive);
        ProvisionPackage.newBuilder()
            .setTargetInstallationDir(unitBInstall.getHome())
            .setPackageOutputFile(archive)
            .buildInstall("unitB", "1.0");
        env.apply(archive);

        assertPaths(env.getUnitEnvironment("unitA").getContentPaths(), "unit_a/a.txt", "shared.txt");
        assertPaths(env.getUnitEnvironment("unitB").getContentPaths(), "unit_b/b.txt", "shared.txt");

        copy(originalInstall, tmp);
        originalInstall.updateFileWithRandomContent("shared.txt")
            .delete("unit_a/a.txt")
            .createFileWithRandomContent("a.txt");
        ProvisionPackage.newBuilder()
            .setTargetInstallationDir(originalInstall.getHome())
            .setCurrentInstallationDir(tmp)
            .setPackageOutputFile(archive)
            .buildPatch("patch1", "unitA", "1.0");
        env.apply(archive);

        copy(unitBInstall, tmp);
        unitBInstall.delete("unit_b/b.txt")
            .createFileWithRandomContent("b.txt");
        ProvisionPackage.newBuilder()
            .setTargetInstallationDir(unitBInstall.getHome())
            .setCurrentInstallationDir(tmp)
            .setPackageOutputFile(archive)
            .buildPatch("patch1", "unitB", "1.0");
        env.apply(archive);

        assertPaths(env.getUnitEnvironment("unitA").getContentPaths(), "a.txt", "shared.txt");
        assertPaths(env.getUnitEnvironment("unitB").getContentPaths(), "b.txt", "shared.txt");

        copy(originalInstall, tmp);
        originalInstall.delete("shared.txt");
        ProvisionPackage.newBuilder()
            .setTargetInstallationDir(originalInstall.getHome())
            .setCurrentInstallationDir(tmp)
            .setPackageOutputFile(archive)
            .buildPatch("patch2", "unitA", "1.0");
        env.apply(archive);

        assertPaths(env.getUnitEnvironment("unitA").getContentPaths(), "a.txt");
        assertPaths(env.getUnitEnvironment("unitB").getContentPaths(), "b.txt", "shared.txt");

        env.rollbackLast();
        assertPaths(env.getUnitEnvironment("unitA").getContentPaths(), "a.txt", "shared.txt");
        assertPaths(env.getUnitEnvironment("unitB").getContentPaths(), "b.txt", "shared.txt");

        env.rollbackLast();
        assertPaths(env.getUnitEnvironment("unitA").getContentPaths(), "a.txt", "shared.txt");
        assertPaths(env.getUnitEnvironment("unitB").getContentPaths(), "unit_b/b.txt", "shared.txt");

        env.rollbackLast();
        assertPaths(env.getUnitEnvironment("unitA").getContentPaths(), "unit_a/a.txt", "shared.txt");
        assertPaths(env.getUnitEnvironment("unitB").getContentPaths(), "unit_b/b.txt", "shared.txt");

        env.rollbackLast();
        assertPaths(env.getUnitEnvironment("unitA").getContentPaths(), "unit_a/a.txt", "shared.txt");
        assertNull(env.getUnitEnvironment("unitB"));

        env.rollbackLast();
        assertNull(env.getUnitEnvironment("unitA"));
        assertNull(env.getUnitEnvironment("unitB"));
    }

    protected static void copy(InstallationBuilder builder, File target) throws IOException {
        IoUtils.recursiveDelete(target);
        IoUtils.copyFile(builder.getHome(), target);
    }

    protected void assertPaths(Collection<ContentPath> paths, String... path) {
        Assert.assertEquals(path.length, paths.size());

        for(String p : path) {
            assertTrue(p, paths.contains(ContentPath.fromString(p)));
        }
    }
}
