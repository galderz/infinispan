/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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
package org.infinispan.marshall.jboss;

import org.infinispan.CacheException;
import org.infinispan.atomic.AtomicHashMap;
import org.infinispan.atomic.AtomicHashMapDelta;
import org.infinispan.atomic.ClearOperation;
import org.infinispan.atomic.PutOperation;
import org.infinispan.atomic.RemoveOperation;
import org.infinispan.commands.RemoteCommandsFactory;
import org.infinispan.commands.control.LockControlCommand;
import org.infinispan.commands.control.RehashControlCommand;
import org.infinispan.commands.control.StateTransferControlCommand;
import org.infinispan.commands.read.GetKeyValueCommand;
import org.infinispan.commands.remote.ClusteredGetCommand;
import org.infinispan.commands.remote.MultipleRpcCommand;
import org.infinispan.commands.remote.SingleRpcCommand;
import org.infinispan.commands.tx.CommitCommand;
import org.infinispan.commands.tx.PrepareCommand;
import org.infinispan.commands.tx.RollbackCommand;
import org.infinispan.commands.write.ClearCommand;
import org.infinispan.commands.write.InvalidateCommand;
import org.infinispan.commands.write.InvalidateL1Command;
import org.infinispan.commands.write.PutKeyValueCommand;
import org.infinispan.commands.write.PutMapCommand;
import org.infinispan.commands.write.RemoveCommand;
import org.infinispan.commands.write.ReplaceCommand;
import org.infinispan.container.entries.ImmortalCacheEntry;
import org.infinispan.container.entries.ImmortalCacheValue;
import org.infinispan.container.entries.MortalCacheEntry;
import org.infinispan.container.entries.MortalCacheValue;
import org.infinispan.container.entries.TransientCacheEntry;
import org.infinispan.container.entries.TransientCacheValue;
import org.infinispan.container.entries.TransientMortalCacheEntry;
import org.infinispan.container.entries.TransientMortalCacheValue;
import org.infinispan.distribution.DefaultConsistentHash;
import org.infinispan.distribution.UnionConsistentHash;
import org.infinispan.loaders.bucket.Bucket;
import org.infinispan.marshall.Externalizer;
import org.infinispan.marshall.Marshallable;
import org.infinispan.marshall.MarshalledValue;
import org.infinispan.marshall.exts.ArrayListExternalizer;
import org.infinispan.marshall.exts.LinkedListExternalizer;
import org.infinispan.marshall.exts.MapExternalizer;
import org.infinispan.marshall.exts.ReplicableCommandExternalizer;
import org.infinispan.marshall.exts.SetExternalizer;
import org.infinispan.marshall.exts.SingletonListExternalizer;
import org.infinispan.remoting.responses.ExceptionResponse;
import org.infinispan.remoting.responses.ExtendedResponse;
import org.infinispan.remoting.responses.RequestIgnoredResponse;
import org.infinispan.remoting.responses.SuccessfulResponse;
import org.infinispan.remoting.responses.UnsuccessfulResponse;
import org.infinispan.remoting.responses.UnsureResponse;
import org.infinispan.remoting.transport.jgroups.JGroupsAddress;
import org.infinispan.transaction.xa.DeadlockDetectingGlobalTransaction;
import org.infinispan.transaction.xa.GlobalTransaction;
import org.infinispan.util.FastCopyHashMap;
import org.infinispan.util.ReflectionUtil;
import org.infinispan.util.Util;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;
import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.ObjectTable;
import org.jboss.marshalling.Unmarshaller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Constant ObjectTable that marshalls constant instances regardless of whether these are generic objects such as
 * UnsuccessfulResponse.INSTANCE, or home grown Externalizer implementations. In both cases, this is a hugely efficient
 * way of sending around constant singleton objects.
 *
 * @author Galder Zamarreño
 * @since 4.0
 */
public class ConstantObjectTable implements ObjectTable {
   private static final Log log = LogFactory.getLog(ConstantObjectTable.class);
   private static final Map<String, String> JDK_EXTERNALIZERS = new HashMap<String, String>();
   static final Set<String> MARSHALLABLES = new HashSet<String>();

