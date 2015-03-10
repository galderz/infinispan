package org.infinispan.commons.api.functional;

import java.io.Serializable;
import java.util.function.BiFunction;
import java.util.function.Function;

public class Functions {

   private Functions() {
      // Contains function definitions, do not instantiate
   }

   @FunctionalInterface
   public interface ValueFunction<V, T> extends Function<Value<V>, T>, Serializable {}

   @FunctionalInterface
   public interface ValueBiFunction<V, T> extends BiFunction<V, Value<V>, T>, Serializable {}

   @FunctionalInterface
   public interface PairFunction<K, V, T> extends Function<Pair<K, V>, T>, Serializable  {}

   @FunctionalInterface
   public interface PairBiFunction<K, V, T1, T2> extends BiFunction<Pair<K, V>, T1, T2>, Serializable  {}

}
