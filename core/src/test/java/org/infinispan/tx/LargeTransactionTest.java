package org.infinispan.tx;

import org.infinispan.Cache;
import org.infinispan.config.Configuration;
import org.infinispan.manager.CacheManager;
import org.infinispan.test.MultipleCacheManagersTest;
import org.infinispan.test.TestingUtil;
import org.infinispan.test.fwk.TestCacheManagerFactory;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;
import org.testng.annotations.Test;

import javax.transaction.TransactionManager;

/**
 * Test for: https://jira.jboss.org/jira/browse/ISPN-149.
 *
 * @author Mircea.Markus@jboss.com
 */
@Test(testName = "tx.LargeTransactionTest", groups = "functional")
public class LargeTransactionTest extends MultipleCacheManagersTest {
   private static Log log = LogFactory.getLog(LargeTransactionTest.class);

   protected void createCacheManagers() throws Throwable {

      Configuration c = new Configuration();
      c.setInvocationBatchingEnabled(true);
      c.setCacheMode(Configuration.CacheMode.REPL_SYNC);
      c.setSyncReplTimeout(30000);
      c.setLockAcquisitionTimeout(60000);
      c.setSyncCommitPhase(true);
      c.setSyncRollbackPhase(true);
      c.setUseLockStriping(false);

      CacheManager manager = TestCacheManagerFactory.createClusteredCacheManager(c);
      manager.start();
      manager.getCache();
      registerCacheManager(manager);
      Cache cache1 = manager.getCache("TestCache");
      assert cache1.getConfiguration().getCacheMode().equals(Configuration.CacheMode.REPL_SYNC);
      cache1.start();

      manager = TestCacheManagerFactory.createClusteredCacheManager(c);
      manager.start();
      registerCacheManager(manager);
      Cache cache2 = manager.getCache("TestCache");
      assert cache1.getConfiguration().getCacheMode().equals(Configuration.CacheMode.REPL_SYNC);
   }

   public void testLargeTx() throws Exception {
      Cache cache1 = cache(0, "TestCache");
      Cache cache2 = cache(1, "TestCache");
      TransactionManager tm = TestingUtil.getTransactionManager(cache1);
      tm.begin();
      for (int i = 0; i < 200; i++)
         cache1.put("key" + i, "value" + i);
      log.trace("___________ before commit");
      tm.commit();

      for (int i = 0; i < 200; i++) {
         assert cache2.get("key" + i).equals("value"+i);
      }
   }

   public void testSinglePutInTx() throws Exception {
      Cache cache1 = cache(0, "TestCache");
      Cache cache2 = cache(1, "TestCache");
      TransactionManager tm = TestingUtil.getTransactionManager(cache1);

      tm.begin();
      cache1.put("key", "val");
      log.trace("___________ before commit");
      tm.commit();

      assert cache2.get("key").equals("val");
   }

   public void testSimplePutNoTx() {
      Cache cache1 = cache(0, "TestCache");
      Cache cache2 = cache(1, "TestCache");
      cache1.put("key", "val");
      assert cache2.get("key").equals("val");
   }
}
