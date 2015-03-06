package org.infinispan.api.functional;

import org.infinispan.commons.api.functional.FunCache;
import org.infinispan.commons.api.functional.Value;
import org.infinispan.distribution.BaseDistFunctionalTest;
import org.testng.annotations.Test;

import java.util.Optional;

import static org.infinispan.commons.api.functional.Mode.AccessMode.READ_ONLY;
import static org.infinispan.commons.api.functional.Mode.AccessMode.WRITE_ONLY;
import static org.infinispan.decorators.Futures.await;
import static org.testng.AssertJUnit.assertEquals;

@Test(groups = "functional", testName = "api.functional.DistMapViaFunCacheTest")
public class DistFunCacheTest extends BaseDistFunctionalTest<Integer, String> {

   public DistFunCacheTest() {
      l1CacheEnabled = false;
      numOwners = 1;
      INIT_CLUSTER_SIZE = 2;
   }

   public void testNonOwnerPutGetViaEval() {
      FunCache<Integer, String> nonOwner = (FunCache<Integer, String>) getNonOwners(1)[0];
      await(
         nonOwner.eval(1, WRITE_ONLY, e -> e.set("one")).thenCompose(x ->
         nonOwner.eval(1, READ_ONLY, Value::get).thenAccept(v ->
            assertEquals(Optional.of("one"), v)
         )
      ));
   }

}
