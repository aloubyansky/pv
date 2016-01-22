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

package org.jboss.provision.test.packaging;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.provision.info.ProvisionUnitInfo;
import org.jboss.provision.instruction.ContentItemInstruction;
import org.jboss.provision.instruction.ProvisionEnvironmentInstruction;
import org.jboss.provision.instruction.ProvisionPackage;
import org.jboss.provision.instruction.ProvisionUnitInstruction;
import org.jboss.provision.io.IoUtils;
import org.jboss.provision.test.util.AssertUtil;
import org.jboss.provision.xml.ProvisionXml;
import org.junit.Test;

/**
 *
 * @author Alexey Loubyansky
 */
public class UninstallPackagingTestCase extends PackagingTestBase {

    @Test
    public void testMain() throws Exception {

        home.createFileWithRandomContent("a.txt")
            .createFileWithRandomContent("b/b.txt")
            .createFileWithRandomContent("c/c/c.txt")
            .createDir("d/e/f");

        ProvisionPackage.newBuilder()
            .setCurrentInstallationDir(home.getHome())
            .setPackageOutputFile(archive)
            .buildUninstall();

        final File unzipDir = unzipPackage();

        final Map<String, byte[]> expectedPaths = new HashMap<String, byte[]>();
        expectedPaths.put("provision.xml", NA);
        assertFilesContent(unzipDir, expectedPaths);

        ProvisionEnvironmentInstruction instruction = null;
        Reader reader = null;
        try {
            reader = new FileReader(new File(unzipDir, "provision.xml"));
            instruction = ProvisionXml.parse(reader);
        } finally {
            IoUtils.safeClose(reader);
        }

        assertNotNull(instruction);
        assertEquals(Collections.singleton(ProvisionUnitInfo.UNDEFINED_INFO.getName()), instruction.getUnitNames());
        final ProvisionUnitInstruction unit = instruction.getUnitInstruction(ProvisionUnitInfo.UNDEFINED_INFO.getName());
        assertNotNull(unit);

        assertEquals(ProvisionUnitInfo.UNDEFINED_INFO.getName(), unit.getName());
        assertEquals(ProvisionUnitInfo.UNDEFINED_INFO.getVersion(), unit.getReplacedVersion());
        assertNull(unit.getVersion());
        assertNull(unit.getId());

        assertEquals(1, unit.getConditions().size());  // version check condition

        final List<ContentItemInstruction> items = unit.getContentInstructions();
        assertEquals(3, items.size());

        for(ContentItemInstruction item : items) {
            final String path = item.getPath().getRelativePath();
            if("a.txt".equals(path)) {
                AssertUtil.assertDelete(item, home.hashOf(path));
            } else if("b/b.txt".equals(path)) {
                AssertUtil.assertDelete(item, home.hashOf(path));
            } else if("c/c/c.txt".equals(path)) {
                AssertUtil.assertDelete(item, home.hashOf(path));
            } else {
                fail("Unexpected path " + path);
            }
        }
    }
}
