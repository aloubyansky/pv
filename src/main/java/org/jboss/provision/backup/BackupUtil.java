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

package org.jboss.provision.backup;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;

import org.jboss.provision.ProvisionErrors;
import org.jboss.provision.ProvisionException;
import org.jboss.provision.info.ContentPath;
import org.jboss.provision.tool.instruction.ContentItemInstruction;
import org.jboss.provision.util.HashUtils;
import org.jboss.provision.util.IoUtils;

/**
 *
 * @author Alexey Loubyansky
 */
class BackupUtil {

    private static final String FALSE = "false";
    private static final String HASH = "hash";
    private static final String LOCATION = "location";
    private static final String RELATIVE_PATH = "relative-path";
    private static final String REPLACED_HASH = "replaced-hash";
    private static final String REQUIRED = "required";
    private static final String TRUE = "true";

    static void record(ContentItemInstruction instruction, File f) throws ProvisionException {

        assert instruction != null : ProvisionErrors.nullArgument("instruction");
        assert f != null : ProvisionErrors.nullArgument("file");

        if(f.exists()) {
            throw ProvisionErrors.backupInstructionFailed(instruction, ProvisionErrors.pathAlreadyExists(f));
        }

        final Properties props = new Properties();
        props.setProperty(REQUIRED, instruction.isRequired() ? TRUE : FALSE);
        if(instruction.getPath().getNamedLocation() != null) {
            props.setProperty(LOCATION, instruction.getPath().getNamedLocation());
        }
        props.setProperty(RELATIVE_PATH, instruction.getPath().getRelativePath());
        if(instruction.getContentHash() != null) {
            props.setProperty(HASH, HashUtils.bytesToHexString(instruction.getContentHash()));
        }
        if(instruction.getReplacedHash() != null) {
            props.setProperty(REPLACED_HASH, HashUtils.bytesToHexString(instruction.getReplacedHash()));
        }

        FileWriter writer = null;
        try {
            writer = new FileWriter(f);
            props.store(writer, null);
        } catch (IOException e) {
            throw ProvisionErrors.backupInstructionFailed(instruction, e);
        } finally {
            IoUtils.safeClose(writer);
        }
    }

    static ContentItemInstruction load(File f) throws ProvisionException {

        assert f != null : ProvisionErrors.nullArgument("file");

        if(!f.exists()) {
            throw ProvisionErrors.backedUpInstructionLoadFailed(ProvisionErrors.pathDoesNotExist(f));
        }

        final Properties props = new Properties();

        FileReader reader = null;
        try {
            reader = new FileReader(f);
            props.load(reader);
        } catch (IOException e) {
            throw ProvisionErrors.backedUpInstructionLoadFailed(e);
        } finally {
            IoUtils.safeClose(reader);
        }

        final String location = props.getProperty(LOCATION);
        final String relativePath = props.getProperty(RELATIVE_PATH);
        if(relativePath == null) {
            throw ProvisionErrors.backedUpInstructionLoadFailed(ProvisionErrors.relativePathMissing());
        }
        final ContentPath path = ContentPath.BUILDER.build(location, relativePath);

        final byte[] hash = props.containsKey(HASH) ? HashUtils.hexStringToByteArray(props.getProperty(HASH)) : null;
        final byte[] replacedHash = props.containsKey(REPLACED_HASH) ? HashUtils.hexStringToByteArray(props.getProperty(REPLACED_HASH)) : null;

        ContentItemInstruction.Builder builder;
        if(hash == null) {
            if(replacedHash == null) {
                throw ProvisionErrors.backedUpInstructionLoadFailed(ProvisionErrors.contentHashMissing());
            }
            builder = ContentItemInstruction.Builder.removeContent(path, replacedHash);
        } else if(replacedHash == null) {
            builder = ContentItemInstruction.Builder.addContent(path, replacedHash);
        } else {
            builder = ContentItemInstruction.Builder.replaceContent(path, hash, replacedHash);
        }

        return builder.setRequired(TRUE.equals(props.getProperty(REQUIRED))).build();
    }
}
