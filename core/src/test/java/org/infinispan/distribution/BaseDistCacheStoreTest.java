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
package org.infinispan.distribution;

import org.infinispan.config.CacheLoaderManagerConfig;
import org.infinispan.config.Configuration;
import org.infinispan.loaders.dummy.DummyInMemoryCacheStore;
import org.infinispan.manager.CacheManager;
import org.infinispan.test.fwk.TestCacheManagerFactory;

/**
 * DistSyncCacheStoreTest.
 *
 * @author Galder Zamarreño
 * @since 4.0
 */
public abstract class BaseDistCacheStoreTest extends BaseDistFunctionalTest {
   boolean shared;
   static int id;

   @Override
   protected CacheManager addClusterEnabledCacheManager() {
      Configuration cfg = new Configuration();
      CacheLoaderManagerConfig clmc = new CacheLoaderManagerConfig();
      clmc.setShared(shared);
      clmc.addCacheLoaderConfig(new DummyInMemoryCacheStore.Cfg(getClass().getSimpleName() + "_" + id++));
      cfg.setCacheLoaderManagerConfig(clmc);
      CacheManager cm = TestCacheManagerFactory.createClusteredCacheManager(cfg);
      cacheManagers.add(cm);
      return cm;
   }
}
