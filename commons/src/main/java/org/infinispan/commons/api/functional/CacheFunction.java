package org.infinispan.commons.api.functional;

import java.io.Serializable;
import java.util.function.Function;

// FIXME: Can we provide a JBoss Externalizer for this?
@FunctionalInterface
public interface CacheFunction<V, T> extends Function<Value<V>, T>, Serializable {
   // Empty
}
