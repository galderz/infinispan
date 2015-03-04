package org.infinispan.commands.functional;

import org.infinispan.cache.impl.FunEntryImpl;
import org.infinispan.commands.Visitor;
import org.infinispan.commands.write.AbstractDataWriteCommand;
import org.infinispan.commands.write.ValueMatcher;
import org.infinispan.commons.api.functional.FunEntry;
import org.infinispan.commons.api.functional.Modes;
import org.infinispan.commons.api.functional.Modes.AccessMode;
import org.infinispan.container.entries.CacheEntry;
import org.infinispan.context.Flag;
import org.infinispan.context.InvocationContext;

import java.util.Set;
import java.util.function.Function;

// Command for READ_WRITE and WRITE_ONLY commands
public class EvalKeyWriteCommand<V, T> extends AbstractDataWriteCommand {

   public static final byte COMMAND_ID = 47;

   private Function<FunEntry<V>, T> f;
   private AccessMode accessMode;
   private ValueMatcher valueMatcher;
   boolean successful = true;

   public EvalKeyWriteCommand() {
   }

   public EvalKeyWriteCommand(Object key, Function<FunEntry<V>, T> f, AccessMode accessMode) {
      super(key, null);
      this.f = f;
      this.accessMode = accessMode;
      this.valueMatcher = accessMode == AccessMode.WRITE_ONLY
            ? ValueMatcher.MATCH_ALWAYS : ValueMatcher.MATCH_EXPECTED;
   }

   public void setParameters(int commandId, Object[] parameters) {
      // No-op
   }

   @Override
   public Object perform(InvocationContext ctx) throws Throwable {
      // It's not worth looking up the entry if we're never going to apply the change.
      if (valueMatcher == ValueMatcher.MATCH_NEVER) {
         successful = false;
         return null;
      }

      CacheEntry<Object, V> entry = ctx.lookupEntry(key);
      FunEntryImpl<Object, V> funEntry = new FunEntryImpl<>(entry);
      Object ret = f.apply(funEntry);

      return valueMatcher != ValueMatcher.MATCH_EXPECTED_OR_NEW ? ret : null;
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
   public boolean isSuccessful() {
      return successful;
   }

   @Override
   public boolean isConditional() {
      return accessMode == AccessMode.READ_WRITE;
   }

   @Override
   public ValueMatcher getValueMatcher() {
      return valueMatcher;
   }

   @Override
   public void setValueMatcher(ValueMatcher valueMatcher) {
      this.valueMatcher = valueMatcher;
   }

   @Override
   public void updateStatusFromRemoteResponse(Object remoteResponse) {
      // FIXME: Needed for eval commands?
   }

   @Override
   public Object acceptVisitor(InvocationContext ctx, Visitor visitor) throws Throwable {
      return visitor.visitEvalWriteCommand(ctx, this);
   }

}
