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

package org.jboss.provision.test.application.reinstall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Iterator;

import org.jboss.provision.ProvisionEnvironment;
import org.jboss.provision.ProvisionException;
import org.jboss.provision.info.ProvisionEnvironmentInfo;
import org.jboss.provision.info.ProvisionUnitInfo;
import org.jboss.provision.instruction.ProvisionPackage;
import org.jboss.provision.test.application.ApplicationTestBase;
import org.jboss.provision.test.util.AssertUtil;
import org.junit.Test;

/**
 *
 * @author Alexey Loubyansky
 */
public class ReinstallSameVersionUnitTestCase extends ApplicationTestBase {

    @Test
    public void testMain() throws Exception {

        originalInstall.createFileWithRandomContent("a.txt")
            .createFileWithRandomContent("b/b.txt")
            .createFileWithRandomContent("c/c/c.txt")
            .createDir("d/e/f");

        ProvisionPackage.newBuilder()
            .setTargetInstallationDir(originalInstall.getHome())
            .setPackageOutputFile(archive)
            .buildInstall("unitA", "1.0");

        AssertUtil.assertEmptyDirBranch(testInstall.getHome());

        final ProvisionUnitInfo unitInfo = ProvisionUnitInfo.createInfo("unitA", "1.0");
        final ProvisionEnvironment env = ProvisionEnvironment.builder().setEnvironmentHome(testInstall.getHome()).build();
        env.apply(archive);
        assertInstalled(env, unitInfo);

        env.apply(archive);
        assertInstalled(env, unitInfo);

        testInstall.updateFileWithRandomContent("a.txt")
            .delete("c");
        AssertUtil.assertNotIdentical(originalInstall.getHome(), testInstall.getHome(), true);

        env.apply(archive);
        assertInstalled(env, unitInfo);
    }

    protected void assertInstalled(final ProvisionEnvironment env, ProvisionUnitInfo unitInfo) throws ProvisionException {
        AssertUtil.assertIdentical(originalInstall.getHome(), testInstall.getHome(), true);
        AssertUtil.assertEnvInfo(env.getEnvironmentInfo(), unitInfo);
        Iterator<ProvisionEnvironmentInfo> envHistory = env.environmentHistory();
        assertTrue(envHistory.hasNext());
        AssertUtil.assertEnvInfo(envHistory.next(), unitInfo);
        assertFalse(envHistory.hasNext());
        Iterator<ProvisionUnitInfo> unitHistory = env.unitHistory("unitA");
        assertTrue(unitHistory.hasNext());
        assertEquals(unitHistory.next(), unitInfo);
        assertFalse(unitHistory.hasNext());
    }
}
