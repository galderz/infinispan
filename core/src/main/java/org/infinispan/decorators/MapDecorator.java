package org.infinispan.decorators;

import org.infinispan.AdvancedCache;
import org.infinispan.cache.impl.MethodMode;
import org.infinispan.commons.api.functional.FunCache;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import static org.infinispan.decorators.Futures.await;

public final class MapDecorator<K, V> implements Map<K, V> {

   // FIXME: Cache locally functions?
   // FIXME: Some of the operations below might get implemented natively for performance reasons

   private final FunCache<K, V> cache;
   private final AdvancedCache<K, V> advCache;

   public MapDecorator(FunCache<K, V> cache, AdvancedCache<K, V> advCache) {
      this.cache = cache;
      this.advCache = advCache;
   }

   @Override
   public int size() {
      return 0;  // TODO: Customise this generated block
   }

   @Override
   public boolean isEmpty() {
      return false;  // TODO: Customise this generated block
   }

   @Override
   public boolean containsKey(Object key) {
      return false;  // TODO: Customise this generated block
   }

   @Override
   public boolean containsValue(Object value) {
      return false;  // TODO: Customise this generated block
   }

   @Override
   public V get(Object key) {
      return null;
//      return await(cache.eval(toK(key), e -> e.getValue().orElse(null),
//            PublicMode.BLOCKING, PublicMode.READ_ONLY));
   }

   @SuppressWarnings("unchecked")
   private K toK(Object key) {
      return (K) key;
   }

   @Override
   public V put(K key, V value) {
      return null;
//      return await(cache.eval(toK(key), e -> {
//         V prev = e.getValue().orElse(null);
//         e.setValue(value);
//         return prev;
//      }, PublicMode.BLOCKING, PublicMode.READ_WRITE, MethodMode.PUT));
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
