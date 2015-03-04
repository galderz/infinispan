package org.infinispan.commons.api.functional;

public class Modes {

   public static enum AccessMode implements Mode {
      READ_ONLY, READ_WRITE, WRITE_ONLY;

      public boolean isWrite() {
         return this == READ_WRITE || this == WRITE_ONLY;
      }
   }

   public static enum WaitMode implements Mode {
      BLOCKING, NON_BLOCKING
   }

}
