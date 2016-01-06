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
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.jboss.provision.ProvisionEnvironment;
import org.jboss.provision.ProvisionEnvironment.Builder;
import org.jboss.provision.ProvisionErrors;
import org.jboss.provision.ProvisionException;
import org.jboss.provision.ProvisionUnitEnvironment;
import org.jboss.provision.UnitUpdatePolicy;
import org.jboss.provision.info.ContentPath;
import org.jboss.provision.info.ProvisionUnitInfo;
import org.jboss.provision.io.FileTask;
import org.jboss.provision.io.FileUtils;
import org.jboss.provision.tool.instruction.ContentItemInstruction;
import org.jboss.provision.tool.instruction.UpdatePolicy;
import org.jboss.provision.util.HashUtils;

/**
 *
 * @author Alexey Loubyansky
 */
public class AuditUtil {

    private static final String CONTENT_POLICY = ".content";
    private static final String DOT = ".";
    private static final String DEFAULT_POLICY = "default-policy";
    private static final String ENV_HOME = "env-home";
    private static final String FALSE = "false";
    private static final String HASH = "hash";
    private static final String LOCATION = "location.";
    private static final String POLICY = ".policy";
    private static final String RELATIVE_PATH = "relative-path";
    private static final String REPLACED_HASH = "replaced-hash";
    private static final String REQUIRED = "required";
    private static final String TRUE = "true";
    private static final String UNIT_HOME = ".home";
    private static final String UNIT_POLICY = ".unit";
    private static final String VERSION = ".version";

    static void record(ContentItemInstruction instruction, File f) throws ProvisionException {

        assert instruction != null : ProvisionErrors.nullArgument("instruction");
        assert f != null : ProvisionErrors.nullArgument("file");

        if(f.exists()) {
            throw ProvisionErrors.pathAlreadyExists(f);
        }

        final Properties props = new Properties();
        props.setProperty(REQUIRED, instruction.isRequired() ? TRUE : FALSE);
        if(instruction.getPath().getLocationName() != null) {
            props.setProperty(LOCATION, instruction.getPath().getLocationName());
        }
        props.setProperty(RELATIVE_PATH, instruction.getPath().getRelativePath());
        if(instruction.getContentHash() != null) {
            props.setProperty(HASH, HashUtils.bytesToHexString(instruction.getContentHash()));
        }
        if(instruction.getReplacedHash() != null) {
            props.setProperty(REPLACED_HASH, HashUtils.bytesToHexString(instruction.getReplacedHash()));
        }

        try {
            FileUtils.writeProperties(f, props);
        } catch (IOException e) {
            throw ProvisionErrors.failedToAuditInstruction(instruction, e);
        }
    }

    static ContentItemInstruction load(File f) throws ProvisionException {

        assert f != null : ProvisionErrors.nullArgument("file");

        if(!f.exists()) {
            throw ProvisionErrors.failedToLoadInstructionAuditRecord(ProvisionErrors.pathDoesNotExist(f));
        }

        Properties props;
        try {
            props = FileUtils.loadProperties(f);
        } catch (IOException e) {
            throw ProvisionErrors.failedToLoadInstructionAuditRecord(e);
        }

        final String location = props.getProperty(LOCATION);
        final String relativePath = props.getProperty(RELATIVE_PATH);
        if(relativePath == null) {
            throw ProvisionErrors.failedToLoadInstructionAuditRecord(ProvisionErrors.relativePathMissing());
        }
        final ContentPath path = ContentPath.create(location, relativePath);

        final byte[] hash = props.containsKey(HASH) ? HashUtils.hexStringToByteArray(props.getProperty(HASH)) : null;
        final byte[] replacedHash = props.containsKey(REPLACED_HASH) ? HashUtils.hexStringToByteArray(props.getProperty(REPLACED_HASH)) : null;

        ContentItemInstruction.Builder builder;
        if(hash == null) {
            if(replacedHash == null) {
                throw ProvisionErrors.failedToLoadInstructionAuditRecord(ProvisionErrors.contentHashMissing());
            }
            builder = ContentItemInstruction.Builder.removeContent(path, replacedHash);
        } else if(replacedHash == null) {
            builder = ContentItemInstruction.Builder.addContent(path, replacedHash);
        } else {
            builder = ContentItemInstruction.Builder.replaceContent(path, hash, replacedHash);
        }

        return builder.setRequired(TRUE.equals(props.getProperty(REQUIRED))).build();
    }

    public static void record(ProvisionEnvironment env, File f) throws ProvisionException {
        try {
            FileUtils.writeProperties(f, toProperties(env, f));
        } catch (IOException e) {
            throw ProvisionErrors.failedToAuditEnvironment(e);
        }
    }

