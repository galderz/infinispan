package org.infinispan.decorators;

import org.infinispan.commons.CacheException;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

// FIXME: Location TBD
public class Futures {

   public static <T> T await(CompletableFuture<T> cf) {
      try {
         // FIXME: Should be timed...
         return cf.get();
      } catch (InterruptedException | ExecutionException e) {
         throw new CacheException(e);
      }
   }

}