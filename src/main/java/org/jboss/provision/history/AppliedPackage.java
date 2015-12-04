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
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.jboss.provision.ProvisionErrors;
import org.jboss.provision.ProvisionException;
import org.jboss.provision.util.FileUtils;
import org.jboss.provision.util.IoUtils;

/**
 *
 * @author Alexey Loubyansky
 */
public class AppliedPackage {

    private static final String LAST_PKG_TXT = "lastpackage.txt";
    private static final String PREV_PKG_TXT = "prevpackage.txt";
    private static final String UNITS_PROPS = "units.properties";

    static AppliedPackage loadLastAppliedPackage(File pkgHistoryDir) throws ProvisionException {
        final File lastUpdateDir = getLastAppliedPkgDir(pkgHistoryDir);
        if(!lastUpdateDir.exists()) {
            throw ProvisionErrors.pathDoesNotExist(lastUpdateDir);
        }
        return loadFromDir(lastUpdateDir);
    }

    static AppliedPackage loadFromDir(File dir) throws ProvisionException {
        assert dir != null : ProvisionErrors.nullArgument("dir");
        return new AppliedPackage(dir);
    }

    static File getLastAppliedPkgDir(final File pkgHistoryDir) throws ProvisionException {
        assert pkgHistoryDir != null : ProvisionErrors.nullArgument("pkgHistoryDir");
        String dirName;
        try {
            dirName = FileUtils.readFile(IoUtils.newFile(pkgHistoryDir, LAST_PKG_TXT));
        } catch (IOException e) {
            throw ProvisionErrors.readError(IoUtils.newFile(pkgHistoryDir, LAST_PKG_TXT), e);
        }
        return new File(pkgHistoryDir, dirName);
    }

    private static Map<String, String> propsToMap(Properties props) {
        if(props.isEmpty()) {
            return Collections.emptyMap();
        }
        final Enumeration<?> propertyNames = props.propertyNames();
        if(props.size() == 1) {
            final String name = (String) propertyNames.nextElement();
            return Collections.singletonMap(name, props.getProperty(name));
        }
        final Map<String, String> map = new HashMap<String, String>(props.size());
        while(propertyNames.hasMoreElements()) {
            final String name = (String) propertyNames.nextElement();
            map.put(name, props.getProperty(name));
        }
        return map;
    }

    private final File dir;
    private Map<String, String> units;
    private String prevPkgDir;

    private AppliedPackage(File dir) throws ProvisionException {
        assert dir != null : ProvisionErrors.nullArgument("dir");
        this.dir = dir;
    }

    public Collection<String> getUnitNames() throws ProvisionException {
        if(units == null) {
            loadUnits();
        }
        return units.keySet();
    }

    public String getUnitDirName(String unitName) throws ProvisionException {
        if(units == null) {
            loadUnits();
        }
        return units.get(unitName);
    }

    public String getPrevPackageDirName() throws ProvisionException {
        if(prevPkgDir == null) {
            loadPrevPackageDir();
        }
        return prevPkgDir;
    }

    protected void loadPrevPackageDir() throws ProvisionException {
        try {
            prevPkgDir = FileUtils.readFile(IoUtils.newFile(dir, PREV_PKG_TXT));
        } catch (IOException e) {
            throw ProvisionErrors.readError(IoUtils.newFile(dir, PREV_PKG_TXT), e);
        }
    }

    protected void loadUnits() throws ProvisionException {
        try {
            units = propsToMap(FileUtils.loadProperties(IoUtils.newFile(dir, UNITS_PROPS)));
        } catch (IOException e) {
            throw ProvisionErrors.readError(IoUtils.newFile(dir, UNITS_PROPS), e);
        }
    }
}
