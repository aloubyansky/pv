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

package org.jboss.provision;

import java.io.File;
import java.util.Iterator;
import java.util.NoSuchElementException;
import org.jboss.provision.UnitInstructionHistory.UnitRecord;
import org.jboss.provision.info.ProvisionEnvironmentInfo;
import org.jboss.provision.info.ProvisionUnitInfo;

/**
 *
 * @author Alexey Loubyansky
 */
class ProvisionEnvironmentHistory {

    static File getDefaultHistoryDir(File envHome) {
        return new File(envHome, ProvisionEnvironment.DEF_HISTORY_DIR);
    }

    static ProvisionEnvironmentHistory getInstance(ProvisionEnvironment env) {
        assert env != null : ProvisionErrors.nullArgument("env");
        return new ProvisionEnvironmentHistory(getDefaultHistoryDir(env.getEnvironmentHome()));
    }

/*    static ProvisionEnvironmentHistory getInstance(File historyHome) {
        return new ProvisionEnvironmentHistory(historyHome);
    }

    static ProvisionEnvironmentHistory forEnvironment(File envHome) {
        return new ProvisionEnvironmentHistory(new File(envHome, ProvisionEnvironment.DEF_HISTORY_DIR));
    }
*/
    static boolean storesHistory(File dir) throws ProvisionException {
        assert dir != null : ProvisionErrors.nullArgument("dir");
        if(!dir.exists()) {
            return false;
        }
        if(!dir.isDirectory()) {
            return false;
        }
        return EnvInstructionHistory.getInstance(dir).getLastAppliedId() != null;
    }

    private final File historyHome;

    protected ProvisionEnvironmentHistory(File historyHome) {
        assert historyHome != null : ProvisionErrors.nullArgument("historyHome");
        this.historyHome = historyHome;
    }

    File getHistoryHome() {
        return historyHome;
    }

    EnvInstructionHistory getEnvInstructionHistory() {
        return EnvInstructionHistory.getInstance(historyHome);
    }

    ProvisionEnvironment getCurrentEnvironment() throws ProvisionException {
        final EnvInstructionHistory.EnvRecord appliedInstr = EnvInstructionHistory.getInstance(historyHome).loadLastApplied();
        return appliedInstr == null ? null : appliedInstr.getUpdatedEnvironment();
    }

    EnvInstructionHistory.EnvRecord getLastEnvironmentRecord() throws ProvisionException {
        return EnvInstructionHistory.getInstance(historyHome).loadLastApplied();
    }

    UnitInstructionHistory.UnitRecord getLastUnitRecord(String unitName) throws ProvisionException {
        return UnitInstructionHistory.getInstance(EnvInstructionHistory.getInstance(historyHome), unitName).loadLast();
    }

    Iterator<EnvInstructionHistory.EnvRecord> appliedInstructions() {
        return new Iterator<EnvInstructionHistory.EnvRecord>() {
            boolean doNext = true;
            EnvInstructionHistory.EnvRecord appliedInstr;
            @Override
            public boolean hasNext() {
                if(doNext) {
                    doNext();
                }
                return appliedInstr != null;
            }
            @Override
            public EnvInstructionHistory.EnvRecord next() {
                if(hasNext()) {
                    doNext = true;
                        return appliedInstr;
                }
                throw new NoSuchElementException();
            }
            protected void doNext() {
                if(!doNext) {
                    return;
                }
                try {
                    if (appliedInstr == null) {
                        appliedInstr = EnvInstructionHistory.getInstance(historyHome).loadLastApplied();
                    } else {
                        appliedInstr = appliedInstr.getPrevious();
                    }
                } catch(ProvisionException e) {
                    throw new IllegalStateException(e);
                }
                doNext = false;
            }};
    }

    Iterator<UnitRecord> unitBackupRecords(final String unitName) {
        assert unitName != null : ProvisionErrors.nullArgument("unitName");
        return new Iterator<UnitRecord>() {
            boolean doNext = true;
            UnitRecord record;
            @Override
            public boolean hasNext() {
                if(doNext) {
                    doNext();
                }
                return record != null;
            }
            @Override
            public UnitRecord next() {
                if(hasNext()) {
                    doNext = true;
                        return record;
                }
                throw new NoSuchElementException();
            }
            protected void doNext() {
                if(!doNext) {
                    return;
                }
                try {
                    if (record == null) {
                        record = UnitInstructionHistory.getInstance(EnvInstructionHistory.getInstance(historyHome), unitName).loadLast();
                    } else {
                        record = record.getPrevious();
                    }
                } catch(ProvisionException e) {
                    throw new IllegalStateException(e);
                }
                doNext = false;
            }};
    }

    Iterator<ProvisionEnvironmentInfo> environmentIterator() {
        return new Iterator<ProvisionEnvironmentInfo>() {
            final Iterator<EnvInstructionHistory.EnvRecord> delegate = appliedInstructions();
            @Override
            public boolean hasNext() {
                return delegate.hasNext();
            }
            @Override
            public ProvisionEnvironmentInfo next() {
                try {
                    return delegate.next().getUpdatedEnvironment().getEnvironmentInfo();
                } catch (ProvisionException e) {
                    throw new IllegalStateException(e);
                }
            }};
    }

    Iterator<ProvisionUnitInfo> unitIterator(final String unitName) {
        return new Iterator<ProvisionUnitInfo>() {
            final Iterator<UnitRecord> delegate = unitBackupRecords(unitName);
            @Override
            public boolean hasNext() {
                return delegate.hasNext();
            }
            @Override
            public ProvisionUnitInfo next() {
                try {
                    return delegate.next().getUpdatedUnitInfo();
                } catch (ProvisionException e) {
                    throw new IllegalStateException(e);
                }
            }};
    }
}
