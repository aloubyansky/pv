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

package org.jboss.provision.test.instruction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jboss.provision.info.ContentPath;
import org.jboss.provision.info.ProvisionUnitInfo;
import org.jboss.provision.test.TestWithInstallationBuilder;
import org.jboss.provision.test.util.AssertUtil;
import org.jboss.provision.tool.ProvisionInfoReader;
import org.jboss.provision.tool.ProvisionInstructionBuilder;
import org.jboss.provision.tool.instruction.ContentItemInstruction;
import org.jboss.provision.tool.instruction.ProvisionUnitInstruction;
import org.junit.Test;

/**
 *
 * @author Alexey Loubyansky
 */
public class UninstallInstructionsTestCase extends TestWithInstallationBuilder {

    @Test
    public void testMain() throws Exception {

        home.createFileWithRandomContent("a.txt")
            .createFileWithRandomContent("b/b.txt")
            .createFileWithRandomContent("c/c/c.txt")
            .createDir("d/e/f");

        final ProvisionUnitInstruction install = ProvisionInstructionBuilder.uninstall(ProvisionInfoReader.readContentInfo(home.getHome()));

        assertNotNull(install);
        assertNull(install.getId());
        assertEquals(ProvisionUnitInfo.UNDEFINED_INFO.getName(), install.getName());
        assertEquals(ProvisionUnitInfo.UNDEFINED_INFO.getVersion(), install.getReplacedVersion());
        assertNull(install.getVersion());

        assertEquals(1, install.getConditions().size()); // version condition

        final List<ContentItemInstruction> contentInstructions = install.getContentInstructions();
        assertEquals(3, contentInstructions.size());

        final Set<ContentPath> knownPaths = new HashSet<ContentPath>(3);
        knownPaths.add(ContentPath.create("a.txt"));
        knownPaths.add(ContentPath.create("b/b.txt"));
        knownPaths.add(ContentPath.create("c/c/c.txt"));

        for(ContentItemInstruction item : contentInstructions) {
            final ContentPath path = item.getPath();
            if(!knownPaths.remove(path)) {
                fail("unexpected path " + path);
            }
            AssertUtil.assertDelete(item, home.hashOf(path.getRelativePath()));
        }
    }
}
