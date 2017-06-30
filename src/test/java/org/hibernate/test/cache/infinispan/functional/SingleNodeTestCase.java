package org.hibernate.test.cache.infinispan.functional;
import javax.transaction.Status;
import javax.transaction.TransactionManager;
import org.hibernate.cache.RegionFactory;
import org.hibernate.cache.infinispan.InfinispanRegionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.Environment;
import org.hibernate.engine.transaction.spi.TransactionFactory;
import org.hibernate.service.jdbc.connections.spi.ConnectionProvider;
import org.hibernate.service.jta.platform.internal.JtaPlatformInitiator;
import org.hibernate.service.jta.platform.spi.JtaPlatform;
import org.hibernate.test.cache.infinispan.tm.JtaPlatformImpl;
import org.hibernate.testing.junit.functional.FunctionalTestCase;
<<<<<<< HEAD
import org.hibernate.transaction.CMTTransactionFactory;
import org.hibernate.transaction.TransactionFactory;
=======
import org.hibernate.engine.transaction.internal.jta.CMTTransactionFactory;
<<<<<<< HEAD
>>>>>>> HHH-5949 - Migrate, complete and integrate TransactionFactory as a service
import org.hibernate.transaction.TransactionManagerLookup;
=======
>>>>>>> HHH-5949 - Migrate, complete and integrate TransactionFactory as a service
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;

/**
 * @author Galder Zamarreño
 * @since 3.5
 */
public abstract class SingleNodeTestCase extends FunctionalTestCase {
   private static final Log log = LogFactory.getLog(SingleNodeTestCase.class);
   private final TransactionManager tm;

   public SingleNodeTestCase(String string) {
      super(string);
      tm = getTransactionManager();
   }

	protected TransactionManager getTransactionManager() {
		try {
			Class<? extends JtaPlatform> jtaPlatformClass = getJtaPlatform();
			if ( jtaPlatformClass == null ) {
				return null;
			}
			else {
				return jtaPlatformClass.newInstance().retrieveTransactionManager();
			}
		}
		catch (Exception e) {
			log.error("Error", e);
			throw new RuntimeException(e);
		}
	}

   public String[] getMappings() {
      return new String[] { 
               "cache/infinispan/functional/Item.hbm.xml", 
               "cache/infinispan/functional/Customer.hbm.xml", 
               "cache/infinispan/functional/Contact.hbm.xml"};
   }

   @Override
   public String getCacheConcurrencyStrategy() {
      return "transactional";
   }

   protected Class<? extends RegionFactory> getCacheRegionFactory() {
      return InfinispanRegionFactory.class;
   }

   protected Class<? extends TransactionFactory> getTransactionFactoryClass() {
      return CMTTransactionFactory.class;
   }

   protected Class<? extends ConnectionProvider> getConnectionProviderClass() {
      return org.hibernate.test.cache.infinispan.tm.XaConnectionProvider.class;
   }

	protected Class<? extends JtaPlatform> getJtaPlatform() {
		return JtaPlatformImpl.class;
	}

   protected boolean getUseQueryCache() {
      return true;
   }

   public void configure(Configuration cfg) {
      super.configure(cfg);
      cfg.setProperty(Environment.USE_SECOND_LEVEL_CACHE, "true");
      cfg.setProperty(Environment.GENERATE_STATISTICS, "true");
      cfg.setProperty(Environment.USE_QUERY_CACHE, String.valueOf(getUseQueryCache()));
      cfg.setProperty(Environment.CACHE_REGION_FACTORY, getCacheRegionFactory().getName());

	   if ( getJtaPlatform() != null ) {
		   cfg.getProperties().put( JtaPlatformInitiator.JTA_PLATFORM, getJtaPlatform() );
	   }
	   cfg.setProperty( Environment.TRANSACTION_STRATEGY, getTransactionFactoryClass().getName() );
	   cfg.setProperty( Environment.CONNECTION_PROVIDER, getConnectionProviderClass().getName() );
   }

   protected void beginTx() throws Exception {
      tm.begin();
   }

   protected void setRollbackOnlyTx() throws Exception {
      tm.setRollbackOnly();
   }

   protected void setRollbackOnlyTx(Exception e) throws Exception {
      log.error("Error", e);
      tm.setRollbackOnly();
      throw e;
   }

   protected void setRollbackOnlyTxExpected(Exception e) throws Exception {
      log.debug("Expected behaivour", e);
      tm.setRollbackOnly();
   }

   protected void commitOrRollbackTx() throws Exception {
      if (tm.getStatus() == Status.STATUS_ACTIVE) tm.commit();
      else tm.rollback();
   }
   
}