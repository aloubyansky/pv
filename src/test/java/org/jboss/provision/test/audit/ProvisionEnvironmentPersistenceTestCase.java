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

package org.jboss.provision.test.audit;

import static org.junit.Assert.assertEquals;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.StringWriter;

import org.jboss.provision.ProvisionEnvironment;
import org.jboss.provision.UnitUpdatePolicy;
import org.jboss.provision.audit.AuditUtil;
import org.jboss.provision.tool.instruction.UpdatePolicy;
import org.jboss.provision.util.IoUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author Alexey Loubyansky
 */
public class ProvisionEnvironmentPersistenceTestCase {

    private File envFile;

    @Before
    public void init() throws Exception {
        envFile = File.createTempFile("env", null);
        IoUtils.recursiveDelete(envFile);
    }

    @After
    public void cleanUp() throws Exception{
        IoUtils.recursiveDelete(envFile);
    }

    @Test
    public void testMain() throws Exception {

        final File installationHome = new File("test-location");
        final File aUnitHome = new File(installationHome, "aUnit");
        final File bUnitHome = new File(aUnitHome, "bUnit");
        final File aLocation = new File("aLocation");
        final File bLocation = new File(bUnitHome, "bLocation");
        final ProvisionEnvironment env = ProvisionEnvironment.create()
            .setEnvironmentHome(installationHome)
            .addUnit("aUnit", "0.0.1.Alpha-SNAPSHOT")
            .addUnit("bUnit", "1.0.0.GA")
            .addUnit("cUnit", "3.2.1.GA")
            .setUnitHome("aUnit", aUnitHome)
            .setUnitHome("bUnit", bUnitHome)
            .setDefaultUnitUpdatePolicy(UnitUpdatePolicy.CONDITIONED)
            .setUnitUpdatePolicy("bUnit", UnitUpdatePolicy.newBuilder()
                    .setUnitPolicy(UpdatePolicy.CONDITIONED)
                    .setDefaultContentPolicy(UpdatePolicy.FORCED)
                    .setPolicy("i/g/n/o/r/e/d", UpdatePolicy.IGNORED)
                    .setPolicy("conditioned", UpdatePolicy.CONDITIONED)
                    .build())
            .setUnitUpdatePolicy("cUnit", UnitUpdatePolicy.FORCED)
            .nameLocation("aLocation", aLocation)
            .nameLocation("bLocation", bLocation)
            .build();

        AuditUtil.record(env, envFile);

        BufferedReader reader = null;
        StringWriter original = new StringWriter();
        try {
            reader = new BufferedReader(new FileReader(envFile));
            String line = reader.readLine();
            while(line != null) {
                original.write(line);
                original.write("\n");
                line = reader.readLine();
            }
        } finally {
            IoUtils.safeClose(reader);
            original.flush();
        }

        final ProvisionEnvironment loadedEnv = AuditUtil.loadEnv(envFile);
/*        IoUtils.recursiveDelete(envFile);
        AuditUtil.record(loadedEnv, envFile);

        StringWriter loadedWriter = new StringWriter();
        try {
            reader = new BufferedReader(new FileReader(envFile));
            String line = reader.readLine();
            while(line != null) {
                loadedWriter.write(line);
                loadedWriter.write("\n");
                line = reader.readLine();
            }
        } finally {
            IoUtils.safeClose(reader);
            loadedWriter.flush();
        }
*/
        assertEquals(env, loadedEnv);
    }
}
