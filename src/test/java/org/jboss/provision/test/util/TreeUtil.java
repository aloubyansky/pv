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

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;

import org.jboss.provision.util.HashUtils;
import org.jboss.provision.util.PropertyUtils;

/**
 *
 * @author Alexey Loubyansky
 */
public class TreeUtil {

    private static final String LN = PropertyUtils.getSystemProperty("line.separator");

    public static void logTree(File f) throws IOException {
        final StringBuilder buf = new StringBuilder();
        buildTree(f, buf, new LinkedList<Boolean>());
        System.out.println(buf.toString());
    }

    private static void buildTree(File f, StringBuilder buf, LinkedList<Boolean> depth) throws IOException {

        if(!depth.isEmpty()) {
            for(int i = 0; i < depth.size() - 1; ++i) {
                if(depth.get(i)) {
                    buf.append("|  ");
                } else {
                    buf.append("   ");
                }
            }
            if(depth.getLast()) {
                buf.append("|--");
            } else {
                buf.append("`--");
            }
        }
        buf.append(f.getName());
        if(f.isDirectory()) {
            buf.append(LN);
            final File[] files = f.listFiles();
            int i = 0;
            while(i < files.length) {
                final File c = files[i++];
                depth.addLast(i != files.length);
                buildTree(c, buf, depth);
                depth.removeLast();
            }
        } else {
            buf.append(" (hash=").append(HashUtils.bytesToHexString(HashUtils.hashFile(f))).append(')');
            buf.append(LN);
        }
    }
}
