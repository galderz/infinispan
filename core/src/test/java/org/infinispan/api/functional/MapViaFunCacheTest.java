package org.infinispan.api.functional;

import org.infinispan.commons.api.functional.FunCache;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.test.SingleCacheManagerTest;
import org.infinispan.test.fwk.TestCacheManagerFactory;
import org.testng.annotations.Test;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import static org.infinispan.commons.api.functional.Mode.AccessMode.*;
import static org.infinispan.decorators.Futures.*;
import static org.testng.AssertJUnit.assertEquals;

@Test(groups = "functional", testName = "api.functional.MapViaFunctionalTest")
public class MapViaFunCacheTest extends SingleCacheManagerTest {

   // FunCache<Integer, String> funCache;
   Map<Integer, String> map;

   @Override
   protected EmbeddedCacheManager createCacheManager() throws Exception {
      EmbeddedCacheManager cm = TestCacheManagerFactory.createCacheManager(false);
      // FIXME: Temporary!!
      map = new TestMapDecorator<>((FunCache<Integer, String>) cm.getCache());
      return cm;
   }

   public void testEmptyGetThenPut() {
      assertEquals(null, map.get(1));
      assertEquals(null, map.put(1, "one"));
      assertEquals("one", map.get(1));
   }

   public void testPutGet() {
      assertEquals(null, map.put(1, "one"));
      assertEquals("one", map.get(1));
   }

   public void testGetAndPut() {
      assertEquals(null, map.put(1, "one"));
      assertEquals("one", map.put(1, "uno"));
      assertEquals("uno", map.get(1));
   }

   public void testContainsKey() {
      assertEquals(false, map.containsKey(1));
      assertEquals(null, map.put(1, "one"));
      assertEquals(true, map.containsKey(1));
   }

   public void testContainsValue() {
      assertEquals(false, map.containsValue("one"));
      assertEquals(null, map.put(1, "one"));
      assertEquals(true, map.containsValue("one"));
      assertEquals(false, map.containsValue("uno"));
   }

   public void testSize() {
      assertEquals(0, map.size());
      assertEquals(null, map.put(1, "one"));
      assertEquals(1, map.size());
      assertEquals(null, map.put(2, "two"));
      assertEquals(null, map.put(3, "three"));
      assertEquals(3, map.size());
   }

   private static class TestMapDecorator<K, V> implements Map<K, V> {
      final FunCache<K, V> cache;

      private TestMapDecorator(FunCache<K, V> cache) {
         this.cache = cache;
      }

      @Override
      public int size() {
         return await(cache.fold(StreamMode.KEYS_ONLY, 0, (p, t) -> t + 1));
      }

      @Override
      public boolean isEmpty() {
         return false;  // TODO: Customise this generated block
      }

      @Override
      public boolean containsKey(Object key) {
         return await(cache.eval(toK(key), READ_ONLY, e -> e.get().isPresent()));
      }

      @Override
      public boolean containsValue(Object value) {
         return await(cache.search(StreamMode.VALUES_ONLY,
            (p) -> p.value().get().equals(value) ? true : null)
         ).isPresent();
      }

      @Override
      public V get(Object key) {
         return await(cache.eval(toK(key), READ_ONLY, e -> e.get().orElse(null)));
      }

      @SuppressWarnings("unchecked")
      private K toK(Object key) {
         return (K) key;
      }

      @Override
      public V put(K key, V value) {
         return await(cache.eval(toK(key), READ_WRITE, e -> {
            V prev = e.get().orElse(null);
            e.set(value);
            return prev;
         }));
      }

      @Override
      public V remove(Object key) {
         return null;  // TODO: Customise this generated block
      }

      @Override
      public void putAll(Map<? extends K, ? extends V> m) {
         // TODO: Customise this generated block
      }

      @Override
      public void clear() {
         // TODO: Customise this generated block
      }

      @Override
      public Set<K> keySet() {
         return null;  // TODO: Customise this generated block
      }

      @Override
      public Collection<V> values() {
         return null;  // TODO: Customise this generated block
      }

      @Override
      public Set<Entry<K, V>> entrySet() {
         return null;  // TODO: Customise this generated block
      }
   }

}
