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
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.provision.info.ProvisionUnitInfo;
import org.jboss.provision.io.IoUtils;
import org.jboss.provision.test.util.AssertUtil;
import org.jboss.provision.test.util.InstallationBuilder;
import org.jboss.provision.tool.ProvisionPackage;
import org.jboss.provision.tool.instruction.ContentItemInstruction;
import org.jboss.provision.tool.instruction.ProvisionEnvironmentInstruction;
import org.jboss.provision.tool.instruction.ProvisionUnitInstruction;
import org.jboss.provision.xml.ProvisionXml;
import org.junit.Test;

/**
 *
 * @author Alexey Loubyansky
 */
public class PatchPackagingTestCase extends PackagingTestBase {

    private InstallationBuilder next;

    @Override
    protected void doInit() {
        next = InstallationBuilder.create();
    }

    @Override
    protected void doCleanUp() {
        IoUtils.recursiveDelete(next.getHome());
    }

    @Test
    public void testMain() throws Exception {

        home.createFileWithRandomContent("a.txt")
            .createFileWithRandomContent("b/b.txt")
            .createFileWithRandomContent("c/c/c.txt")
            .createDir("d/e/f");

        IoUtils.copyFile(home.getHome(), next.getHome());
        next.updateFileWithRandomContent("a.txt")
            .delete("b/b.txt")
            .createFileWithRandomContent("d/d/d/d.txt")
            .delete("d/e")
            .createDir("x/y/z");

        ProvisionPackage.newBuilder()
            .setCurrentInstallationDir(home.getHome())
            .setTargetInstallationDir(next.getHome())
            .setPackageOutputFile(archive)
            .setPatchId("patch1")
            .buildUpdate();

        final File unzipDir = unzipPackage();

        final Map<String, byte[]> expectedPaths = new HashMap<String, byte[]>();
        expectedPaths.put("a.txt", next.hashOf("a.txt"));
        expectedPaths.put("d/d/d/d.txt", next.hashOf("d/d/d/d.txt"));
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
        assertEquals(ProvisionUnitInfo.UNDEFINED_INFO.getVersion(), unit.getVersion());
        assertEquals(ProvisionUnitInfo.UNDEFINED_INFO.getVersion(), unit.getReplacedVersion());
        assertEquals("patch1", unit.getId());

        assertEquals(1, unit.getConditions().size());  // version check condition

        final List<ContentItemInstruction> items = unit.getContentInstructions();
        assertEquals(3, items.size());

        for(ContentItemInstruction item : items) {
            final String path = item.getPath().getRelativePath();
            if("a.txt".equals(path)) {
                AssertUtil.assertReplace(item, home.hashOf(path), next.hashOf(path));
            } else if("b/b.txt".equals(path)) {
                AssertUtil.assertDelete(item, home.hashOf(path));
            } else if("d/d/d/d.txt".equals(path)) {
                AssertUtil.assertAdd(item, next.hashOf(path));
            } else {
                fail("Unexpected path " + path);
            }
        }
    }
}
