package org.infinispan.server.endpoint.subsystem;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.ServiceVerificationHandler;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;

import java.util.List;

public class EventsAdd extends AbstractAddStepHandler {
   static final EventsAdd INSTANCE = new EventsAdd(EventsResource.EVENTS_ATTRIBUTES);

   private final AttributeDefinition[] attributes;

   EventsAdd(final AttributeDefinition[] attributes) {
      this.attributes = attributes;
   }

   @Override
   protected void populateModel(ModelNode operation, ModelNode model) throws OperationFailedException {
      for (AttributeDefinition attr : attributes) {
         attr.validateAndSet(operation, model);
      }
   }

   @Override
   protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model, ServiceVerificationHandler verificationHandler,
         List<ServiceController<?>> newControllers) throws OperationFailedException {
      super.performRuntime(context, operation, model, verificationHandler, newControllers);
      // once we add a cache configuration, we need to restart all the services for the changes to take effect
      context.reloadRequired();
   }

}
