package org.infinispan.server.hotrod.configuration;

public class EventsConfiguration {
   private final long batchInterval;
   private final int batchMaxElements;

   public EventsConfiguration(long batchInterval, int batchMaxElements) {
      this.batchInterval = batchInterval;
      this.batchMaxElements = batchMaxElements;
   }

   public long batchInterval() {
      return batchInterval;
   }

   public int batchMaxElements() {
      return batchMaxElements;
   }

   @Override
   public String toString() {
      return "EventsConfiguration{" +
            "batchInterval=" + batchInterval +
            ", batchMaxElements=" + batchMaxElements +
            '}';
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      EventsConfiguration that = (EventsConfiguration) o;

      if (batchInterval != that.batchInterval) return false;
      if (batchMaxElements != that.batchMaxElements) return false;

      return true;
   }

   @Override
   public int hashCode() {
      int result = (int) (batchInterval ^ (batchInterval >>> 32));
      result = 31 * result + batchMaxElements;
      return result;
   }
}
