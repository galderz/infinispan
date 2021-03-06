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
package org.infinispan.loaders;

import org.infinispan.Cache;
import org.infinispan.config.CacheLoaderManagerConfig;
import org.infinispan.config.Configuration;
import org.infinispan.config.GlobalConfiguration;
import org.infinispan.container.DataContainer;
import org.infinispan.container.entries.InternalCacheEntry;
import org.infinispan.manager.CacheManager;
import org.infinispan.test.AbstractInfinispanTest;
import org.infinispan.test.TestingUtil;
import org.infinispan.test.fwk.TestCacheManagerFactory;
import org.infinispan.util.Util;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * This is a base functional test class containing tests that should be executed for each cache store/loader
 * implementation. As these are functional tests, they should interact against Cache/CacheManager only and any access to
 * the underlying cache store/loader should be done to verify contents.
 */
@Test(groups = "unit", testName = "loaders.BaseCacheStoreFunctionalTest")
public abstract class BaseCacheStoreFunctionalTest extends AbstractInfinispanTest {

   protected abstract CacheStoreConfig createCacheStoreConfig() throws Exception;

   protected CacheStoreConfig csConfig;

   @BeforeMethod
   public void setUp() throws Exception {
      try {
         csConfig = createCacheStoreConfig();
      } catch (Exception e) {
         //in IDEs this won't be printed which makes debugging harder
         e.printStackTrace();
         throw e;
      }
   }

   public void testTwoCachesSameCacheStore() {
      CacheManager localCacheManager = TestCacheManagerFactory.createLocalCacheManager();
      try {
         GlobalConfiguration configuration = localCacheManager.getGlobalConfiguration();
         CacheLoaderManagerConfig clmConfig = new CacheLoaderManagerConfig();
         clmConfig.setCacheLoaderConfigs(Collections.singletonList((CacheLoaderConfig) csConfig));
         localCacheManager.getDefaultConfiguration().setCacheLoaderManagerConfig(clmConfig);
         localCacheManager.defineConfiguration("first", new Configuration());
         localCacheManager.defineConfiguration("second", new Configuration());

         Cache first = localCacheManager.getCache("first");
         Cache second = localCacheManager.getCache("second");
         assert first.getConfiguration().getCacheLoaderManagerConfig().getCacheLoaderConfigs().size() == 1;
         assert second.getConfiguration().getCacheLoaderManagerConfig().getCacheLoaderConfigs().size() == 1;

         first.start();
         second.start();

         first.put("key", "val");
         assert first.get("key").equals("val");
         assert second.get("key") == null;

         second.put("key2", "val2");
         assert second.get("key2").equals("val2");
         assert first.get("key2") == null;
      } finally {
         TestingUtil.killCacheManagers(localCacheManager);
      }
   }

   public void testPreloadAndExpiry() {
      CacheLoaderManagerConfig cacheLoaders = new CacheLoaderManagerConfig();
      cacheLoaders.setPreload(true);
      cacheLoaders.addCacheLoaderConfig(csConfig);
      Configuration cfg = TestCacheManagerFactory.getDefaultConfiguration(false);
      cfg.setCacheLoaderManagerConfig(cacheLoaders);
      CacheManager local = TestCacheManagerFactory.createCacheManager(cfg);
      try {
         Cache<String, String> cache = local.getCache();
         cache.start();

         assert cache.getConfiguration().getCacheLoaderManagerConfig().isPreload();

         cache.put("k1", "v");
         cache.put("k2", "v", 111111, TimeUnit.MILLISECONDS);
         cache.put("k3", "v", -1, TimeUnit.MILLISECONDS, 222222, TimeUnit.MILLISECONDS);
         cache.put("k4", "v", 333333, TimeUnit.MILLISECONDS, 444444, TimeUnit.MILLISECONDS);

         cache.stop();

         cache.start();

         assertCacheEntry(cache, "k1", "v", -1, -1);
         assertCacheEntry(cache, "k2", "v", 111111, -1);
         assertCacheEntry(cache, "k3", "v", -1, 222222);
         assertCacheEntry(cache, "k4", "v", 333333, 444444);
      } finally {
         TestingUtil.killCacheManagers(local);
      }
   }

   private void assertCacheEntry(Cache cache, String key, String value, long lifespanMillis, long maxIdleMillis) {
      DataContainer dc = cache.getAdvancedCache().getDataContainer();
      InternalCacheEntry ice = dc.get(key);
      assert ice != null : "No such entry for key " + key;
      assert Util.safeEquals(ice.getValue(), value) : ice.getValue() + " is not the same as " + value;
      assert ice.getLifespan() == lifespanMillis : "Lifespan " + ice.getLifespan() + " not the same as " + lifespanMillis;
      assert ice.getMaxIdle() == maxIdleMillis : "MaxIdle " + ice.getMaxIdle() + " not the same as " + maxIdleMillis;
      if (lifespanMillis > -1) assert ice.getCreated() > -1 : "Lifespan is set but created time is not";
      if (maxIdleMillis > -1) assert ice.getLastUsed() > -1 : "Max idle is set but last used is not";

   }
}
