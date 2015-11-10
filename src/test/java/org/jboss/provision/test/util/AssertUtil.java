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

package org.jboss.provision.test.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.IOException;

import org.jboss.provision.tool.instruction.ContentItemInstruction;
import org.junit.Assert;

/**
 *
 * @author Alexey Loubyansky
 */
public class AssertUtil {

    public static void assertAdd(ContentItemInstruction item, byte[] hash) throws IOException {
        assertReplace(item, null, hash);
    }

    public static void assertDelete(ContentItemInstruction item, byte[] hash) throws IOException {
        assertReplace(item, hash, null);
    }

    public static void assertReplace(ContentItemInstruction item, byte[] replacedHash, byte[] hash) throws IOException {
        assertEquals(1, item.getConditions().size()); // hash condition
        if(hash == null) {
            assertNull(item.getContentHash());
        } else {
            Assert.assertArrayEquals(hash, item.getContentHash());
        }
        if(replacedHash == null) {
            assertNull(item.getReplacedHash());
        } else {
            Assert.assertArrayEquals(replacedHash, item.getReplacedHash());
        }
    }
}
