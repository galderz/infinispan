package org.infinispan.functional.impl;

import org.infinispan.AdvancedCache;
import org.infinispan.batch.BatchContainer;
import org.infinispan.commons.CacheException;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.context.InvocationContext;
import org.infinispan.context.InvocationContextFactory;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;

import javax.transaction.InvalidTransactionException;
import javax.transaction.Status;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import static org.infinispan.factories.KnownComponentNames.ASYNC_OPERATIONS_EXECUTOR;

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

   static <R> R invokeTx(AdvancedCache<?, ?> cache, Optional<Object> cmdId, Configuration config, Function<InvocationContext, R> f) {
      Transaction ongoing = getOngoingTransaction(cache);
      InvocationContextFactory invCtxFactory = cache.getComponentRegistry().getComponent(InvocationContextFactory.class);
      if (ongoing == null && config.transaction().autoCommit()) {
         TransactionManager tm = cache.getTransactionManager();
         Transaction tx = tryBegin(cache);
         try {
            InvocationContext ctx = invCtxFactory.createInvocationContext(tx, true);
            //ctx.setLockOwner(cmdId);
            cmdId.ifPresent(ctx::setLockOwner);
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

   static <R> CompletableFuture<R> invokeTxAsync(AdvancedCache<?, ?> cache, Optional<Object> cmdId, Configuration config, Function<InvocationContext, R> f) {
      InvocationContextFactory invCtxFactory = cache.getComponentRegistry().getComponent(InvocationContextFactory.class);
      Transaction ongoing = getOngoingTransaction(cache);
      if (ongoing == null) {
         if (config.transaction().autoCommit())
            return CompletableFuture.supplyAsync(() -> {
               R ret = invokeAutoCommitTxAsync(cache, cmdId, f);
               return ret;
            });
         else
            throw new CacheException("Ongoing transaction required when autocommit is disabled");
      }

      // Suspend transaction and resume it in async thread
      try {
         Transaction  suspendedTx = cache.getTransactionManager().suspend();
         log.tracef("Suspended transaction: %s", suspendedTx);
         ExecutorService asyncExec = cache.getComponentRegistry().getComponent(ExecutorService.class, ASYNC_OPERATIONS_EXECUTOR);
         TransactionManager tm = cache.getTransactionManager();
         WithThreadExecutor resumeExecutor = new WithThreadExecutor(Thread.currentThread());
         return CompletableFuture.supplyAsync(() -> {
            try {
               tm.resume(suspendedTx);
               log.tracef("Resumed transaction: %s", suspendedTx);
               InvocationContext ctx = invCtxFactory.createInvocationContext(ongoing, true);
               ctx.setLockOwner(cmdId);
               return f.apply(ctx);
            } catch (Exception e) {
               throw new CacheException("Unable to execute operation with resumed transaction", e);
            }
         }, asyncExec)
            .whenComplete((ret, t) -> {
               log.tracef("Current thread is: " + Thread.currentThread());
               try {
                  tm.resume(suspendedTx);
               } catch (Throwable tt) {
                  throw new CacheException(t);
               }
            })
//            .whenCompleteAsync((ret, t) -> {
//               log.tracef("Current thread is: " + Thread.currentThread());
//            }, resumeExecutor)
         ;
//            .thenApply(ret -> {
//            log.tracef("Try resume transaction on way back: %s", suspendedTx);
//            try {
//               tm.resume(suspendedTx);
//               log.tracef("Resumed transaction on way back: %s", suspendedTx);
//               return ret;
//            } catch (Exception e) {
//               throw new CacheException("Unable to resume transaction on way back", e);
//            }
//         });
      } catch (SystemException e) {
         throw new CacheException("Could not suspend transaction", e);
      }
   }

   static <R> R invokeAutoCommitTxAsync(AdvancedCache<?, ?> cache, Optional<Object> cmdId, Function<InvocationContext, R> f) {
      TransactionManager tm = cache.getTransactionManager();
      Transaction tx = tryBegin(cache);
      try {
         InvocationContextFactory invCtxFactory = cache.getComponentRegistry().getComponent(InvocationContextFactory.class);
         InvocationContext ctx = invCtxFactory.createInvocationContext(tx, true);
         //ctx.setLockOwner(cmdId);
         cmdId.ifPresent(ctx::setLockOwner);
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

   private static class WithThreadExecutor implements Executor {
      private static final Log log = LogFactory.getLog(WithThreadExecutor.class);

      final Thread t;

      private WithThreadExecutor(Thread t) {
         this.t = t;
      }

      @Override
      public void execute(Runnable command) {
         log.tracef("execute %s", command);
         try {
            Executors.newSingleThreadExecutor(r -> t).submit(command).get(5, TimeUnit.SECONDS);
         } catch (Throwable t) {
            throw new CacheException("Unable to execute runnable with thread", t);
         }
      }
   }

}
