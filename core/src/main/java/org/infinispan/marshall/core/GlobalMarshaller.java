package org.infinispan.marshall.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.OutputStream;
import java.io.Serializable;

import org.infinispan.commands.RemoteCommandsFactory;
import org.infinispan.commons.io.ByteBuffer;
import org.infinispan.commons.io.ExposedByteArrayOutputStream;
import org.infinispan.commons.marshall.BufferSizePredictor;
import org.infinispan.commons.marshall.Externalizer;
import org.infinispan.commons.marshall.MarshallableTypeHints;
import org.infinispan.commons.marshall.NotSerializableException;
import org.infinispan.commons.marshall.SerializeWith;
import org.infinispan.commons.marshall.StreamingMarshaller;
import org.infinispan.factories.GlobalComponentRegistry;
import org.infinispan.factories.annotations.Inject;
import org.infinispan.factories.annotations.Start;
import org.infinispan.factories.annotations.Stop;
import org.infinispan.factories.scopes.Scope;
import org.infinispan.factories.scopes.Scopes;
import org.infinispan.marshall.core.internal.BytesObjectInput;
import org.infinispan.marshall.core.internal.BytesObjectOutput;
import org.infinispan.marshall.core.internal.ExternalJBossMarshaller;
import org.infinispan.marshall.core.internal.InternalExternalizerTable;
import org.infinispan.marshall.core.internal.StreamBytesObjectOutput;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;

/**
 * A globally-scoped marshaller. This is needed so that the transport layer
 * can unmarshall requests even before it's known which cache's marshaller can
 * do the job.
 *
 * @author Galder Zamarreño
 * @since 5.0
 */
@Scope(Scopes.GLOBAL)
public class GlobalMarshaller implements StreamingMarshaller {

   private static final Log log = LogFactory.getLog(GlobalMarshaller.class);
   private final boolean trace = log.isTraceEnabled();

   final MarshallableTypeHints marshallableTypeHints = new MarshallableTypeHints();

   private GlobalComponentRegistry gcr;
   private RemoteCommandsFactory cmdFactory;

   private InternalExternalizerTable extTable;
   private StreamingMarshaller external;

   @Inject
   public void inject(GlobalComponentRegistry gcr, RemoteCommandsFactory cmdFactory) {
      this.gcr = gcr;
      this.cmdFactory = cmdFactory;
   }

   @Override
   @Start(priority = 8) // Should start after the externalizer table and before transport
   public void start() {
      this.extTable = new InternalExternalizerTable(gcr, cmdFactory);
      extTable.start();

      // TODO: Make external marshaller configurable
      this.external = new ExternalJBossMarshaller(extTable, gcr.getGlobalConfiguration());
      external.start();
   }

   @Override
   @Stop(priority = 11) // Stop after transport to avoid send/receive and marshaller not being ready
   public void stop() {
      extTable.stop();
      external.stop();
   }

   @Override
   public byte[] objectToByteBuffer(Object obj) throws IOException, InterruptedException {
      try {
         BytesObjectOutput out = writeObjectOutput(obj);
         return out.toBytes(); // trim out unused bytes
      } catch (java.io.NotSerializableException nse) {
         if (log.isDebugEnabled()) log.debug("Object is not serializable", nse);
         throw new NotSerializableException(nse.getMessage(), nse.getCause());
      }
   }

   private BytesObjectOutput writeObjectOutput(Object obj) throws IOException {
      BufferSizePredictor sizePredictor = marshallableTypeHints.getBufferSizePredictor(obj);
      BytesObjectOutput out = writeObjectOutput(obj, sizePredictor.nextSize(obj));
      sizePredictor.recordSize(out.getPosition());
      return out;
   }

   private BytesObjectOutput writeObjectOutput(Object obj, int estimatedSize) throws IOException {
      BytesObjectOutput out = new BytesObjectOutput(estimatedSize, extTable, external);
      Externalizer<Object> ext = extTable.findWriteExternalizer(obj, out);
      if (ext != null)
         ext.writeObject(out, obj);
      else
         external.objectToObjectStream(obj, out);

      return out;
   }

   @Override
   public Object objectFromByteBuffer(byte[] buf) throws IOException, ClassNotFoundException {
      ObjectInput in = BytesObjectInput.from(buf, extTable, external);
      return objectFromObjectInput(in);
   }

