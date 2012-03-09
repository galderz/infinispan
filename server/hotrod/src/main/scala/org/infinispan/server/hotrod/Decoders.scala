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

package org.infinispan.server.hotrod

import org.jboss.netty.buffer.ChannelBuffer
import org.infinispan.server.core.transport.ExtendedChannelBuffer._
import org.infinispan.util.ByteArrayKey
import org.infinispan.Cache
import org.infinispan.server.core.{CacheValue, RequestParameters}
import HotRodOperation._
import org.infinispan.server.core.transport.ExtendedChannelBuffer._
import collection.immutable.BitSet.BitSet1
import org.infinispan.server.core.transport.NettyTransport
import OperationStatus._
import org.jboss.netty.channel.{ChannelHandlerContext, Channel}
import java.util.concurrent.ConcurrentMap
import org.infinispan.util.concurrent.ConcurrentMapFactory
import org.infinispan.manager.EmbeddedCacheManager
import v2.{NotifyingCacheImpl, NotifyingCache, KeyListener}
import java.net.SocketAddress

/**
 * // TODO: Document this
 * @author Galder Zamarreño
 * @since // TODO
 */
object Decoders {

   /**
    * Decoder for version 1.0 of the Hot Rod protocol.
    */
   object Decoder10 extends AbstractDecoder1x

   /**
    * Decoder for version 2.0 of the Hot Rod protocol.
    */
   object Decoder20 extends AbstractDecoder1x {

      import HotRod2xOperation._
      import HotRod2xOpResponse._

      // TODO: Clear up on stop...
      // TODO: Make it clustered
      private val notifyingCaches: ConcurrentMap[CacheId, NotifyingCache]  =
         ConcurrentMapFactory.makeConcurrentMap();

      override protected def readOpCode(streamOp: Short, version: Byte,
              msgId: Long): (Enumeration#Value, Boolean) = {
         streamOp match {
            case 0x1B => (AddListenerRequest, false)
            case 0x1D => (RemoveListenerRequest, false)
            case _ => super.readOpCode(streamOp, version, msgId)
         }
      }

      override def customReadHeader(h: HotRodHeader, buffer: ChannelBuffer,
              cache: Cache[ByteArrayKey, CacheValue], ctx: ChannelHandlerContext,
              transport: NettyTransport): AnyRef = {
         h.op match {
            case PingRequest => {
               // Read source id
               val srcId = new ByteArrayKey(readRangedBytes(buffer))
               transport.addChannelSourceId(srcId, ctx.getChannel.getId)
               // Set the app id in the handler context so that
               // it can be retrieved be adding the listener
               ctx.setAttachment(srcId)
               createPingResponse(h)
            }
            case _ => super.customReadHeader(h, buffer, cache, ctx, transport)
         }
      }

      override def customReadKey(h: HotRodHeader, buffer: ChannelBuffer,
              cache: Cache[ByteArrayKey, CacheValue], ctx: ChannelHandlerContext,
              transport: NettyTransport): AnyRef = {
         h.op match {
            case AddListenerRequest => {
               // Retrieve application id from context
               val listenerSrcId = ctx.getAttachment.asInstanceOf[ByteArrayKey]
               // Read listener id
               val listenerId = readUnsignedInt(buffer)
               val listenerKey = ListenerKey(listenerId, listenerSrcId)
               // Events to listen for
               val events = BitSetEnumeration.fromBitSet(
                  ListenerEvent, readUnsignedInt(buffer))
               // Listener granularity
               buffer.readByte() match {
                  case 0x00 => // Key granularity
                     addListener(Some(readKey(buffer)), listenerKey, events,
                           cache, transport, h, ctx)
                  case 0x01 => { // Cache granularity
                     addListener(None, listenerKey, events, cache, transport,
                           h, ctx)
                  }
               }
            }
            case RemoveListenerRequest => {
               // Retrieve application id from context
               val listenerSrcId = ctx.getAttachment.asInstanceOf[ByteArrayKey]
               // Read listener id
               val listenerId = readUnsignedInt(buffer)
               // Remove listener, if present
               removeListener(ListenerKey(listenerId, listenerSrcId),
                  new CacheId(cache.getName, ctx.getChannel.getLocalAddress), h)
            }
            case _ => super.customReadKey(h, buffer, cache, ctx, transport)
         }
      }

