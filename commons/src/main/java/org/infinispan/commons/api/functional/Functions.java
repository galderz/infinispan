package org.infinispan.commons.api.functional;

import java.io.Serializable;
import java.util.function.BiFunction;
import java.util.function.Function;

public class Functions {

   private Functions() {
      // Contains function definitions, do not instantiate
   }

   @FunctionalInterface
   public interface MutableFunction<V, T> extends Function<MutableValue<V>, T>, Serializable {}

   @FunctionalInterface
   public interface MutableBiFunction<V, T> extends BiFunction<V, MutableValue<V>, T>, Serializable {}

   @FunctionalInterface
   public interface ImmutableFunction<K, V, T> extends Function<Pair<K, V>, T>, Serializable  {}

   @FunctionalInterface
   public interface ImmutableBiFunction<K, V, T1, T2> extends BiFunction<Pair<K, V>, T1, T2>, Serializable  {}

}
