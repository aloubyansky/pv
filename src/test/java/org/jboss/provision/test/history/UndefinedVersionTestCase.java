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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.Iterator;

import org.jboss.provision.ProvisionEnvironment;
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
public class UndefinedVersionTestCase extends ApplicationTestBase {

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
        assertFalse(env.environmentHistory().hasNext());

        env.apply(archive);

        Iterator<ProvisionEnvironmentInfo> envHistory = env.environmentHistory();
        assertTrue(envHistory.hasNext());
        final ProvisionEnvironmentInfo installedInfo = envHistory.next();
        assertFalse(envHistory.hasNext());
        assertEquals(Collections.singleton(ProvisionUnitInfo.UNDEFINED_NAME), installedInfo.getUnitNames());
        assertEquals(ProvisionUnitInfo.UNDEFINED_VERSION, installedInfo.getUnitInfo(ProvisionUnitInfo.UNDEFINED_NAME).getVersion());

        originalInstall.updateFileWithRandomContent("a.txt")
            .createFileWithRandomContent("d/d.txt");
        ProvisionPackage.newBuilder()
            .setCurrentInstallationDir(testInstall.getHome())
            .setTargetInstallationDir(originalInstall.getHome())
            .setPackageOutputFile(archive)
            .buildPatch("patch1");

        AssertUtil.assertNotIdentical(originalInstall.getHome(), testInstall.getHome(), true);
        env.apply(archive);
        AssertUtil.assertIdentical(originalInstall.getHome(), testInstall.getHome(), true);

        envHistory = env.environmentHistory();
        assertTrue(envHistory.hasNext());
        final ProvisionEnvironmentInfo patchedInfo = envHistory.next();

        assertEquals(installedInfo, patchedInfo);

        ProvisionPackage.newBuilder()
            .setCurrentInstallationDir(testInstall.getHome())
            .setPackageOutputFile(archive)
            .buildUninstall();
        env.apply(archive);
        AssertUtil.assertEmptyDirBranch(testInstall.getHome());
        assertFalse(env.environmentHistory().hasNext());
        assertTrue(env.getEnvironmentInfo().getUnitNames().isEmpty());
    }
}
