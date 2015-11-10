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

import static org.junit.Assert.assertNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jboss.provision.tool.instruction.ContentItemInstruction;
import org.jboss.provision.util.HashUtils;
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
        Assert.assertEquals(1, item.getConditions().size()); // hash condition
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

    public static void assertEquals(File original, File target) {
        assertEquals(original, target, false);
    }

    public static void assertEquals(File original, File target, boolean ignoreEmptyDirs) {

        if(original.isDirectory()) {
            Assert.assertTrue(target.isDirectory());

            final Set<String> targetNames = target.list().length == 0 ?
                    Collections.<String>emptySet() : new HashSet<String>(Arrays.asList(target.list()));
            for(File o : original.listFiles()) {
                if(!targetNames.remove(o.getName())) {
                    if (ignoreEmptyDirs) {
                        if (!o.isDirectory() || !FSUtils.isEmptyDirBranch(o)) {
                            Assert.fail(target.getAbsolutePath() + " is missing child " + o.getName());
                        }
                    } else {
                        Assert.fail(target.getAbsolutePath() + " is missing child " + o.getName());
                    }
                } else {
                    assertEquals(o, new File(target, o.getName()), ignoreEmptyDirs);
                }
            }
            if(!targetNames.isEmpty()) {
                if(ignoreEmptyDirs) {
                    List<String> emptyDirs = new ArrayList<String>();
                    for (String t : targetNames) {
                        final File tFile = new File(target, t);
                        if (tFile.isDirectory() && FSUtils.isEmptyDirBranch(tFile)) {
                            emptyDirs.add(t);
                        }
                    }
                    targetNames.removeAll(emptyDirs);
                }
                if(!targetNames.isEmpty()) {
                    Assert.fail(target.getAbsolutePath() + " contains unexpected children " + targetNames);
                }
            }
        } else {
            Assert.assertEquals(original.getName(), target.getName());
            Assert.assertTrue(target.isFile());
            try {
                Assert.assertArrayEquals(HashUtils.hashFile(original), HashUtils.hashFile(target));
            } catch (IOException e) {
                throw new IllegalStateException("Failed to compute hash", e);
            }
        }
    }
}
