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

import javax.xml.namespace.QName;
import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;

import org.jboss.staxmapper.XMLExtendedStreamReader;

/**
 *
 * @author Alexey Loubyansky
 */
class ParseUtils {

    private ParseUtils() {
    }

    /**
     * Get an exception reporting an unexpected XML element.
     * @param reader the stream reader
     * @return the exception
     */
    public static XMLStreamException unexpectedElement(final XMLExtendedStreamReader reader) {
        return new XMLStreamException("Unexpected element " + reader.getName(), reader.getLocation());
    }

    /**
     * Creates an exception indicating an unexpected attribute, represented by the {@code name} parameter, was
     * encountered.
     *
     * @param name     the unexpected attribute name.
     * @param location the location of the error.
     *
     * @return a {@link XMLStreamException} for the error.
     */
    public static XMLStreamException unexpectedAttribute(QName name, final Location location) {
        return new XMLStreamException("Unexpected attribute " + name, location);
    }

    /**
     * Get an exception reporting a missing, required XML attribute.
     * @param reader the stream reader
     * @param required a set of enums whose toString method returns the
     *        attribute name
     * @return the exception
     */
    public static XMLStreamException missingRequiredAttributes(final XMLExtendedStreamReader reader, final String... required) {
        final StringBuilder b = new StringBuilder("Missing required attribute(s): ");
        for (int i = 0; i < required.length; i++) {
            final String o = required[i];
            b.append(o);
            if (required.length > i + 1) {
                b.append(", ");
            }
        }
        return new XMLStreamException(b.toString(), reader.getLocation());
    }
}
