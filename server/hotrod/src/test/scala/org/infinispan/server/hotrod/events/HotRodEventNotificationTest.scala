package org.infinispan.server.hotrod.events

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

import org.testng.annotations.Test
import org.infinispan.server.hotrod.test.HotRodClient
import org.infinispan.server.hotrod.OperationStatus._
import org.infinispan.server.hotrod.HotRodSingleNodeTest
import org.infinispan.server.hotrod.test.HotRodTestingUtil._
import org.infinispan.server.core.test.Stoppable.useStoppables
import java.lang.reflect.Method

/**
 * // TODO: Document this
 *
 *
 *
 * @author Galder Zamarreño
 * @since // TODO
 */
@Test(groups = Array("functional"), testName = "server.hotrod.events.HotRodRemoteListenerTest")
class HotRodEventNotificationTest extends HotRodSingleNodeTest {

   // TODO: Test multiple source subscriptions for same cache name

   def testClientsSameSources(m: Method) {
      useStoppables(connectClient, connectClient) { clients =>
         val client1 = clients.head
         val client2 = clients.tail.head
         // Ping with application id
         val srcId = Array(1.toByte, 2.toByte, 3.toByte)
         assertStatus(client1.ping(srcId), Success)
         assertStatus(client2.ping(srcId), Success)

         client1.assertPut(m)
         assertStatus(client1.addEventListener(999, 4, k(m)), Success)
         assertStatus(client2.remove(k(m)), Success)
         assertKeyDoesNotExist(client1.assertGet(m))

         // Both clients are local to the source
         // No notifications should be received
         assertNotificationNotReceived(client1)
         assertNotificationNotReceived(client2)
      }
   }

   def testClientsDiffSources(m: Method) {
      useStoppables(connectClient, connectClient) { clients =>
         val client1 = clients.head
         val client2 = clients.tail.head
         assertStatus(client1.ping(Array(1.toByte, 2.toByte)), Success)
         assertStatus(client2.ping(Array(4.toByte, 5.toByte)), Success)

         client1.assertPut(m)
         assertStatus(client1.addEventListener(888, 4, k(m)), Success)
         assertStatus(client2.remove(k(m)), Success)
         assertKeyDoesNotExist(client1.assertGet(m))

         // Operation not originated locally and a listener added
         assertNotification(client1, 888, 2, k(m))
         // Operation originated locally, so no notification
         assertNotificationNotReceived(client2)
      }
   }

   def testEntryCreatedNotification(m: Method) {
      useStoppables(connectClient, connectClient) { clients =>
         val client1 = clients.head
         val client2 = clients.tail.head
         assertStatus(client1.ping(Array(1.toByte, 2.toByte)), Success)
         assertStatus(client2.ping(Array(4.toByte, 5.toByte)), Success)

         assertStatus(client1.addEventListener(777, 1, k(m)), Success)
         // Try with a putIfAbsent
         assertStatus(client2.putIfAbsent(k(m), 0, 0, v(m)), Success)
         assertNotification(client1, 777, 0, k(m))
         // Try with a normal put
         val key2 = k(m, "k2-")
         assertStatus(client1.addEventListener(666, 1, key2), Success)
         assertStatus(client2.put(key2, 0, 0, v(m)), Success)
         assertNotification(client1, 666, 0, key2)
      }
   }

   def testEntryUpdatedNotification(m: Method) {
      useStoppables(connectClient, connectClient) { clients =>
         val client1 = clients.head
         val client2 = clients.tail.head
         assertStatus(client1.ping(Array(1.toByte, 2.toByte)), Success)
         assertStatus(client2.ping(Array(4.toByte, 5.toByte)), Success)

         client1.assertPut(m)
         assertStatus(client1.addEventListener(555, 2, k(m)), Success)
         // Try with a put
         assertStatus(client2.put(k(m), 0, 0, v(m, "v2-")), Success)
         assertNotification(client1, 555, 1, k(m))
         // Try with a replace
         val key2 = k(m, "k2-")
         assertStatus(client1.addEventListener(444, 2, key2), Success)
         assertStatus(client2.put(key2, 0, 0, v(m)), Success)
         assertStatus(client2.replace(key2, 0, 0, v(m, "v2-")), Success)
         assertNotification(client1, 444, 1, key2)
         // Try with a conditional replace
         val key3 = k(m, "k3-")
         assertStatus(client2.addEventListener(333, 2, key3), Success)
         assertStatus(client1.put(key3, 0, 0, v(m)), Success)
         val resp = client1.getWithVersion(key3, 0)
         assertSuccess(resp, v(m), 0)
         assertStatus(client1.replaceIfUnmodified(
            key3, 0, 0, v(m, "v2-"), resp.dataVersion), Success)
         assertNotification(client2, 333, 1, key3)
      }
   }

   def testEntryRemovedNotification(m: Method) {
      useStoppables(connectClient, connectClient) { clients =>
         val client1 = clients.head
         val client2 = clients.tail.head
         assertStatus(client1.ping(Array(1.toByte, 2.toByte)), Success)
         assertStatus(client2.ping(Array(4.toByte, 5.toByte)), Success)

         // Try with a simple remove
         client1.assertPut(m)
         assertStatus(client1.addEventListener(222, 4, k(m)), Success)
         assertStatus(client2.remove(k(m)), Success)
         assertNotification(client1, 222, 2, k(m))
         // Try with a conditional remove
         val key2 = k(m, "k2-")
         assertStatus(client2.addEventListener(111, 4, key2), Success)
         assertStatus(client1.put(key2, 0, 0, v(m)), Success)
         val resp = client1.getWithVersion(key2, 0)
         assertSuccess(resp, v(m), 0)
         assertStatus(client1.removeIfUnmodified(
            key2, 0, 0, v(m), resp.dataVersion), Success)
         assertNotification(client2, 111, 2, key2)
      }
   }

