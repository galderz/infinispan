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

package org.infinispan.distexec.mapreduce;

import org.infinispan.Cache;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.test.MultipleCacheManagersTest;
import org.testng.annotations.Test;

import java.util.Iterator;
import java.util.Map;

import static org.testng.AssertJUnit.assertEquals;

/**
 * Test map/reduce behaviour when nodes start without all defined caches
 * started, or without all caches defined.
 *
 * @author Ray Tsang
 * @author Galder Zamarreño
 * @since 5.3
 */
@Test(groups = "functional", testName = "distexec.mapreduce.AsymmetricMapReduceTest")
public class AsymmetricMapReduceTest extends MultipleCacheManagersTest {

   @Override
   protected void createCacheManagers() throws Throwable {
      ConfigurationBuilder builder = getDefaultClusteredCacheConfig(CacheMode.DIST_SYNC, true);
      createCluster(builder, 2);
      // works when using: createClusteredCaches(2, builder);
   }

   public void testMapReduce() {
      Cache<String, Boolean> cache = cacheManagers.get(0).getCache();
      final int count = 10;
      for (int i = 0; i < count; i++)
         cache.putIfAbsent(String.valueOf(i), Boolean.TRUE);

      log.info("Put in 10 items...");

      Cache<String, Boolean> thisCache = cacheManagers.get(1).getCache();
      MapReduceTask<String, Boolean, String, Boolean> task = new MapReduceTask<String, Boolean, String, Boolean>(thisCache);
      log.info("About to execute M/R");
      Map<String, Boolean> result = task.mappedWith(new TestMapper()).reducedWith(new TestReducer()).execute();
      assertEquals(count, result.size());
   }

   static class TestMapper implements Mapper<String, Boolean, String, Boolean> {
      @Override
      public void map(String key, Boolean value, Collector<String, Boolean> collector) {
         collector.emit(key, true);
      }
   }

   static class TestReducer implements Reducer<String, Boolean> {
      @Override
      public Boolean reduce(String reducedKey, Iterator<Boolean> iter) {
         return true;
      }
   }

}
