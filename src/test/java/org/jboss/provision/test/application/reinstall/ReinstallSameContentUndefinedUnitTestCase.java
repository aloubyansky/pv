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
import org.jboss.provision.io.IoUtils;
import org.jboss.provision.test.application.ApplicationTestBase;
import org.jboss.provision.test.util.AssertUtil;
import org.junit.Test;

/**
 *
 * @author Alexey Loubyansky
 */
public class ReinstallSameContentUndefinedUnitTestCase extends ApplicationTestBase {

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

        AssertUtil.assertEmptyDirBranch(testInstall.getHome());

        final ProvisionEnvironment env = ProvisionEnvironment.builder().setEnvironmentHome(testInstall.getHome()).build();
        env.apply(archive);
        assertInstalled(env);

        env.apply(archive);
        assertInstalled(env);

        testInstall.updateFileWithRandomContent("a.txt")
            .delete("c");
        AssertUtil.assertNotIdentical(originalInstall.getHome(), testInstall.getHome(), true);

        env.apply(archive);
        assertInstalled(env);
    }

    protected void assertInstalled(final ProvisionEnvironment env) throws ProvisionException {
        AssertUtil.assertIdentical(originalInstall.getHome(), testInstall.getHome(), true);
        AssertUtil.assertEnvInfo(env.getEnvironmentInfo(), ProvisionUnitInfo.UNDEFINED_INFO);
        Iterator<ProvisionEnvironmentInfo> envHistory = env.environmentHistory();
        assertTrue(envHistory.hasNext());
        AssertUtil.assertEnvInfo(envHistory.next(), ProvisionUnitInfo.UNDEFINED_INFO);
        assertFalse(envHistory.hasNext());
        Iterator<ProvisionUnitInfo> unitHistory = env.unitHistory(ProvisionUnitInfo.UNDEFINED_NAME);
        assertTrue(unitHistory.hasNext());
        assertEquals(unitHistory.next(), ProvisionUnitInfo.UNDEFINED_INFO);
        assertFalse(unitHistory.hasNext());
    }

    @Test
    public void testContentOnly() throws Exception {

        // tests that matched content does not cause problems during installation
        originalInstall.createFileWithRandomContent("a.txt")
            .createFileWithRandomContent("b/b.txt")
            .createFileWithRandomContent("c/c/c.txt")
            .createDir("d/e/f");

        ProvisionPackage.newBuilder()
            .setTargetInstallationDir(originalInstall.getHome())
            .setPackageOutputFile(archive)
            .buildInstall();

        IoUtils.copyFile(originalInstall.getHome(), testInstall.getHome());
        AssertUtil.assertIdentical(originalInstall.getHome(), testInstall.getHome());

        final ProvisionEnvironment env = ProvisionEnvironment.builder().setEnvironmentHome(testInstall.getHome()).build();
        AssertUtil.assertHistoryEmpty(env);
        env.apply(archive);

        AssertUtil.assertIdentical(originalInstall.getHome(), testInstall.getHome(), true);
        AssertUtil.assertEnvInfo(env.getEnvironmentInfo(), ProvisionUnitInfo.UNDEFINED_NAME, ProvisionUnitInfo.UNDEFINED_VERSION);
    }
}
