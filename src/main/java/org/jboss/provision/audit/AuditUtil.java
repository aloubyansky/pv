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
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.jboss.provision.ProvisionEnvironment;
import org.jboss.provision.ProvisionEnvironment.Builder;
import org.jboss.provision.ProvisionErrors;
import org.jboss.provision.ProvisionException;
import org.jboss.provision.UnitUpdatePolicy;
import org.jboss.provision.info.ContentPath;
import org.jboss.provision.tool.instruction.ContentItemInstruction;
import org.jboss.provision.tool.instruction.UpdatePolicy;
import org.jboss.provision.util.HashUtils;
import org.jboss.provision.util.IoUtils;

/**
 *
 * @author Alexey Loubyansky
 */
class AuditUtil {

    private static final String CONTENT_POLICY = ".content";
    private static final String DOT = ".";
    private static final String DEFAULT_POLICY = "default-policy";
    private static final String FALSE = "false";
    private static final String HASH = "hash";
    private static final String INSTALLATION_HOME = "installation-home";
    private static final String LOCATION = "location";
    private static final String PKG_FILE = "package-file";
    private static final String POLICY = "policy.";
    private static final String RELATIVE_PATH = "relative-path";
    private static final String REPLACED_HASH = "replaced-hash";
    private static final String REQUIRED = "required";
    private static final String TRUE = "true";
    private static final String UNIT_HOME = "home.";
    private static final String UNIT_POLICY = ".unit";

