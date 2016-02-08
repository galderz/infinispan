package org.infinispan.interceptors.impl;

import org.infinispan.commands.VisitableCommand;
import org.infinispan.context.InvocationContext;
import org.infinispan.interceptors.SequentialInterceptor;

import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Invoke
 *
 * @author Dan Berindei
 * @since 9.0
 */
public class AsyncSubCommandInvoker<T> implements SequentialInterceptor.ForkReturnHandler {
   private Object returnValue;
   private final Iterator<VisitableCommand> subCommands;
   private final SequentialInterceptor.ForkReturnHandler finalReturnHandler;

   private AsyncSubCommandInvoker(Object returnValue, Iterator<VisitableCommand> subCommands,
         SequentialInterceptor.ForkReturnHandler finalReturnHandler) {
      this.returnValue = returnValue;
      this.subCommands = subCommands;
      this.finalReturnHandler = finalReturnHandler;
   }

   public static <T> CompletableFuture<Void> forEach(InvocationContext ctx, VisitableCommand command,
         Object returnValue, Stream<VisitableCommand> subCommandStream,
         SequentialInterceptor.ForkReturnHandler finalReturnHandler) throws Throwable {
      AsyncSubCommandInvoker<T> forker =
            new AsyncSubCommandInvoker<>(returnValue, subCommandStream.iterator(), finalReturnHandler);
      return forker.handle(ctx, command, null, null);
   }

   @Override
   public CompletableFuture<Void> handle(InvocationContext ctx, VisitableCommand subCommand,
         Object subReturnValue, Throwable throwable) throws Throwable {
      if (throwable != null)
         throw throwable;

      if (subCommands.hasNext()) {
         VisitableCommand newCommand = subCommands.next();
         return ctx.forkInvocation(newCommand, this);
      } else {
         return finalReturnHandler.handle(ctx, subCommand, returnValue, null);
      }
   }
}
