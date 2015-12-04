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

import org.jboss.provision.ProvisionErrors;
import org.jboss.provision.ProvisionException;

/**
 *
 * @author Alexey Loubyansky
 */
public class ProvisionEnvironmentHistory {

    private static final String PKG_HISTORY_DIR = "packages";
    private static final String UNITS_HISTORY_DIR = "units";

    public static ProvisionEnvironmentHistory create(File dir) {
        return new ProvisionEnvironmentHistory(dir);
    }

    private final File pkgHistory;
    private final File unitsHistory;

    private ProvisionEnvironmentHistory(File dir) {
        assert dir != null : ProvisionErrors.nullArgument("dir");
        this.pkgHistory = new File(dir, PKG_HISTORY_DIR);
        this.unitsHistory = new File(dir, UNITS_HISTORY_DIR);
    }

    public AppliedPackage getLastAppliedPackage() throws ProvisionException {
        return AppliedPackage.loadLastAppliedPackage(pkgHistory);
    }

    public AppliedUnitUpdate getLastAppliedUnitUpdate(String unitName) throws ProvisionException {
        return AppliedUnitUpdate.loadLastAppliedUpdate(unitsHistory, unitName);
    }
}
