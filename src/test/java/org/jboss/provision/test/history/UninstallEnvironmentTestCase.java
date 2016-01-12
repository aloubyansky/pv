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

package org.jboss.provision.test.history;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import java.util.Collections;

import org.jboss.provision.ProvisionEnvironment;
import org.jboss.provision.history.ProvisionEnvironmentHistory;
import org.jboss.provision.test.application.ApplicationTestBase;
import org.jboss.provision.test.util.AssertUtil;
import org.jboss.provision.tool.ProvisionPackage;
import org.jboss.provision.tool.ProvisionTool;
import org.junit.Test;

/**
 *
 * @author Alexey Loubyansky
 */
public class UninstallEnvironmentTestCase extends ApplicationTestBase {

    @Override
    public void doInit() {
        originalInstall.createFileWithRandomContent("a.txt")
        .createFileWithRandomContent("b/b.txt")
        .createFileWithRandomContent("c/c/c.txt");
    }

    @Test
    public void testMain() throws Exception {

        ProvisionPackage.newBuilder()
            .setTargetInstallationDir(originalInstall.getHome())
            .setPackageOutputFile(archive)
            .buildInstall("unitA", "1.0");

        final ProvisionEnvironment env = ProvisionEnvironment.builder().setEnvironmentHome(testInstall.getHome()).build();
        assertNull(ProvisionEnvironmentHistory.getInstance(env).getCurrentEnvironment());
        AssertUtil.assertEmptyDirBranch(testInstall.getHome());

        ProvisionTool.apply(env, archive);

        final ProvisionEnvironmentHistory history = ProvisionEnvironmentHistory.getInstance(env);
        assertNotNull(history);
        AssertUtil.assertIdentical(originalInstall.getHome(), testInstall.getHome());

        final ProvisionEnvironment curEnv = history.getCurrentEnvironment();
        assertEquals(Collections.singleton("unitA"), curEnv.getUnitNames());
        assertEquals("1.0", curEnv.getUnitEnvironment("unitA").getUnitInfo().getVersion());

        ProvisionPackage.newBuilder()
            .setCurrentInstallationDir(testInstall.getHome())
            .setPackageOutputFile(archive)
            .buildUninstall("unitA", "1.0");
        ProvisionTool.apply(curEnv, archive);
        AssertUtil.assertEmptyDirBranch(testInstall.getHome());
        assertNull(ProvisionEnvironmentHistory.getInstance(curEnv).getCurrentEnvironment());
    }
}
