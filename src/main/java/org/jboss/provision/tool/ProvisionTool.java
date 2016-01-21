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

package org.jboss.provision.tool;

import java.io.File;

import org.jboss.provision.ProvisionEnvironment;
import org.jboss.provision.ProvisionErrors;
import org.jboss.provision.ProvisionException;
import org.jboss.provision.history.EnvironmentHistoryRecord;
import org.jboss.provision.history.ProvisionEnvironmentHistory;
import org.jboss.provision.tool.ApplicationContextImpl.CommitCallback;
import org.jboss.provision.tool.instruction.ProvisionEnvironmentInstruction;

/**
 *
 * @author Alexey Loubyansky
 */
public class ProvisionTool {

    private ProvisionTool() {
    }

    public static ProvisionEnvironment apply(ProvisionEnvironment env, File packageFile) throws ProvisionException {
        assert env != null : ProvisionErrors.nullArgument("env");
        assert packageFile != null : ProvisionErrors.nullArgument("packageFile");
        final ApplicationContextImpl appCtx = new ApplicationContextImpl(env, ContentSource.forZip(packageFile));
        return appCtx.processPackage(packageFile);
    }

    public static ProvisionEnvironment rollbackLast(ProvisionEnvironment env) throws ProvisionException {
        assert env != null : ProvisionErrors.nullArgument("env");
        final EnvironmentHistoryRecord record = ProvisionEnvironmentHistory.getInstance(env).getLastRecord();
        if(record == null) {
            throw ProvisionErrors.noHistoryRecordedUntilThisPoint();
        }
        final ApplicationContextImpl appCtx = new ApplicationContextImpl(env, record.getBackup());
        ProvisionEnvironmentInstruction rollback = record.getAppliedInstruction().getRollback();
        return appCtx.apply(rollback, CommitCallback.ROLLBACK);
    }
}