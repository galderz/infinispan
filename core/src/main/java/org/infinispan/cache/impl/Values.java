package org.infinispan.cache.impl;

import org.infinispan.commons.api.functional.MutableValue;
import org.infinispan.container.entries.CacheEntry;

import java.util.Optional;

public class Values {

   private static final MutableValue<?> EMPTY = new MutableValue() {
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

   public static <V> MutableValue<V> empty() {
      @SuppressWarnings("unchecked")
      MutableValue<V> v = (MutableValue<V>) EMPTY;
      return v;
   }

   public static <K, V> MutableValue<V> of(CacheEntry<K, V> entry) {
      return new MutableValueImpl<>(entry);
   }

   private static class MutableValueImpl<K, V> implements MutableValue<V> {

      private final CacheEntry<K, V> entry;

      public MutableValueImpl(CacheEntry<K, V> entry) {
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
