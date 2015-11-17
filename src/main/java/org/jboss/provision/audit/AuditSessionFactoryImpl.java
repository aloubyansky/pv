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

import java.io.File;

import org.jboss.provision.ProvisionEnvironment;
import org.jboss.provision.ProvisionException;

/**
 *
 * @author Alexey Loubyansky
 */
class AuditSessionFactoryImpl extends AuditSessionFactory {

    private static final String AUDIT_DIR = ".pvaudit";

    /* (non-Javadoc)
     * @see org.jboss.provision.audit.AuditSessionFactory#startSession(org.jboss.provision.ProvisionEnvironment)
     */
    @Override
    public AuditSession startSession(ProvisionEnvironment env) throws ProvisionException {
        return AuditSessionImpl.start(new File(env.getInstallationHome(), AUDIT_DIR));
    }

    @Override
    public AuditSession loadCrushedSession(ProvisionEnvironment env) throws ProvisionException {
        return AuditSessionImpl.load(new File(env.getInstallationHome(), AUDIT_DIR));
    }
}