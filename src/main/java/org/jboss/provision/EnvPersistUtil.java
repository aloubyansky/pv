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
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import org.jboss.provision.info.ContentPath;
import org.jboss.provision.info.ProvisionUnitInfo;
import org.jboss.provision.instruction.UpdatePolicy;
import org.jboss.provision.io.ContentWriter;
import org.jboss.provision.io.IoUtils;

/**
 *
 * @author Alexey Loubyansky
 */
public class EnvPersistUtil {

    private static final String CONTENT_POLICY = ".content";
    private static final String DOT = ".";
    private static final String DEFAULT_POLICY = "default-policy";
    private static final String ENV_HOME = "env-home";
    private static final String LOCATION = "location.";
    private static final String PATCHES = ".patches";
    private static final String POLICY = ".policy";
    private static final String UNIT_HOME = ".home";
    private static final String UNIT_POLICY = ".unit";
    private static final String VERSION = ".version";

    public static void loadEnv(ProvisionEnvironmentBuilder envBuilder, File f) throws ProvisionException {
        try {
            loadEnv(envBuilder, new FileReader(f));
        } catch (FileNotFoundException e) {
            throw ProvisionErrors.failedToLoadEnvironmentRecord(e);
        }
    }
    public static void loadEnv(ProvisionEnvironmentBuilder envBuilder, Reader reader) throws ProvisionException {
        final Properties props = new Properties();
        try {
            props.load(reader);
        } catch (IOException e) {
            throw ProvisionErrors.failedToLoadEnvironmentRecord(e);
        } finally {
            IoUtils.safeClose(reader);
        }

        UnitUpdatePolicy.Builder defPolicy = null;
        for(String prop : props.stringPropertyNames()) {
            if(prop.equals(ENV_HOME)) {
                envBuilder.setEnvironmentHome(new File(props.getProperty(prop)));
            } else if(prop.startsWith(DEFAULT_POLICY)) {
                if(defPolicy == null) {
                    defPolicy = UnitUpdatePolicy.newBuilder();
                }
                if(prop.startsWith(UNIT_POLICY, DEFAULT_POLICY.length())) {
                    defPolicy.setUnitPolicy(UpdatePolicy.valueOf(props.getProperty(prop)));
                } else if(prop.startsWith(CONTENT_POLICY, DEFAULT_POLICY.length())) {
                    defPolicy.setDefaultContentPolicy(UpdatePolicy.valueOf(props.getProperty(prop)));
                } else {
                    final String path = prop.substring(DEFAULT_POLICY.length() + 1);
                    defPolicy.setPolicy(path, UpdatePolicy.valueOf(props.getProperty(prop)));
                }
            } else if(prop.startsWith(LOCATION)) {
                final String name = prop.substring(LOCATION.length());
                envBuilder.nameLocation(name, ContentPath.fromString(props.getProperty(prop)));
            } else {
                throw ProvisionErrors.unknownEnvironmentProperty(prop);
            }
        }

        if(defPolicy != null) {
            envBuilder.setDefaultUnitUpdatePolicy(defPolicy.build());
        }
    }

    public static void loadUnitEnv(ProvisionEnvironmentBuilder envBuilder, File f) throws ProvisionException {
        try {
            loadUnitEnv(envBuilder, new FileReader(f));
        } catch (FileNotFoundException e) {
            throw ProvisionErrors.failedToLoadEnvironmentRecord(e);
        }
    }

