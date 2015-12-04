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

package org.jboss.provision.audit;

import java.util.Collection;

import org.jboss.provision.ProvisionEnvironment;
import org.jboss.provision.ProvisionException;
import org.jboss.provision.ProvisionUnitEnvironment;

/**
 *
 * @author Alexey Loubyansky
 */
public interface ProvisionEnvironmentJournal {

    class Factory {
        public static ProvisionEnvironmentJournal startSession(ProvisionEnvironment env) throws ProvisionException {
            return EnvironmentJournalImpl.start(env);
        }

        public static ProvisionEnvironmentJournal loadCrushedSession(ProvisionEnvironment env) throws ProvisionException {
            return EnvironmentJournalImpl.load(env);
        }
    }

    boolean isRecording();

    void record(ProvisionEnvironment env) throws ProvisionException;

    ProvisionUnitJournal getUnitJournal(ProvisionUnitEnvironment unitEnv) throws ProvisionException;

    Collection<ProvisionUnitJournal> getUnitJournals();

    void discardBackup() throws ProvisionException;

    void close() throws ProvisionException;
}