   private Object objectFromObjectInput(ObjectInput in) throws IOException, ClassNotFoundException {
      Externalizer<Object> ext = extTable.findReadExternalizer(in);
      if (ext != null)
         return ext.readObject(in);
      else {
         try {
            return external.objectFromObjectStream(in);
         } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
         }
      }
   }

   @Override
   public ObjectOutput startObjectOutput(OutputStream os, boolean isReentrant, int estimatedSize) throws IOException {
      BytesObjectOutput out = new BytesObjectOutput(estimatedSize, extTable, external);
      return new StreamBytesObjectOutput(os, out);
   }

   @Override
   public void objectToObjectStream(Object obj, ObjectOutput out) throws IOException {
      out.writeObject(obj);
   }

   @Override
   public void finishObjectOutput(ObjectOutput oo) {
      try {
         oo.flush();
      } catch (IOException e) {
         // ignored
      }
   }

   @Override
   public Object objectFromByteBuffer(byte[] bytes, int offset, int len) throws IOException, ClassNotFoundException {
      // Ignore length since boundary checks are not so useful here where the
      // unmarshalling code knows what to expect specifically. E.g. if reading
      // a byte[] subset within it, it's always appended with length.
      BytesObjectInput in = BytesObjectInput.from(bytes, offset, extTable, external);
      return objectFromObjectInput(in);
   }

   @Override
   public Object objectFromInputStream(InputStream is) throws IOException, ClassNotFoundException {
      // This is a very limited use case, e.g. reading from a JDBC ResultSet InputStream
      // So, this copying of the stream into a byte[] has not been problematic so far,
      // though it's not really ideal.
      int len = is.available();
      ExposedByteArrayOutputStream bytes;
      byte[] buf;
      if(len > 0) {
         bytes = new ExposedByteArrayOutputStream(len);
         buf = new byte[Math.min(len, 1024)];
      } else {
         // Some input stream providers do not implement available()
         bytes = new ExposedByteArrayOutputStream();
         buf = new byte[1024];
      }
      int bytesRead;
      while ((bytesRead = is.read(buf, 0, buf.length)) != -1) bytes.write(buf, 0, bytesRead);
      return objectFromByteBuffer(bytes.getRawBuffer(), 0, bytes.size());
   }

   @Override
   public boolean isMarshallable(Object o) throws Exception {
      Class<?> clazz = o.getClass();
      boolean containsMarshallable = marshallableTypeHints.isKnownMarshallable(clazz);
      if (containsMarshallable) {
         boolean marshallable = marshallableTypeHints.isMarshallable(clazz);
         if (trace)
            log.tracef("Marshallable type '%s' known and is marshallable=%b",
                  clazz.getName(), marshallable);

         return marshallable;
      } else {
         if (isMarshallableCandidate(o)) {
            boolean isMarshallable = true;
            try {
               objectToBuffer(o);
            } catch (Exception e) {
               isMarshallable = false;
               throw e;
            } finally {
               marshallableTypeHints.markMarshallable(clazz, isMarshallable);
            }
            return isMarshallable;
         }
         return false;
      }
   }

   private boolean isMarshallableCandidate(Object o) {
      return o instanceof Serializable
            || extTable.marshallable(o).isMarshallable()
            || o.getClass().getAnnotation(SerializeWith.class) != null
            || isExternalMarshallable(o);
   }

   private boolean isExternalMarshallable(Object o) {
      try {
         return external.isMarshallable(o);
      } catch (Exception e) {
         throw new NotSerializableException(
               "Object of type " + o.getClass() + " expected to be marshallable", e);
      }
   }

   @Override
   public BufferSizePredictor getBufferSizePredictor(Object o) {
      return marshallableTypeHints.getBufferSizePredictor(o.getClass());
   }

   @Override
   public ByteBuffer objectToBuffer(Object o) throws IOException, InterruptedException {
      try {
         BytesObjectOutput out = writeObjectOutput(o);
         return out.toByteBuffer();
      } catch (java.io.NotSerializableException nse) {
         if (log.isDebugEnabled()) log.debug("Object is not serializable", nse);
         throw new NotSerializableException(nse.getMessage(), nse.getCause());
      }
   }

   @Override
   public byte[] objectToByteBuffer(Object obj, int estimatedSize) throws IOException, InterruptedException {
      try {
         BytesObjectOutput out = writeObjectOutput(obj, estimatedSize);
         return out.toBytes();
      } catch (java.io.NotSerializableException nse) {
         if (log.isDebugEnabled()) log.debug("Object is not serializable", nse);
         throw new NotSerializableException(nse.getMessage(), nse.getCause());
      }
   }

   @Override
   public ObjectInput startObjectInput(InputStream is, boolean isReentrant) {
      throw new UnsupportedOperationException("No longer in use");
   }

   @Override
   public void finishObjectInput(ObjectInput oi) {
      throw new UnsupportedOperationException("No longer in use");
   }

   @Override
   public Object objectFromObjectStream(ObjectInput in) {
      throw new UnsupportedOperationException("No longer in use");
   }

}
