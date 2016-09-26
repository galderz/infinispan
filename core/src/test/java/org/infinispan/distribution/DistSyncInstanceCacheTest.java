package org.infinispan.distribution;

import static org.testng.internal.junit.ArrayAsserts.assertArrayEquals;

import java.util.Random;

import org.infinispan.Cache;
import org.testng.annotations.Test;

@Test(groups = {"functional", "smoke"}, testName = "distribution.DistSyncInstanceCacheTest")
public class DistSyncInstanceCacheTest extends BaseDistFunctionalTest<Object, byte[]> {

   Random r = new Random();

   public DistSyncInstanceCacheTest() {
      INIT_CLUSTER_SIZE = 3;
      l1CacheEnabled = false;
   }

   public void testPutFromNonOwner() {
      String key = "12345678901234567890";
      byte[] value = new byte[1000];
      r.nextBytes(value);
      Cache<Object, byte[]> nonOwner = getFirstNonOwner(key);
      nonOwner.put(key, value);
      assertArrayEquals(value, nonOwner.get(key));
   }

}
