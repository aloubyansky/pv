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
import static org.junit.Assert.fail;

import java.util.List;

import org.jboss.provision.info.ProvisionInfoReader;
import org.jboss.provision.info.ProvisionUnitContentInfo;
import org.jboss.provision.info.ProvisionUnitInfo;
import org.jboss.provision.instruction.ContentItemInstruction;
import org.jboss.provision.instruction.ProvisionInstructionBuilder;
import org.jboss.provision.instruction.ProvisionUnitInstruction;
import org.jboss.provision.test.TestWithInstallationBuilder;
import org.jboss.provision.test.util.AssertUtil;
import org.junit.Test;

/**
 *
 * @author Alexey Loubyansky
 */
public class PatchInstructionsTestCase extends TestWithInstallationBuilder {

    @Test
    public void testMain() throws Exception {

        home.createFileWithRandomContent("a.txt")
            .createFileWithRandomContent("b/b.txt")
            .createFileWithRandomContent("c/c/c.txt")
            .createDir("d/e/f");

        final ProvisionUnitContentInfo stateA = ProvisionInfoReader.readContentInfo(home.getHome());
        final byte[] stateAHashA = home.hashOf("a.txt");
        final byte[] stateAHashB = home.hashOf("b/b.txt");

        home.updateFileWithRandomContent("a.txt")
            .delete("b/b.txt")
            .createFileWithRandomContent("d/d/d/d.txt")
            .delete("d/e/f")
            .createDir("g/h/i");

        final ProvisionUnitContentInfo stateB = ProvisionInfoReader.readContentInfo(home.getHome());

        final ProvisionUnitInstruction replace = ProvisionInstructionBuilder.patch("patch1", stateA, stateB);

        assertNotNull(replace);
        assertEquals("patch1", replace.getId());
        assertEquals(ProvisionUnitInfo.UNDEFINED_INFO.getName(), replace.getUnitName());
        assertEquals(ProvisionUnitInfo.UNDEFINED_INFO.getVersion(), replace.getRequiredVersion());
        assertEquals(ProvisionUnitInfo.UNDEFINED_INFO.getVersion(), replace.getResultingVersion());

        assertEquals(1, replace.getConditions().size()); // version condition

        final List<ContentItemInstruction> contentInstructions = replace.getContentInstructions();
        assertEquals(3, contentInstructions.size());

        for(ContentItemInstruction item : contentInstructions) {
            final String path = item.getPath().getRelativePath();
            if(path.equals("a.txt")) {
                AssertUtil.assertReplace(item, stateAHashA, home.hashOf(path));
            } else if(path.equals("b/b.txt")) {
                AssertUtil.assertDelete(item, stateAHashB);
            } else if(path.equals("d/d/d/d.txt")) {
                AssertUtil.assertAdd(item, home.hashOf(path));
            } else {
                fail("unexpected path " + path);
            }
        }
    }
}
