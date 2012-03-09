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

import org.infinispan.server.core.CacheValue
import org.infinispan.util.ByteArrayKey
import org.infinispan.{AdvancedCache, AbstractDelegatingAdvancedCache}
import org.infinispan.server.core.transport.NettyTransport
import org.infinispan.server.hotrod._
import ListenerEvent._
import java.util.concurrent.{ConcurrentMap, TimeUnit}
import logging.Log
import org.infinispan.util.concurrent.ConcurrentMapFactory
import scala.collection.JavaConversions._
import OperationStatus._

/**
 * // TODO: Document this
 * @author Galder Zamarreño
 * @since // TODO
 */
class NotifyingCacheImpl(cache: AdvancedCache[ByteArrayKey, CacheValue],
        opSrcId: ByteArrayKey, transport: NettyTransport,
        listeners: ConcurrentMap[ListenerKey, EventInterest])
        extends AbstractDelegatingAdvancedCache[ByteArrayKey, CacheValue](cache)
                with NotifyingCache with Log {

   def this(cache: AdvancedCache[ByteArrayKey, CacheValue], opSrcId:
            ByteArrayKey, transport: NettyTransport) =
      this(cache, opSrcId, transport, ConcurrentMapFactory.makeConcurrentMap())

   def addListener(listenerKey: ListenerKey, eventInterest: EventInterest) =
      listeners.put(listenerKey, eventInterest)

   def removeListener(listenerKey: ListenerKey) = listeners.remove(listenerKey)

   def hasListeners(): Boolean = !listeners.isEmpty

   def withSource(srcId: ByteArrayKey): NotifyingCache =
      new NotifyingCacheImpl(cache, srcId, transport, listeners)

   override def putIfAbsent(k: ByteArrayKey, v: CacheValue, life: Long,
           lifeUnit: TimeUnit, maxIdle: Long,
           maxIdleUnit: TimeUnit): CacheValue = {
      sendNotificationIfNeeded(
         cache.putIfAbsent(k, v, life, lifeUnit, maxIdle, maxIdleUnit),
         CacheEntryCreated, k, (ret: CacheValue) => ret == null)
   }

   override def put(k: ByteArrayKey, v: CacheValue, life: Long,
           lifeUnit: TimeUnit, maxIdle: Long, 
           maxIdleUnit: TimeUnit): CacheValue = {
      val ret = cache.put(k, v, life, lifeUnit, maxIdle, maxIdleUnit)
      sendNotificationIfNeeded(ret,
         if (ret == null) CacheEntryCreated else CacheEntryUpdated,
         k, (ret: CacheValue) => true)
   }

   override def replace(k: ByteArrayKey, v: CacheValue, life: Long,
           lifeUnit: TimeUnit, maxIdle: Long,
           maxIdleUnit: TimeUnit): CacheValue = {
      sendNotificationIfNeeded(
         cache.replace(k, v, life, lifeUnit, maxIdle, maxIdleUnit),
         CacheEntryUpdated, k, (ret: CacheValue) => ret != null)
   }

   override def replace(k: ByteArrayKey, oldV: CacheValue, v: CacheValue,
           life: Long, lifeUnit: TimeUnit, maxIdle: Long,
           maxIdleUnit: TimeUnit): Boolean = {
      sendNotificationIfNeeded(
         cache.replace(k, oldV, v, life, lifeUnit, maxIdle, maxIdleUnit),
         CacheEntryUpdated, k, (ret: Boolean) => ret)
   }

   override def remove(k: AnyRef): CacheValue = {
      sendNotificationIfNeeded(cache.remove(k), CacheEntryRemoved, k,
         (ret: AnyRef) => ret != null)
   }

   override def remove(k: AnyRef, v: AnyRef): Boolean = {
      sendNotificationIfNeeded(cache.remove(k, v), CacheEntryRemoved, k,
         (ret: Boolean) => ret)
   }

   private def sendNotificationIfNeeded[T <: Any](ret: T,
           event: ListenerEvent, key: AnyRef, isOpExecuted: (T) => Boolean): T = {
      if (isOpExecuted(ret)) {
         // TODO: Do in a separate thread after checking whether the listeners are non-empty...
         mapAsScalaMap(listeners).foreach { case (listener, eventInterest) =>
            if (opSrcId != listener.srcId 
                    && eventInterest.events.contains(event)) {
//               eventInterest.key match {
//                  case Some(interestKey) =>
//                     if (key == interestKey)
//                        sendNotification(listener, event, interestKey.getData)
//                  case None =>
//                     sendNotification(listener, event,
//                           key.asInstanceOf[ByteArrayKey].getData)
//               }
            }
         }
      }
      ret
   }

   private def sendNotification(listener: ListenerKey, event: ListenerEvent,
            key: Array[Byte]) {
      // 1. Build response
      val rsp = new EventResponse(Success, listener.id, event, key)
      // 2. Find the right channel to send the notification
      val ch = transport.getChannelSource(listener.srcId)
      trace("Send event notification %s to channel %s of source %s",
            rsp, ch, listener.srcId)
      ch.write(rsp) // Async write
   }

}
