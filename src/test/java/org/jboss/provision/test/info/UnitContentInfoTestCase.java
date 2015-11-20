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

package org.jboss.provision.test.info;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.util.Collection;

import org.jboss.provision.info.ContentItemInfo;
import org.jboss.provision.info.ContentPath;
import org.jboss.provision.info.ProvisionUnitContentInfo;
import org.jboss.provision.info.ProvisionUnitInfo;
import org.jboss.provision.test.TestWithInstallationBuilder;
import org.jboss.provision.tool.ProvisionInfoReader;
import org.jboss.provision.util.HashUtils;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author Alexey Loubyansky
 */
public class UnitContentInfoTestCase extends TestWithInstallationBuilder {

    @Test
    public void testMain() throws Exception {

        home.createFileWithRandomContent("a.txt")
            .createFileWithRandomContent("b/b.txt")
            .createFileWithRandomContent("c/c/c.txt")
            .createDir("d/e/f");

        final ProvisionUnitContentInfo unitInfo = ProvisionInfoReader.readContentInfo(home.getHome());
        assertNotNull(unitInfo);
        assertEquals(ProvisionUnitInfo.UNDEFINED_INFO.getName(), unitInfo.getName());
        assertEquals(ProvisionUnitInfo.UNDEFINED_INFO.getVersion(), unitInfo.getVersion());

        final Collection<ContentItemInfo> contentInfo = unitInfo.getContentInfo();
        assertEquals(3, contentInfo.size());

        assertContent(unitInfo, "a.txt");
        assertContent(unitInfo, "b/b.txt");
        assertContent(unitInfo, "c/c/c.txt");
    }

    private void assertContent(final ProvisionUnitContentInfo unitInfo, String relativePath) throws IOException {
        ContentItemInfo itemInfo = unitInfo.getContentInfo(ContentPath.BUILDER.build(relativePath));
        assertNotNull(itemInfo);
        assertNull(itemInfo.getPath().getNamedLocation());
        assertEquals(relativePath, itemInfo.getPath().getRelativePath());
        Assert.assertArrayEquals(HashUtils.hashFile(home.resolvePath(relativePath)), itemInfo.getContentHash());
    }
}
