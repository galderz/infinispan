package org.infinispan.functional.persistence;

import java.lang.reflect.Method;

import javax.transaction.NotSupportedException;
import javax.transaction.SystemException;

import org.infinispan.Cache;
import org.infinispan.functional.decorators.FunctionalAdvancedCache;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.persistence.CacheLoaderFunctionalTest;
import org.testng.annotations.Test;

@Test(groups = "functional", testName = "functional.persistence.FunctionalPersistenceTest")
public class FunctionalPersistenceTest extends CacheLoaderFunctionalTest {

   @Override
   protected Cache<String, String> getCache(EmbeddedCacheManager cm) {
      Cache<String, String> cache = super.getCache(cm);
      return FunctionalAdvancedCache.create(cache.getAdvancedCache());
   }

   @Override
   protected Cache<String, String> getCache(EmbeddedCacheManager cm, String name) {
      Cache<String, String> cache = super.getCache(cm, name);
      return FunctionalAdvancedCache.create(cache.getAdvancedCache());
   }

}
