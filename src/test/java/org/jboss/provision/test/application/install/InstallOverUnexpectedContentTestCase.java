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

package org.jboss.provision.test.application.install;

import static org.junit.Assert.fail;

import org.jboss.provision.ProvisionEnvironment;
import org.jboss.provision.ProvisionException;
import org.jboss.provision.test.application.ApplicationTestBase;
import org.jboss.provision.test.util.FSUtils;
import org.jboss.provision.tool.ProvisionPackage;
import org.jboss.provision.tool.ProvisionTool;
import org.jboss.provision.util.IoUtils;
import org.junit.Test;

/**
 *
 * @author Alexey Loubyansky
 */
public class InstallOverUnexpectedContentTestCase extends ApplicationTestBase {

    @Test
    public void testMain() throws Exception {

        original.createFileWithRandomContent("a.txt")
            .createFileWithRandomContent("b/b.txt")
            .createFileWithRandomContent("c/c/c.txt")
            .createDir("d/e/f");

        ProvisionPackage.newBuilder()
            .setTargetInstallationDir(original.getHome())
            .setPackageOutputFile(archive)
            .buildInstall();

        IoUtils.mkdir(installDir, "b");
        FSUtils.writeRandomContent(IoUtils.newFile(installDir, "b", "b.txt"));

        final ProvisionEnvironment env = ProvisionEnvironment.Builder.forPackage(archive).setInstallationHome(installDir).build();
        try {
            ProvisionTool.apply(env);
            fail("install didn't fail");
        } catch(ProvisionException e) {
            // expected
        }
    }
}
