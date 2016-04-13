package org.infinispan.context;

import org.infinispan.factories.scopes.Scope;
import org.infinispan.factories.scopes.Scopes;

/**
 * Manages the association between an {@link org.infinispan.context.InvocationContext} and the calling thread.
 *
 * @author Manik Surtani (manik AT infinispan DOT org)
 * @author Mircea.Markus@jboss.com
 * @since 4.0
 * @deprecated Will be removed once AdvancedCache.with() has been removed
 */
@Scope(Scopes.GLOBAL)
@Deprecated
public interface InvocationContextContainer {

   ClassLoader getClassloaderContext();

   /**
    * Associate the classloader parameter with the calling thread.
    */
   void setThreadLocal(ClassLoader classloaderContext);

   /**
    * Remove the stored classloader from the calling thread.
    *
    * Must be called as each thread exists the interceptor chain.
    */
   void clearThreadLocal();
}
