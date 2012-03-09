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

import org.infinispan.Cache
import org.infinispan.remoting.transport.Address
import org.infinispan.server.hotrod._
import org.jboss.netty.buffer.ChannelBuffer
import org.infinispan.manager.EmbeddedCacheManager
import org.infinispan.server.core.transport.ExtendedChannelBuffer._

/**
 * // TODO: Document this
 * @author Galder Zamarreño
 * @since // TODO
 */
class AbstractEncoder20 extends AbstractEncoder11 {

   override def getTopologyResponse(r: Response,
           members: Cache[Address, ServerAddress],
           server: HotRodServer): AbstractTopologyResponse = {
      r match {
         // Listener events do not send topology info
         case e: EventResponse => null
         case _ => super.getTopologyResponse(r, members, server)
      }
   }

   override def writeResponse(r: Response, buf: ChannelBuffer, cm: EmbeddedCacheManager, server: HotRodServer) {
      r match {
         case e: EventResponse =>
            writeUnsignedInt(e.listenerId, buf)
            buf.writeByte(e.event.id)
            writeRangedBytes(e.key, buf)
         case _ => super.writeResponse(r, buf, cm, server)
      }
   }

}
