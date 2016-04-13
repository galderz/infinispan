package org.infinispan.marshall.core;

import org.infinispan.atomic.DeltaCompositeKey;
import org.infinispan.atomic.impl.AtomicHashMap;
import org.infinispan.atomic.impl.AtomicHashMapDelta;
import org.infinispan.atomic.impl.ClearOperation;
import org.infinispan.atomic.impl.PutOperation;
import org.infinispan.atomic.impl.RemoveOperation;
import org.infinispan.commands.CommandInvocationId;
import org.infinispan.commands.RemoteCommandsFactory;
import org.infinispan.commands.write.ValueMatcher;
import org.infinispan.commons.hash.MurmurHash3;
import org.infinispan.commons.io.ByteBuffer;
import org.infinispan.commons.io.ByteBufferImpl;
import org.infinispan.commons.marshall.AbstractMarshaller;
import org.infinispan.commons.marshall.AdvancedExternalizer;
import org.infinispan.commons.marshall.DelegatingObjectInput;
import org.infinispan.commons.marshall.DelegatingObjectOutput;
import org.infinispan.commons.marshall.Externalizer;
import org.infinispan.commons.marshall.MarshallableFunctionExternalizers;
import org.infinispan.commons.marshall.SerializeWith;
import org.infinispan.commons.marshall.StreamingMarshaller;
import org.infinispan.commons.util.ImmutableListCopy;
import org.infinispan.commons.util.Immutables;
import org.infinispan.container.entries.ImmortalCacheEntry;
import org.infinispan.container.entries.ImmortalCacheValue;
import org.infinispan.container.entries.MortalCacheEntry;
import org.infinispan.container.entries.MortalCacheValue;
import org.infinispan.container.entries.TransientCacheEntry;
import org.infinispan.container.entries.TransientCacheValue;
import org.infinispan.container.entries.TransientMortalCacheEntry;
import org.infinispan.container.entries.TransientMortalCacheValue;
import org.infinispan.container.entries.metadata.MetadataImmortalCacheEntry;
import org.infinispan.container.entries.metadata.MetadataImmortalCacheValue;
import org.infinispan.container.entries.metadata.MetadataMortalCacheEntry;
import org.infinispan.container.entries.metadata.MetadataMortalCacheValue;
import org.infinispan.container.entries.metadata.MetadataTransientCacheEntry;
import org.infinispan.container.entries.metadata.MetadataTransientCacheValue;
import org.infinispan.container.entries.metadata.MetadataTransientMortalCacheEntry;
import org.infinispan.container.entries.metadata.MetadataTransientMortalCacheValue;
import org.infinispan.container.versioning.NumericVersion;
import org.infinispan.container.versioning.SimpleClusteredVersion;
import org.infinispan.context.Flag;
import org.infinispan.distribution.ch.impl.*;
import org.infinispan.factories.GlobalComponentRegistry;
import org.infinispan.factories.annotations.ComponentName;
import org.infinispan.factories.annotations.Inject;
import org.infinispan.factories.annotations.Start;
import org.infinispan.factories.scopes.Scope;
import org.infinispan.factories.scopes.Scopes;
import org.infinispan.filter.AcceptAllKeyValueFilter;
import org.infinispan.filter.CacheFilters;
import org.infinispan.filter.CollectionKeyFilter;
import org.infinispan.filter.CompositeKeyFilter;
import org.infinispan.filter.CompositeKeyValueFilter;
import org.infinispan.filter.KeyFilterAsKeyValueFilter;
import org.infinispan.filter.KeyValueFilterAsKeyFilter;
import org.infinispan.filter.NullValueConverter;
import org.infinispan.functional.impl.EntryViews;
import org.infinispan.functional.impl.MetaParams;
import org.infinispan.functional.impl.MetaParamsInternalMetadata;
import org.infinispan.marshall.exts.*;
import org.infinispan.metadata.EmbeddedMetadata;
import org.infinispan.metadata.impl.InternalMetadataImpl;
import org.infinispan.notifications.cachelistener.cluster.ClusterEvent;
import org.infinispan.notifications.cachelistener.cluster.ClusterEventCallable;
import org.infinispan.notifications.cachelistener.cluster.ClusterListenerRemoveCallable;
import org.infinispan.notifications.cachelistener.cluster.ClusterListenerReplicateCallable;
import org.infinispan.notifications.cachelistener.cluster.MultiClusterEventCallable;
import org.infinispan.notifications.cachelistener.filter.CacheEventConverterAsConverter;
import org.infinispan.notifications.cachelistener.filter.CacheEventFilterAsKeyValueFilter;
import org.infinispan.notifications.cachelistener.filter.CacheEventFilterConverterAsKeyValueFilterConverter;
import org.infinispan.notifications.cachelistener.filter.ConverterAsCacheEventConverter;
import org.infinispan.notifications.cachelistener.filter.KeyFilterAsCacheEventFilter;
import org.infinispan.notifications.cachelistener.filter.KeyValueFilterAsCacheEventFilter;
import org.infinispan.partitionhandling.AvailabilityMode;
import org.infinispan.remoting.responses.CacheNotFoundResponse;
import org.infinispan.remoting.responses.ExceptionResponse;
import org.infinispan.remoting.responses.SuccessfulResponse;
import org.infinispan.remoting.responses.UnsuccessfulResponse;
import org.infinispan.remoting.responses.UnsureResponse;
import org.infinispan.remoting.transport.jgroups.JGroupsAddress;
import org.infinispan.remoting.transport.jgroups.JGroupsTopologyAwareAddress;
import org.infinispan.statetransfer.StateChunk;
import org.infinispan.statetransfer.TransactionInfo;
import org.infinispan.stream.StreamMarshalling;
import org.infinispan.stream.impl.intops.IntermediateOperationExternalizer;
import org.infinispan.stream.impl.termop.TerminalOperationExternalizer;
import org.infinispan.topology.CacheJoinInfo;
import org.infinispan.topology.CacheStatusResponse;
import org.infinispan.topology.CacheTopology;
import org.infinispan.topology.ManagerStatusResponse;
import org.infinispan.topology.PersistentUUID;
import org.infinispan.transaction.xa.DldGlobalTransaction;
import org.infinispan.transaction.xa.GlobalTransaction;
import org.infinispan.transaction.xa.recovery.InDoubtTxInfoImpl;
import org.infinispan.transaction.xa.recovery.RecoveryAwareDldGlobalTransaction;
import org.infinispan.transaction.xa.recovery.RecoveryAwareGlobalTransaction;
import org.infinispan.transaction.xa.recovery.SerializableXid;
import org.infinispan.util.KeyValuePair;
import org.infinispan.xsite.statetransfer.XSiteState;
import org.mk300.marshal.minimum.MinimumMarshaller;
import org.mk300.marshal.minimum.io.OInput;
import org.mk300.marshal.minimum.io.OInputImpl;
import org.mk300.marshal.minimum.io.OOutput;
import org.mk300.marshal.minimum.io.OOutputImpl;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import static org.infinispan.factories.KnownComponentNames.GLOBAL_MARSHALLER;

