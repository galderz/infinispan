package org.infinispan.cache.impl;

import org.infinispan.commons.api.functional.Value;
import org.infinispan.container.entries.CacheEntry;

import java.util.Optional;

public class Values {

   private static final Value<?> EMPTY = new Value() {
      @Override
      public Optional get() {
         return Optional.empty();
      }

      @Override
      public Void set(Object value) {
         return null;
      }

      @Override
      public Void remove() {
         return null;
      }
   };

   public static <V> Value<V> empty() {
      @SuppressWarnings("unchecked")
      Value<V> v = (Value<V>) EMPTY;
      return v;
   }

   public static <K, V> Value<V> of(CacheEntry<K, V> entry) {
      return new ValueImpl<>(entry);
   }

   private static class ValueImpl<K, V> implements Value<V> {

      private final CacheEntry<K, V> entry;

      public ValueImpl(CacheEntry<K, V> entry) {
         this.entry = entry;
      }

      @Override
      public Optional<V> get() {
         return Optional.ofNullable(entry).map(CacheEntry::getValue);
      }

      @Override
      public Void set(V value) {
         entry.setValue(value);
         entry.setChanged(true);
         return null;
      }

      @Override
      public Void remove() {
         entry.setRemoved(true);
         // entry.setValid(false); <- FIXME: Is it needed?
         entry.setChanged(true);
         return null;
      }

   }

}
