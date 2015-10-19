package org.infinispan.functional.tx;

import org.infinispan.commons.api.functional.EntryView;
import org.infinispan.commons.api.functional.EntryView.ReadEntryView;
import org.infinispan.commons.api.functional.FunctionalMap;
import org.infinispan.commons.api.functional.FunctionalMap.ReadOnlyMap;
import org.infinispan.commons.api.functional.FunctionalMap.WriteOnlyMap;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.functional.AbstractFunctionalTest;
import org.testng.annotations.Test;

import javax.transaction.Transaction;
import javax.transaction.TransactionManager;

import static org.infinispan.functional.FunctionalTestUtils.await;
import static org.infinispan.functional.FunctionalTestUtils.ro;
import static org.infinispan.functional.FunctionalTestUtils.wo;
import static org.infinispan.test.TestingUtil.withTx;
import static org.infinispan.test.fwk.TestCacheManagerFactory.getDefaultCacheConfiguration;
import static org.testng.AssertJUnit.assertEquals;

@Test(groups = "functional", testName = "functional.tx.FunctionalTxTest")
public class FunctionalExplicitTxTest extends AbstractFunctionalTest {

   @Override
   protected ConfigurationBuilder baseConfiguration() {
      ConfigurationBuilder cfg = getDefaultCacheConfiguration(true);
      cfg.transaction().autoCommit(false); // Don't allow implicit transactions
      return cfg;
   }

   TransactionManager tm() {
      return cacheManagers.get(0).<Integer, String>getCache().getAdvancedCache().getTransactionManager();
   }

   public void testMultiOpTx() throws Exception {
      WriteOnlyMap<Integer, String> woMap = wo(fmapL1);
      ReadOnlyMap<Integer, String> roMap = ro(fmapL1);
      withTx(tm(), () -> {
//            Transaction tx = tm().getTransaction();
//            try {
               return await(woMap.eval(1, view -> view.set("one"))
                  .thenCompose(ignore -> woMap.eval(2, view -> view.set("two")))
                  .thenCompose(ignore -> woMap.eval(3, view -> view.set("three"))));
//            } finally {
//               tm().resume(tx);
//            }
         }
      );
//      assertEquals(3, roMap.keys().count());
//      await(roMap.eval(1, ReadEntryView::get).thenAccept(v -> assertEquals("one", v)));
//      await(roMap.eval(2, ReadEntryView::get).thenAccept(v -> assertEquals("two", v)));
//      await(roMap.eval(3, ReadEntryView::get).thenAccept(v -> assertEquals("three", v)));
   }


}
