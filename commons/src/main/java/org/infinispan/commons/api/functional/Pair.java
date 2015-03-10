package org.infinispan.commons.api.functional;

import java.util.Optional;

/**
 * Represents an immutable key/value pair.
 *
 * This class has been designed for use with functional cache stream-like methods.
 */
public interface Pair<K, V> {

   /**
    * Optional key.
    * It'll be empty when only iterating over cached values.
    * It'll be non-empty when iterating over keys or both keys and values.
    */
   Optional<K> key();

   /**
    * Optional value.
    * It'll be empty when only iterating over cached keys.
    * It'll be non-empty when iterating over values or both keys and values.
    */
   Optional<V> value();

}
