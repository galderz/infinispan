/*
 * Copyright 2012 Red Hat, Inc. and/or its affiliates.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA
 */

package org.infinispan.server.hotrod.v2

import org.infinispan.AdvancedCache
import org.infinispan.util.ByteArrayKey
import org.infinispan.server.core.CacheValue
import org.infinispan.server.hotrod.{EventInterest, ListenerKey}

/**
 * // TODO: Document this
 * @author Galder Zamarreño
 * @since // TODO
 */
trait NotifyingCache extends AdvancedCache[ByteArrayKey, CacheValue] {

   def addListener(listenerKey: ListenerKey, eventInterest: EventInterest)

   def removeListener(listenerKey: ListenerKey)

   def hasListeners(): Boolean

   /**
    *
    * @param srcId
    */
   def withSource(srcId: ByteArrayKey): NotifyingCache

}
