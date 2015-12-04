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

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.jboss.provision.tool.instruction.ProvisionEnvironmentInstruction;
import org.jboss.staxmapper.XMLElementWriter;
import org.jboss.staxmapper.XMLMapper;

/**
 *
 * @author Alexey Loubyansky
 */
public class ProvisionXml {

    public static final String PROVISION_XML = "provision.xml";

    private static final XMLMapper MAPPER = XMLMapper.Factory.create();
    private static final XMLInputFactory INPUT_FACTORY = XMLInputFactory.newInstance();
    private static final XMLOutputFactory OUTPUT_FACTORY = XMLOutputFactory.newFactory();

    static {
        MAPPER.registerRootElement(new QName(Namespace.PROVISION_1_0.getNamespace(), ProvisionXml_1_0.Element.PROVISION.name), ProvisionXml_1_0.INSTANCE);
    }

    public enum Namespace {

        PROVISION_1_0("urn:jboss:provision:1.0"),
        UNKNOWN(null);

        private final String namespace;
        Namespace(String namespace) {
            this.namespace = namespace;
        }

        public String getNamespace() {
            return namespace;
        }

        static Map<String, Namespace> elements = new HashMap<String, Namespace>();
        static {
            for(Namespace element : Namespace.values()) {
                if(element != UNKNOWN) {
                    elements.put(element.namespace, element);
                }
            }
        }

        static Namespace forUri(String name) {
            final Namespace element = elements.get(name);
            return element == null ? UNKNOWN : element;
        }
    }

    private ProvisionXml() {
    }

    public static void marshal(final Writer writer, final ProvisionEnvironmentInstruction instructions) throws XMLStreamException {
        final XMLOutputFactory outputFactory = OUTPUT_FACTORY;
        final XMLStreamWriter streamWriter = outputFactory.createXMLStreamWriter(writer);
        MAPPER.deparseDocument((XMLElementWriter<?>) ProvisionXml_1_0.INSTANCE, instructions, streamWriter);
        streamWriter.close();
    }

    public static void marshal(final OutputStream os, final ProvisionEnvironmentInstruction instructions) throws XMLStreamException {
        final XMLOutputFactory outputFactory = OUTPUT_FACTORY;
        final XMLStreamWriter streamWriter = outputFactory.createXMLStreamWriter(os);
        MAPPER.deparseDocument((XMLElementWriter<?>) ProvisionXml_1_0.INSTANCE, instructions, streamWriter);
        streamWriter.close();
    }

    public static ProvisionEnvironmentInstruction parse(final Reader input) throws XMLStreamException {
        return parse(getXMLInputFactory().createXMLStreamReader(input));
    }

    public static ProvisionEnvironmentInstruction parse(final InputStream input) throws XMLStreamException {
        return parse(getXMLInputFactory().createXMLStreamReader(input));
    }

    protected static ProvisionEnvironmentInstruction parse(final XMLStreamReader reader) throws XMLStreamException {
        try {
            final ParsingResult result = new ParsingResult();
            MAPPER.parseDocument(result, reader);
            return result.getResult();
        } finally {
            reader.close();
        }
    }

    private static XMLInputFactory getXMLInputFactory() throws XMLStreamException {
        final XMLInputFactory inputFactory = INPUT_FACTORY;
        setIfSupported(inputFactory, XMLInputFactory.IS_VALIDATING, Boolean.FALSE);
        setIfSupported(inputFactory, XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        return inputFactory;
    }

    private static void setIfSupported(final XMLInputFactory inputFactory, final String property, final Object value) {
        if (inputFactory.isPropertySupported(property)) {
            inputFactory.setProperty(property, value);
        }
    }

    public static class ParsingResult {

        private ProvisionEnvironmentInstruction result;

        public ProvisionEnvironmentInstruction getResult() {
            return result;
        }

        public void setResult(ProvisionEnvironmentInstruction result) {
            this.result = result;
        }
    }
}