   static {
      JDK_EXTERNALIZERS.put(ArrayList.class.getName(), ArrayListExternalizer.class.getName());
      JDK_EXTERNALIZERS.put(LinkedList.class.getName(), LinkedListExternalizer.class.getName());
      JDK_EXTERNALIZERS.put(HashMap.class.getName(), MapExternalizer.class.getName());
      JDK_EXTERNALIZERS.put(TreeMap.class.getName(), MapExternalizer.class.getName());
      JDK_EXTERNALIZERS.put(HashSet.class.getName(), SetExternalizer.class.getName());
      JDK_EXTERNALIZERS.put(TreeSet.class.getName(), SetExternalizer.class.getName());
      JDK_EXTERNALIZERS.put("java.util.Collections$SingletonList", SingletonListExternalizer.class.getName());

      MARSHALLABLES.add(GlobalTransaction.class.getName());
      MARSHALLABLES.add(DeadlockDetectingGlobalTransaction.class.getName());
      MARSHALLABLES.add(JGroupsAddress.class.getName());
      MARSHALLABLES.add("org.infinispan.util.Immutables$ImmutableMapWrapper");
      MARSHALLABLES.add(MarshalledValue.class.getName());
      MARSHALLABLES.add(FastCopyHashMap.class.getName());

      MARSHALLABLES.add("org.infinispan.transaction.TransactionLog$LogEntry");
      MARSHALLABLES.add(ExtendedResponse.class.getName());
      MARSHALLABLES.add(SuccessfulResponse.class.getName());
      MARSHALLABLES.add(ExceptionResponse.class.getName());
      MARSHALLABLES.add(RequestIgnoredResponse.class.getName());
      MARSHALLABLES.add(UnsuccessfulResponse.class.getName());
      MARSHALLABLES.add(UnsureResponse.class.getName());      

      MARSHALLABLES.add(StateTransferControlCommand.class.getName());
      MARSHALLABLES.add(ClusteredGetCommand.class.getName());
      MARSHALLABLES.add(MultipleRpcCommand.class.getName());
      MARSHALLABLES.add(SingleRpcCommand.class.getName());
      MARSHALLABLES.add(GetKeyValueCommand.class.getName());
      MARSHALLABLES.add(PutKeyValueCommand.class.getName());
      MARSHALLABLES.add(RemoveCommand.class.getName());
      MARSHALLABLES.add(InvalidateCommand.class.getName());
      MARSHALLABLES.add(ReplaceCommand.class.getName());
      MARSHALLABLES.add(ClearCommand.class.getName());
      MARSHALLABLES.add(PutMapCommand.class.getName());
      MARSHALLABLES.add(PrepareCommand.class.getName());
      MARSHALLABLES.add(CommitCommand.class.getName());
      MARSHALLABLES.add(RollbackCommand.class.getName());
      MARSHALLABLES.add(InvalidateL1Command.class.getName());
      MARSHALLABLES.add(LockControlCommand.class.getName());
      MARSHALLABLES.add(RehashControlCommand.class.getName());

      MARSHALLABLES.add(ImmortalCacheEntry.class.getName());
      MARSHALLABLES.add(MortalCacheEntry.class.getName());
      MARSHALLABLES.add(TransientCacheEntry.class.getName());
      MARSHALLABLES.add(TransientMortalCacheEntry.class.getName());
      MARSHALLABLES.add(ImmortalCacheValue.class.getName());
      MARSHALLABLES.add(MortalCacheValue.class.getName());
      MARSHALLABLES.add(TransientCacheValue.class.getName());
      MARSHALLABLES.add(TransientMortalCacheValue.class.getName());

      MARSHALLABLES.add(AtomicHashMap.class.getName());
      MARSHALLABLES.add(Bucket.class.getName());
      MARSHALLABLES.add("org.infinispan.tree.NodeKey");
      MARSHALLABLES.add("org.infinispan.tree.Fqn");
      MARSHALLABLES.add(AtomicHashMapDelta.class.getName());
      MARSHALLABLES.add(PutOperation.class.getName());
      MARSHALLABLES.add(RemoveOperation.class.getName());
      MARSHALLABLES.add(ClearOperation.class.getName());
      MARSHALLABLES.add(DefaultConsistentHash.class.getName());
      MARSHALLABLES.add(UnionConsistentHash.class.getName());
   }