@Scope(Scopes.GLOBAL)
public class MinMarshaller extends AbstractMarshaller implements StreamingMarshaller {

   private static final int ID_MIN_MAR = 255;
   private static final int ID_ANN_EXT = 254;

   private final Map<Class<?>, AdvancedExternalizer<Object>> writers = new WeakHashMap<>();
   private final Map<Integer, AdvancedExternalizer<Object>> readers = new HashMap<>();

   private RemoteCommandsFactory cmdFactory;
   private GlobalComponentRegistry gcr;
   private org.infinispan.commons.marshall.Marshaller globalMarshaller;

   @Inject
   public void inject(RemoteCommandsFactory cmdFactory, GlobalComponentRegistry gcr,
         @ComponentName(GLOBAL_MARSHALLER) org.infinispan.commons.marshall.Marshaller globalMarshaller) {
      this.cmdFactory = cmdFactory;
      this.gcr = gcr;
      this.globalMarshaller = globalMarshaller;
   }

   @Start(priority = 7) // Should start before global marshaller
   public void start() {
      loadInternalMarshallables();
   }

   private void loadInternalMarshallables() {
      addInternalExternalizer(new ListExternalizer());
      addInternalExternalizer(new MapExternalizer());
      addInternalExternalizer(new SetExternalizer());
      addInternalExternalizer(new EnumSetExternalizer());
      addInternalExternalizer(new ArrayExternalizers.ListArray());
      addInternalExternalizer(new SingletonListExternalizer());

      addInternalExternalizer(new IntSummaryStatisticsExternalizer());
      addInternalExternalizer(new LongSummaryStatisticsExternalizer());
      addInternalExternalizer(new DoubleSummaryStatisticsExternalizer());

      addInternalExternalizer(new GlobalTransaction.Externalizer());
      addInternalExternalizer(new RecoveryAwareGlobalTransaction.Externalizer());
      addInternalExternalizer(new DldGlobalTransaction.Externalizer());
      addInternalExternalizer(new RecoveryAwareDldGlobalTransaction.Externalizer());
      addInternalExternalizer(new JGroupsAddress.Externalizer());
      addInternalExternalizer(new ImmutableListCopy.Externalizer());
      addInternalExternalizer(new Immutables.ImmutableMapWrapperExternalizer());
      addInternalExternalizer(new MarshalledValue.Externalizer(globalMarshaller));
      addInternalExternalizer(new ByteBufferImpl.Externalizer());

      addInternalExternalizer(new SuccessfulResponse.Externalizer());
      addInternalExternalizer(new ExceptionResponse.Externalizer());
      addInternalExternalizer(new UnsuccessfulResponse.Externalizer());
      addInternalExternalizer(new UnsureResponse.Externalizer());
      addInternalExternalizer(new CacheNotFoundResponse.Externalizer());

      ReplicableCommandExternalizer cmExt =
            new ReplicableCommandExternalizer(cmdFactory, gcr);
      addInternalExternalizer(cmExt);
      addInternalExternalizer(new CacheRpcCommandExternalizer(gcr, cmExt));

      addInternalExternalizer(new ImmortalCacheEntry.Externalizer());
      addInternalExternalizer(new MortalCacheEntry.Externalizer());
      addInternalExternalizer(new TransientCacheEntry.Externalizer());
      addInternalExternalizer(new TransientMortalCacheEntry.Externalizer());
      addInternalExternalizer(new ImmortalCacheValue.Externalizer());
      addInternalExternalizer(new MortalCacheValue.Externalizer());
      addInternalExternalizer(new TransientCacheValue.Externalizer());
      addInternalExternalizer(new TransientMortalCacheValue.Externalizer());

      addInternalExternalizer(new SimpleClusteredVersion.Externalizer());
      addInternalExternalizer(new MetadataImmortalCacheEntry.Externalizer());
      addInternalExternalizer(new MetadataMortalCacheEntry.Externalizer());
      addInternalExternalizer(new MetadataTransientCacheEntry.Externalizer());
      addInternalExternalizer(new MetadataTransientMortalCacheEntry.Externalizer());
      addInternalExternalizer(new MetadataImmortalCacheValue.Externalizer());
      addInternalExternalizer(new MetadataMortalCacheValue.Externalizer());
      addInternalExternalizer(new MetadataTransientCacheValue.Externalizer());
      addInternalExternalizer(new MetadataTransientMortalCacheValue.Externalizer());

      addInternalExternalizer(new DeltaCompositeKey.DeltaCompositeKeyExternalizer());
      addInternalExternalizer(new AtomicHashMap.Externalizer());
      addInternalExternalizer(new AtomicHashMapDelta.Externalizer());
      addInternalExternalizer(new PutOperation.Externalizer());
      addInternalExternalizer(new RemoveOperation.Externalizer());
      addInternalExternalizer(new ClearOperation.Externalizer());
      addInternalExternalizer(new JGroupsTopologyAwareAddress.Externalizer());

      addInternalExternalizer(new SerializableXid.XidExternalizer());
      addInternalExternalizer(new InDoubtTxInfoImpl.Externalizer());

      addInternalExternalizer(new MurmurHash3.Externalizer());
      addInternalExternalizer(new HashFunctionPartitioner.Externalizer());
      addInternalExternalizer(new AffinityPartitioner.Externalizer());

      addInternalExternalizer(new DefaultConsistentHash.Externalizer());
      addInternalExternalizer(new ReplicatedConsistentHash.Externalizer());
      addInternalExternalizer(new DefaultConsistentHashFactory.Externalizer());
      addInternalExternalizer(new ReplicatedConsistentHashFactory.Externalizer());
      addInternalExternalizer(new SyncConsistentHashFactory.Externalizer());
      addInternalExternalizer(new SyncReplicatedConsistentHashFactory.Externalizer());
      addInternalExternalizer(new TopologyAwareConsistentHashFactory.Externalizer());
      addInternalExternalizer(new TopologyAwareSyncConsistentHashFactory.Externalizer());
      addInternalExternalizer(new CacheTopology.Externalizer());
      addInternalExternalizer(new CacheJoinInfo.Externalizer());
      addInternalExternalizer(new TransactionInfo.Externalizer());
      addInternalExternalizer(new StateChunk.Externalizer());

      addInternalExternalizer(new Flag.Externalizer());
      addInternalExternalizer(new ValueMatcher.Externalizer());
      addInternalExternalizer(new AvailabilityMode.Externalizer());

      addInternalExternalizer(new EmbeddedMetadata.Externalizer());

      addInternalExternalizer(new NumericVersion.Externalizer());
      addInternalExternalizer(new KeyValuePair.Externalizer());
      addInternalExternalizer(new InternalMetadataImpl.Externalizer());
      addInternalExternalizer(new MarshalledEntryImpl.Externalizer(globalMarshaller));

      addInternalExternalizer(new CollectionKeyFilter.Externalizer());
      addInternalExternalizer(new KeyFilterAsKeyValueFilter.Externalizer());
      addInternalExternalizer(new KeyValueFilterAsKeyFilter.Externalizer());
      addInternalExternalizer(new ClusterEvent.Externalizer());
      addInternalExternalizer(new ClusterEventCallable.Externalizer());
      addInternalExternalizer(new ClusterListenerRemoveCallable.Externalizer());
      addInternalExternalizer(new ClusterListenerReplicateCallable.Externalizer());
      addInternalExternalizer(new XSiteState.XSiteStateExternalizer());
      addInternalExternalizer(new CompositeKeyFilter.Externalizer());
      addInternalExternalizer(new CompositeKeyValueFilter.Externalizer());
      addInternalExternalizer(new CacheStatusResponse.Externalizer());
      addInternalExternalizer(new CacheEventConverterAsConverter.Externalizer());
      addInternalExternalizer(new CacheEventFilterAsKeyValueFilter.Externalizer());
      addInternalExternalizer(new CacheEventFilterConverterAsKeyValueFilterConverter.Externalizer());
      addInternalExternalizer(new ConverterAsCacheEventConverter.Externalizer());
      addInternalExternalizer(new KeyFilterAsCacheEventFilter.Externalizer());
      addInternalExternalizer(new KeyValueFilterAsCacheEventFilter.Externalizer());
      addInternalExternalizer(new NullValueConverter.Externalizer());
      addInternalExternalizer(new AcceptAllKeyValueFilter.Externalizer());
      addInternalExternalizer(new ManagerStatusResponse.Externalizer());
      addInternalExternalizer(new MultiClusterEventCallable.Externalizer());

      addInternalExternalizer(new IntermediateOperationExternalizer());
      addInternalExternalizer(new TerminalOperationExternalizer());
      addInternalExternalizer(new StreamMarshalling.StreamMarshallingExternalizer());
      addInternalExternalizer(new CommandInvocationId.Externalizer());
      addInternalExternalizer(new CacheFilters.CacheFiltersExternalizer());


      addInternalExternalizer(new OptionalExternalizer());

      addInternalExternalizer(new MetaParamsInternalMetadata.Externalizer());
      addInternalExternalizer(new MetaParams.Externalizer());

      // TODO: Add other MetaParam externalizers
      addInternalExternalizer(new MetaParamExternalizers.LifespanExternalizer());
      addInternalExternalizer(new MetaParamExternalizers.EntryVersionParamExternalizer());
      addInternalExternalizer(new MetaParamExternalizers.NumericEntryVersionExternalizer());

      addInternalExternalizer(new EntryViews.ReadWriteSnapshotViewExternalizer());
      addInternalExternalizer(new MarshallableFunctionExternalizers.ConstantLambdaExternalizer());
      addInternalExternalizer(new MarshallableFunctionExternalizers.LambdaWithMetasExternalizer());
      addInternalExternalizer(new MarshallableFunctionExternalizers.SetValueIfEqualsReturnBooleanExternalizer());
      addInternalExternalizer(new PersistentUUID.Externalizer());
   }