    static void record(ContentItemInstruction instruction, File f) throws ProvisionException {

        assert instruction != null : ProvisionErrors.nullArgument("instruction");
        assert f != null : ProvisionErrors.nullArgument("file");

        if(f.exists()) {
            throw ProvisionErrors.pathAlreadyExists(f);
        }

        final Properties props = new Properties();
        props.setProperty(REQUIRED, instruction.isRequired() ? TRUE : FALSE);
        if(instruction.getPath().getNamedLocation() != null) {
            props.setProperty(LOCATION, instruction.getPath().getNamedLocation());
        }
        props.setProperty(RELATIVE_PATH, instruction.getPath().getRelativePath());
        if(instruction.getContentHash() != null) {
            props.setProperty(HASH, HashUtils.bytesToHexString(instruction.getContentHash()));
        }
        if(instruction.getReplacedHash() != null) {
            props.setProperty(REPLACED_HASH, HashUtils.bytesToHexString(instruction.getReplacedHash()));
        }

        try {
            writeProperties(f, props);
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
            props = loadProperties(f);
        } catch (IOException e) {
            throw ProvisionErrors.failedToLoadInstructionAuditRecord(e);
        }

        final String location = props.getProperty(LOCATION);
        final String relativePath = props.getProperty(RELATIVE_PATH);
        if(relativePath == null) {
            throw ProvisionErrors.failedToLoadInstructionAuditRecord(ProvisionErrors.relativePathMissing());
        }
        final ContentPath path = ContentPath.BUILDER.build(location, relativePath);

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

    static void record(ProvisionEnvironment env, File f) throws ProvisionException {

        assert env != null : ProvisionErrors.nullArgument("env");
        assert f != null : ProvisionErrors.nullArgument("file");

        if(f.exists()) {
            throw ProvisionErrors.pathAlreadyExists(f);
        }

        final Properties props = new Properties();
        props.setProperty(PKG_FILE, env.getPackageFile().getAbsolutePath());
        props.setProperty(INSTALLATION_HOME, env.getInstallationHome().getAbsolutePath());

        final UnitUpdatePolicy defaultPolicy = env.getDefaultUnitUpdatePolicy();
        props.setProperty(DEFAULT_POLICY + UNIT_POLICY, defaultPolicy.getUnitPolicy().name());
        props.setProperty(DEFAULT_POLICY + CONTENT_POLICY, defaultPolicy.getDefaultContentPolicy().name());
        for(String path : defaultPolicy.getPaths()) {
            props.setProperty(DEFAULT_POLICY + DOT + path, defaultPolicy.getContentPolicy(path).name());
        }

        for(String unitName : env.getUnitNames()) {
            final File unitHome = env.getUnitHome(unitName);
            if(unitHome != null) {
                props.setProperty(UNIT_HOME + unitName, unitHome.getAbsolutePath());
            }
            final UnitUpdatePolicy updatePolicy = env.getUnitUpdatePolicy(unitName);
            if(updatePolicy != null) {
                props.setProperty(POLICY + unitName + UNIT_POLICY, updatePolicy.getUnitPolicy().name());
                props.setProperty(POLICY + unitName + CONTENT_POLICY, updatePolicy.getDefaultContentPolicy().name());
                for(String path : updatePolicy.getPaths()) {
                    final UpdatePolicy pathPolicy = updatePolicy.getContentPolicy(path);
                    if(pathPolicy != null) {
                        props.setProperty(POLICY + unitName + DOT + path, pathPolicy.name());
                    }
                }
            }
        }

        try {
            writeProperties(f, props);
        } catch (IOException e) {
            throw ProvisionErrors.failedToAuditEnvironment(e);
        }
    }

    static ProvisionEnvironment loadEnv(File f) throws ProvisionException {

        assert f != null : ProvisionErrors.nullArgument("file");

        if(!f.exists()) {
            throw ProvisionErrors.failedToLoadEnvironmentAuditRecord(ProvisionErrors.pathDoesNotExist(f));
        }

        Properties props;
        try {
            props = loadProperties(f);
        } catch (IOException e) {
            throw ProvisionErrors.failedToLoadEnvironmentAuditRecord(e);
        }

        final Builder envBuilder = ProvisionEnvironment.Builder.create();
        UnitUpdatePolicy.Builder defPolicy = null;
        Map<String, UnitUpdatePolicy.Builder> unitPolicies = null;
        for(String prop : props.stringPropertyNames()) {
            if(prop.equals(PKG_FILE)) {
                envBuilder.setPackageFile(new File(props.getProperty(prop)));
            } else if(prop.equals(INSTALLATION_HOME)) {
                envBuilder.setInstallationHome(new File(props.getProperty(prop)));
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
            } else if(prop.startsWith(UNIT_HOME)) {
                final String unitName = prop.substring(UNIT_HOME.length());
                envBuilder.setUnitHome(unitName, new File(props.getProperty(prop)));
            } else if(prop.startsWith(POLICY)) {
                if(unitPolicies == null) {
                    unitPolicies = new HashMap<String, UnitUpdatePolicy.Builder>();
                }
                int i = prop.indexOf(UNIT_POLICY);
                if(i > 0) {
                    final String unitName = prop.substring(POLICY.length(), i);
                    getPolicyBuilder(unitPolicies, unitName).setUnitPolicy(UpdatePolicy.valueOf(props.getProperty(prop)));
                } else {
                    i = prop.indexOf(CONTENT_POLICY);
                    if(i > 0) {
                        final String unitName = prop.substring(POLICY.length(), i);
                        getPolicyBuilder(unitPolicies, unitName).setDefaultContentPolicy(UpdatePolicy.valueOf(props.getProperty(prop)));
                    } else {
                        i = prop.indexOf('.', POLICY.length() + 1);
                        if(i < 0) {
                            throw ProvisionErrors.unknownEnvironmentProperty(prop);
                        }
                        final String unitName = prop.substring(POLICY.length(), i);
                        final String path = prop.substring(i + 1);
                        getPolicyBuilder(unitPolicies, unitName).setPolicy(path, UpdatePolicy.valueOf(props.getProperty(prop)));
                    }
                }
            } else {
                throw ProvisionErrors.unknownEnvironmentProperty(prop);
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

    private static UnitUpdatePolicy.Builder getPolicyBuilder(Map<String, UnitUpdatePolicy.Builder> unitPolicies,
            final String unitName) {
        UnitUpdatePolicy.Builder policyBuilder = unitPolicies.get(unitName);
        if(policyBuilder == null) {
            policyBuilder = UnitUpdatePolicy.newBuilder();
            unitPolicies.put(unitName, policyBuilder);
        }
        return policyBuilder;
    }

    private static void writeProperties(File f, final Properties props) throws IOException {
        FileWriter writer = null;
        try {
            writer = new FileWriter(f);
            props.store(writer, null);
        } finally {
            IoUtils.safeClose(writer);
        }
    }

    private static Properties loadProperties(File f) throws IOException {
        final Properties props = new Properties();
        FileReader reader = null;
        try {
            reader = new FileReader(f);
            props.load(reader);
        } finally {
            IoUtils.safeClose(reader);
        }
        return props;
    }
}