   /**
    * Contains mapping of classes to their corresponding Externalizer classes via ExternalizerAdapter instances.
    */
   private final Map<Class<?>, ExternalizerAdapter> writers = new IdentityHashMap<Class<?>, ExternalizerAdapter>();

   /**
    * Contains mapping of ids to their corresponding Externalizer classes via ExternalizerAdapter instances.
    */
   private final Map<Integer, ExternalizerAdapter> readers = new HashMap<Integer, ExternalizerAdapter>();

   public void start(RemoteCommandsFactory cmdFactory, org.infinispan.marshall.Marshaller ispnMarshaller) {
      HashSet<Integer> ids = new HashSet<Integer>();

      for (Map.Entry<String, String> entry : JDK_EXTERNALIZERS.entrySet()) {
         try {
            Class clazz = Util.loadClass(entry.getKey());
            Externalizer ext = null;
            try {
               ext = (Externalizer) Util.getInstance(entry.getValue());
            } catch (Exception e) {
               throw new CacheException("Could not instantiate entry: " + entry, e);
            }
            Marshallable marshallable = ReflectionUtil.getAnnotation(ext.getClass(), Marshallable.class);
            int id = marshallable.id();
            ids.add(id);
            ExternalizerAdapter adapter = new ExternalizerAdapter(id, ext);
            writers.put(clazz, adapter);
            readers.put(id, adapter);
         } catch (ClassNotFoundException e) {
            if (log.isDebugEnabled())
               log.debug("Unable to load class {0}", e.getMessage());
         }
      }

      for (String marshallableClass : MARSHALLABLES) {
         try {
            Class clazz = Util.loadClass(marshallableClass);
            Marshallable marshallable = ReflectionUtil.getAnnotation(clazz, Marshallable.class);
            if (marshallable != null && !marshallable.externalizer().equals(Externalizer.class)) {
               int id = marshallable.id();
               Externalizer ext = null;
               try {
                  ext = Util.getInstance(marshallable.externalizer());
               } catch (Exception e) {
                  throw new CacheException("Could not instantiate the externalizer: " + marshallable.externalizer(), e);
               }
               if (!ids.add(id))
                  throw new CacheException("Duplicate id found! id=" + id + " in " + ext.getClass().getName() + " is shared by another marshallable class.");
               if (ext instanceof ReplicableCommandExternalizer) {
                  ((ReplicableCommandExternalizer) ext).inject(cmdFactory);
               }
               if (ext instanceof MarshalledValue.Externalizer) {
                  ((MarshalledValue.Externalizer) ext).inject(ispnMarshaller);
               }

               ExternalizerAdapter adapter = new ExternalizerAdapter(id, ext);
               writers.put(clazz, adapter);
               readers.put(id, adapter);
            }
         } catch (ClassNotFoundException e) {
            if (!marshallableClass.startsWith("org.infinispan")) {
               if (log.isDebugEnabled()) log.debug("Unable to load class {0}", e.getMessage());
            }
         }
      }
   }

   public void stop() {
      writers.clear();
      readers.clear();
   }

   public Writer getObjectWriter(Object o) throws IOException {
      return writers.get(o.getClass());
   }

   public Object readObject(Unmarshaller input) throws IOException, ClassNotFoundException {
      ExternalizerAdapter adapter = readers.get(input.readUnsignedByte());
      return adapter.readObject(input);
   }

   static class ExternalizerAdapter implements Writer {
      final int id;
      final Externalizer externalizer;

      ExternalizerAdapter(int id, Externalizer externalizer) {
         this.id = id;
         this.externalizer = externalizer;
      }

      public Object readObject(Unmarshaller input) throws IOException, ClassNotFoundException {
         return externalizer.readObject(input);
      }

      public void writeObject(Marshaller output, Object object) throws IOException {
         output.write(id);
         externalizer.writeObject(output, object);
      }
   }
}
