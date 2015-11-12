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

    public static void assertIdentical(File original, File target) {
        assertIdentical(original, target, false);
    }

    public static void assertIdentical(File original, File target, boolean ignoreEmptyDirs) {
        final String error = compareContent(original, target, ignoreEmptyDirs);
        if(error != null) {
            Assert.fail(error);
        }
    }

    public static void assertNotIdentical(File original, File target, boolean ignoreEmptyDirs) {
        final String error = compareContent(original, target, ignoreEmptyDirs);
        if(error == null) {
            Assert.fail("The branches are identical");
        }
    }

    private static String compareContent(File original, File target, boolean ignoreEmptyDirs) {

        if(original.isDirectory()) {
            if(!target.isDirectory()) {
                return target.getAbsolutePath() + " is not a directory while " + original.getAbsolutePath() + " is";
            }

            final Set<String> targetNames = target.list().length == 0 ?
                    Collections.<String>emptySet() : new HashSet<String>(Arrays.asList(target.list()));
            for(File o : original.listFiles()) {
                if(!targetNames.remove(o.getName())) {
                    if (ignoreEmptyDirs) {
                        if (!o.isDirectory() || !FSUtils.isEmptyDirBranch(o)) {
                            return target.getAbsolutePath() + " is missing child " + o.getName();
                        }
                    } else {
                        return target.getAbsolutePath() + " is missing child " + o.getName();
                    }
                } else {
                    final String result = compareContent(o, new File(target, o.getName()), ignoreEmptyDirs);
                    if(result != null) {
                        return result;
                    }
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
                    return target.getAbsolutePath() + " contains unexpected children " + targetNames;
                }
            }
        } else {
            if(!original.getName().equals(target.getName())) {
                return "File names don't match: " + original.getAbsolutePath() + " vs " + target.getAbsolutePath();
            }
            if(target.isDirectory()) {
                return target.getAbsolutePath() + " is a directory while " + original.getAbsolutePath() + " is not";
            }
            try {
                if(!Arrays.equals(HashUtils.hashFile(original), HashUtils.hashFile(target))) {
                    return "Hashes of " + original.getAbsolutePath() + " and " + target.getAbsolutePath() + " don't match";
                }
            } catch (IOException e) {
                throw errorToComputeHash(e);
            }
        }
        return null;
    }

    public static void assertExpectedContentInTarget(File expected, File target) {
        assertExpectedContentInTarget(expected, target, false);
    }

    public static void assertExpectedContentInTarget(File expected, File target, boolean ignoreEmptyDirs) {
        final String error = expectedContentInTarget(expected, target, ignoreEmptyDirs);
        if(error != null) {
            Assert.fail(error);
        }
    }

    public static void assertExpectedFilesNotInTarget(File expected, File target, boolean all) {
        final String error = expectedFilesNotInTarget(expected, target, all);
        if(error != null) {
            Assert.fail(error);
        }
    }

    private static String expectedFilesNotInTarget(File expected, File target, boolean all) {

        if(expected.isDirectory()) {
            if(!target.isDirectory()) {
                throw errorNotADir(target);
            }
            String error = null;
            for(File e : expected.listFiles()) {
                final File t = new File(target, e.getName());
                if(t.exists()) {
                    if(e.isDirectory()) {
                        if(FSUtils.isEmptyDirBranch(e) || FSUtils.isEmptyDirBranch(t)) {
                            continue;
                        }
                    }
                    error = expectedFilesNotInTarget(e, t, all);
                    if(all) {
                        if(error != null) {
                            return error;
                        }
                    } else if(error == null) {
                        return null;
                    }
                }
            }
            if(error != null) {
                return error;
            }
        } else {
            if(target.isDirectory()) {
                errorIsADir(target);
            }
            if(!expected.getName().equals(target.getName())) {
                return null;
            }
            try {
                if(!Arrays.equals(HashUtils.hashFile(expected), HashUtils.hashFile(target))) {
                    return null;
                }
            } catch (IOException e) {
                throw errorToComputeHash(e);
            }
            return target.getAbsolutePath() + " is not expected";
        }
        return null;
    }

    private static String expectedContentInTarget(File expected, File target, boolean ignoreEmptyDirs) {

        if(expected.isDirectory()) {
            if(!target.isDirectory()) {
                return target.getAbsolutePath() + " is not a directory while " + expected.getAbsolutePath() + " is";
            }

            for(File o : expected.listFiles()) {
                final File t = new File(target, o.getName());
                if(o.isDirectory()) {
                    if(t.exists()) {
                        final String error = expectedContentInTarget(o, t, ignoreEmptyDirs);
                        if(error != null) {
                            return error;
                        }
                    } else if (!(ignoreEmptyDirs && FSUtils.isEmptyDirBranch(o))) {
                        return target.getAbsolutePath() + " is missing child " + o.getName();
                    }
                } else {
                    final String error = expectedContentInTarget(o, t, ignoreEmptyDirs);
                    if(error != null) {
                        return error;
                    }
                }
            }
        } else {
            if(!target.exists()) {
                return target.getAbsolutePath() + " does not exist";
            }
            if(!expected.getName().equals(target.getName())) {
                return "File names don't match: " + expected.getAbsolutePath() + " vs " + target.getAbsolutePath();
            }
            if(target.isDirectory()) {
                return target.getAbsolutePath() + " is a directory while " + expected.getAbsolutePath() + " is not";
            }
            try {
                if(!Arrays.equals(HashUtils.hashFile(expected), HashUtils.hashFile(target))) {
                    return "Hashes of " + expected.getAbsolutePath() + " and " + target.getAbsolutePath() + " don't match";
                }
            } catch (IOException e) {
                throw errorToComputeHash(e);
            }
        }
        return null;
    }

    public static void assertEmptyDirBranch(File dir) {
        if(!FSUtils.isEmptyDirBranch(dir)) {
            Assert.fail(dir.getAbsolutePath() + " contains files in its branch");
        }
    }

    private static IllegalStateException errorNotADir(File f) {
        return error(f.getAbsolutePath() + " is not a directory");
    }

    private static IllegalStateException errorIsADir(File f) {
        return error(f.getAbsolutePath() + " is a directory");
    }

    private static IllegalStateException errorToComputeHash(IOException e) {
        return error("Failed to compute hash", e);
    }

    private static IllegalStateException error(String msg) {
        return new IllegalStateException(msg);
    }

    private static IllegalStateException error(String msg, Throwable t) {
        return new IllegalStateException(msg, t);
    }
}
