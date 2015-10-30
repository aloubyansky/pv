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

import java.util.HashSet;
import java.util.Set;

import org.jboss.provision.ProvisionErrors;
import org.jboss.provision.ProvisionException;
import org.jboss.provision.info.ContentItemInfo;
import org.jboss.provision.info.ContentPath;
import org.jboss.provision.info.ProvisionUnitContentInfo;
import org.jboss.provision.tool.instruction.ContentItemInstruction;
import org.jboss.provision.tool.instruction.ProvisionUnitInstruction;

/**
 *
 * @author Alexey Loubyansky
 */
public class ProvisionInstructionBuilder {

    private ProvisionInstructionBuilder() {
    }

    public static ProvisionUnitInstruction install(ProvisionUnitContentInfo unitInfo) {

        final ProvisionUnitInstruction.Builder builder = ProvisionUnitInstruction.Builder.installUnit(unitInfo.getName(), unitInfo.getVersion());
        for(ContentItemInfo content : unitInfo.getContentInfo()) {
            builder.addContentInstruction(ContentItemInstruction.Builder.addContent(content.getPath(), content.getContentHash()).build());
        }
        return builder.build();
    }

    public static ProvisionUnitInstruction uninstall(ProvisionUnitContentInfo unitInfo) {

        final ProvisionUnitInstruction.Builder builder = ProvisionUnitInstruction.Builder.uninstallUnit(unitInfo.getName(), unitInfo.getVersion());
        for(ContentItemInfo content : unitInfo.getContentInfo()) {
            builder.addContentInstruction(ContentItemInstruction.Builder.removeContent(content.getPath(), content.getContentHash()).build());
        }
        return builder.build();
    }

    public static ProvisionUnitInstruction replace(ProvisionUnitContentInfo replacedUnit, ProvisionUnitContentInfo nextUnit) throws ProvisionException {

        if(!replacedUnit.getName().equals(nextUnit.getName())) {
            throw ProvisionErrors.unitNamesDoNotMatch(replacedUnit.getName(), nextUnit.getName());
        }

        final ProvisionUnitInstruction.Builder builder = ProvisionUnitInstruction.Builder.replaceUnit(nextUnit.getName(), replacedUnit.getVersion(), nextUnit.getVersion());

        final Set<ContentPath> commonPaths = new HashSet<ContentPath>();
        for(ContentItemInfo nextItem : nextUnit.getContentInfo()) {
            final ContentItemInfo prevItem = replacedUnit.getContentInfo(nextItem.getPath());

            final ContentItemInstruction itemInstruction;
            if(prevItem == null) {
                itemInstruction = ContentItemInstruction.Builder.addContent(nextItem.getPath(), nextItem.getContentHash()).build();
            } else {
                commonPaths.add(nextItem.getPath());
                itemInstruction = ContentItemInstruction.Builder.replaceContent(nextItem.getPath(), nextItem.getContentHash(), prevItem.getContentHash()).build();
            }
            builder.addContentInstruction(itemInstruction);
        }
        for(ContentItemInfo prevItem : replacedUnit.getContentInfo()) {
            if(!commonPaths.contains(prevItem.getPath())) {
                builder.addContentInstruction(ContentItemInstruction.Builder.removeContent(prevItem.getPath(), prevItem.getContentHash()).build());
            }
        }
        return builder.build();
    }
}
