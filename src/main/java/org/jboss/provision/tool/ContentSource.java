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
            public boolean isAvailable(ProvisionUnitEnvironment unitEnv, ContentPath path) {
                return zip.getEntry(path.getRelativePath()) != null;
            }
            @Override
            public InputStream getInputStream(ProvisionUnitEnvironment unitEnv, ContentPath path, boolean errorIfNotResolved) throws ProvisionException {
                return getInputStream(path, errorIfNotResolved);
            }
            @Override
            public InputStream getInputStream(ProvisionEnvironment env, ContentPath path, boolean errorIfNotResolved) throws ProvisionException {
                return getInputStream(path, errorIfNotResolved);
            }
            protected InputStream getInputStream(ContentPath path, boolean errorIfNotResolved) throws ProvisionException {
                try {
                    return zip.getInputStream(new ZipEntry(path.getRelativePath())); // TODO THIS NEEDS A BETTER PATH BINDING
                } catch (ZipException e) {
                    if(errorIfNotResolved) {
                        throw ProvisionErrors.zipFormatError(f, e);
                    }
                } catch (IOException e) {
                    if(errorIfNotResolved) {
                        throw ProvisionErrors.readError(f, e);
                    }
                }
                return null;
            }
        };
    }

    public static ContentSource forBackup(final File instrDir) throws ProvisionException {
        assert instrDir != null : ProvisionErrors.nullArgument("backupDir");
        if(!instrDir.exists()) {
            throw ProvisionErrors.pathDoesNotExist(instrDir);
        }
        return new ContentSource() {
            final String units = "units";
            final String instrId = instrDir.getName();
            final File historyDir = instrDir.getParentFile();

            @Override
            public void close() throws IOException {
            }

            public boolean isAvailable(ProvisionUnitEnvironment unitEnv, ContentPath path) {
                return new File(getBaseDir(unitEnv), path.getFSRelativePath()).exists();
            }

            @Override
            public InputStream getInputStream(ProvisionEnvironment env, ContentPath path, boolean errorIfNotResolved) throws ProvisionException {
                return getInputStream(historyDir, path, errorIfNotResolved);
            }

            @Override
            public InputStream getInputStream(ProvisionUnitEnvironment unitEnv, ContentPath path, boolean errorIfNotResolved) throws ProvisionException {
                final File unitDir = getBaseDir(unitEnv);
                if(!unitDir.exists()) {
                    if(errorIfNotResolved) {
                        throw ProvisionErrors.pathDoesNotExist(unitDir);
                    }
                    return null;
                }
                return getInputStream(unitDir, path, errorIfNotResolved);
            }

            protected File getBaseDir(ProvisionUnitEnvironment unitEnv) {
                return IoUtils.newFile(historyDir, units, unitEnv.getUnitInfo().getName(), instrId);
            }

            protected InputStream getInputStream(final File baseDir, ContentPath path, boolean errorIfNotResolved) throws ProvisionException {
                final File targetFile = new File(baseDir, path.getFSRelativePath());
                try {
                    return new FileInputStream(targetFile);
                } catch (FileNotFoundException e) {
                    if(errorIfNotResolved) {
                        throw ProvisionErrors.pathDoesNotExist(targetFile);
                    }
                }
                return null;
            }
        };
    }

    public abstract boolean isAvailable(ProvisionUnitEnvironment unitEnv, ContentPath path);

    public InputStream getInputStream(ProvisionEnvironment env, ContentPath path) throws ProvisionException {
        return getInputStream(env, path, true);
    }

    public abstract InputStream getInputStream(ProvisionEnvironment env, ContentPath path, boolean errorIfNotResolved) throws ProvisionException;

    public InputStream getInputStream(ProvisionUnitEnvironment unitEnv, ContentPath path) throws ProvisionException {
        return getInputStream(unitEnv, path, true);
    }

    public abstract InputStream getInputStream(ProvisionUnitEnvironment unitEnv, ContentPath path, boolean errorIfNotResolved) throws ProvisionException;
}
