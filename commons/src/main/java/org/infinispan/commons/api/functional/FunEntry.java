package org.infinispan.commons.api.functional;

import java.util.Optional;

public interface FunEntry<V> {

   Optional<V> getValue();

   // FIXME: setting value should fire modified/created listener events?

   // Using Void returns instead of void in order to avoid, as much as possible,
   // the need to add `Consumer` overloaded methods in FunCache

   Void setValue(V value);

   Void remove();

}
