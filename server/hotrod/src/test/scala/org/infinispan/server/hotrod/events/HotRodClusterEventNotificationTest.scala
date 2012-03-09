package org.infinispan.server.hotrod.events

import org.infinispan.server.hotrod.HotRodMultiNodeTest
import org.infinispan.config.Configuration
import org.infinispan.test.AbstractCacheTest._
import org.infinispan.server.hotrod.test.HotRodClient
import org.infinispan.server.hotrod.test.Closeable.use
import org.infinispan.server.hotrod.test.HotRodTestingUtil._
import org.infinispan.server.hotrod.OperationStatus._
import java.lang.reflect.Method
import org.testng.annotations.Test

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

/**
 * // TODO: Document this
 * @author Galder Zamarreño
 * @since // TODO
 */
@Test(groups = Array("functional"), testName = "server.hotrod.events.HotRodClusterEventNotificationTest")
class HotRodClusterEventNotificationTest extends HotRodMultiNodeTest {

   protected def cacheName: String = "hotRodClusterEvents"

   protected def protocolVersion: Byte = 20

   protected def createCacheConfig: Configuration = {
      val config = getDefaultClusteredConfig(Configuration.CacheMode.REPL_SYNC)
      config.setFetchInMemoryState(true)
      config
   }

   def testReceiveNotificationsWithCluster(m: Method) {
      // 2 node cluster - 2 clients, one to each node in cluster
//      use(connectClient(0), connectClient(1)) { clients =>
      val client1 = clients.tail.head
      val client2 = clients.head
      // 2 clients different source id
      assertStatus(client1.ping(Array(1.toByte, 2.toByte)), Success)
      assertStatus(client2.ping(Array(4.toByte, 5.toByte)), Success)
      // 1 client registers listener in 1st server
      assertStatus(client1.addEventListener(891, 1), Success)
      // 2nd client puts some data in the 2nd server
      assertStatus(client2.put(k(m), 0, 0, v(m)), Success)
      // 1st client needs to be notified
      assertNotification(client1, 891, 0, k(m))
//      }
   }

//   private def connectClient(serverIndex: Int): HotRodClient =
//      new HotRodClient("127.0.0.1", servers.apply(serverIndex).getPort,
//            cacheName, 60, 20)

}
