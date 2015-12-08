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
import java.util.UUID;

import org.jboss.provision.ProvisionEnvironment;
import org.jboss.provision.ProvisionErrors;
import org.jboss.provision.ProvisionException;
import org.jboss.provision.tool.instruction.ProvisionEnvironmentInstruction;
import org.jboss.provision.tool.instruction.ProvisionUnitInstruction;

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

    public void addLastAppliedPackage(ProvisionEnvironmentInstruction instruction) throws ProvisionException {
        assert instruction != null : ProvisionErrors.nullArgument("instruction");

        final File pkgDir = new File(pkgHistory, UUID.randomUUID().toString());
        if(pkgDir.exists()) {
            throw ProvisionErrors.pathAlreadyExists(pkgDir);
        }
        final AppliedPackage pkg = new AppliedPackage(pkgDir);

        if(instruction.getUnitNames().isEmpty()) {
            throw ProvisionErrors.packageDoesNotAffectUnits();
        }
        for(String unitName : instruction.getUnitNames()) {
            final ProvisionUnitInstruction unitInstr = instruction.getUnitInstruction(unitName);
        }
    }

    public void addAppliedInstruction(ProvisionEnvironment env, ProvisionEnvironmentInstruction instruction) throws ProvisionException {
        final AppliedEnvironmentInstruction appliedInstr = AppliedEnvironmentInstruction.create(env, instruction);
    }
}
