package org.infinispan.cache.impl;

import org.infinispan.commons.api.functional.Pair;

import java.util.Optional;

public class Pairs {

   // FIXME: Add a pair of both key and a value

   // FIXME: Provide good hash code for pairs

   public static <K, V> Pair<K, V> ofKey(K key) {
      return new KeyOnlyPair<>(key);
   }

   public static <K, V> Pair<K, V> ofValue(V value) {
      return new ValueOnlyPair<>(value);
   }

   private static abstract class AbstractSingleElement<K, V, T> implements Pair<K, V> {
      protected final Optional<T> elem;

      protected AbstractSingleElement(T elem) {
         this.elem = Optional.of(elem);
      }

      @Override
      public Optional<K> key() {
         return Optional.empty();
      }

      @Override
      public Optional<V> value() {
         return Optional.empty();
      }
   }

   private static final class KeyOnlyPair<K, V> extends AbstractSingleElement<K, V, K> {
      private KeyOnlyPair(K key) {
         super(key);
      }

      @Override
      public Optional<K> key() {
         return elem;
      }
   }

   private static final class ValueOnlyPair<K, V> extends AbstractSingleElement<K, V, V> {
      private ValueOnlyPair(V value) {
         super(value);
      }

      @Override
      public Optional<V> value() {
         return elem;
      }
   }

}
