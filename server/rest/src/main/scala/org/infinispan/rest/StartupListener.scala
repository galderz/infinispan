package org.infinispan.rest


import javax.servlet.{ServletContextListener, ServletContextEvent}
import org.infinispan.manager.DefaultCacheManager


/**
 * To init the cache manager. Nice to do this on startup as any config problems will be picked up before any
 * requests are attempted to be serviced. Less kitten carnage.
 */
class StartupListener extends ServletContextListener {
  val INFINISPAN_CONF = "infinispan.server.rest.cfg"
  def contextInitialized(ev: ServletContextEvent) = {

    ManagerInstance.instance = makeCacheManager(ev)
    ManagerInstance.instance.start
  }
  def contextDestroyed(ev: ServletContextEvent) =  ManagerInstance.instance.stop

  /** Prefer the system property, but also allow the servlet context to set the path to the config */
  def makeCacheManager(ev: ServletContextEvent) = 
    (System.getProperty(INFINISPAN_CONF), ev.getServletContext.getAttribute(INFINISPAN_CONF)) match {
      case (s: String, null) => new DefaultCacheManager(s)
      case (null, s: String) => new DefaultCacheManager(s)
      case _ => new DefaultCacheManager //fall back to LOCAL mode
    }

}