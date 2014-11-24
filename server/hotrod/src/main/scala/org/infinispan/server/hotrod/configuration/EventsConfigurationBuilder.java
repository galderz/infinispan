package org.infinispan.server.hotrod.configuration;

import org.infinispan.commons.configuration.Builder;

public class EventsConfigurationBuilder extends AbstractHotRodServerChildConfigurationBuilder implements Builder<EventsConfiguration> {

   private long batchInterval = 1000;
   private int batchMaxElements = 1000;

   protected EventsConfigurationBuilder(HotRodServerChildConfigurationBuilder builder) {
      super(builder);
   }

   public EventsConfigurationBuilder batchInterval(long batchInterval) {
      this.batchInterval = batchInterval;
      return this;
   }

   public EventsConfigurationBuilder batchMaxElements(int batchMaxElements) {
      this.batchMaxElements = batchMaxElements;
      return this;
   }

   @Override
   public void validate() {
      // No-op
   }

   @Override
   public EventsConfiguration create() {
      return new EventsConfiguration(batchInterval, batchMaxElements);
   }

   @Override
   public Builder<?> read(EventsConfiguration template) {
      batchInterval = template.batchInterval();
      batchMaxElements = template.batchMaxElements();
      return this;
   }

}
