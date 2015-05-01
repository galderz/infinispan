package org.infinispan.client.hotrod.stress.near;

import org.HdrHistogram.Histogram;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.NearCacheMode;
import org.infinispan.client.hotrod.impl.InvalidatedNearRemoteCache;
import org.infinispan.commons.util.ReflectionUtil;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.server.hotrod.HotRodServer;
import org.infinispan.test.TestingUtil;
import org.testng.annotations.Test;

import java.util.concurrent.TimeUnit;

import static org.infinispan.client.hotrod.test.HotRodClientTestingUtil.killRemoteCacheManager;
import static org.infinispan.client.hotrod.test.HotRodClientTestingUtil.killServers;

@Test(groups = "stress", testName = "client.hotrod.stress.near.LocalNearCachePerfTest")
public class LocalNearCachePerfTest extends AbstractNearCachePerfTest {

   public void testLocalPreloadAndGet() {
      runPreloadGet("l_PL_get", NearCacheMode.DISABLED, -1);
   }

   public void testLocalPreloadAndNearCacheGet() {
      runPreloadGet("l_PL_NC_get", NearCacheMode.INVALIDATED, -1);
   }

   public void testLocalPreloadAndNearCache100Get() {
      runPreloadGet("l_PL_NC_100_get", NearCacheMode.INVALIDATED, 100);
   }

   void runPreloadGet(String name, NearCacheMode nearCacheMode, int maxEntries) {
      EmbeddedCacheManager cm = createCacheManager();
      HotRodServer server = createHotRodServer(cm);
      preloadData(server.getPort());
      RemoteCacheManager remotecm = getRemoteCacheManager(server.getPort(), nearCacheMode, maxEntries);
      try {
         preloadAndGet(name, remotecm);
      } finally {
         killRemoteCacheManager(remotecm);
         killServers(server);
         TestingUtil.killCacheManagers(cm);
      }
   }

   void preloadAndGet(String testName, RemoteCacheManager remotecm) {
      RemoteCache<Integer, Integer> remote = remotecm.getCache();
      Histogram histogram = new Histogram(TimeUnit.SECONDS.toNanos(10), 3);
      System.out.printf("[%s] Warming up... %d iterations of %d GET operations %n",
            testName, WARMUP_NUM_ITERATIONS, WARMUP_NUM_OPERATIONS);

      for (int i = 0; i < WARMUP_NUM_ITERATIONS; i++)
         sendGets(WARMUP_NUM_OPERATIONS, remote, KEY_RANGE, histogram);

      reset(histogram, remote);

      System.out.printf("[%s] Sending %d GET operations%n", testName, NUM_OPERATIONS);
      long start = System.nanoTime();
      sendGets(NUM_OPERATIONS, remote, KEY_RANGE, histogram);
      long end = System.nanoTime();
      long totalTime = end - start;

      // Post-correct histogram
      histogram = histogram.copyCorrectedForCoordinatedOmission(EXPECTED_INTERVAL);

      outputHistogram(testName, histogram);
      outputThroughput(testName, NUM_OPERATIONS, totalTime);
   }

   void reset(Histogram histogram, RemoteCache<Integer, Integer> remote) {
      histogram.reset();
      try {
         InvalidatedNearRemoteCache nearRemoteCache = ReflectionUtil.unwrap(remote, InvalidatedNearRemoteCache.class);
         //nearRemoteCache.clearLocalCache();
      } catch (IllegalArgumentException e) {
         // Ignore
      }
   }

}
