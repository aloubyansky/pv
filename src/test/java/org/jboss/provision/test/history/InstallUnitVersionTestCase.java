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
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.jboss.provision.ProvisionEnvironment;
import org.jboss.provision.ProvisionUnitEnvironment;
import org.jboss.provision.history.ProvisionEnvironmentHistory;
import org.jboss.provision.test.application.ApplicationTestBase;
import org.jboss.provision.test.util.AssertUtil;
import org.jboss.provision.tool.ProvisionPackage;
import org.jboss.provision.tool.ProvisionTool;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author Alexey Loubyansky
 */
public class InstallUnitVersionTestCase extends ApplicationTestBase {

    @Test
    public void testMain() throws Exception {

        originalInstall.createFileWithRandomContent("a.txt")
            .createFileWithRandomContent("b/b.txt")
            .createFileWithRandomContent("c/c/c.txt");

        ProvisionPackage.newBuilder()
            .setTargetInstallationDir(originalInstall.getHome())
            .setPackageOutputFile(archive)
            .buildInstall("unitA", "1.0");

        AssertUtil.assertEmptyDirBranch(testInstall.getHome());

        final ProvisionEnvironment env = ProvisionEnvironment.builder().setEnvironmentHome(testInstall.getHome()).build();
        final ProvisionEnvironmentHistory history = ProvisionEnvironmentHistory.getInstance(env);
        assertNull(history.getCurrentEnvironment());

        ProvisionTool.apply(env, archive);

        final ProvisionEnvironment env2 = history.getCurrentEnvironment();
        assertNotNull(env2);
        Assert.assertNotEquals(env, env2);

        assertTrue(env.getUnitNames().isEmpty());
        assertEquals(Collections.singleton("unitA"), env2.getUnitNames());
        ProvisionUnitEnvironment unitAEnv = env2.getUnitEnvironment("unitA");
        assertEquals("1.0", unitAEnv.getUnitInfo().getVersion());
    }
}
