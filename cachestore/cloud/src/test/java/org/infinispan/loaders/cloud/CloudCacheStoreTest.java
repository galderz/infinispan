package org.infinispan.loaders.cloud;

import org.infinispan.CacheDelegate;
import org.infinispan.container.entries.InternalCacheEntry;
import org.infinispan.container.entries.InternalEntryFactory;
import org.infinispan.io.UnclosableObjectInputStream;
import org.infinispan.io.UnclosableObjectOutputStream;
import org.infinispan.loaders.BaseCacheStoreTest;
import org.infinispan.loaders.CacheLoaderException;
import org.infinispan.loaders.CacheStore;
import org.infinispan.marshall.Marshaller;
import org.jclouds.blobstore.BlobStoreContext;
import org.jclouds.blobstore.integration.StubBlobStoreContextBuilder;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.infinispan.loaders.cloud.StubCloudServiceBuilder.buildCloudCacheStoreWithStubCloudService;
import static org.testng.Assert.assertEquals;

@Test(groups = "unit", testName = "loaders.cloud.CloudCacheStoreTest")
public class CloudCacheStoreTest extends BaseCacheStoreTest {

   private static final String csBucket = "Bucket1";
   private static final String cs2Bucket = "Bucket2";
   protected CacheStore cs2;

   protected CacheStore createCacheStore() throws Exception {
      CacheStore store = buildCloudCacheStoreWithStubCloudService(csBucket, getMarshaller());
      store.start();
      return store;
   }

   protected CacheStore createAnotherCacheStore() throws Exception {
      CacheStore store = buildCloudCacheStoreWithStubCloudService(cs2Bucket, getMarshaller());
      store.start();
      return store;
   }

   @BeforeMethod
   @Override
   public void setUp() throws Exception {
      super.setUp();
      cs.clear();
      Set entries = cs.loadAll();
      assert entries.isEmpty();
      cs2 = createAnotherCacheStore();
      cs2.clear();
      entries = cs2.loadAll();
      assert entries.isEmpty();
   }


   @AfterMethod
   @Override
   public void tearDown() throws CacheLoaderException {
      for (CacheStore cacheStore : Arrays.asList(cs, cs2)) {
         if (cacheStore != null) {
            cacheStore.clear();
            cacheStore.stop();
         }
      }
      cs = null; cs2 = null;
   }


   @SuppressWarnings("unchecked")
   @Override
   @Test(enabled = false, description = "Disabled until JClouds gains a proper streaming API")
   public void testStreamingAPI() throws IOException, ClassNotFoundException, CacheLoaderException {
      cs.store(InternalEntryFactory.create("k1", "v1", -1, -1));
      cs.store(InternalEntryFactory.create("k2", "v2", -1, -1));
      cs.store(InternalEntryFactory.create("k3", "v3", -1, -1));

      Marshaller marshaller = getMarshaller();
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ObjectOutput oo = marshaller.startObjectOutput(out, false);
      try {
         cs.toStream(new UnclosableObjectOutputStream(oo));
      } finally {
         marshaller.finishObjectOutput(oo);
         out.close();
      }

      ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
      ObjectInput oi = marshaller.startObjectInput(in, false);
      try {
         cs2.fromStream(new UnclosableObjectInputStream(oi));
      } finally {
         marshaller.finishObjectInput(oi);
         in.close();
      }

      Set<InternalCacheEntry> set = cs2.loadAll();
      assertEquals(set.size(), 3);
      Set expected = new HashSet();
      expected.add("k1");
      expected.add("k2");
      expected.add("k3");
      for (InternalCacheEntry se : set) assert expected.remove(se.getKey());
      assert expected.isEmpty();
   }

   public void testNegativeHashCodes() throws CacheLoaderException {
      ObjectWithNegativeHashcode objectWithNegativeHashcode = new ObjectWithNegativeHashcode();
      cs.store(InternalEntryFactory.create(objectWithNegativeHashcode, "hello", -1, -1));
      InternalCacheEntry ice = cs.load(objectWithNegativeHashcode);
      assert ice.getKey().equals(objectWithNegativeHashcode);
      assert ice.getValue().equals("hello");
   }

   private static class ObjectWithNegativeHashcode implements Serializable {
      String s = "hello";
      private static final long serialVersionUID = 5010691348616186237L;

      @Override
      public boolean equals(Object o) {
         if (this == o) return true;
         if (o == null || getClass() != o.getClass()) return false;

         ObjectWithNegativeHashcode blah = (ObjectWithNegativeHashcode) o;
         return !(s != null ? !s.equals(blah.s) : blah.s != null);
      }

      @Override
      public int hashCode() {
         return -700;
      }
   }

   @Override
   @Test(enabled = false, description = "Disabled until we can build the blobstore stub to retain state somewhere.")
   public void testStopStartDoesNotNukeValues() throws InterruptedException, CacheLoaderException {

   }

   @SuppressWarnings("unchecked")
   @Override
   @Test(enabled = false, description = "Disabled until JClouds gains a proper streaming API")
   public void testStreamingAPIReusingStreams() throws IOException, ClassNotFoundException, CacheLoaderException {
      cs.store(InternalEntryFactory.create("k1", "v1", -1, -1));
      cs.store(InternalEntryFactory.create("k2", "v2", -1, -1));
      cs.store(InternalEntryFactory.create("k3", "v3", -1, -1));

      Marshaller marshaller = getMarshaller();
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] dummyStartBytes = {1, 2, 3, 4, 5, 6, 7, 8};
      byte[] dummyEndBytes = {8, 7, 6, 5, 4, 3, 2, 1};
      ObjectOutput oo = marshaller.startObjectOutput(out, false);
      try {
         oo.write(dummyStartBytes);
         cs.toStream(new UnclosableObjectOutputStream(oo));
         oo.flush();
         oo.write(dummyEndBytes);
      } finally {
         marshaller.finishObjectOutput(oo);
         out.close();
      }

      // first pop the start bytes
      byte[] dummy = new byte[8];
      ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
      ObjectInput oi = marshaller.startObjectInput(in, false);
      try {
         int bytesRead = oi.read(dummy, 0, 8);
         assert bytesRead == 8;
         for (int i = 1; i < 9; i++) assert dummy[i - 1] == i : "Start byte stream corrupted!";
         cs2.fromStream(new UnclosableObjectInputStream(oi));
         bytesRead = oi.read(dummy, 0, 8);
         assert bytesRead == 8;
         for (int i = 8; i > 0; i--) assert dummy[8 - i] == i : "Start byte stream corrupted!";
      } finally {
         marshaller.finishObjectInput(oi);
         in.close();
      }

      Set<InternalCacheEntry> set = cs2.loadAll();
      assertEquals(set.size(), 3);
      Set expected = new HashSet();
      expected.add("k1");
      expected.add("k2");
      expected.add("k3");
      for (InternalCacheEntry se : set) assert expected.remove(se.getKey());
      assert expected.isEmpty();
   }
}