      private def addListener(key: Option[ByteArrayKey],
              listenerKey: ListenerKey, events: ListenerEvent.ValueSet,
              cache: Cache[ByteArrayKey, CacheValue],
              transport: NettyTransport, h: HotRodHeader,
              ctx: ChannelHandlerContext): Response = {
         val notifyingCache = getNotifyingCache(
               cache, listenerKey.srcId, transport, ctx)
         val eventInterest = new KeyEventInterest(events, key.get)
         // TODO: How to add the listener in all nodes in the cluster? A single instance won't work (DIST)
         notifyingCache.addListener(listenerKey, eventInterest)
         trace("Add listener %s for events %s", listenerKey, eventInterest)
         new Response(h.version, h.messageId, h.cacheName,
            h.clientIntel, AddListenerResponse , Success,
            h.topologyId)
      }
      
      private def getNotifyingCache(cache: Cache[ByteArrayKey, CacheValue],
              opSrcId: ByteArrayKey, transport: NettyTransport,
              ctx: ChannelHandlerContext): NotifyingCache = {
         val cacheId = CacheId(cache.getName, ctx.getChannel.getLocalAddress)
         val existing = notifyingCaches.get(cacheId)
         if (existing == null) {
            val newCache = new NotifyingCacheImpl(
               cache.getAdvancedCache, opSrcId, transport)
            val prev = notifyingCaches.putIfAbsent(cacheId, newCache)
            if (prev != null) prev else newCache
         } else {
            existing
         }
      }

      private def removeListener(listenerKey: ListenerKey, cacheId: CacheId, h: HotRodHeader): Response = {
         val cache = notifyingCaches.get(cacheId)
         if (cache != null) {
            trace("Remove listener", listenerKey)
            cache.removeListener(listenerKey)
            if (!cache.hasListeners())
               notifyingCaches.remove(cacheId)
         }

         new Response(h.version, h.messageId, h.cacheName,
            h.clientIntel, RemoveListenerResponse, Success,
            h.topologyId)
      }

      override def getCache(name: String, cm: EmbeddedCacheManager,
              ctx: ChannelHandlerContext): Cache[ByteArrayKey, CacheValue] = {
         val cacheId = CacheId(name, ctx.getChannel.getLocalAddress)
         val notifyingCache = notifyingCaches.get(cacheId)
         if (notifyingCache == null)
            super.getCache(name, cm, ctx)
         else
            notifyingCache.withSource(ctx.getAttachment.asInstanceOf[ByteArrayKey])
      }

   }

}

object HotRod2xOperation extends Enumeration(30) {
   type HotRod2xOperation = Value
   val AddListenerRequest = Value
   val RemoveListenerRequest = Value
}

object HotRod2xOpResponse extends Enumeration {
   type HotRod2xOpResponse = Value
   val AddListenerResponse = Value(0x1C)
   val RemoveListenerResponse = Value(0x1E)
}

object ListenerEvent extends Enumeration {
   type ListenerEvent = Value
   val CacheEntryCreated = Value(0x00)
   val CacheEntryUpdated = Value(0x01)
   val CacheEntryRemoved = Value(0x02)
}

//case class CacheEvent(cacheName: String, event: ListenerEvent.ListenerEvent)
//
case class ListenerKey(id: Int, srcId: ByteArrayKey)
//
////case class ListenerSubscription(id: Int, srcId: ByteArrayKey, subscription: EventInterest)
//

abstract class EventInterest(val events: ListenerEvent.ValueSet)

case class CacheWideEventInterest(override val events: ListenerEvent.ValueSet)
        extends EventInterest(events)

case class KeyEventInterest(override val events: ListenerEvent.ValueSet,
        key: ByteArrayKey) extends EventInterest(events)

case class CacheId(cacheName: String, localAddress: SocketAddress)
