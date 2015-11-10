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

package org.jboss.provision.test.xml;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.StringReader;
import java.io.StringWriter;

import org.jboss.provision.tool.ProvisionInfoReader;
import org.jboss.provision.tool.ProvisionInstructionBuilder;
import org.jboss.provision.tool.instruction.ProvisionPackageInstruction;
import org.jboss.provision.xml.ProvisionXml;
import org.junit.Test;

/**
 *
 * @author Alexey Loubyansky
 */
public class XmlWriterTestCase {

    @Test
    public void testXmlWriter() throws Exception {

        final File root = new File("/home/olubyans/git/pv/");
        final ProvisionPackageInstruction pkg = ProvisionPackageInstruction.Builder.newPackage()
                .add(ProvisionInstructionBuilder.install(ProvisionInfoReader.readContentInfo("test", "x.x.x", root)))
                .add(ProvisionInstructionBuilder.uninstall(ProvisionInfoReader.readContentInfo("test2", "y.y.y", root)))
                .build();

        final StringWriter writer = new StringWriter();
        ProvisionXml.marshal(writer, pkg);
        final String xml = writer.getBuffer().toString();

        final ProvisionPackageInstruction parsedPkg = ProvisionXml.parse(new StringReader(xml));
        assertEquals(pkg, parsedPkg);
        //ProvisionPackageBuilder.build(installUnit, root, new File(root, "package.pvn"));
    }
}
