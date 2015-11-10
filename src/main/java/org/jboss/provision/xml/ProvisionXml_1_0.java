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

package org.jboss.provision.xml;

import java.util.HashMap;
import java.util.Map;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

import org.jboss.provision.info.ContentPath;
import org.jboss.provision.tool.instruction.ContentItemInstruction;
import org.jboss.provision.tool.instruction.ProvisionPackageInstruction;
import org.jboss.provision.tool.instruction.ProvisionUnitInstruction;
import org.jboss.provision.tool.instruction.ProvisionUnitInstruction.Builder;
import org.jboss.provision.util.HashUtils;
import org.jboss.provision.xml.ProvisionXml.ParsingResult;
import org.jboss.staxmapper.XMLElementReader;
import org.jboss.staxmapper.XMLElementWriter;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 *
 * @author Alexey Loubyansky
 */
class ProvisionXml_1_0 implements XMLStreamConstants, XMLElementReader<ParsingResult>, XMLElementWriter<ProvisionPackageInstruction> {

    static final ProvisionXml_1_0 INSTANCE = new ProvisionXml_1_0();

    enum Element {

        ADD("add"),
        INSTALL("install"),
        PATCH("patch"),
        PROVISION("provision"),
        REMOVE("remove"),
        REPLACE("replace"),
        UNINSTALL("uninstall"),
        UPDATE("update"),

        // default unknown element
        UNKNOWN(null);

        public final String name;
        Element(String name) {
            this.name = name;
        }

        static Map<String, Element> elements = new HashMap<String, Element>();
        static {
            for(Element element : Element.values()) {
                if(element != UNKNOWN) {
                    elements.put(element.name, element);
                }
            }
        }

        static Element forName(String name) {
            final Element element = elements.get(name);
            return element == null ? UNKNOWN : element;
        }
    }

    enum Attribute {

        FROM("from"),
        HASH("hash"),
        LOCATION("location"),
        NAME("name"),
        PATCH_ID("patch-id"),
        PATH("path"),
        REPLACED_HASH("replaced-hash"),
        TO("to"),
        VERSION("version"),

        // default unknown attribute
        UNKNOWN(null);

        private final String name;
        Attribute(String name) {
            this.name = name;
        }

        static Map<String, Attribute> attributes = new HashMap<String, Attribute>();
        static {
            for(Attribute attribute : Attribute.values()) {
                if(attribute != UNKNOWN) {
                    attributes.put(attribute.name, attribute);
                }
            }
        }

        static Attribute forName(String name) {
            final Attribute attribute = attributes.get(name);
            return attribute == null ? UNKNOWN : attribute;
        }
    }

    private ProvisionXml_1_0() {
    }

    @Override
    public void writeContent(XMLExtendedStreamWriter writer, ProvisionPackageInstruction instructions) throws XMLStreamException {

        writer.writeStartDocument();
        writer.writeStartElement(Element.PROVISION.name);
        writer.writeDefaultNamespace(ProvisionXml.Namespace.PROVISION_1_0.getNamespace());

        for(String unitName : instructions.getUnitNames()) {
            writeUnit(writer, instructions.getUnitInstruction(unitName));
        }

        writer.writeEndElement();
        writer.writeEndDocument();
    }

