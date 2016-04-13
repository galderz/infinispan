package org.infinispan.context;

import org.infinispan.configuration.global.GlobalConfiguration;
import org.infinispan.factories.annotations.Inject;
import org.infinispan.factories.annotations.Stop;


/**
 * InvocationContextContainer implementation.
 *
 * @author Dan Berindei
 * @since 7.0
 */
public class InvocationContextContainerImpl implements InvocationContextContainer {

   // We need to keep the InvocationContext in a thread-local in order to support
   // AdvancedCache.with(ClassLoader). The alternative would be to change the marshalling
   // SPI to accept a ClassLoader parameter.
   private final ThreadLocal<ClassLoader> classloaderHolder = new ThreadLocal<ClassLoader>();

   private ClassLoader configuredClassLoader;

   @Inject
   public void init(GlobalConfiguration globalConfiguration) {
      configuredClassLoader = globalConfiguration.classLoader();
   }

   // As late as possible
   @Stop(priority = 999)
   public void stop() {
      // Because some thread-locals may keep a reference to the InvocationContextContainer,
      // we need to clear the reference to the classloader on stop
      configuredClassLoader = null;
   }

   @Override
   public ClassLoader getClassloaderContext() {
      return classloaderHolder.get();
   }

   @Override
   public void setThreadLocal(ClassLoader classloaderContext) {
      if (isThreadLocalRequired(classloaderContext)) {
         classloaderHolder.set(classloaderContext);
      }
   }

   @Override
   public void clearThreadLocal() {
      classloaderHolder.remove();
   }

   private boolean isThreadLocalRequired(ClassLoader classloaderContext) {
      return classloaderContext != null &&
            classloaderContext != configuredClassLoader;
   }
}
