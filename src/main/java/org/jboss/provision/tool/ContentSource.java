/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
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

package org.jboss.provision.tool;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.jboss.provision.ProvisionEnvironment;
import org.jboss.provision.ProvisionErrors;
import org.jboss.provision.ProvisionException;
import org.jboss.provision.ProvisionUnitEnvironment;
import org.jboss.provision.info.ContentPath;
import org.jboss.provision.io.IoUtils;

/**
 *
 * @author Alexey Loubyansky
 */
public abstract class ContentSource implements Closeable {

    public static ContentSource forZip(final File f) throws ProvisionException {
        assert f != null : ProvisionErrors.nullArgument("f");
        if(!f.exists()) {
            throw ProvisionErrors.pathDoesNotExist(f);
        }
        ZipFile tmpZip = null;
        try {
            tmpZip = new ZipFile(f);
        } catch (IOException e) {
            IoUtils.safeClose(tmpZip);
            throw ProvisionErrors.readError(f, e);
        }
        final ZipFile zip = tmpZip;
        return new ContentSource() {
            @Override
            public void close() throws IOException {
                if(zip != null) {
                    zip.close();
                }
            }
            @Override
            public InputStream getInputStream(ProvisionUnitEnvironment unitEnv, ContentPath path) throws ProvisionException {
                return getInputStream(path);
            }
            @Override
            public InputStream getInputStream(ProvisionEnvironment env, ContentPath path) throws ProvisionException {
                return getInputStream(path);
            }
            protected InputStream getInputStream(ContentPath path) throws ProvisionException {
                try {
                    return zip.getInputStream(new ZipEntry(path.getRelativePath())); // TODO THIS NEEDS A BETTER PATH BINDING
                } catch (ZipException e) {
                    throw ProvisionErrors.zipFormatError(f, e);
                } catch (IOException e) {
                    throw ProvisionErrors.readError(f, e);
                }
            }
        };
    }

    public static ContentSource forBackup(final File backupDir) throws ProvisionException {
        assert backupDir != null : ProvisionErrors.nullArgument("backupDir");
        if(!backupDir.exists()) {
            throw ProvisionErrors.pathDoesNotExist(backupDir);
        }
        return new ContentSource() {
            final String units = "units";

            final File baseDir = backupDir;

            @Override
            public void close() throws IOException {
            }

            @Override
            public InputStream getInputStream(ProvisionEnvironment env, ContentPath path) throws ProvisionException {
                return getInputStream(baseDir, path);
            }

            @Override
            public InputStream getInputStream(ProvisionUnitEnvironment unitEnv, ContentPath path) throws ProvisionException {
                final File unitDir = IoUtils.newFile(baseDir, units, unitEnv.getUnitInfo().getName());
                if(!unitDir.exists()) {
                    throw ProvisionErrors.pathDoesNotExist(unitDir);
                }
                return getInputStream(unitDir, path);
            }

            protected InputStream getInputStream(final File baseDir, ContentPath path) throws ProvisionException {
                final File targetFile = new File(baseDir, path.getFSRelativePath());
                try {
                    return new FileInputStream(targetFile);
                } catch (FileNotFoundException e) {
                    throw ProvisionErrors.pathDoesNotExist(targetFile);
                }
            }
        };
    }

    public abstract InputStream getInputStream(ProvisionEnvironment env, ContentPath path) throws ProvisionException;

    public abstract InputStream getInputStream(ProvisionUnitEnvironment unitEnv, ContentPath path) throws ProvisionException;
}
