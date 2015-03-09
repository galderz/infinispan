package org.infinispan.commands.functional;

import org.infinispan.cache.impl.Values;
import org.infinispan.commands.Visitor;
import org.infinispan.commands.read.AbstractDataCommand;
import org.infinispan.commands.read.RemoteFetchingCommand;
import org.infinispan.commons.api.functional.Functions.MutableFunction;
import org.infinispan.commons.api.functional.MutableValue;
import org.infinispan.container.entries.CacheEntry;
import org.infinispan.container.entries.InternalCacheEntry;
import org.infinispan.context.InvocationContext;

// Command for READ_ONLY commands
public class EvalKeyReadOnlyCommand<V, T> extends AbstractDataCommand implements RemoteFetchingCommand {

   public static final byte COMMAND_ID = 46;

   private MutableFunction<V, T> f;
   private InternalCacheEntry remotelyFetchedValue;

   public EvalKeyReadOnlyCommand(Object key, MutableFunction<V, T> f) {
      super(key, null);
      this.f = f;
   }

   public EvalKeyReadOnlyCommand() {
   }

   @Override
   public void setParameters(int commandId, Object[] parameters) {
      // No-op
   }

   @Override
   public Object perform(InvocationContext ctx) throws Throwable {
      CacheEntry<Object, V> entry = ctx.lookupEntry(key);
      return perform(entry);
   }

   public Object perform(CacheEntry<Object, V> entry) {
      MutableValue<V> funEntry = Values.of(entry);
      return f.apply(funEntry);
   }

   @Override
   public byte getCommandId() {
      return COMMAND_ID;
   }

   @Override
   public Object[] getParameters() {
      return new Object[0];
   }

   @Override
   public Object acceptVisitor(InvocationContext ctx, Visitor visitor) throws Throwable {
      return visitor.visitEvalKeyReadOnlyCommand(ctx, this);
   }

   @Override
   public String toString() {
      return "EvalKeyReadOnlyCommand{" +
            "f=" + f +
            '}';
   }

   @Override
   public void setRemotelyFetchedValue(InternalCacheEntry remotelyFetchedValue) {
      this.remotelyFetchedValue = remotelyFetchedValue;
   }

   @Override
   public InternalCacheEntry getRemotelyFetchedValue() {
      return remotelyFetchedValue;
   }
}
