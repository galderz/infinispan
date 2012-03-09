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

import org.infinispan.server.hotrod.Constants._
import org.infinispan.server.hotrod.OperationStatus._
import Operation2xResponse._
import org.infinispan.server.hotrod.{ListenerEvent, Response}
import ListenerEvent._
import org.infinispan.util.Util

/**
 * // TODO: Document this
 * @author Galder Zamarreño
 * @since // TODO
 */
class EventResponse(override val status: OperationStatus,
                            val listenerId: Int, val event: ListenerEvent, val key: Array[Byte])
      extends Response(VERSION_20, 0, "", 3, EventResponse,
                       status, 0) {

   override def toString = {
      new StringBuilder().append("EventResponse").append("{")
        .append("version=").append(version)
        .append(", status=").append(status)
        .append(", listenerId=").append(listenerId)
        .append(", event=").append(event)
        .append(", key=").append(Util.printArray(key, true))
        .append(", cacheName=").append(cacheName)
        .append("}").toString()
   }

}
