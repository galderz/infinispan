/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.infinispan.commands.read;

import org.infinispan.commands.Visitor;
import org.infinispan.container.entries.CacheEntry;
import org.infinispan.context.InvocationContext;
import org.infinispan.marshall.Ids;
import org.infinispan.marshall.Marshallable;
import org.infinispan.marshall.exts.ReplicableCommandExternalizer;
import org.infinispan.notifications.cachelistener.CacheNotifier;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;

/**
 * Implements functionality defined by {@link org.infinispan.Cache#get(Object)} and 
 * {@link org.infinispan.Cache#containsKey(Object)} operations
 *
 * @author Manik Surtani (<a href="mailto:manik@jboss.org">manik@jboss.org</a>)
 * @since 4.0
 */
@Marshallable(externalizer = ReplicableCommandExternalizer.class, id = Ids.GET_KEY_VALUE_COMMAND)
public class GetKeyValueCommand extends AbstractDataCommand {
   public static final byte COMMAND_ID = 4;
   private static final Log log = LogFactory.getLog(GetKeyValueCommand.class);
   private static final boolean trace = log.isTraceEnabled();
   private CacheNotifier notifier;
   private boolean returnCacheEntry;

   public GetKeyValueCommand(Object key, CacheNotifier notifier) {
      this.key = key;
      this.notifier = notifier;
   }

   public GetKeyValueCommand() {
   }

   public Object acceptVisitor(InvocationContext ctx, Visitor visitor) throws Throwable {
      return visitor.visitGetKeyValueCommand(ctx, this);
   }

   /**
    * Will make this method to return an {@link CacheEntry} instead of the corresponding value associated with the key.
    */
   public void setReturnCacheEntry(boolean returnCacheEntry) {
      this.returnCacheEntry = returnCacheEntry;
   }

   public Object perform(InvocationContext ctx) throws Throwable {
      CacheEntry entry = ctx.lookupEntry(key);
      if (entry == null || entry.isNull()) {
         if (trace) log.trace("Entry not found");
         return null;
      }
      if (entry.isRemoved()) {
         if (trace) log.trace("Entry has been deleted and is of type " + entry.getClass().getSimpleName());
         return null;
      }
      notifier.notifyCacheEntryVisited(key, true, ctx);
      Object result = returnCacheEntry ? entry : entry.getValue();
      if (trace) log.trace("Found value " + result);
      notifier.notifyCacheEntryVisited(key, false, ctx);
      return result;
   }

   public byte getCommandId() {
      return COMMAND_ID;
   }
}
