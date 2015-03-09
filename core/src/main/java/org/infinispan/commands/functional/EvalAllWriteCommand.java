package org.infinispan.commands.functional;

import org.infinispan.cache.impl.Values;
import org.infinispan.commands.Visitor;
import org.infinispan.commands.write.AbstractDataWriteCommand;
import org.infinispan.commands.write.ValueMatcher;
import org.infinispan.commons.api.functional.Functions.MutableBiFunction;
import org.infinispan.commons.api.functional.Mode.AccessMode;
import org.infinispan.commons.api.functional.MutableValue;
import org.infinispan.container.entries.CacheEntry;
import org.infinispan.context.InvocationContext;

// Command for READ_WRITE and WRITE_ONLY commands
public class EvalAllWriteCommand<V, T> extends AbstractDataWriteCommand {

   public static final byte COMMAND_ID = 48;

   private MutableBiFunction<V, T> f;
   private AccessMode accessMode;
   private ValueMatcher valueMatcher;
   boolean successful = true;
   private V value;

   public EvalAllWriteCommand() {
   }

   public EvalAllWriteCommand(Object key, V value, AccessMode accessMode, MutableBiFunction<V, T> f) {
      super(key, null);
      this.f = f;
      this.accessMode = accessMode;
      this.valueMatcher = accessMode == AccessMode.WRITE_ONLY
            ? ValueMatcher.MATCH_ALWAYS : ValueMatcher.MATCH_EXPECTED;
      this.value = value;
   }

   @Override
   public Object perform(InvocationContext ctx) throws Throwable {
      // It's not worth looking up the entry if we're never going to apply the change.
      if (valueMatcher == ValueMatcher.MATCH_NEVER) {
         successful = false;
         return null;
      }

      CacheEntry<Object, V> entry = ctx.lookupEntry(key);
      if (entry == null) return null;

      MutableValue<V> funEntry = Values.of(entry);
      Object ret = f.apply(value, funEntry);
      return valueMatcher != ValueMatcher.MATCH_EXPECTED_OR_NEW ? ret : null;
   }

   @Override
   public byte getCommandId() {
      return COMMAND_ID;
   }

   @Override
   public Object[] getParameters() {
      return new Object[]{key, f, valueMatcher, value};
   }

   public void setParameters(int commandId, Object[] parameters) {
      key = parameters[0];
      f = (MutableBiFunction<V, T>) parameters[1];
      valueMatcher = (ValueMatcher) parameters[2];
      value = (V) parameters[3];
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
      return visitor.visitEvalAllWriteCommand(ctx, this);
   }

}
