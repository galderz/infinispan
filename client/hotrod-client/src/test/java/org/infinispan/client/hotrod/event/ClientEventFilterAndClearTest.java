package org.infinispan.client.hotrod.event;

import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.annotation.ClientCacheEntryCreated;
import org.infinispan.client.hotrod.annotation.ClientCacheEntryRemoved;
import org.infinispan.client.hotrod.annotation.ClientListener;
import org.infinispan.client.hotrod.test.HotRodClientTestingUtil;
import org.infinispan.client.hotrod.test.SingleHotRodServerTest;
import org.infinispan.metadata.Metadata;
import org.infinispan.notifications.cachelistener.filter.CacheEventFilter;
import org.infinispan.notifications.cachelistener.filter.CacheEventFilterFactory;
import org.infinispan.notifications.cachelistener.filter.EventType;
import org.infinispan.server.hotrod.HotRodServer;
import org.infinispan.server.hotrod.configuration.HotRodServerConfigurationBuilder;
import org.testng.annotations.Test;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.testng.AssertJUnit.assertEquals;

@Test(groups = "functional", testName = "client.hotrod.event.ClientEventFilterAndClearTest")
public class ClientEventFilterAndClearTest extends SingleHotRodServerTest {

   @Override
   protected HotRodServer createHotRodServer() {
      HotRodServerConfigurationBuilder builder = new HotRodServerConfigurationBuilder();
      HotRodServer server = HotRodClientTestingUtil.startHotRodServer(cacheManager, builder);
      server.addCacheEventFilterFactory("sample-filter-factory", new FilterFactory());
      return server;
   }

   public void testFilterNotCalledOnClear() {
      RemoteCache<Integer, String> cache = remoteCacheManager.getCache();

      final CreateOnlyListener listener = new CreateOnlyListener();
      cache.addClientListener(listener);
      cache.put(1, "one");
      assertEquals(new Integer(1), listener.poll());
      assertEquals(0, listener.events.size());

      cache.clear();
   }

   @ClientListener(filterFactoryName = "sample-filter-factory")
   public static final class CreateOnlyListener {

      BlockingQueue<Integer> events = new ArrayBlockingQueue<>(16);

      @ClientCacheEntryCreated
      @SuppressWarnings("unused")
      public void handleCreatedEvent(ClientCacheEntryCreatedEvent<Integer> e) {
         events.offer(e.getKey());
      }

//      @ClientCacheEntryRemoved
//      @SuppressWarnings("unused")
//      public void handleRemovedEvent(ClientCacheEntryRemovedEvent<Integer> e) {
//         events.offer(e.getKey());
//      }

      public Integer poll() {
         try {
            return events.poll(10, TimeUnit.SECONDS);
         } catch (InterruptedException e) {
            throw new AssertionError(e);
         }
      }
   }

   public static class Filter<K, V> implements CacheEventFilter<K, V> {

      @Override
      public boolean accept(Object key, Object oldValue, Metadata oldMetadata, Object newValue, Metadata newMetadata, EventType eventType) {
         return newValue.equals("one");
      }

   }

   public static class FilterFactory implements CacheEventFilterFactory {

      @Override
      public <K, V> CacheEventFilter<K, V> getFilter(Object[] params) {
         return new Filter<>();
      }

   }

}
