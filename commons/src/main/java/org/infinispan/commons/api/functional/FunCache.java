package org.infinispan.commons.api.functional;

import org.infinispan.commons.api.functional.Modes.AccessMode;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

// Name TBD, package TBD
public interface FunCache<K, V> {

   // FIXME: Consider passing in Modes similar to Flags in AdvancedCache

   // FIXME: Consider having a two methods instead of one: `apply` that takes Function, and `accept` that takes Consumer

   <T> CompletableFuture<T> eval(K key, AccessMode mode, Function<FunEntry<V>, T> f);

}
