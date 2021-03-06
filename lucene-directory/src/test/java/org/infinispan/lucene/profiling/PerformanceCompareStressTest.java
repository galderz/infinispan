/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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
package org.infinispan.lucene.profiling;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.infinispan.Cache;
import org.infinispan.lucene.CacheKey;
import org.infinispan.lucene.CacheTestSupport;
import org.infinispan.lucene.InfinispanDirectory;
import org.infinispan.lucene.testutils.ClusteredCacheFactory;
import org.infinispan.manager.CacheManager;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * PerformanceCompareStressTest is useful to get an idea on relative performance between Infinispan
 * in local or clustered mode against a RAMDirectory.
 * To be reliable set a long DURATION_MS.
 * This is not meant as a benchmark but used to detect regressions.
 * 
 * This requires Lucene > 2.9.1 or Lucene > 3.0.0 because of https://issues.apache.org/jira/browse/LUCENE-2095
 * 
 * @author Sanne Grinovero
 * @since 4.0
 */
@Test(groups = "profiling", testName = "lucene.profiling.PerformanceCompareStressTest")
public class PerformanceCompareStressTest {
   
   /** Concurrent Threads in tests */
   private static final int THREADS = 1;
   
   private static final long DURATION_MS = 100000;
   
   private static final ClusteredCacheFactory cacheFactory = new ClusteredCacheFactory(CacheTestSupport.createTestConfiguration());

   @Test
   public void profileTestRAMDirectory() throws InterruptedException, IOException {
      RAMDirectory dir = new RAMDirectory();
      testDirectory(dir, "RAMDirectory");
   }
   
   @Test
   public void profileTestInfinispanDirectory() throws InterruptedException, IOException {
      //these default are not for performance settings but meant for problem detection:
      Cache<CacheKey,Object> cache = cacheFactory.createClusteredCache();
      InfinispanDirectory dir = new InfinispanDirectory(cache, "iname");
      testDirectory(dir, "InfinispanClustered");
   }
   
   @Test
   public void profileInfinispanLocalDirectory() throws InterruptedException, IOException {
      CacheManager cacheManager = CacheTestSupport.createLocalCacheManager();
      try {
         Cache<CacheKey, Object> cache = cacheManager.getCache();
         InfinispanDirectory dir = new InfinispanDirectory(cache, "iname");
         testDirectory(dir, "InfinispanLocal");
      } finally {
         cacheManager.stop();
      }
   }
   
   private void testDirectory(Directory dir, String testLabel) throws InterruptedException, IOException {
      SharedState state = new SharedState(1000);
      CacheTestSupport.initializeDirectory(dir);
      ExecutorService e = Executors.newFixedThreadPool(THREADS);
      for (int i=0; i<THREADS; i++) {
         e.execute(new LuceneUserThread(dir, state));
      }
      e.shutdown();
      state.startWaitingThreads();
      Thread.sleep(DURATION_MS);
      state.quit();
      long searchesCount = state.incrementIndexSearchesCount(0);
      long writerTaskCount = state.incrementIndexWriterTaskCount(0);
      e.awaitTermination(10, TimeUnit.SECONDS);
      System.out.println(
               "Test " + testLabel +" run in " + DURATION_MS + "ms:\n\tSearches: " + searchesCount + "\n\t" + "Writes: " + writerTaskCount);
   }
   
   @BeforeClass
   public static void beforeTest() {
      cacheFactory.start();
   }

   @AfterClass
   public static void afterTest() {
      cacheFactory.stop();
   }

}
