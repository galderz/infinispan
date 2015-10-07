package org.infinispan.functional.impl;

import org.infinispan.AdvancedCache;
import org.infinispan.batch.BatchContainer;
import org.infinispan.commons.CacheException;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.context.InvocationContext;
import org.infinispan.context.InvocationContextFactory;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;

import javax.transaction.Status;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import java.util.Optional;
import java.util.function.Function;

/**
 * Helper class for doing transactional and non-transactional invocations.
 * This class contains a lot of code duplicated from CacheImpl but reworked
 * to take advantage of functional capabilities in Java 8. This class,
 * once refactored, could be usable by CacheImpl too.
 */
final class FunctionalInvocations {

   private static final Log log = LogFactory.getLog(FunctionalInvocations.class);

   static <R> R invoke(AdvancedCache<?, ?> cache, int keyCount, Optional<Object> cmdId, Function<InvocationContext, R> f) {
      Configuration config = cache.getCacheConfiguration();
      if (config.transaction().transactionMode().isTransactional())
         return invokeTx(cache, cmdId, config, f);

      // Non-transactional
      InvocationContextFactory invCtxFactory = cache.getComponentRegistry().getComponent(InvocationContextFactory.class);
      InvocationContext ctx = invCtxFactory.createInvocationContext(true, keyCount);
      ctx.setLockOwner(cmdId);
      return f.apply(ctx);
   }

   private static <R> R invokeTx(AdvancedCache<?, ?> cache, Optional<Object> cmdId, Configuration config, Function<InvocationContext, R> f) {
      Transaction ongoing = getOngoingTransaction(cache);
      InvocationContextFactory invCtxFactory = cache.getComponentRegistry().getComponent(InvocationContextFactory.class);
      if (ongoing == null && config.transaction().autoCommit()) {
         TransactionManager tm = cache.getTransactionManager();
         Transaction tx = tryBegin(cache);
         try {
            InvocationContext ctx = invCtxFactory.createInvocationContext(tx, true);
            ctx.setLockOwner(cmdId);
            return f.apply(ctx);
         } catch (Exception e) {
            try {
               tm.setRollbackOnly();
            } catch (SystemException e1) {
               log.tracef("Could not rollback", tx); //best effort
            }
            throw e;
         } finally {
            try {
               if (tm.getStatus() == Status.STATUS_ACTIVE) tm.commit();
               else tm.rollback();
            } catch (Throwable t) {
               throw new CacheException("Could not commit implicit transaction", t);
            }
         }
      }

      InvocationContext ctx = invCtxFactory.createInvocationContext(ongoing, true);
      ctx.setLockOwner(cmdId);
      return f.apply(ctx);
   }

   private static Transaction getOngoingTransaction(AdvancedCache<?, ?> cache) {
      try {
         Transaction transaction = null;
         TransactionManager tm = cache.getTransactionManager();
         Configuration config = cache.getCacheConfiguration();
         if (tm != null) {
            transaction = tm.getTransaction();
            if (transaction == null && config.invocationBatching().enabled()) {
               transaction = cache.getBatchContainer().getBatchTransaction();
            }
         }
         return transaction;
      } catch (SystemException e) {
         throw new CacheException("Unable to get transaction", e);
      }
   }

   private static Transaction tryBegin(AdvancedCache<?, ?> cache) {
      TransactionManager tm = cache.getTransactionManager();
      if (tm == null) {
         return null;
      }
      try {
         tm.begin();
         final Transaction transaction = getOngoingTransaction(cache);
         if (log.isTraceEnabled()) {
            log.tracef("Implicit transaction started! Transaction: %s", transaction);
         }
         return transaction;
      } catch (RuntimeException e) {
         throw e;
      } catch (Exception e) {
         throw new CacheException("Unable to begin implicit transaction.", e);
      }
   }

}