    public static void loadUnitEnv(ProvisionEnvironmentBuilder envBuilder, Reader reader) throws ProvisionException {
        final Properties props = new Properties();
        try {
            props.load(reader);
        } catch (IOException e) {
            throw ProvisionErrors.failedToLoadEnvironmentRecord(e);
        }

        String unitName = null;
        UnitUpdatePolicy.Builder policyBuilder = null;
        for(String prop : props.stringPropertyNames()) {
            int i = prop.indexOf('.');
            if(i <= 0) {
                throw ProvisionErrors.unknownEnvironmentProperty(prop);
            }
            if(unitName == null) {
                unitName = prop.substring(0, i);
                envBuilder.addUnit(unitName, ProvisionUnitInfo.UNDEFINED_INFO.getVersion());
            }

            if (prop.startsWith(UNIT_HOME, i)) {
                envBuilder.setUnitHome(unitName, ContentPath.fromString(props.getProperty(prop)));
            } else if (prop.startsWith(POLICY, i)) {
                if(policyBuilder == null) {
                    policyBuilder = UnitUpdatePolicy.newBuilder();
                }
                i += POLICY.length();
                if(prop.startsWith(UNIT_POLICY, i)) {
                    policyBuilder.setUnitPolicy(UpdatePolicy.valueOf(props.getProperty(prop)));
                } else if(prop.startsWith(CONTENT_POLICY, i)) {
                    policyBuilder.setDefaultContentPolicy(UpdatePolicy.valueOf(props.getProperty(prop)));
                } else {
                    final String path = prop.substring(i + 1);
                    policyBuilder.setPolicy(path, UpdatePolicy.valueOf(props.getProperty(prop)));
                }
            } else if(prop.startsWith(VERSION, i)) {
                envBuilder.addUnit(unitName, props.getProperty(prop));
            } else if(prop.startsWith(PATCHES, i)) {
                envBuilder.addUnitPatches(unitName, Arrays.asList(props.getProperty(prop).split(",")));
            } else {
                throw ProvisionErrors.unknownEnvironmentProperty(prop);
            }
        }

        if(policyBuilder != null) {
            envBuilder.setUnitUpdatePolicy(unitName, policyBuilder.build());
        }
    }

    static ContentWriter createWriter(ProvisionEnvironment env, File f) throws ProvisionException {
        return ContentWriter.forProperties(toProperties(env, false), f);
    }

    static ContentWriter createWriter(ProvisionUnitEnvironment env, File f) throws ProvisionException {
        final Properties props = new Properties();
        toProperties(env, props);
        return ContentWriter.forProperties(props, f);
    }

    public static Properties toProperties(ProvisionEnvironment env, boolean includeUnits) throws ProvisionException {
        assert env != null : ProvisionErrors.nullArgument("env");

        final Properties props = new Properties();
        props.setProperty(ENV_HOME, env.getEnvironmentHome().getAbsolutePath());

        final UnitUpdatePolicy defaultPolicy = env.getUpdatePolicy();
        props.setProperty(DEFAULT_POLICY + UNIT_POLICY, defaultPolicy.getUnitPolicy().name());
        props.setProperty(DEFAULT_POLICY + CONTENT_POLICY, defaultPolicy.getDefaultContentPolicy().name());
        for(String path : defaultPolicy.getPaths()) {
            props.setProperty(DEFAULT_POLICY + DOT + path, defaultPolicy.getContentPolicy(path).name());
        }
        for(String name : env.getLocationNames()) {
            props.setProperty(LOCATION + name, env.getNamedLocation(name).toString());
        }
        if (includeUnits) {
            for (String unitName : env.getUnitNames()) {
                toProperties(env.getUnitEnvironment(unitName), props);
            }
        }
        return props;
    }

    public static Properties toProperties(ProvisionUnitEnvironment unitEnv) {
        final Properties props = new Properties();
        toProperties(unitEnv, props);
        return props;
    }

    private static void toProperties(ProvisionUnitEnvironment unitEnv, Properties props) {
        final ProvisionUnitInfo unitInfo = unitEnv.getUnitInfo();
        props.setProperty(unitInfo.getName() + VERSION, unitInfo.getVersion());

        final List<String> patches = unitInfo.getPatches();
        switch(patches.size()) {
            case 0:
                break;
            case 1:
                props.setProperty(unitInfo.getName() + PATCHES, patches.get(0));
                break;
            default:
                final StringBuilder buf = new StringBuilder();
                buf.append(patches.get(0));
                for(int i = 1; i < patches.size(); ++i) {
                    buf.append(',').append(patches.get(i));
                }
                props.setProperty(unitInfo.getName() + PATCHES, buf.toString());
        }

        final ContentPath unitHome = unitEnv.getHomePath();
        if(unitHome != null) {
            props.setProperty(unitInfo.getName() + UNIT_HOME, unitHome.toString());
        }
        final UnitUpdatePolicy updatePolicy = unitEnv.getUpdatePolicy();
        if(updatePolicy != null) {
            props.setProperty(unitInfo.getName() + POLICY + UNIT_POLICY, updatePolicy.getUnitPolicy().name());
            props.setProperty(unitInfo.getName() + POLICY + CONTENT_POLICY, updatePolicy.getDefaultContentPolicy().name());
            for(String path : updatePolicy.getPaths()) {
                final UpdatePolicy pathPolicy = updatePolicy.getContentPolicy(path);
                if(pathPolicy != null) {
                    props.setProperty(unitInfo.getName() + POLICY + DOT + path, pathPolicy.name());
                }
            }
        }
    }
}
