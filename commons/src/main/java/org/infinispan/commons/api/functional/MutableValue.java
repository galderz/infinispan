package org.infinispan.commons.api.functional;

import java.util.Optional;

public interface MutableValue<V> {

   Optional<V> get();

   // FIXME: setting value should fire modified/created listener events?

   // Using Void returns instead of void in order to avoid, as much as possible,
   // the need to add `Consumer` overloaded methods in FunCache

   Void set(V value);

   // Instead of creating set(Optional<V>...), add a simple remove() method that
   // removes the value. This feels cleaner and less cumbersome than having to
   // always pass in Optional to set()
   Void remove();

}