   <T> void addInternalExternalizer(AdvancedExternalizer<T> ext) {
      Set<Class<? extends T>> typeClasses = ext.getTypeClasses();
      for (Class<? extends T> typeClass : typeClasses) {
         writers.put(typeClass, (AdvancedExternalizer<Object>) ext);
         readers.put(ext.getId(), (AdvancedExternalizer<Object>) ext);
      }
   }

   @Override
   protected ByteBuffer objectToBuffer(Object o, int estimatedSize) throws IOException, InterruptedException {
      if (o != null) {
         Class<?> type = o.getClass();
         AdvancedExternalizer<Object> ext = writers.get(type);
         if (ext != null) {
            OOutputImpl oo = new OOutputImpl(estimatedSize);
            oo.write(ext.getId());
            ext.writeObject(new MinObjectOutput(oo, writers), o);
            byte[] bytes = oo.toBytes();
            return new ByteBufferImpl(bytes, 0, bytes.length);
         } else {
            SerializeWith ann = type.getAnnotation(SerializeWith.class);
            if (ann != null) {
               Externalizer<Object> annExt = createExternalizerFromAnnotation(ann);
               OOutputImpl oo = new OOutputImpl(estimatedSize);
               oo.write(ID_ANN_EXT);
               oo.writeObject(annExt);
               annExt.writeObject(new MinObjectOutput(oo, writers), o);
               byte[] bytes = oo.toBytes();
               return new ByteBufferImpl(bytes, 0, bytes.length);
            } else {
               OOutputImpl oo = new OOutputImpl(estimatedSize);
               oo.write(ID_MIN_MAR);
               oo.writeObject(o);
               byte[] bytes = oo.toBytes();
               return new ByteBufferImpl(bytes, 0, bytes.length);
            }
         }
      } else {
         OOutputImpl oo = new OOutputImpl(1 + 2);
         oo.write(ID_MIN_MAR);
         oo.writeObject(null);
         byte[] bytes = oo.toBytes();
         return new ByteBufferImpl(bytes, 0, bytes.length);
      }
   }