    public static ProvisionEnvironment loadEnv(File f) throws ProvisionException {

        assert f != null : ProvisionErrors.nullArgument("file");

        if(!f.exists()) {
            throw ProvisionErrors.failedToLoadEnvironmentAuditRecord(ProvisionErrors.pathDoesNotExist(f));
        }

        Properties props;
        try {
            props = FileUtils.loadProperties(f);
        } catch (IOException e) {
            throw ProvisionErrors.failedToLoadEnvironmentAuditRecord(e);
        }

        final Builder envBuilder = ProvisionEnvironment.newBuilder();
        UnitUpdatePolicy.Builder defPolicy = null;
        Map<String, UnitUpdatePolicy.Builder> unitPolicies = null;
        Set<String> addedUnits = Collections.emptySet();
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
                int i = prop.indexOf('.');
                if(i <= 0) {
                    throw ProvisionErrors.unknownEnvironmentProperty(prop);
                }
                final String unitName = prop.substring(0, i);
                if(!addedUnits.contains(unitName)) {
                    envBuilder.addUnit(unitName, ProvisionUnitInfo.UNDEFINED_INFO.getVersion());
                    switch(addedUnits.size()) {
                        case 0:
                            addedUnits = Collections.singleton(unitName);
                            break;
                        case 1:
                            addedUnits = new HashSet<String>(addedUnits);
                        default:
                            addedUnits.add(unitName);
                    }
                }

                if (prop.startsWith(UNIT_HOME, i)) {
                    envBuilder.setUnitHome(unitName, ContentPath.fromString(props.getProperty(prop)));
                } else if (prop.startsWith(POLICY, i)) {
                    if (unitPolicies == null) {
                        unitPolicies = new HashMap<String, UnitUpdatePolicy.Builder>();
                    }
                    i += POLICY.length();
                    if(prop.startsWith(UNIT_POLICY, i)) {
                        getPolicyBuilder(unitPolicies, unitName).setUnitPolicy(UpdatePolicy.valueOf(props.getProperty(prop)));
                    } else if(prop.startsWith(CONTENT_POLICY, i)) {
                            getPolicyBuilder(unitPolicies, unitName).setDefaultContentPolicy(
                                    UpdatePolicy.valueOf(props.getProperty(prop)));
                    } else {
                        final String path = prop.substring(i + 1);
                        getPolicyBuilder(unitPolicies, unitName).setPolicy(path, UpdatePolicy.valueOf(props.getProperty(prop)));
                    }
                } else if(prop.startsWith(VERSION, i)) {
                    envBuilder.addUnit(unitName, props.getProperty(prop));
                } else {
                    throw ProvisionErrors.unknownEnvironmentProperty(prop);
                }
            }
        }

        if(defPolicy != null) {
            envBuilder.setDefaultUnitUpdatePolicy(defPolicy.build());
        }
        if(unitPolicies != null) {
            for(String unitName : unitPolicies.keySet()) {
                envBuilder.setUnitUpdatePolicy(unitName, unitPolicies.get(unitName).build());
            }
        }
        return envBuilder.build();
    }

    public static FileTask createRecordTask(ProvisionEnvironment env, File f) throws ProvisionException {
        final Properties props = toProperties(env, f);
        return FileTask.write(f, props);
    }

    protected static Properties toProperties(ProvisionEnvironment env, File f) throws ProvisionException {
        assert env != null : ProvisionErrors.nullArgument("env");
        assert f != null : ProvisionErrors.nullArgument("file");

        if(f.exists()) {
            throw ProvisionErrors.pathAlreadyExists(f);
        }

        final Properties props = new Properties();
        props.setProperty(ENV_HOME, env.getEnvironmentHome().getAbsolutePath());

        final UnitUpdatePolicy defaultPolicy = env.getUpdatePolicy();
        props.setProperty(DEFAULT_POLICY + UNIT_POLICY, defaultPolicy.getUnitPolicy().name());
        props.setProperty(DEFAULT_POLICY + CONTENT_POLICY, defaultPolicy.getDefaultContentPolicy().name());
        for(String path : defaultPolicy.getPaths()) {
            props.setProperty(DEFAULT_POLICY + DOT + path, defaultPolicy.getContentPolicy(path).name());
        }

        for(String unitName : env.getUnitNames()) {
            final ProvisionUnitEnvironment unitEnv = env.getUnitEnvironment(unitName);
            props.setProperty(unitName + VERSION, unitEnv.getUnitInfo().getVersion());
            final ContentPath unitHome = unitEnv.getHomePath();
            if(unitHome != null) {
                props.setProperty(unitName + UNIT_HOME, unitHome.toString());
            }
            final UnitUpdatePolicy updatePolicy = unitEnv.getUpdatePolicy();
            if(updatePolicy != null) {
                props.setProperty(unitName + POLICY + UNIT_POLICY, updatePolicy.getUnitPolicy().name());
                props.setProperty(unitName + POLICY + CONTENT_POLICY, updatePolicy.getDefaultContentPolicy().name());
                for(String path : updatePolicy.getPaths()) {
                    final UpdatePolicy pathPolicy = updatePolicy.getContentPolicy(path);
                    if(pathPolicy != null) {
                        props.setProperty(unitName + POLICY + DOT + path, pathPolicy.name());
                    }
                }
            }
        }

        for(String name : env.getLocationNames()) {
            props.setProperty(LOCATION + name, env.getNamedLocation(name).toString());
        }
        return props;
    }

    private static UnitUpdatePolicy.Builder getPolicyBuilder(Map<String, UnitUpdatePolicy.Builder> unitPolicies,
            final String unitName) {
        UnitUpdatePolicy.Builder policyBuilder = unitPolicies.get(unitName);
        if(policyBuilder == null) {
            policyBuilder = UnitUpdatePolicy.newBuilder();
            unitPolicies.put(unitName, policyBuilder);
        }
        return policyBuilder;
    }
}
