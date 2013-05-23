/*
 * JBoss, Home of Professional Open Source
 * Copyright 2013 Red Hat Inc. and/or its affiliates and other
 * contributors as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a full listing of
 * individual contributors.
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

package org.infinispan.notifications.cachelistener.event;

import java.util.Map;

/**
 * This event subtype is passed in to any method annotated with
 * {@link org.infinispan.notifications.cachelistener.annotation.CacheEntriesExpired}.
 *
 * @author Galder Zamarreño
 * @since 5.3
 */
public interface CacheEntriesExpiredEvent<K, V> extends Event<K, V> {

   // Made cache entries expired event plural instead of singular to better
   // deal with future improvements, such as expiration notifications from
   // cache stores, which could expire entries in group.

   /**
    * Retrieves entries being expired.
    *
    * @return A map containing the key/value pairs of the cache entries being
    * expired if {@link #isPre()} is true. Otherwise, if {@link #isPre()}
    * is false, a map containing the keys being expired with their values set
    * to null.
    */
   Map<K, V> getEntries();

}
