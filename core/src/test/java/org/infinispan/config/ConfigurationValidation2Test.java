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
package org.infinispan.config;

import org.infinispan.manager.CacheManager;
import org.infinispan.test.SingleCacheManagerTest;
import org.infinispan.test.fwk.TestCacheManagerFactory;
import org.testng.annotations.Test;

import static org.infinispan.config.Configuration.CacheMode.LOCAL;
import static org.infinispan.config.Configuration.CacheMode.REPL_ASYNC;

/**
 * ConfigurationValidationTest.
 *
 * @author Sanne Grinovero
 * @since 4.0
 */
@Test(groups = "functional", testName = "config.ConfigurationValidationTest")
public class ConfigurationValidation2Test extends SingleCacheManagerTest {

   public void testWrongCacheModeConfiguration() {
      cacheManager.getCache().put("key", "value");
   }

   public void testCacheModeConfiguration() {
      cacheManager.getCache("local").put("key", "value");
   }


   @Override
   protected CacheManager createCacheManager() throws Exception {
      GlobalConfiguration gc = GlobalConfiguration.getClusteredDefault();
      Configuration config = new Configuration();
      config.setCacheMode(REPL_ASYNC);
      CacheManager cm = TestCacheManagerFactory.createCacheManager(gc, config);
      config = new Configuration();
      config.setCacheMode(LOCAL);
      cm.defineConfiguration("local", config);
      return cm;
   }
}