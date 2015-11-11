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
import org.jboss.provision.test.application.ApplicationTestBase;
import org.jboss.provision.test.util.AssertUtil;
import org.jboss.provision.test.util.FSUtils;
import org.jboss.provision.tool.ProvisionPackage;
import org.jboss.provision.tool.ProvisionTool;
import org.jboss.provision.tool.instruction.UpdatePolicy;
import org.jboss.provision.util.IoUtils;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author Alexey Loubyansky
 */
public class UninstallModifiedContentTestCase extends ApplicationTestBase {

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

        original.createFileWithRandomContent("a.txt")
            .createFileWithRandomContent("b/b.txt")
            .createFileWithRandomContent("c/c/c.txt")
            .createDir("d/e/f");

        ProvisionPackage.newBuilder()
            .setCurrentInstallationDir(original.getHome())
            .setPackageOutputFile(archive)
            .buildUninstall();


        IoUtils.mkdir(temp, "b");
        FSUtils.writeRandomContent(IoUtils.newFile(temp, "b", "bb.txt"));
        IoUtils.mkdir(temp, "c/c");
        FSUtils.writeRandomContent(IoUtils.newFile(temp, "c", "cc.txt"));
        IoUtils.mkdir(temp, "d");
        FSUtils.writeRandomContent(IoUtils.newFile(temp, "d", "dd.txt"));
        IoUtils.mkdir(temp, "e");

        IoUtils.copyFile(temp, installDir);
        IoUtils.copyFile(original.getHome(), installDir);

        FSUtils.writeRandomContent(IoUtils.newFile(installDir, "b", "b.txt"));

        ProvisionEnvironment env = ProvisionEnvironment.Builder.forPackage(archive).setInstallationHome(installDir).build();
        try {
            ProvisionTool.apply(env);
            Assert.fail("Modified content uninstalled");
        } catch(ProvisionException e) {
            // expected
        }

        env = ProvisionEnvironment.Builder.forPackage(archive)
                .setInstallationHome(installDir)
                .setDefaultUnitUpdatePolicy(new UnitUpdatePolicy() {
                    @Override
                    public UpdatePolicy getUnitPolicy() {
                        return UpdatePolicy.FORCED;
                    }

                    @Override
                    public UpdatePolicy getContentPolicy(String path) {
                        return UpdatePolicy.FORCED;
                    }}).build();
        ProvisionTool.apply(env);

        AssertUtil.assertExpectedContentInTarget(temp, installDir);
        AssertUtil.assertExpectedFilesNotInTarget(original.getHome(), installDir);
    }
}
