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

import org.infinispan.util.ByteArrayKey
import org.infinispan.notifications.Listener
import org.infinispan.notifications.cachelistener.annotation.{CacheEntryRemoved, CacheEntryModified, CacheEntryCreated}
import org.infinispan.notifications.cachelistener.event.{CacheEntryEvent, CacheEntryRemovedEvent, CacheEntryModifiedEvent, CacheEntryCreatedEvent}
import org.infinispan.server.hotrod.{OperationStatus, ListenerEvent}
import org.infinispan.server.core.CacheValue
import org.infinispan.server.core.transport.NettyTransport
import scala.collection.JavaConversions._
import ListenerEvent._

/**
 * // TODO: Document this
 *
 *
 *
 * @author Galder Zamarreño
 * @since // TODO
 */
@Listener(sync = false)
class KeyListener(listenerId: Int, appId: ByteArrayKey,
        eventInterest: ListenerEvent.ValueSet, k: ByteArrayKey,
        transport: NettyTransport) {

   // TODO: Consider this
   // We could maintain a single key listener for all keys in a cache,
   // but it'd require some synchronization of keys. But if you're gonna have
   // that many key listeners, might as well use a cache-level listener

   @CacheEntryModified
   def entryModified(e: CacheEntryModifiedEvent[ByteArrayKey, CacheValue]) {
      // TODO: Deal with both cache entry created and modified - doable, see javadoc
      if (needsNotifications(e, ListenerEvent.CacheEntryUpdated)) {
         // TODO: Send notification...
      }
   }

   @CacheEntryRemoved
   def entryRemoved(e: CacheEntryRemovedEvent[ByteArrayKey, CacheValue]) {
      val event = ListenerEvent.CacheEntryRemoved
      if (needsNotifications(e, event)) {
         // 1. Build response
         val rsp = new EventResponse(OperationStatus.Success,
               listenerId, event, e.getKey.getData)
         // 2. Send to all channels, except the channel of origin
         // TODO: Find origin of call
         val ch = transport.getChannelSource(appId)
         ch.write(rsp)

//         // TODO: Send it in parallel?
//         asScalaIterator(transport.acceptedChannels.iterator()).foreach { ch =>
//            // TODO: Send it to all channels and mark isLocal for the channel from which it came?
//            ch.write(rsp)
//         }
      }
   }

   override def toString: String = {
      new StringBuilder().append("KeyListener").append("{")
              .append("listenerId=").append(listenerId)
              .append(", eventInterest=").append(eventInterest)
              .append(", key=").append(k)
              .append("}").toString()
   }

   private def needsNotifications(e: CacheEntryEvent[ByteArrayKey, CacheValue],
           listenEvent: ListenerEvent) =
      !e.isPre && eventInterest.contains(listenEvent) && k == e.getKey

}
