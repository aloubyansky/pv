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

package org.jboss.provision.history;

import java.io.File;
import java.io.IOException;

import org.jboss.provision.ProvisionErrors;
import org.jboss.provision.ProvisionException;
import org.jboss.provision.util.FileUtils;
import org.jboss.provision.util.IoUtils;

/**
 *
 * @author Alexey Loubyansky
 */
class AppliedUnitUpdate {

    private static final String BACKUP_DIR = "backup";
    private static final String INSTRUCTIONS = "instructions";
    private static final String LAST_UPDATE_TXT = "lastupdate.txt";
    private static final String PKG_DIR_TXT = "pkg-dir.txt";
    private static final String PREV_UPDATE_TXT = "prevupdate.txt";

    static AppliedUnitUpdate loadLastAppliedUpdate(File unitHistoryDir, String unitName) throws ProvisionException {
        final File lastUpdateDir = getLastAppliedUpdateDir(unitHistoryDir, unitName);
        if(!lastUpdateDir.exists()) {
            throw ProvisionErrors.pathDoesNotExist(lastUpdateDir);
        }
        return new AppliedUnitUpdate(unitName, lastUpdateDir);
    }

    static File getLastAppliedUpdateDir(final File unitHistoryDir, final String unitName) throws ProvisionException {
        assert unitHistoryDir != null : ProvisionErrors.nullArgument("unitHistoryDir");
        assert unitName != null : ProvisionErrors.nullArgument("unitName");
        String dirName;
        try {
            dirName = FileUtils.readFile(IoUtils.newFile(unitHistoryDir, unitName, LAST_UPDATE_TXT));
        } catch (IOException e) {
            throw ProvisionErrors.readError(IoUtils.newFile(unitHistoryDir, unitName, LAST_UPDATE_TXT), e);
        }
        return new File(unitHistoryDir, dirName);
    }

    private final String unitName;
    private final File dir;

    AppliedUnitUpdate(String unitName, File dir) {
        assert unitName != null : ProvisionErrors.nullArgument("unitName");
        assert dir != null : ProvisionErrors.nullArgument("dir");
        this.unitName = unitName;
        this.dir = dir;
    }
}