   @Override
   public Object objectFromByteBuffer(byte[] buf, int offset, int length) throws IOException, ClassNotFoundException {
      ByteArrayInputStream is = new ByteArrayInputStream(buf, offset, length);
      int extType = is.read();
      AdvancedExternalizer<Object> ext = readers.get(extType);
      if (ext != null) {
         byte[] bytes = new byte[length - 1];
         System.arraycopy(buf, offset + 1, bytes, 0, length - 1);
         OInput oi = new OInputImpl(bytes);
         return ext.readObject(new MinObjectInput(oi, readers));
      } else {
         byte[] bytes = new byte[length - 1];
         System.arraycopy(buf, offset + 1, bytes, 0, length - 1);
         OInput oi = new OInputImpl(bytes);
         return oi.readObject();
      }
   }

   @Override
   public boolean isMarshallable(Object o) throws Exception {
      return MinimumMarshaller.isDefined(o.getClass());
   }

   @Override
   public ObjectOutput startObjectOutput(OutputStream os, boolean isReentrant, int estimatedSize) throws IOException {
      return ((DelegatingObjectOutput) os).objectOutput;
//      return new MinObjectOutput(new OOutputImpl(estimatedSize), writers);
   }

   @Override
   public void finishObjectOutput(ObjectOutput oo) {
      try {
         oo.close();
      } catch (IOException e) {
         throw new RuntimeException(e);
      }
   }

