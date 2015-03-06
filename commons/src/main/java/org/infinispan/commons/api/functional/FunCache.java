package org.infinispan.commons.api.functional;

import org.infinispan.commons.api.functional.Mode.AccessMode;
import org.infinispan.commons.api.functional.Mode.StreamMode;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

// Name TBD, package TBD
public interface FunCache<K, V> {

   // FIXME: Consider passing in Modes similar to Flags in AdvancedCache, e.g. withModes()...
   // FIXME: Add BLOCKING mode for Map/ConcurrentMap/Infinispan sync operations

   // FIXME: Consider having a two methods instead of one: `apply` that takes Function, and `accept` that takes Consumer

   // FIXME: Use super/extends in functiona parameters/returns

   <T> CompletableFuture<T> eval(K key, AccessMode mode, CacheFunction<V, T> f);

   // IMPORTANT:
   // FunCache should not expose Stream instances externally because Stream methods
   // are designed for collections that are not concurrently updated. Instead,
   // we should select those functions that are most relevant for the end-user
   // use cases. This is exact same reason why ConcurrentHashMap does not expose
   // any Streams directly.

   // Search function, return the first available non-null result of applying
   // a given function on each element; skipping further search when a result
   // is found.
   //
   // The search mode defines the scope of the search, whether keys only,
   // values only, or both. If keys only are searched, value parameter is always null.
   // If values only searched, keys is null.
   //
   // To avoid bloating the API with Optionals, and avoid the need to overload for
   // different search types, Optional is reserved only for the return parameter,
   // when the user can type safely determine whether the search found something
   // or not.
   <T> CompletableFuture<Optional<T>> search(StreamMode mode,
         Function<Pair<? super K, ? super V>, ? extends T> f);

   //<T> CompletableFuture<Optional<? extends T>> searchValues(Function<? super V, Optional<? extends T>> f);

   // Apply a fold function on all entries (e.g. can be used to calculate size).
   // It differs with search in that the fold function gets applied to all
   // elements in the cache, whereas search stops the moment the function returns
   // non-null.
   <T> CompletableFuture<T> fold(StreamMode mode, T z,
         BiFunction<Pair<? super K, ? super V>, ? super T, ? extends T> f);

}
