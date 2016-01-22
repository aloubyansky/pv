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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.jboss.provision.history.ProvisionEnvironmentHistory;
import org.jboss.provision.info.ContentPath;
import org.jboss.provision.info.ProvisionUnitInfo;

/**
 *
 * @author Alexey Loubyansky
 */
public class ProvisionEnvironmentBuilder {

    File envHome;
    Map<String, ContentPath> namedLocations = Collections.emptyMap();
    Map<String, ContentPath> unitHomes = Collections.emptyMap();
    UnitUpdatePolicy defaultUnitUpdatePolicy = UnitUpdatePolicy.CONDITIONED;
    Map<String, UnitUpdatePolicy> unitUpdatePolicies = Collections.emptyMap();
    Map<String, ProvisionUnitInfo> unitInfos = Collections.emptyMap();

    ProvisionEnvironmentBuilder() {
    }

    public ProvisionEnvironmentBuilder setEnvironmentHome(File envHome) {
        this.envHome = envHome;
        return this;
    }

    public ProvisionEnvironmentBuilder nameLocation(String name, String relativePath) {
        return nameLocation(name, ContentPath.forPath(relativePath));
    }

    public ProvisionEnvironmentBuilder nameLocation(String name, ContentPath relativePath) {
        switch(namedLocations.size()) {
            case 0:
                namedLocations = Collections.singletonMap(name, relativePath);
                break;
            case 1:
                namedLocations = new HashMap<String, ContentPath>(namedLocations);
            default:
                namedLocations.put(name, relativePath);
        }
        return this;
    }

    public ProvisionEnvironmentBuilder addUnit(final String name, final String version) {
        assert name != null : ProvisionErrors.nullArgument("name");
        assert version != null : ProvisionErrors.nullArgument("version");
        return addUnit(ProvisionUnitInfo.createInfo(name, version));
    }

    public ProvisionEnvironmentBuilder addUnit(ProvisionUnitInfo unitInfo) {
        switch(unitInfos.size()) {
            case 0:
                unitInfos = Collections.singletonMap(unitInfo.getName(), unitInfo);
                break;
            case 1:
                unitInfos = new HashMap<String, ProvisionUnitInfo>(unitInfos);
            default:
                unitInfos.put(unitInfo.getName(), unitInfo);
        }
        return this;
    }

    public ProvisionEnvironmentBuilder copyUnit(ProvisionUnitEnvironment unitEnv) throws ProvisionException {
        addUnit(unitEnv.getUnitInfo());
        final String unitName = unitEnv.getUnitInfo().getName();
        if(unitEnv.getHomePath() != null) {
            setUnitHome(unitName, unitEnv.getHomePath());
        }
        if(unitEnv.getUpdatePolicy() != null) {
            setUnitUpdatePolicy(unitName, unitEnv.getUpdatePolicy());
        }
        return this;
    }

    public ProvisionEnvironmentBuilder setUnitHome(String unitName, ContentPath unitHome) throws ProvisionException {
        if(!unitInfos.containsKey(unitName)) {
            throw ProvisionErrors.unknownUnit(unitName);
        }
        switch(unitHomes.size()) {
            case 0:
                unitHomes = Collections.singletonMap(unitName, unitHome);
                break;
            case 1:
                unitHomes = new HashMap<String, ContentPath>(unitHomes);
            default:
                unitHomes.put(unitName, unitHome);
        }
        return this;
    }

    public ProvisionEnvironmentBuilder setDefaultUnitUpdatePolicy(UnitUpdatePolicy unitPolicy) {
        this.defaultUnitUpdatePolicy = unitPolicy;
        return this;
    }

    public ProvisionEnvironmentBuilder setUnitUpdatePolicy(String unitName, UnitUpdatePolicy updatePolicy) throws ProvisionException {
        if(!unitInfos.containsKey(unitName)) {
            throw ProvisionErrors.unknownUnit(unitName);
        }
        switch(unitUpdatePolicies.size()) {
            case 0:
                unitUpdatePolicies = Collections.singletonMap(unitName, updatePolicy);
                break;
            case 1:
                unitUpdatePolicies = new HashMap<String, UnitUpdatePolicy>(unitUpdatePolicies);
            default:
                unitUpdatePolicies.put(unitName, updatePolicy);
        }
        return this;
    }

    public ProvisionEnvironment build() throws ProvisionException {
        if(envHome == null) {
            throw new ProvisionException(ProvisionErrors.nullArgument("envHome"));
        }
        if(ProvisionEnvironmentHistory.storesHistory(envHome)) {
            throw ProvisionErrors.environmentAlreadyExists(envHome);
        }
        return new ProvisionEnvironment(this);
    }
}