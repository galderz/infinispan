package org.infinispan.api.functional;

import org.infinispan.Cache;
import org.infinispan.commons.api.functional.FunCache;
import org.infinispan.commons.api.functional.FunEntry;
import org.infinispan.commons.api.functional.Modes;
import org.infinispan.commons.api.functional.Modes.AccessMode;
import org.infinispan.decorators.Futures;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.test.SingleCacheManagerTest;
import org.infinispan.test.fwk.TestCacheManagerFactory;
import org.testng.annotations.Test;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.infinispan.commons.api.functional.Modes.AccessMode.*;
import static org.infinispan.decorators.Futures.*;
import static org.testng.AssertJUnit.assertEquals;

@Test(groups = "functional", testName = "api.functional.MapViaFunctionalTest")
public class MapViaFunCacheTest extends SingleCacheManagerTest {

   FunCache<Integer, String> funCache;

   @Override
   protected EmbeddedCacheManager createCacheManager() throws Exception {
      EmbeddedCacheManager cm = TestCacheManagerFactory.createCacheManager(false);
      // FIXME: Temporary!!
      funCache = (FunCache<Integer, String>) cm.getCache();
      return cm;
   }

   public void testEmptyGetThenPutViaEval() {
      await(
         funCache.eval(1, READ_ONLY, FunEntry::getValue).thenCompose(v ->
         funCache.eval(1, WRITE_ONLY, e -> e.setValue("one")).thenCompose(x ->
         funCache.eval(1, READ_ONLY, FunEntry::getValue).thenAccept(v2 -> {
            assertEquals(Optional.empty(), v);
            assertEquals(Optional.of("one"), v2);
         })
      )));
   }

   public void testPutViaEval() {
      await(
         funCache.eval(1, WRITE_ONLY, e -> e.setValue("one")).thenCompose(x ->
         funCache.eval(1, READ_ONLY, FunEntry::getValue).thenAccept(v ->
            assertEquals(Optional.of("one"), v)
         )
      ));
   }

   public void testGetAndPutViaEval() {
      await(
         funCache.eval(1, WRITE_ONLY, e -> e.setValue("one")).thenCompose(x ->
         funCache.eval(1, READ_ONLY, e -> {
            Optional<String> prev = e.getValue();
            e.setValue("uno");
            return prev;
         }).thenAccept(v ->
            assertEquals(Optional.of("one"), v)
         )
      ));
   }

}
