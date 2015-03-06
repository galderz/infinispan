package org.infinispan.commons.api.functional;

import java.util.Optional;

public interface Pair<K, V> {

   Optional<K> key();

   Optional<V> value();

}
