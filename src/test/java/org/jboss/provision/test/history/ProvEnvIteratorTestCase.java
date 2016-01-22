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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.jboss.provision.ProvisionEnvironment;
import org.jboss.provision.info.ProvisionEnvironmentInfo;
import org.jboss.provision.instruction.ProvisionPackage;
import org.jboss.provision.test.application.ApplicationTestBase;
import org.junit.Test;

/**
 *
 * @author Alexey Loubyansky
 */
public class ProvEnvIteratorTestCase extends ApplicationTestBase {

    @Test
    public void testEmptyHistory() throws Exception {
        final ProvisionEnvironment env = ProvisionEnvironment.builder().setEnvironmentHome(testInstall.getHome()).build();
        Iterator<ProvisionEnvironmentInfo> i = env.environmentHistory();
        assertFalse(i.hasNext());
        try {
            i.next();
            fail("NoSuchElementException expected");
        } catch(NoSuchElementException e) {
            // expected
        }
    }

    @Test
    public void testMain() throws Exception {

        originalInstall.createFileWithRandomContent("a.txt")
            .createFileWithRandomContent("b/b.txt")
            .createFileWithRandomContent("c/c/c.txt");

        ProvisionPackage.newBuilder()
            .setTargetInstallationDir(originalInstall.getHome())
            .setPackageOutputFile(archive)
            .buildInstall("unitA", "1.0");

        final ProvisionEnvironment env = ProvisionEnvironment.builder().setEnvironmentHome(testInstall.getHome()).build();
        final ProvisionEnvironmentInfo infoEmpty = env.getEnvironmentInfo();
        assertTrue(infoEmpty.getUnitNames().isEmpty());

        env.apply(archive);
        final ProvisionEnvironmentInfo info1_0 = env.getEnvironmentInfo();
        assertEquals(Collections.singleton("unitA"), info1_0.getUnitNames());
        assertEquals("1.0", info1_0.getUnitInfo("unitA").getVersion());

        originalInstall.createFileWithRandomContent("d/d/d/d.txt");
        ProvisionPackage.newBuilder()
            .setCurrentInstallationDir(testInstall.getHome())
            .setTargetInstallationDir(originalInstall.getHome())
            .setPackageOutputFile(archive)
            .buildUpdate("unitA", "1.0", "1.1");
        env.apply(archive);
        final ProvisionEnvironmentInfo info1_1 = env.getEnvironmentInfo();
        assertEquals(Collections.singleton("unitA"), info1_1.getUnitNames());
        assertEquals("1.1", info1_1.getUnitInfo("unitA").getVersion());

        originalInstall.updateFileWithRandomContent("d/d/d/d.txt");
        ProvisionPackage.newBuilder()
            .setCurrentInstallationDir(testInstall.getHome())
            .setTargetInstallationDir(originalInstall.getHome())
            .setPackageOutputFile(archive)
            .buildPatch("patch1", "unitA", "1.1");
        env.apply(archive);
        final ProvisionEnvironmentInfo infoPatch1 = env.getEnvironmentInfo();
        assertEquals(Collections.singleton("unitA"), infoPatch1.getUnitNames());
        assertEquals("1.1", infoPatch1.getUnitInfo("unitA").getVersion());

        final Iterator<ProvisionEnvironmentInfo> i = env.environmentHistory();

        assertTrue(i.hasNext());
        ProvisionEnvironmentInfo envInfo = i.next();
        assertEquals(infoPatch1, envInfo);

        assertTrue(i.hasNext());
        envInfo = i.next();
        assertEquals(info1_1, envInfo);

        assertTrue(i.hasNext());
        envInfo = i.next();
        assertEquals(info1_0, envInfo);

        assertFalse(i.hasNext());
        try {
            i.next();
            fail("NoSuchElementException expected");
        } catch(NoSuchElementException e) {
            // expected
        }
    }
}
