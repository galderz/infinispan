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

package org.infinispan.notifications.cachelistener;

import org.infinispan.Cache;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.container.DataContainer;
import org.infinispan.eviction.EvictionStrategy;
import org.infinispan.loaders.dummy.DummyInMemoryCacheStoreConfigurationBuilder;
import org.infinispan.notifications.Listener;
import org.infinispan.notifications.cachelistener.annotation.CacheEntriesExpired;
import org.infinispan.notifications.cachelistener.event.CacheEntriesExpiredEvent;
import org.infinispan.notifications.cachelistener.event.Event;
import org.infinispan.test.AbstractInfinispanTest;
import org.infinispan.test.CacheManagerCallable;
import org.infinispan.test.fwk.TestCacheManagerFactory;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.infinispan.test.TestingUtil.sleepThread;
import static org.infinispan.test.TestingUtil.withCacheManager;
import static org.testng.AssertJUnit.*;

/**
 * Test verifies that cache entry expiration
 *
 * @author Galder Zamarreño
 * @since 5.3
 */
@Test(groups = "functional", testName = "marshall.VersionAwareMarshallerTest")
public class ExpirationNotificationTest extends AbstractInfinispanTest {

   private static final long EXPIRATION_TIMEOUT = 2000;

   // TODO: test when entry is expired as a result of reading from the cache store

   public void testExpirationListenersNotifiedAfterGet() {
      withCacheManager(new CacheManagerCallable(
            TestCacheManagerFactory.createLocalCacheManager(false)) {
         @Override
         public void call() {
            expirationListenersNotified(1, "v1", CacheOperation.GET,
                  cm.<Integer, String>getCache());
         }
      });
   }

   public void testExpirationListenersNotifiedAfterContainsKey() {
      withCacheManager(new CacheManagerCallable(
            TestCacheManagerFactory.createLocalCacheManager(false)) {
         @Override
         public void call() {
            expirationListenersNotified(2, "v1", CacheOperation.CONTAINS_KEY,
                  cm.<Integer, String>getCache());
         }
      });
   }

   public void testExpirationListenersNotifiedAfterRemove() {
      withCacheManager(new CacheManagerCallable(
            TestCacheManagerFactory.createLocalCacheManager(false)) {
         @Override
         public void call() {
            expirationListenersNotified(3, "v1", CacheOperation.REMOVE,
                  cm.<Integer, String>getCache());
         }
      });
   }

   public void testExpirationListenersNotifiedAfterPurgeExpired() {
      withCacheManager(new CacheManagerCallable(
            TestCacheManagerFactory.createLocalCacheManager(false)) {
         @Override
         public void call() {
            expirationListenersNotified(4, "v1", CacheOperation.PURGE_EXPIRED,
                  cm.<Integer, String>getCache());
         }
      });
   }

   public void testExpirationListenersNotifiedAfterEvictAndGet() {
      ConfigurationBuilder builder = new ConfigurationBuilder();
      // No passivation required. Store data in container, remove it directly
      // from the data container and then try to load it
      builder.loaders().addLoader(DummyInMemoryCacheStoreConfigurationBuilder.class);

      withCacheManager(new CacheManagerCallable(
            TestCacheManagerFactory.createCacheManager(builder)) {
         @Override
         public void call() {
            expirationListenersNotified(5, "v1", CacheOperation.EVICT,
                  cm.<Integer, String>getCache());
         }
      });
   }

   private void expirationListenersNotified(final Integer key, final String value,
         final CacheOperation op, final Cache<Integer, String> cache) {
      Map<Integer, String> expectedEntriesPre = new HashMap<Integer, String>();
      expectedEntriesPre.put(key, value);
      Map<Integer, String> expectedEntriesPost = new HashMap<Integer, String>();
      expectedEntriesPost.put(key, null);
      ExpirationListener listener = new ExpirationListener(
            expectedEntriesPre, expectedEntriesPost);
      cache.addListener(listener);

      long lifespan = EXPIRATION_TIMEOUT;
      cache.put(key, value, lifespan, TimeUnit.MILLISECONDS);
      sleepThread(3 * EXPIRATION_TIMEOUT);
      Future<Void> getAfterExpirationFuture = fork(new Callable<Void>() {
         @Override
         public Void call() throws Exception {
            switch (op) {
               case GET:
                  assertNull(cache.get(key));
                  return null;
               case CONTAINS_KEY:
                  assertFalse(cache.containsKey(key));
                  return null;
               case REMOVE:
                  assertNull(cache.remove(key));
                  return null;
               case PURGE_EXPIRED:
                  // Mimic eviction manager work which will call data container to purge expired entries
                  cache.getAdvancedCache().getDataContainer().purgeExpired();
                  return null;
               case EVICT:
                  // Evict results on a notification, so delete directly from container
                  cache.getAdvancedCache().getDataContainer().remove(key);
                  assertNull(cache.get(key));
                  return null;
               default:
                  throw new IllegalStateException("Unexpected operation: " + op);
            }
         }
      });

      DataContainer dataContainer = cache.getAdvancedCache().getDataContainer();

      try {
         // 1. Wait for pre=true to reach and assert data container contents
         awaitLatch(listener.assertPreLatch);
         assertEquals(value, dataContainer.peek(key).getValue());

         // 2. Let listener continue
         listener.preLatch.countDown();

         // 3. Wait for pre=false to reach and assert cache contents
         awaitLatch(listener.assertPostLatch);
         assertEquals(null, dataContainer.peek(key));

         // 4. Let listener continue
         listener.postLatch.countDown();

         // 5. Verify forked operation
         getAfterExpirationFuture.get(30, TimeUnit.SECONDS);
      } catch (Exception e) {
         throw new AssertionError(e);
      }
   }

   private void awaitLatch(CountDownLatch latch) throws InterruptedException {
      boolean latchReached = latch.await(30, TimeUnit.SECONDS);
      if (!latchReached)
         fail("Latch not reached");
   }

   private enum CacheOperation {
      GET, CONTAINS_KEY, REMOVE, PURGE_EXPIRED, EVICT
   }

   @Listener
   public class ExpirationListener {

      final CountDownLatch assertPreLatch = new CountDownLatch(1);
      final CountDownLatch preLatch = new CountDownLatch(1);
      final CountDownLatch assertPostLatch = new CountDownLatch(1);
      final CountDownLatch postLatch = new CountDownLatch(1);

      final Map<Integer, String> expectedEntriesPre;
      final Map<Integer, String> expectedEntriesPost;

      public ExpirationListener(Map<Integer, String> expectedEntriesPre,
            Map<Integer, String> expectedEntriesPost) {
         this.expectedEntriesPre = expectedEntriesPre;
         this.expectedEntriesPost = expectedEntriesPost;
      }

      @CacheEntriesExpired
      @SuppressWarnings("unused")
      public void cacheEntriesExpired(CacheEntriesExpiredEvent e) throws Exception {
         if (e.isPre()) {
            assertPreLatch.countDown();
            log.debug("In listener, isPre=true, assert event and wait to continue...");
            assertEquals(e.getType(), Event.Type.CACHE_ENTRIES_EXPIRED);
            assertEquals(expectedEntriesPre, e.getEntries());
            awaitLatch(preLatch);
         } else {
            assertPostLatch.countDown();
            log.debug("In listener, isPre=false, wait to continue...");
            assertEquals(e.getType(), Event.Type.CACHE_ENTRIES_EXPIRED);
            assertEquals(expectedEntriesPost, e.getEntries());
            awaitLatch(postLatch);
         }
      }

   }

}