    @Override
    public void readElement(XMLExtendedStreamReader reader, ParsingResult result) throws XMLStreamException {

        final ProvisionPackageInstruction.Builder builder = ProvisionPackageInstruction.Builder.newPackage();
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            final String localName = reader.getLocalName();
            final Element element = Element.forName(localName);
            final ProvisionUnitInstruction unit;
            switch (element) {
                case INSTALL:
                    unit = readInstall(reader, result);
                    break;
                case UNINSTALL:
                    unit = readUninstall(reader, result);
                    break;
                case UPDATE:
                    unit = readUpdate(reader, result);
                    break;
                case PATCH:
                    unit = readPatch(reader, result);
                    break;
                default:
                    throw ParseUtils.unexpectedElement(reader);
            }
            builder.add(unit);
        }
        result.setResult(builder.build());
    }

    private ProvisionUnitInstruction readInstall(XMLExtendedStreamReader reader, ParsingResult result) throws XMLStreamException {

        String name = null;
        String version = null;
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            if (Attribute.NAME == attribute) {
                name = value;
            } else if (Attribute.VERSION == attribute) {
                version = value;
            } else {
                throw ParseUtils.unexpectedAttribute(reader.getAttributeName(i), reader.getLocation());
            }
        }
        if(name == null) {
            ParseUtils.missingRequiredAttributes(reader, Attribute.NAME.name);
        }
        if(version == null) {
            ParseUtils.missingRequiredAttributes(reader, Attribute.VERSION.name);
        }

        final Builder builder = ProvisionUnitInstruction.Builder.installUnit(name, version);
        readContentInstructions(reader, builder);
        return builder.build();
    }

    private ProvisionUnitInstruction readUninstall(XMLExtendedStreamReader reader, ParsingResult result) throws XMLStreamException {
        String name = null;
        String version = null;
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            if (Attribute.NAME == attribute) {
                name = value;
            } else if (Attribute.VERSION == attribute) {
                version = value;
            } else {
                throw ParseUtils.unexpectedAttribute(reader.getAttributeName(i), reader.getLocation());
            }
        }
        if(name == null) {
            ParseUtils.missingRequiredAttributes(reader, Attribute.NAME.name);
        }
        if(version == null) {
            ParseUtils.missingRequiredAttributes(reader, Attribute.VERSION.name);
        }

        final Builder builder = ProvisionUnitInstruction.Builder.uninstallUnit(name, version);
        readContentInstructions(reader, builder);
        return builder.build();
    }

    private ProvisionUnitInstruction readUpdate(XMLExtendedStreamReader reader, ParsingResult result) throws XMLStreamException {
        String name = null;
        String from = null;
        String to = null;
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            if (Attribute.NAME == attribute) {
                name = value;
            } else if (Attribute.FROM == attribute) {
                from = value;
            } else if (Attribute.TO == attribute) {
                to = value;
            } else {
                throw ParseUtils.unexpectedAttribute(reader.getAttributeName(i), reader.getLocation());
            }
        }
        if(name == null) {
            ParseUtils.missingRequiredAttributes(reader, Attribute.NAME.name);
        }
        if(from == null) {
            ParseUtils.missingRequiredAttributes(reader, Attribute.FROM.name);
        }
        if(to == null) {
            ParseUtils.missingRequiredAttributes(reader, Attribute.TO.name);
        }

        final Builder builder = ProvisionUnitInstruction.Builder.replaceUnit(name, to, from);
        readContentInstructions(reader, builder);
        return builder.build();
    }

    private ProvisionUnitInstruction readPatch(XMLExtendedStreamReader reader, ParsingResult result) throws XMLStreamException {
        String name = null;
        String version = null;
        String patchId = null;
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            if (Attribute.NAME == attribute) {
                name = value;
            } else if (Attribute.VERSION == attribute) {
                version = value;
            } else if (Attribute.PATCH_ID == attribute) {
                patchId = value;
            } else {
                throw ParseUtils.unexpectedAttribute(reader.getAttributeName(i), reader.getLocation());
            }
        }
        if(name == null) {
            ParseUtils.missingRequiredAttributes(reader, Attribute.NAME.name);
        }
        if(version == null) {
            ParseUtils.missingRequiredAttributes(reader, Attribute.VERSION.name);
        }
        if(patchId == null) {
            ParseUtils.missingRequiredAttributes(reader, Attribute.PATCH_ID.name);
        }

        final Builder builder = ProvisionUnitInstruction.Builder.patchUnit(name, version, patchId);
        readContentInstructions(reader, builder);
        return builder.build();
    }

    private void readContentInstructions(XMLExtendedStreamReader reader, Builder builder) throws XMLStreamException {
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            final String localName = reader.getLocalName();
            final Element element = Element.forName(localName);
            final ContentItemInstruction item;
            switch (element) {
                case ADD:
                    item = readAddInstruction(reader);
                    break;
                case REMOVE:
                    item = readRemoveInstruction(reader);
                    break;
                case REPLACE:
                    item = readReplaceInstruction(reader);
                    break;
                default:
                    throw ParseUtils.unexpectedElement(reader);
            }
            builder.addContentInstruction(item);
            while(reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            }
        }
    }

    private ContentItemInstruction readAddInstruction(XMLExtendedStreamReader reader) throws XMLStreamException {
        String location = null;
        String path = null;
        String hash = null;
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            if (Attribute.LOCATION == attribute) {
                location = value;
            } else if (Attribute.PATH == attribute) {
                path = value;
            } else if (Attribute.HASH == attribute) {
                hash = value;
            } else {
                throw ParseUtils.unexpectedAttribute(reader.getAttributeName(i), reader.getLocation());
            }
        }
        if(path == null) {
            ParseUtils.missingRequiredAttributes(reader, Attribute.PATH.name);
        }
        if(hash == null) {
            ParseUtils.missingRequiredAttributes(reader, Attribute.HASH.name);
        }
        return ContentItemInstruction.Builder.addContent(ContentPath.BUILDER.build(location, path), HashUtils.hexStringToByteArray(hash)).build();
    }

    private ContentItemInstruction readRemoveInstruction(XMLExtendedStreamReader reader) throws XMLStreamException {
        String location = null;
        String path = null;
        String hash = null;
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            if (Attribute.LOCATION == attribute) {
                location = value;
            } else if (Attribute.PATH == attribute) {
                path = value;
            } else if (Attribute.HASH == attribute) {
                hash = value;
            } else {
                throw ParseUtils.unexpectedAttribute(reader.getAttributeName(i), reader.getLocation());
            }
        }
        if(path == null) {
            ParseUtils.missingRequiredAttributes(reader, Attribute.PATH.name);
        }
        if(hash == null) {
            ParseUtils.missingRequiredAttributes(reader, Attribute.HASH.name);
        }
        return ContentItemInstruction.Builder.removeContent(ContentPath.BUILDER.build(location, path), HashUtils.hexStringToByteArray(hash)).build();
    }

    private ContentItemInstruction readReplaceInstruction(XMLExtendedStreamReader reader) throws XMLStreamException {
        String location = null;
        String path = null;
        String hash = null;
        String replacedHash = null;
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            if (Attribute.LOCATION == attribute) {
                location = value;
            } else if (Attribute.PATH == attribute) {
                path = value;
            } else if (Attribute.HASH == attribute) {
                hash = value;
            } else if (Attribute.REPLACED_HASH == attribute) {
                replacedHash = value;
            } else {
                throw ParseUtils.unexpectedAttribute(reader.getAttributeName(i), reader.getLocation());
            }
        }
        if(path == null) {
            ParseUtils.missingRequiredAttributes(reader, Attribute.PATH.name);
        }
        if(hash == null) {
            ParseUtils.missingRequiredAttributes(reader, Attribute.HASH.name);
        }
        if(replacedHash == null) {
            ParseUtils.missingRequiredAttributes(reader, Attribute.REPLACED_HASH.name);
        }
        return ContentItemInstruction.Builder.replaceContent(ContentPath.BUILDER.build(location, path),
                HashUtils.hexStringToByteArray(hash),
                HashUtils.hexStringToByteArray(replacedHash)).build();
    }

    protected void writeUnit(XMLExtendedStreamWriter writer, ProvisionUnitInstruction unit) throws XMLStreamException {

        if(unit.getReplacedVersion() == null) {
            // INSTALL
            writer.writeStartElement(Element.INSTALL.name);
            writer.writeAttribute(Attribute.NAME.name, unit.getName());
            writer.writeAttribute(Attribute.VERSION.name, unit.getVersion());
        } else if(unit.getVersion() == null) {
            // UNINSTALL
            writer.writeStartElement(Element.UNINSTALL.name);
            writer.writeAttribute(Attribute.NAME.name, unit.getName());
            writer.writeAttribute(Attribute.VERSION.name, unit.getReplacedVersion());
        } else if(unit.getReplacedVersion().equals(unit.getVersion())) {
            // PATCH
            writer.writeStartElement(Element.PATCH.name);
            writer.writeAttribute(Attribute.NAME.name, unit.getName());
            writer.writeAttribute(Attribute.VERSION.name, unit.getVersion());
            writer.writeAttribute(Attribute.PATCH_ID.name, unit.getId());
        } else {
            // UPDATE
            writer.writeStartElement(Element.UPDATE.name);
            writer.writeAttribute(Attribute.NAME.name, unit.getName());
            writer.writeAttribute(Attribute.FROM.name, unit.getReplacedVersion());
            writer.writeAttribute(Attribute.TO.name, unit.getVersion());
        }

        for(ContentItemInstruction item : unit.getContentInstructions()) {
            write(writer, item);
        }

        writer.writeEndElement();
    }

    protected void write(XMLExtendedStreamWriter writer, ContentItemInstruction item) throws XMLStreamException {

        byte[] hash = item.getContentHash();
        if(item.getReplacedHash() == null) {
            writer.writeStartElement(Element.ADD.name);
        } else if(item.getContentHash() == null) {
            writer.writeStartElement(Element.REMOVE.name);
            hash = item.getReplacedHash();
        } else {
            writer.writeStartElement(Element.REPLACE.name);
            writer.writeAttribute(Attribute.REPLACED_HASH.name, HashUtils.bytesToHexString(item.getReplacedHash()));
        }

        writer.writeAttribute(Attribute.HASH.name, HashUtils.bytesToHexString(hash));

        final ContentPath path = item.getPath();
        if(path.getNamedLocation() != null) {
            writer.writeAttribute(Attribute.LOCATION.name, path.getNamedLocation());
        }
        writer.writeAttribute(Attribute.PATH.name, path.getRelativePath());
        writer.writeEndElement();
    }
}
