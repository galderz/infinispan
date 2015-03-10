package org.infinispan.commons.api.functional;

import org.infinispan.commons.api.functional.Functions.ValueFunction;
import org.infinispan.commons.api.functional.Functions.ValueBiFunction;
import org.infinispan.commons.api.functional.Functions.PairFunction;
import org.infinispan.commons.api.functional.Functions.PairBiFunction;
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

   /**
    * Evaluates a function against a given key. The function takes a Value
    * instance as parameter, which is a place-holder for the current value
    * associated with that key in the cache. The function then returns an
    * instance of a type defined for the user.
    *
    * With this method, a very big subset of operations can be implemented.
    * For example: Map's get/put/containsKey/remove, ConcurrentMap's
    * putIfAbsent/replace/remove, JCache's getAndPut... and many others.
    *
    * AccessMode is an crucial parameter which provides a hint on what the
    * function will be doing. For example, if access mode is read only,
    * then it means that we don't need to acquire locks on the key. If the
    * access mode is write only, then we don't need to retrieve the current value
    * associated with the key.
    *
    * If the function does not return any value, the method should expect a `Void`
    * return instead of `void`. This avoids the need to overload this method to
    * take a Consumer lambda. This is an unfortunate side effect of Java's design
    * decision not to treat `void` as an Object.
    */
   <T> CompletableFuture<T> eval(K key, AccessMode mode,
         ValueFunction<? super V, ? extends T> f);

   /**
    * Evaluates a function against a collection of key/value pairs. The
    * function is applied for each key in the collection, and the function
    * takes the value passed in associated with the key, and a place-holder for
    * for the current value associated with that key in the cache.
    *
    * The function signature has been designed in such way into order to make
    * sure that it takes the value to be associated with that cache entry from
    * the map passed in. Alternative designs could involve passing in the key
    * and the function itself looking up the entry in the map, but doing so
    * means that each function references the full map and hence it'd bloat the
    * function considerably when trying to serialize it.
    *
    * It is Ok for single key functions to reference an external value because
    * it's only one entry involved, but when you have multiple entries, each
    * function should only care about the value it needs, it doesn't need to
    * know about all values.
    *
    * This method can be used to implement both Map's putAll and, with a
    * little bit of tweaking, JCache's getAll.
    *
    * The reason why this method is so important, instead of letting the user
    * iterate and call eval(K) is because internal components can decide how
    * the execution should be done and do any necessary splitting to be as
    * efficient as possible.
    */
   <T> Map<K, CompletableFuture<T>> evalAll(Map<? extends K, ? extends V> iter, AccessMode mode,
         ValueBiFunction<? super V, ? extends T> f);

   /**
    * Clears the cache, an operation that works on the cache as a whole.
    * It could be implemented via `evalAll`, but implementations often have
    * a way to execute it in a much more efficient way than iterating over
    * each entry, hence keep it as a separate method.
    */
   CompletableFuture<Void> clearAll();

   // IMPORTANT:
   // FunCache should not expose Stream instances externally because Stream methods
   // are designed for collections that are not concurrently updated. Instead,
   // we should select those functions that are most relevant for the end-user
   // use cases. This is exact same reason why ConcurrentHashMap does not expose
   // any Streams directly.

   /**
    * Search function, return the first available non-null result of applying
    * a given function on each element; skipping further search when a result
    * is found.
    *
    * The search mode defines the scope of the search, whether keys only,
    * values only, or both. If keys only are searched, value parameter is always null.
    * If values only searched, keys is null.
    *
    * To avoid bloating the API with Optionals, and avoid the need to overload for
    * different search types, Optional is reserved only for the return parameter,
    * when the user can type safely determine whether the search found something
    * or not.
    */
   <T> CompletableFuture<Optional<T>> search(StreamMode mode,
         PairFunction<? super K, ? super V, ? extends T> f);

   /**
    * Apply a fold function on all entries (e.g. can be used to calculate size).
    * It differs with search in that the fold function gets applied to all
    * elements in the cache, whereas search stops the moment the function returns
    * non-null.
    */
   <T> CompletableFuture<T> fold(StreamMode mode, T z,
         PairBiFunction<? super K, ? super V, ? super T, ? extends T> f);

}