   def testUpdateEventInterestByReAddingListener(m: Method) {
      useStoppables(connectClient, connectClient) { clients =>
         val client1 = clients.head
         val client2 = clients.tail.head
         assertStatus(client1.ping(Array(1.toByte, 2.toByte)), Success)
         assertStatus(client2.ping(Array(4.toByte, 5.toByte)), Success)

         // First client does a put and waits for modifications
         client1.assertPut(m)
         assertStatus(client1.addEventListener(147, 2, k(m)), Success)
         assertStatus(client2.put(k(m), 0, 0, v(m, "v2-")), Success)
         assertNotification(client1, 147, 1, k(m))

         // Now first client changes to wait for removals only
         assertStatus(client1.addEventListener(147, 4, k(m)), Success)
         // And second client modifies the key
         assertStatus(client2.put(k(m), 0, 0, v(m, "v3-")), Success)
         assertNotificationNotReceived(client1)
         assertStatus(client2.remove(k(m)), Success)
         assertNotification(client1, 147, 2, k(m))
      }
   }

   def testEntryCreatedAndRemovedNotification(m: Method) {
      useStoppables(connectClient, connectClient) { clients =>
         val client1 = clients.head
         val client2 = clients.tail.head
         assertStatus(client1.ping(Array(1.toByte, 2.toByte)), Success)
         assertStatus(client2.ping(Array(4.toByte, 5.toByte)), Success)

         // Both create(1) and remove(4)
         assertStatus(client1.addEventListener(258, 5, k(m)), Success)
         // Try with a putIfAbsent
         assertStatus(client2.putIfAbsent(k(m), 0, 0, v(m)), Success)
         assertNotification(client1, 258, 0, k(m))
         // Try with a remove
         assertStatus(client2.remove(k(m)), Success)
         assertNotification(client1, 258, 2, k(m))
      }
   }

   def testAllEntryNotifications(m: Method) {
      useStoppables(connectClient, connectClient) { clients =>
         val client1 = clients.head
         val client2 = clients.tail.head
         assertStatus(client1.ping(Array(1.toByte, 2.toByte)), Success)
         assertStatus(client2.ping(Array(4.toByte, 5.toByte)), Success)

         // All: create(1), update(2) and remove(4)
         assertStatus(client1.addEventListener(169, 7, k(m)), Success)
         // Try with a putIfAbsent
         assertStatus(client2.putIfAbsent(k(m), 0, 0, v(m)), Success)
         assertNotification(client1, 169, 0, k(m))
         // Try with a put to update
         assertStatus(client2.put(k(m), 0, 0, v(m, "v2-")), Success)
         assertNotification(client1, 169, 1, k(m))
         // Try with a remove
         assertStatus(client2.remove(k(m)), Success)
         assertNotification(client1, 169, 2, k(m))
      }
   }

   def testAllCacheEntryNotifications(m: Method) {
      useStoppables(connectClient, connectClient) { clients =>
         val client1 = clients.head
         val client2 = clients.tail.head
         assertStatus(client1.ping(Array(1.toByte, 2.toByte)), Success)
         assertStatus(client2.ping(Array(4.toByte, 5.toByte)), Success)

         // All: create(1), update(2) and remove(4)
         assertStatus(client1.addEventListener(289, 7), Success)

         // Try with a putIfAbsent
         val key1 = k(m, "k1-")
         assertStatus(client2.putIfAbsent(key1, 0, 0, v(m)), Success)
         assertNotification(client1, 289, 0, key1)
         // Try with a put to update
         val key2 = k(m, "k2-")
         assertStatus(client2.putIfAbsent(key2, 0, 0, v(m)), Success)
         assertStatus(client2.put(key2, 0, 0, v(m, "v2-")), Success)
         assertNotification(client1, 289, 0, key2)
         assertNotification(client1, 289, 1, key2)
         // Try with a remove
         val key3 = k(m, "k3-")
         assertStatus(client2.put(key3, 0, 0, v(m)), Success)
         assertStatus(client2.remove(key3), Success)
         assertNotification(client1, 289, 0, key3)
         assertNotification(client1, 289, 2, key3)
      }
   }

   def testRemoveListener(m: Method) {
      useStoppables(connectClient, connectClient) { clients =>
         val client1 = clients.head
         val client2 = clients.tail.head
         assertStatus(client1.ping(Array(1.toByte, 2.toByte)), Success)
         assertStatus(client2.ping(Array(4.toByte, 5.toByte)), Success)

         // All: create(1), update(2) and remove(4)
         assertStatus(client1.addEventListener(561, 7), Success)

         // Try with a putIfAbsent
         val key1 = k(m, "k1-")
         assertStatus(client2.putIfAbsent(key1, 0, 0, v(m)), Success)
         assertNotification(client1, 561, 0, key1)

         // Remove listener
         assertStatus(client1.removeEventListener(561), Success)

         // Try with a put to update
         val key2 = k(m, "k2-")
         assertStatus(client2.putIfAbsent(key2, 0, 0, v(m)), Success)
         assertNotificationNotReceived(client1)
      }
   }

   override protected def connectClient: HotRodClient =
      new HotRodClient("127.0.0.1", server.getPort, cacheName, 60, 20)

}
