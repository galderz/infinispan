package org.infinispan.loaders.bdbje;

import com.sleepycat.bind.EntryBinding;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.util.RuntimeExceptionWrapper;
import org.infinispan.container.entries.InternalCacheEntry;
import org.infinispan.marshall.Marshaller;

import java.io.IOException;

class InternalCacheEntryBinding implements EntryBinding<InternalCacheEntry> {
   Marshaller m;

   InternalCacheEntryBinding(Marshaller m) {
      this.m = m;
   }

   public InternalCacheEntry entryToObject(DatabaseEntry entry) {
      try {
         return (InternalCacheEntry) m.objectFromByteBuffer(entry.getData());
      } catch (IOException e) {
         throw new RuntimeExceptionWrapper(e);
      } catch (ClassNotFoundException e) {
         throw new RuntimeExceptionWrapper(e);
      }
   }

   public void objectToEntry(InternalCacheEntry object, DatabaseEntry entry) {
      byte[] b;
      try {
         b = m.objectToByteBuffer(object);
      } catch (IOException e) {
         throw new RuntimeExceptionWrapper(e);
      }
      entry.setData(b);
   }
}
