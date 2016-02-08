package org.infinispan.interceptors.impl;

import org.infinispan.commands.tx.CommitCommand;
import org.infinispan.commands.tx.PrepareCommand;
import org.infinispan.commands.tx.RollbackCommand;
import org.infinispan.context.impl.TxInvocationContext;
import org.infinispan.factories.annotations.Inject;
import org.infinispan.interceptors.DDSequentialInterceptor;
import org.infinispan.notifications.cachelistener.CacheNotifier;

import java.util.concurrent.CompletableFuture;

/**
 * The interceptor in charge of firing off notifications to cache listeners
 *
 * @author <a href="mailto:manik@jboss.org">Manik Surtani</a>
 * @since 9.0
 */
public class NotificationInterceptor extends DDSequentialInterceptor {
   private CacheNotifier notifier;
   private ReturnHandler transactionCompleteReturnHandler = (ctx1, command1, rv, throwable) -> {
      if (throwable != null)
         return null;

      boolean successful = !(command1 instanceof RollbackCommand);
      notifier.notifyTransactionCompleted(((TxInvocationContext) ctx1).getGlobalTransaction(), successful, ctx1);
      return null;
   };

   @Inject
   public void injectDependencies(CacheNotifier notifier) {
      this.notifier = notifier;
   }

   @Override
   public CompletableFuture<Void> visitPrepareCommand(TxInvocationContext ctx, PrepareCommand command) throws Throwable {
      if (!command.isOnePhaseCommit())
         return ctx.continueInvocation();

      return ctx.onReturn(transactionCompleteReturnHandler);
   }

   @Override
   public CompletableFuture<Void> visitCommitCommand(TxInvocationContext ctx, CommitCommand command) throws Throwable {
      return ctx.onReturn(transactionCompleteReturnHandler);
   }

   @Override
   public CompletableFuture<Void> visitRollbackCommand(TxInvocationContext ctx, RollbackCommand command) throws Throwable {
      return ctx.onReturn(transactionCompleteReturnHandler);
   }
}