   @Override
   public void objectToObjectStream(Object obj, ObjectOutput out) throws IOException {
      // TODO: Customise this generated block
   }

   @Override
   public ObjectInput startObjectInput(InputStream is, boolean isReentrant) throws IOException {
      return ((DelegatingObjectInput) is).objectInput;
      //return new MinObjectInput(new OInputImpl(inputStreamToBytes(is)), readers);
   }

//   private static byte[] inputStreamToBytes(InputStream in) throws IOException {
//      int ch1 = in.read();
//      int ch2 = in.read();
//      int ch3 = in.read();
//      int ch4 = in.read();
//      if ((ch1 | ch2 | ch3 | ch4) < 0)
//         throw new EOFException();
//      int len = ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4 << 0));
//      byte[] buf = new byte[len];
//      int n = 0;
//      while (n < len) {
//         int count = in.read(buf, 0 + n, len - n);
//         if (count < 0)
//            throw new EOFException();
//         n += count;
//
//      }
//      return buf;
//   }

   @Override
   public void finishObjectInput(ObjectInput oi) {
      // TODO: Customise this generated block
   }

   @Override
   public Object objectFromObjectStream(ObjectInput in) throws IOException, ClassNotFoundException, InterruptedException {
      return null;  // TODO: Customise this generated block
   }

