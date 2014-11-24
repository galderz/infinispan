package org.infinispan.server.endpoint.subsystem;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ReloadRequiredRemoveStepHandler;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelType;

public class EventsResource extends SimpleResourceDefinition {
   private static final PathElement EVENTS_PATH = PathElement.pathElement(ModelKeys.EVENTS, ModelKeys.EVENTS_NAME);

   static final SimpleAttributeDefinition BATCH_INTERVAL =
         new SimpleAttributeDefinitionBuilder(ModelKeys.BATCH_INTERVAL, ModelType.LONG, false)
               .setAllowExpression(true)
               .setXmlName(ModelKeys.BATCH_INTERVAL)
               .setRestartAllServices()
               .build();

   static final SimpleAttributeDefinition BATCH_MAX_ELEMENTS =
         new SimpleAttributeDefinitionBuilder(ModelKeys.BATCH_MAX_ELEMENTS, ModelType.INT, false)
               .setAllowExpression(true)
               .setXmlName(ModelKeys.BATCH_MAX_ELEMENTS)
               .setRestartAllServices()
               .build();

   static final SimpleAttributeDefinition[] EVENTS_ATTRIBUTES = { BATCH_INTERVAL, BATCH_MAX_ELEMENTS };

   public EventsResource() {
      super(EVENTS_PATH, EndpointExtension.getResourceDescriptionResolver(ModelKeys.EVENTS), EventsAdd.INSTANCE, ReloadRequiredRemoveStepHandler.INSTANCE);
   }

   @Override
   public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
      super.registerAttributes(resourceRegistration);

      final OperationStepHandler writeHandler = new ReloadRequiredWriteAttributeHandler(EVENTS_ATTRIBUTES);
      for (AttributeDefinition attr : EVENTS_ATTRIBUTES) {
         resourceRegistration.registerReadWriteAttribute(attr, null, writeHandler);
      }
   }

}
