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

import org.jboss.provision.ProvisionEnvironment;
import org.jboss.provision.ProvisionErrors;
import org.jboss.provision.ProvisionException;
import org.jboss.provision.io.IoUtils;
import org.jboss.provision.tool.instruction.ProvisionEnvironmentInstruction;

/**
 *
 * @author Alexey Loubyansky
 */
public class ProvisionEnvironmentHistory {

    public static final String DEF_HISTORY_DIR = ".pvh";

    public static ProvisionEnvironmentHistory getInstance(ProvisionEnvironment env) {
        assert env != null : ProvisionErrors.nullArgument("env");
        return new ProvisionEnvironmentHistory(new File(env.getEnvironmentHome(), DEF_HISTORY_DIR));
    }

    public static ProvisionEnvironmentHistory getInstance(File historyHome) {
        return new ProvisionEnvironmentHistory(historyHome);
    }

    public static ProvisionEnvironmentHistory forEnvironment(File envHome) {
        return new ProvisionEnvironmentHistory(new File(envHome, DEF_HISTORY_DIR));
    }

    public static boolean storesHistory(File dir) throws ProvisionException {
        assert dir != null : ProvisionErrors.nullArgument("dir");
        if(!dir.exists()) {
            return false;
        }
        if(!dir.isDirectory()) {
            return false;
        }
        return AppliedEnvironmentInstruction.getLastAppliedInstrDir(dir) != null;
    }

    private final File historyHome;

    private ProvisionEnvironmentHistory(File historyHome) {
        assert historyHome != null : ProvisionErrors.nullArgument("historyHome");
        this.historyHome = historyHome;
    }

    public ProvisionEnvironment update(ProvisionEnvironment currentEnv, ProvisionEnvironmentInstruction instruction) throws ProvisionException {
        final AppliedEnvironmentInstruction appliedInstr = AppliedEnvironmentInstruction.create(currentEnv, instruction);
        final ProvisionEnvironment updatedEnv = appliedInstr.getUpdatedEnvironment();
        if(updatedEnv.getUnitNames().isEmpty()) { // delete the history when the environment is uninstalled
            IoUtils.recursiveDelete(historyHome);
        } else {
            AppliedEnvironmentInstruction.persist(appliedInstr, historyHome);
        }
        return updatedEnv;
    }

    public ProvisionEnvironment getCurrentEnvironment() throws ProvisionException {
        final AppliedEnvironmentInstruction appliedInstr = AppliedEnvironmentInstruction.loadLast(historyHome);
        return appliedInstr == null ? null : appliedInstr.getUpdatedEnvironment();
    }
}
