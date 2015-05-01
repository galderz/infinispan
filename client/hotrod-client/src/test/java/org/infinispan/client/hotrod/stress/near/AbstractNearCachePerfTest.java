package org.infinispan.client.hotrod.stress.near;

import org.HdrHistogram.Histogram;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.client.hotrod.configuration.NearCacheMode;
import org.infinispan.client.hotrod.impl.NearRemoteCache;
import org.infinispan.client.hotrod.test.HotRodClientTestingUtil;
import org.infinispan.client.hotrod.test.InternalRemoteCacheManager;
import org.infinispan.client.hotrod.test.RemoteCacheManagerCallable;
import org.infinispan.commons.util.ReflectionUtil;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.server.hotrod.HotRodServer;
import org.infinispan.test.fwk.TestCacheManagerFactory;
import org.testng.annotations.Test;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.infinispan.client.hotrod.test.HotRodClientTestingUtil.withRemoteCacheManager;
import static org.infinispan.server.hotrod.test.HotRodTestingUtil.hotRodCacheConfiguration;
import static org.testng.AssertJUnit.assertEquals;

@Test
public abstract class AbstractNearCachePerfTest {

   static final int WARMUP_NUM_ITERATIONS = 10;
   static final int WARMUP_NUM_OPERATIONS = 5_000;
   static final int NUM_OPERATIONS = 1_000_000;
   static final int NUM_KEYS_PRELOAD = 1_000;
   static final int KEY_RANGE = 1_000;
   static final int EXPECTED_INTERVAL = 10_000_000; // 10ms
   static final long TIMESTAMP = System.currentTimeMillis();
   static final Random R = new Random();

   EmbeddedCacheManager createCacheManager() {
      return TestCacheManagerFactory.createCacheManager(hotRodCacheConfiguration());
   }

   HotRodServer createHotRodServer(EmbeddedCacheManager cm) {
      return HotRodClientTestingUtil.startHotRodServer(cm);
   }

   RemoteCacheManager getRemoteCacheManager(int port) {
      return getRemoteCacheManager(port, NearCacheMode.DISABLED, -1);
   }

   RemoteCacheManager getRemoteCacheManager(int port, NearCacheMode nearCacheMode, int maxEntries) {
      ConfigurationBuilder builder = new ConfigurationBuilder();
      builder.nearCache().mode(nearCacheMode).maxEntries(maxEntries);
      builder.addServer().host("127.0.0.1").port(port);
      return new InternalRemoteCacheManager(builder.build());
   }

   void preloadData(int port) {
      // Preload data
      withRemoteCacheManager(new RemoteCacheManagerCallable(getRemoteCacheManager(port)) {
         @Override
         public void call() {
            RemoteCache<Integer, Integer> remote = rcm.getCache();
            Map<Integer, Integer> map = new HashMap<>();
            for (int i = 0; i < NUM_KEYS_PRELOAD; ++i) map.put(i, i);
            remote.putAll(map);
         }
      });
   }

   static void sendGets(int numOperations, RemoteCache<Integer, Integer> remote, int possibilities, Histogram histogram) {
      for (int i = 0; i < numOperations; i++) {
         int key = R.nextInt(possibilities);
         long start = System.nanoTime();
         Integer value = remote.get(key);
         long end = System.nanoTime();
         assertEquals(key, value.intValue());
         // Expect a sample to be recorded every so often, to avoid Coordinate Omission problem
         histogram.recordValueWithExpectedInterval(end - start, EXPECTED_INTERVAL);
      }
   }

   static void reset(RemoteCache<Integer, Integer> remote) {
      try {
         NearRemoteCache nearRemoteCache = ReflectionUtil.unwrap(remote, NearRemoteCache.class);
         nearRemoteCache.clearLocalCache();
      } catch (IllegalArgumentException e) {
         // Ignore
      }
   }

   static void outputHistogram(String testName, Histogram histogram) {
      System.out.printf("[%s] Histogram of RTT latencies in microseconds.%n", testName);
      String outputFilePath = String.format("target/%x-%s.hgrm", TIMESTAMP, testName);
      try {
         PrintStream fileOutput = new PrintStream(new FileOutputStream(outputFilePath, true));
         histogram.outputPercentileDistribution(fileOutput, 1000.0);
      } catch (FileNotFoundException e) {
         // If file cannot be written, direct to standard output
         histogram.outputPercentileDistribution(System.out, 1000.0);
      }
   }

   static void outputThroughput(String testName, int totalNumOps, long totalTime) {
      double opsPerSec = ((totalNumOps) * TimeUnit.SECONDS.toNanos(1)) / (double) totalTime;
      System.out.printf("[%s] Performed %d GET operations in %d seconds, at %.02g ops/sec %n",
            testName, totalNumOps, TimeUnit.NANOSECONDS.toSeconds(totalTime), opsPerSec);
   }

   <T> T futureGet(Future<T> future) {
      try {
         return future.get();
      } catch (InterruptedException | ExecutionException e) {
         throw new AssertionError(e);
      }
   }

   static int barrierAwait(CyclicBarrier barrier) {
      try {
         return barrier.await();
      } catch (InterruptedException | BrokenBarrierException e) {
         throw new AssertionError(e);
      }
   }

}
