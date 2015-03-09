package org.infinispan.commons.api.functional;

import org.infinispan.commons.api.functional.Functions.MutableFunction;
import org.infinispan.commons.api.functional.Functions.MutableBiFunction;
import org.infinispan.commons.api.functional.Functions.ImmutableFunction;
import org.infinispan.commons.api.functional.Functions.ImmutableBiFunction;
import org.infinispan.commons.api.functional.Mode.AccessMode;
import org.infinispan.commons.api.functional.Mode.StreamMode;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

// Name TBD, package TBD
public interface FunCache<K, V> {

   // FIXME: Consider passing in Modes similar to Flags in AdvancedCache, e.g. withModes()...
   // FIXME: Add BLOCKING mode for Map/ConcurrentMap/Infinispan sync operations

   // FIXME: Consider having a two methods instead of one: `apply` that takes Function, and `accept` that takes Consumer

   <T> CompletableFuture<T> eval(K key, AccessMode mode,
         MutableFunction<? super V, ? extends T> f);

   // Generic method for evaluation a function over a number of elements.
   // In the design of this method, it was very important to make sure that
   // the function takes the value to be associated with that cache entry from
   // the map passed in. Alternative solutions could involve passing in the
   // key and the function itself looking up the entry in the map, but doing so
   // means that each function references the full map and hence it'd bloat the
   // function considerably when trying to serialize it.
   //
   // It is Ok for single key functions to reference an external value because
   // it's only one entry involved, but when you have multiple entries, each
   // function should only care about the value it needs, it doesn't need to
   // know about all values.
   //
   // This method can be used to implement both Map's putAll and, with a
   // little bit of tweaking, JCache's getAll.
   //
   // The reason why this method is so important, instead of letting the user
   // iterate and call eval(K) is because internal components can decide how
   // the execution should be done and do any necessary splitting to be as
   // efficient as possible.
   <T> Map<K, CompletableFuture<T>> evalAll(Map<? extends K, ? extends V> iter, AccessMode mode,
         MutableBiFunction<? super V, ? extends T> f);

   // Clear the cache, a particular operation
   CompletableFuture<Void> clearAll();

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
         ImmutableFunction<? super K, ? super V, ? extends T> f);

   // Apply a fold function on all entries (e.g. can be used to calculate size).
   // It differs with search in that the fold function gets applied to all
   // elements in the cache, whereas search stops the moment the function returns
   // non-null.
   <T> CompletableFuture<T> fold(StreamMode mode, T z,
         ImmutableBiFunction<? super K, ? super V, ? super T, ? extends T> f);

}
