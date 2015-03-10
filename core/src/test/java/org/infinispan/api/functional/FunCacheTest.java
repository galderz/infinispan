package org.infinispan.api.functional;

import org.infinispan.commons.api.functional.FunCache;
import org.infinispan.commons.api.functional.Value;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.test.SingleCacheManagerTest;
import org.infinispan.test.fwk.TestCacheManagerFactory;
import org.testng.annotations.Test;

import java.util.Optional;

import static org.infinispan.commons.api.functional.Mode.AccessMode.READ_ONLY;
import static org.infinispan.commons.api.functional.Mode.AccessMode.WRITE_ONLY;
import static org.infinispan.decorators.Futures.await;
import static org.testng.AssertJUnit.assertEquals;

@Test(groups = "functional", testName = "api.functional.FunCacheTest")
public class FunCacheTest extends SingleCacheManagerTest {

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
         funCache.eval(1, READ_ONLY, Value::get).thenCompose(v ->
         funCache.eval(1, WRITE_ONLY, e -> e.set("one")).thenCompose(x ->
         funCache.eval(1, READ_ONLY, Value::get).thenAccept(v2 -> {
            assertEquals(Optional.empty(), v);
            assertEquals(Optional.of("one"), v2);
         })
      )));
   }

   public void testPutGetViaEval() {
      await(
         funCache.eval(1, WRITE_ONLY, e -> e.set("one")).thenCompose(x ->
         funCache.eval(1, READ_ONLY, Value::get).thenAccept(v ->
            assertEquals(Optional.of("one"), v)
         )
      ));
   }

   public void testGetAndPutViaEval() {
      await(
         funCache.eval(1, WRITE_ONLY, e -> e.set("one")).thenCompose(x ->
         funCache.eval(1, READ_ONLY, e -> {
            Optional<String> prev = e.get();
            e.set("uno");
            return prev;
         }).thenAccept(v ->
            assertEquals(Optional.of("one"), v)
         )
      ));
   }

}
