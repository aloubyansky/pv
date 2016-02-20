/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author Alexey Loubyansky
 */
class PathsOwnership {

    private Map<String, PathOwnership> ownerships = Collections.emptyMap();

    void addExternalOwner(String path) {
        final PathOwnership ownership = ownerships.get(path);
        if(ownership == null) {
            addOwnership(path, new PathOwnership(true));
        } else {
            ownership.setExternalOwner(true);
        }
    }

    void addOwner(String path, String owner) {
        final PathOwnership ownership = ownerships.get(path);
        if(ownership == null) {
            addOwnership(path, new PathOwnership(owner));
        } else {
            ownership.addOwner(owner);
        }
    }

    /**
     * If the owner is actually registered as the owner for the path,
     * the owner will be unregistered. If the owner is not registered as the owner
     * for the path, nothing will happen.
     * In any case, the method will return true if after unregistering the owner,
     * the path is still owned by somebody (including an external owner). If after
     * unregistering the owner the path does not belong to any owner, the method
     * will return false.
     *
     * @param path  path
     * @param owner  owner
     * @return  whether after the removing the owner for the path, the path is still owned
     *          by somebody (including an external owner)
     */
    boolean removeOwner(String path, String owner) {
        final PathOwnership ownership = ownerships.get(path);
        if(ownership == null) {
            return false;
        }
        if(ownership.removeOwner(owner) && !ownership.isOwned()) {
            if(ownerships.size() == 1) {
                ownerships = Collections.emptyMap();
            } else {
                ownerships.remove(path);
            }
            return false;
        }
        return true;
    }

    boolean isOnlyOwner(String path, String owner) {
        final PathOwnership ownership = ownerships.get(path);
        if(ownership == null) {
            return false;
        }
        return ownership.isOnlyOwner(owner);
    }

    private void addOwnership(String path, PathOwnership ownership) {
        switch(ownerships.size()) {
            case 0:
                ownerships = Collections.singletonMap(path, ownership);
                break;
            case 1:
                ownerships = new HashMap<String, PathOwnership>(ownerships);
            default:
                ownerships.put(path, ownership);
        }
    }

    @Override
    public String toString() {
        return ownerships.toString();
    }

    static class PathOwnership {

        private boolean externalOwner;
        private Set<String> owners;

        PathOwnership(boolean externalOwner) {
            this.externalOwner = externalOwner;
        }

        PathOwnership(String owner) {
            owners = Collections.singleton(owner);
        }

        void setExternalOwner(boolean externalOwner) {
            this.externalOwner = externalOwner;
        }

        boolean hasExternalOwner() {
            return externalOwner;
        }

        boolean isOwner(String owner) {
            return owners.contains(owner);
        }

        void addOwner(String owner) {
            switch(owners.size()) {
                case 0:
                    owners = Collections.singleton(owner);
                    break;
                case 1:
                    owners = new HashSet<String>(owners);
                default:
                    owners.add(owner);
            }
        }

        boolean removeOwner(String owner) {
            switch(owners.size()) {
                case 0:
                    break;
                case 1:
                    if(owners.contains(owner)) {
                        owners = Collections.emptySet();
                        return true;
                    }
                    break;
                default:
                    return owners.remove(owner);
            }
            return false;
        }

        boolean isOwned() {
            return externalOwner || !owners.isEmpty();
        }

        boolean isOnlyOwner(String owner) {
            return !externalOwner && owners.size() == 1 && owners.contains(owner);
        }

        @Override
        public String toString() {
            return owners + " " + externalOwner;
        }
    }
}
