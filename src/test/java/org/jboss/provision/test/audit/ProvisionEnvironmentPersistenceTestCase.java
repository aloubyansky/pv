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

import java.io.File;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Properties;

import org.jboss.provision.EnvPersistUtil;
import org.jboss.provision.ProvisionEnvironment;
import org.jboss.provision.ProvisionEnvironmentBuilder;
import org.jboss.provision.UnitUpdatePolicy;
import org.jboss.provision.info.ContentPath;
import org.jboss.provision.instruction.UpdatePolicy;
import org.jboss.provision.io.IoUtils;
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
        final ContentPath aUnitHome = ContentPath.forPath("aUnit");
        final ContentPath bUnitHome = ContentPath.create("bLocation", "bUnit");
        final ContentPath aLocation = ContentPath.forPath("a/a.txt");
        final ContentPath bLocation = ContentPath.forName("aLocation");
        final ContentPath cLocation = ContentPath.create("bLocation", "b/b.txt");
        final ProvisionEnvironment env = ProvisionEnvironment.builder()
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
            .nameLocation("cLocation", cLocation)
            .build();

        Properties props = EnvPersistUtil.toProperties(env, false);
        final StringWriter envWriter = new StringWriter();
        props.store(envWriter, null);

        props = EnvPersistUtil.toProperties(env.getUnitEnvironment("aUnit"));
        final StringWriter unitAWriter = new StringWriter();
        props.store(unitAWriter, null);

        props = EnvPersistUtil.toProperties(env.getUnitEnvironment("bUnit"));
        final StringWriter unitBWriter = new StringWriter();
        props.store(unitBWriter, null);

        props = EnvPersistUtil.toProperties(env.getUnitEnvironment("cUnit"));
        final StringWriter unitCWriter = new StringWriter();
        props.store(unitCWriter, null);


        final ProvisionEnvironmentBuilder envBuilder = ProvisionEnvironment.builder();
        EnvPersistUtil.loadEnv(envBuilder, new StringReader(envWriter.getBuffer().toString()));
        EnvPersistUtil.loadUnitEnv(envBuilder, new StringReader(unitAWriter.getBuffer().toString()));
        EnvPersistUtil.loadUnitEnv(envBuilder, new StringReader(unitBWriter.getBuffer().toString()));
        EnvPersistUtil.loadUnitEnv(envBuilder, new StringReader(unitCWriter.getBuffer().toString()));


        final ProvisionEnvironment loadedEnv = envBuilder.build();
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
