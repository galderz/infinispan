package org.infinispan.tracing;

import org.infinispan.commands.VisitableCommand;

public class Tracer {

   public static void trace(VisitableCommand command) {
      System.out.println("[" + Thread.currentThread().getName() + "][tracer] Invoking: " + command);
   }

   public static void traceDispatch(Object obj) {
      System.out.println("[dispatcher] Sending: " + obj);
   }

}
