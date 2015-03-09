package org.infinispan.api.functional;

import org.infinispan.commons.api.functional.FunCache;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.test.SingleCacheManagerTest;
import org.infinispan.test.fwk.TestCacheManagerFactory;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

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

   public void testGetAndRemove() {
      assertEquals(null, map.put(1, "one"));
      assertEquals("one", map.get(1));
      assertEquals("one", map.remove(1));
      assertEquals(null, map.get(1));
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

   public void testEmpty() {
      assertEquals(true, map.isEmpty());
      assertEquals(null, map.put(1, "one"));
      assertEquals("one", map.get(1));
      assertEquals(false, map.isEmpty());
      assertEquals("one", map.remove(1));
      assertEquals(true, map.isEmpty());
   }

   public void testPutAll() {
      assertEquals(true, map.isEmpty());
      Map<Integer, String> data = new HashMap<>();
      data.put(1, "one");
      data.put(2, "two");
      data.put(3, "three");
      map.putAll(data);
      assertEquals("one", map.get(1));
      assertEquals("two", map.get(2));
      assertEquals("three", map.get(3));
   }

   public void testClear() {
      assertEquals(true, map.isEmpty());
      Map<Integer, String> data = new HashMap<>();
      data.put(1, "one");
      data.put(2, "two");
      data.put(3, "three");
      map.putAll(data);
      map.clear();
      assertEquals(null, map.get(1));
      assertEquals(null, map.get(2));
      assertEquals(null, map.get(3));
   }

   public void testKeyValueAndEntrySets() {
      assertEquals(true, map.isEmpty());
      Map<Integer, String> data = new HashMap<>();
      data.put(1, "one");
      data.put(2, "two");
      data.put(3, "three");
      map.putAll(data);

      Set<Integer> keys = map.keySet();
      assertEquals(3, keys.size());
      Set<Integer> expectedKeys = new HashSet<>(Arrays.asList(1, 2, 3));
      keys.forEach(expectedKeys::remove);
      assertEquals(true, expectedKeys.isEmpty());

      Collection<String> values = map.values();
      assertEquals(3, values.size());
      Set<String> expectedValues = new HashSet<>(Arrays.asList("one", "two", "three"));
      values.forEach(expectedValues::remove);
      assertEquals(true, expectedValues.isEmpty());

      Set<Map.Entry<Integer, String>> entries = map.entrySet();
      assertEquals(3, entries.size());
      entries.removeAll(data.entrySet());
      assertEquals(true, entries.isEmpty());
   }

   private static class TestMapDecorator<K, V> implements Map<K, V> {
      final FunCache<K, V> cache;

      private TestMapDecorator(FunCache<K, V> cache) {
         this.cache = cache;
      }

      @Override
      public int size() {
         // FIXME: Could be more efficient with a potential StreamMode.NONE
         return await(cache.fold(StreamMode.KEYS_ONLY, 0, (p, t) -> t + 1));
      }

      @Override
      public boolean isEmpty() {
         // Finishes early, as soon as an entry is found
         return !await(cache.search(StreamMode.VALUES_ONLY,
               (p) -> p.value().isPresent() ? true : null)
         ).isPresent();
      }

      @Override
      public boolean containsKey(Object key) {
         return await(cache.eval(toK(key), READ_ONLY, e -> e.get().isPresent()));
      }

      @Override
      public boolean containsValue(Object value) {
         // Finishes early, as soon as the value is found
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
         return await(cache.eval(toK(key), READ_WRITE, v -> {
            V prev = v.get().orElse(null);
            v.set(value);
            return prev;
         }));
      }

      @Override
      public V remove(Object key) {
         return await(cache.eval(toK(key), READ_WRITE, v -> {
            V prev = v.get().orElse(null);
            v.remove();
            return prev;
         }));
      }

      @Override
      public void putAll(Map<? extends K, ? extends V> m) {
         Map<K, CompletableFuture<Object>> futures = cache.evalAll(m, WRITE_ONLY, (x, v) -> {
            v.set(x);
            return null;
         });

         // Wait for all futures to complete
         await(CompletableFuture.allOf(
            futures.values().toArray(new CompletableFuture[futures.size()])));
      }

      @Override
      public void clear() {
         await(cache.clearAll());
      }

      @Override
      public Set<K> keySet() {
         return await(cache.fold(StreamMode.KEYS_ONLY, new HashSet<>(), (p, set) -> {
            set.add(p.key().get());
            return set;
         }));
      }

      @Override
      public Collection<V> values() {
         return await(cache.fold(StreamMode.VALUES_ONLY, new HashSet<>(), (p, set) -> {
            set.add(p.value().get());
            return set;
         }));
      }

      @Override
      public Set<Entry<K, V>> entrySet() {
         return await(cache.fold(StreamMode.KEYS_AND_VALUES, new HashSet<>(), (p, set) -> {
            set.add(new Entry<K, V>() {
               @Override
               public K getKey() {
                  return p.key().get();
               }

               @Override
               public V getValue() {
                  return p.value().get();
               }

               @Override
               public V setValue(V value) {
                  V prev = p.value().get();
                  cache.eval(p.key().get(), WRITE_ONLY, v -> v.set(value));
                  return prev;
               }

               @Override
               public boolean equals(Object o) {
                  if (o == this)
                     return true;
                  if (o instanceof Map.Entry) {
                     Map.Entry<?,?> e = (Map.Entry<?,?>)o;
                     if (Objects.equals(p.key().get(), e.getKey()) &&
                        Objects.equals(p.value().get(), e.getValue()))
                        return true;
                  }
                  return false;
               }

               @Override
               public int hashCode() {
                  return p.hashCode();
               }
            });
            return set;
         }));
      }
   }

}
