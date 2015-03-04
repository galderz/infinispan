package org.infinispan.cache.impl;

import org.infinispan.commons.api.functional.FunEntry;
import org.infinispan.container.entries.CacheEntry;

import java.util.Optional;

public class FunEntryImpl<K, V> implements FunEntry<V> {

   private final CacheEntry<K, V> entry;

   public FunEntryImpl(CacheEntry<K, V> entry) {
      this.entry = entry;
   }

   @Override
   public Optional<V> getValue() {
      return Optional.ofNullable(entry).map(CacheEntry::getValue);
   }

   @Override
   public Void setValue(V value) {
      entry.setValue(value);
      entry.setChanged(true);
      return null;
   }

   @Override
   public Void remove() {
      entry.setRemoved(true);
      return null;
   }

}
