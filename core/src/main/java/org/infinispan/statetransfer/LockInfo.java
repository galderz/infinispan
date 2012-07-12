/*
 * Copyright 2012 Red Hat, Inc. and/or its affiliates.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA
 */

package org.infinispan.statetransfer;

import org.infinispan.marshall.AbstractExternalizer;
import org.infinispan.marshall.Ids;
import org.infinispan.transaction.xa.GlobalTransaction;
import org.infinispan.util.Util;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Set;

/**
 * Utility class used for lock transfer during topology changes.
 *
 * @author Mircea Markus
 * @since 5.1
 */
public class LockInfo {

   private final GlobalTransaction globalTransaction;

   private final Object key;

   public LockInfo(GlobalTransaction globalTransaction, Object key) {
      this.globalTransaction = globalTransaction;
      this.key = key;
   }

   public GlobalTransaction getGlobalTransaction() {
      return globalTransaction;
   }

   public Object getKey() {
      return key;
   }


   public static class Externalizer extends AbstractExternalizer<LockInfo> {

      @Override
      public Integer getId() {
         return Ids.LOCK_INFO;
      }

      @Override
      public Set<Class<? extends LockInfo>> getTypeClasses() {
         return Util.<Class<? extends LockInfo>>asSet(LockInfo.class);
      }

      @Override
      public void writeObject(ObjectOutput output, LockInfo object) throws IOException {
         output.writeObject(object.globalTransaction);
         output.writeObject(object.key);
      }

      @Override
      public LockInfo readObject(ObjectInput input) throws IOException, ClassNotFoundException {
         return new LockInfo((GlobalTransaction) input.readObject(), input.readObject());
      }
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      LockInfo lockInfo = (LockInfo) o;

      if (globalTransaction != null ? !globalTransaction.equals(lockInfo.globalTransaction) : lockInfo.globalTransaction != null)
         return false;
      if (key != null ? !key.equals(lockInfo.key) : lockInfo.key != null) return false;

      return true;
   }

   @Override
   public int hashCode() {
      int result = globalTransaction != null ? globalTransaction.hashCode() : 0;
      result = 31 * result + (key != null ? key.hashCode() : 0);
      return result;
   }

   @Override
   public String toString() {
      return "LockInfo{" +
            "globalTransaction=" + globalTransaction +
            ", key=" + key +
            '}';
   }
}
