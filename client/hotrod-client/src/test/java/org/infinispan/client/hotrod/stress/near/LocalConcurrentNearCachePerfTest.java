package org.infinispan.client.hotrod.stress.near;

import org.HdrHistogram.Histogram;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.NearCacheMode;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.server.hotrod.HotRodServer;
import org.infinispan.test.TestingUtil;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterTest;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.infinispan.client.hotrod.test.HotRodClientTestingUtil.killRemoteCacheManagers;
import static org.infinispan.client.hotrod.test.HotRodClientTestingUtil.killServers;

@Test(groups = "stress", testName = "client.hotrod.stress.near.LocalConcurrentNearCachePerfTest")
public class LocalConcurrentNearCachePerfTest extends AbstractNearCachePerfTest {

   static int NUM_CLIENTS = 2;
   static int NUM_THREADS_PER_CLIENT = 5;
   static AtomicInteger ID = new AtomicInteger();
   static ExecutorService EXEC = Executors.newCachedThreadPool();

   @AfterClass
   public static void shutdownExecutor() {
      EXEC.shutdown();
   }

   @AfterTest(alwaysRun = true)
   public void gc() {
      System.gc();
      TestingUtil.sleepThread(500);
      System.gc();
   }

   public void testLocalPreloadAndConcurrentGet() {
      runPreloadGet("l_PL_CC_get", NearCacheMode.DISABLED, -1);
   }

   public void testLocalPreloadAndConcurrentNearCacheGet() {
      runPreloadGet("l_PL_CC_NC_get", NearCacheMode.INVALIDATED, -1);
   }

   void runPreloadGet(String name, NearCacheMode nearCacheMode, int maxEntries) {
      EmbeddedCacheManager cm = createCacheManager();
      HotRodServer server = createHotRodServer(cm);
      preloadData(server.getPort());
      RemoteCacheManager[] remotecms = new RemoteCacheManager[NUM_CLIENTS];
      for (int i = 0; i < NUM_CLIENTS; i++)
         remotecms[i] = getRemoteCacheManager(server.getPort(), nearCacheMode, maxEntries);
      try {
         preloadAndGet(name, remotecms);
      } finally {
         killRemoteCacheManagers(remotecms);
         killServers(server);
         TestingUtil.killCacheManagers(cm);
      }
   }

   void preloadAndGet(String testName, RemoteCacheManager[] remotecms) {
      // Warmup phase
      List<Future<Measures>> warmupFutures = executePhase(testName, remotecms, true);
      for (Future<?> f : warmupFutures) futureGet(f);

      // Main set
      List<Future<Measures>> mainSetFutures = executePhase(testName, remotecms, false);
      Histogram aggregate = new Histogram(TimeUnit.SECONDS.toNanos(10), 3);
      long totalTime = 0;
      for (Future<Measures> f : mainSetFutures) {
         Measures measures = futureGet(f);
         aggregate.add(measures.histogram);
         totalTime += measures.totalTime;
      }

      // Output aggregate result
      outputHistogram(testName, aggregate);
      outputThroughput(testName, NUM_OPERATIONS * NUM_CLIENTS * NUM_THREADS_PER_CLIENT, totalTime);
   }

   List<Future<Measures>> executePhase(String testName, RemoteCacheManager[] remotecms, boolean isWarmup) {
      CyclicBarrier barrier = new CyclicBarrier((NUM_CLIENTS * NUM_THREADS_PER_CLIENT) + 1);
      List<Future<Measures>> futures = new ArrayList<>(NUM_CLIENTS * NUM_THREADS_PER_CLIENT);
      for (RemoteCacheManager remotecm : remotecms) {
         RemoteCache<Integer, Integer> remote = remotecm.getCache();
         for (int i = 0; i < NUM_THREADS_PER_CLIENT; i++) {
            Callable<Measures> call = isWarmup
                  ? new Warmup(barrier, testName, remote)
                  : new MainSet(barrier, testName, remote);
            futures.add(EXEC.submit(call));
         }
      }
      barrierAwait(barrier); // wait for all threads to be ready
      barrierAwait(barrier); // wait for all threads to finish
      return futures;
   }

   static abstract class Runner implements Callable<Measures> {

      final CyclicBarrier barrier;
      final RemoteCache<Integer, Integer> remote;
      final Histogram histogram = new Histogram(TimeUnit.SECONDS.toNanos(10), 3);
      final int threadId;
      final String testName;

      Runner(CyclicBarrier barrier, String testName, RemoteCache<Integer, Integer> remote) {
         this.barrier = barrier;
         this.remote = remote;
         this.threadId = ID.incrementAndGet();
         this.testName = testName;
      }

      @Override
      public Measures call() throws Exception {
         barrierAwait(barrier);
         try {
            long totalTime = measure();
            return new Measures(
                  histogram.copyCorrectedForCoordinatedOmission(EXPECTED_INTERVAL),
                  totalTime);
         } finally {
            barrierAwait(barrier);
         }
      }

      abstract long measure();
   }

   final static class MainSet extends Runner {

      MainSet(CyclicBarrier barrier, String testName, RemoteCache<Integer, Integer> remote) {
         super(barrier, testName, remote);
      }

      @Override
      long measure() {
         System.out.printf("[%s-%d] Sending %d GET operations%n", testName, threadId, NUM_OPERATIONS);
         long start = System.nanoTime();
         sendGets(NUM_OPERATIONS, remote, KEY_RANGE, histogram);
         long end = System.nanoTime();
         return end - start;
      }
   }

   final static class Warmup extends Runner {

      Warmup(CyclicBarrier barrier, String testName, RemoteCache<Integer, Integer> remote) {
         super(barrier, testName, remote);
      }

      @Override
      long measure() {
         System.out.printf("[%s-%d] Warming up... %d iterations of %d GET operations %n",
               testName, threadId, WARMUP_NUM_ITERATIONS, WARMUP_NUM_OPERATIONS);

         long start = System.nanoTime();
         for (int i = 0; i < WARMUP_NUM_ITERATIONS; i++)
            sendGets(WARMUP_NUM_OPERATIONS, remote, KEY_RANGE, histogram);
         long end = System.nanoTime();

         reset(remote); // Clear local near cache
         return end - start;
      }
   }

   final static class Measures {

      final Histogram histogram;
      final long totalTime;

      Measures(Histogram histogram, long totalTime) {
         this.histogram = histogram;
         this.totalTime = totalTime;
      }
   }
}