   @Override
   public void stop() {
      // TODO: Customise this generated block
   }

   private static Externalizer<Object> createExternalizerFromAnnotation(SerializeWith ann) {
      try {
         return (Externalizer<Object>) ann.value().newInstance();
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }

   static final class MinObjectOutput implements ObjectOutput {

      final OOutput oo;
      final Map<Class<?>, AdvancedExternalizer<Object>> writers;

      MinObjectOutput(OOutput oo, Map<Class<?>, AdvancedExternalizer<Object>> writers) {
         this.oo = oo;
         this.writers = writers;
      }

      @Override
      public void writeObject(Object obj) throws IOException {
         if (obj != null) {
            Class<?> type = obj.getClass();
            AdvancedExternalizer<Object> ext = writers.get(type);
            if (ext != null) {
               oo.write(ext.getId());
               ext.writeObject(this, obj);
            } else {
               SerializeWith ann = type.getAnnotation(SerializeWith.class);
               if (ann != null) {
                  Externalizer<Object> annExt = createExternalizerFromAnnotation(ann);
                  oo.write(ID_ANN_EXT);
                  oo.writeObject(annExt);
                  annExt.writeObject(this, obj);
               } else {
                  oo.write(ID_MIN_MAR);
                  oo.writeObject(obj);
               }
            }
         } else {
            oo.write(ID_MIN_MAR);
            oo.writeObject(obj);
         }
      }

      @Override
      public void write(int b) throws IOException {
         oo.write(b);
      }

      @Override
      public void write(byte[] b) throws IOException {
         oo.write(b);
      }

      @Override
      public void write(byte[] b, int off, int len) throws IOException {
         oo.write(b, off, len);
      }

      @Override
      public void writeBoolean(boolean v) throws IOException {
         oo.writeBoolean(v);
      }

      @Override
      public void writeByte(int v) throws IOException {
         oo.writeByte(v);
      }

      @Override
      public void writeShort(int v) throws IOException {
         oo.writeShort((short) v);
      }

      @Override
      public void writeChar(int v) throws IOException {
         oo.writeChar((char) v);
      }

      @Override
      public void writeInt(int v) throws IOException {
         oo.writeInt(v);
      }

      @Override
      public void writeLong(long v) throws IOException {
         oo.writeLong(v);
      }

      @Override
      public void writeFloat(float v) throws IOException {
         oo.writeFloat(v);
      }

      @Override
      public void writeDouble(double v) throws IOException {
         oo.writeDouble(v);
      }

      @Override
      public void writeBytes(String s) throws IOException {
         oo.writeString(s);
      }

      @Override
      public void writeChars(String s) throws IOException {
         writeBytes(s);
      }

      @Override
      public void writeUTF(String s) throws IOException {
         oo.writeUTF(s);
      }

      @Override
      public void flush() throws IOException {
         oo.flush();
      }

      @Override
      public void close() throws IOException {
         oo.close();
      }
   }

   static final class MinObjectInput implements ObjectInput {

      final OInput oi;
      final Map<Integer, AdvancedExternalizer<Object>> readers;

      MinObjectInput(OInput oi, Map<Integer, AdvancedExternalizer<Object>> readers) {
         this.oi = oi;
         this.readers = readers;
      }

      @Override

      public Object readObject() throws ClassNotFoundException, IOException {
         byte signedByte = oi.readByte();
         int unsignedByte = signedByte & 0xff;
         if (unsignedByte == ID_ANN_EXT) {
            Externalizer<Object> annExt = (Externalizer<Object>) oi.readObject();
            return annExt.readObject(this);
         } else {
            AdvancedExternalizer<Object> ext = readers.get(unsignedByte);
            if (ext != null) {
               return ext.readObject(this);
            }
            return oi.readObject();
         }
      }

      @Override
      public int read() throws IOException {
         return oi.readByte();
      }

      @Override
      public int read(byte[] b) throws IOException {
         return 0;
      }

      @Override
      public int read(byte[] b, int off, int len) throws IOException {
         return 0;  // TODO: Customise this generated block
      }

      @Override
      public long skip(long n) throws IOException {
         oi.skip((int) n);
         return n;
      }

      @Override
      public int available() throws IOException {
         return 0;  // TODO: Customise this generated block
      }

      @Override
      public void close() throws IOException {
         // TODO: Customise this generated block
      }

      @Override
      public void readFully(byte[] b) throws IOException {
         oi.readFully(b);
      }

      @Override
      public void readFully(byte[] b, int off, int len) throws IOException {
         oi.readFully(b);
      }

      @Override
      public int skipBytes(int n) throws IOException {
         oi.skip(n);
         return n;
      }

      @Override
      public boolean readBoolean() throws IOException {
         return oi.readBoolean();
      }

      @Override
      public byte readByte() throws IOException {
         return oi.readByte();
      }

      @Override
      public int readUnsignedByte() throws IOException {
         return oi.readByte();
      }

      @Override
      public short readShort() throws IOException {
         return oi.readShort();
      }

      @Override
      public int readUnsignedShort() throws IOException {
         return oi.readShort();
      }

      @Override
      public char readChar() throws IOException {
         return oi.readChar();
      }

      @Override
      public int readInt() throws IOException {
         return oi.readInt();
      }

      @Override
      public long readLong() throws IOException {
         return oi.readLong();
      }

      @Override
      public float readFloat() throws IOException {
         return oi.readFloat();
      }

      @Override
      public double readDouble() throws IOException {
         return oi.readDouble();
      }

      @Override
      public String readLine() throws IOException {
         return null;  // TODO: Customise this generated block
      }

      @Override
      public String readUTF() throws IOException {
         return oi.readUTF();
      }
   }

}
