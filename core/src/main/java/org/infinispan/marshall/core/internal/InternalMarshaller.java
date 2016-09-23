package org.infinispan.marshall.core.internal;

import org.infinispan.commands.RemoteCommandsFactory;
import org.infinispan.commons.io.ByteBuffer;
import org.infinispan.commons.io.ByteBufferImpl;
import org.infinispan.commons.io.ExposedByteArrayOutputStream;
import org.infinispan.commons.logging.Log;
import org.infinispan.commons.logging.LogFactory;
import org.infinispan.commons.marshall.BufferSizePredictor;
import org.infinispan.commons.marshall.Externalizer;
import org.infinispan.commons.marshall.MarshallableTypeHints;
import org.infinispan.commons.marshall.NotSerializableException;
import org.infinispan.commons.marshall.SerializeWith;
import org.infinispan.commons.marshall.StreamingMarshaller;
import org.infinispan.factories.GlobalComponentRegistry;
import org.jboss.marshalling.util.IdentityIntMap;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.OutputStream;
import java.io.Serializable;

// TODO: If wrapped around GlobalMarshaller, there might not be need to implement StreamingMarshaller interface
// If exposed directly, e.g. not wrapped by GlobalMarshaller, it'd need to implement StreamingMarshaller
public final class InternalMarshaller implements StreamingMarshaller {

   private static final Log log = LogFactory.getLog(InternalMarshaller.class);
   private static final boolean trace = log.isTraceEnabled();

   private static final IdentityIntMap<Class<?>> BASIC_CLASSES;

   static final int NOT_FOUND                      = -1;

   static final int ID_SMALL                       = 0x100;
   static final int ID_MEDIUM                      = 0x10000;

   // 0x03-0x0F reserved
   static final int ID_NULL                        = 0x00;
   static final int ID_BYTE_ARRAY                  = 0x01; // byte[].class
   static final int ID_STRING                      = 0x02; // String.class

   static final int ID_BOOLEAN_OBJ                 = 0x10; // Boolean.class
   static final int ID_BYTE_OBJ                    = 0x11; // ..etc..
   static final int ID_CHAR_OBJ                    = 0x12;
   static final int ID_DOUBLE_OBJ                  = 0x13;
   static final int ID_FLOAT_OBJ                   = 0x14;
   static final int ID_INT_OBJ                     = 0x15;
   static final int ID_LONG_OBJ                    = 0x16;
   static final int ID_SHORT_OBJ                   = 0x17;

   static final int ID_BOOLEAN_ARRAY               = 0x18; // boolean[].class
   static final int ID_CHAR_ARRAY                  = 0x19; // ..etc..
   static final int ID_DOUBLE_ARRAY                = 0x1A;
   static final int ID_FLOAT_ARRAY                 = 0x1B;
   static final int ID_INT_ARRAY                   = 0x1C;
   static final int ID_LONG_ARRAY                  = 0x1D;
   static final int ID_SHORT_ARRAY                 = 0x1E;

   static final int ID_OBJECT_ARRAY                = 0x1F; // Object[]

   static final int ID_ARRAY_EMPTY                 = 0x28; // zero elements
   static final int ID_ARRAY_SMALL                 = 0x29; // <=0x100 elements
   static final int ID_ARRAY_MEDIUM                = 0x2A; // <=0x10000 elements
   static final int ID_ARRAY_LARGE                 = 0x2B; // <0x80000000 elements

   // 0x2C-0x2F unused

   static final int ID_INTERNAL                    = 0x30;
   static final int ID_PREDEFINED                  = 0x31;
   static final int ID_ANNOTATED                   = 0x32;
   static final int ID_EXTERNAL                    = 0x33;

   static {
      final IdentityIntMap<Class<?>> map = new IdentityIntMap<Class<?>>(0x0.6p0f);
      map.put(String.class, ID_STRING);
      map.put(byte[].class, ID_BYTE_ARRAY);

      map.put(Boolean.class, ID_BOOLEAN_OBJ);
      map.put(Byte.class, ID_BYTE_OBJ);
      map.put(Character.class, ID_CHAR_OBJ);
      map.put(Double.class, ID_DOUBLE_OBJ);
      map.put(Float.class, ID_FLOAT_OBJ);
      map.put(Integer.class, ID_INT_OBJ);
      map.put(Long.class, ID_LONG_OBJ);
      map.put(Short.class, ID_SHORT_OBJ);

      map.put(boolean[].class, ID_BOOLEAN_ARRAY);
      map.put(char[].class, ID_CHAR_ARRAY);
      map.put(double[].class, ID_DOUBLE_ARRAY);
      map.put(float[].class, ID_FLOAT_ARRAY);
      map.put(int[].class, ID_INT_ARRAY);
      map.put(long[].class, ID_LONG_ARRAY);
      map.put(short[].class, ID_SHORT_ARRAY);

      map.put(Object[].class, ID_OBJECT_ARRAY);

      BASIC_CLASSES = map.clone();
   }

   final MarshallableTypeHints marshallableTypeHints = new MarshallableTypeHints();

   final Encoding enc = new BytesEncoding();
   final InternalExternalizerTable externalizers;

   final StreamingMarshaller external;

   public InternalMarshaller(GlobalComponentRegistry gcr, RemoteCommandsFactory cmdFactory) {
      this.externalizers = new InternalExternalizerTable(enc, gcr, cmdFactory);
      //this.external = new ExternalJavaMarshaller();
      this.external = new ExternalJBossMarshaller(externalizers, gcr.getGlobalConfiguration());
   }

   @Override
   public void start() {
      externalizers.start();
      external.start();
   }

   @Override
   public void stop() {
      externalizers.stop();
      external.stop();
   }

   @Override
   public byte[] objectToByteBuffer(Object obj) throws IOException, InterruptedException {
      BytesObjectOutput out = writeObjectOutput(obj);
      return out.toBytes(); // trim out unused bytes
   }

   private BytesObjectOutput writeObjectOutput(Object obj) throws IOException {
      BufferSizePredictor sizePredictor = marshallableTypeHints.getBufferSizePredictor(obj);
      BytesObjectOutput out = writeObjectOutput(obj, sizePredictor.nextSize(obj));
      sizePredictor.recordSize(out.pos);
      return out;
   }

   private BytesObjectOutput writeObjectOutput(Object obj, int estimatedSize) throws IOException {
      BytesObjectOutput out = new BytesObjectOutput(estimatedSize, this);
      writeNullableObject(obj, out);
      return out;
   }

   void writeNullableObject(Object obj, BytesObjectOutput out) throws IOException {
      if (obj == null) {
         out.writeByte(ID_NULL);
         return;
      }

      int id = BASIC_CLASSES.get(obj.getClass(), NOT_FOUND);
      switch (id) {
         case ID_BYTE_ARRAY:
            out.writeByte(id);
            writeByteArray((byte[]) obj, out);
            break;
         case ID_STRING:
            out.writeByte(id);
            writeString(out, (String) obj);
            break;
         case ID_BOOLEAN_OBJ:
            out.writeByte(id);
            out.writeBoolean((boolean) obj);
            break;
         case ID_BYTE_OBJ:
            out.writeByte(id);
            out.writeByte((int) obj);
            break;
         case ID_CHAR_OBJ:
            out.writeByte(id);
            out.writeChar((int) obj);
            break;
         case ID_DOUBLE_OBJ:
            out.writeByte(id);
            out.writeDouble((double) obj);
            break;
         case ID_FLOAT_OBJ:
            out.writeByte(id);
            out.writeFloat((float) obj);
            break;
         case ID_INT_OBJ:
            out.writeByte(id);
            out.writeInt((int) obj);
            break;
         case ID_LONG_OBJ:
            out.writeByte(id);
            out.writeLong((long) obj);
            break;
         case ID_SHORT_OBJ:
            out.writeByte(id);
            out.writeShort((int) obj);
            break;
         case ID_BOOLEAN_ARRAY:
            out.writeByte(id);
            writeBooleanArray((boolean[]) obj, out);
            break;
         case ID_CHAR_ARRAY:
            out.writeByte(id);
            writeCharArray((char[]) obj, out);
            break;
         case ID_DOUBLE_ARRAY:
            out.writeByte(id);
            writeDoubleArray((double[]) obj, out);
            break;
         case ID_FLOAT_ARRAY:
            out.writeByte(id);
            writeFloatArray((float[]) obj, out);
            break;
         case ID_INT_ARRAY:
            out.writeByte(id);
            writeIntArray((int[]) obj, out);
            break;
         case ID_LONG_ARRAY:
            out.writeByte(id);
            writeLongArray((long[]) obj, out);
            break;
         case ID_SHORT_ARRAY:
            out.writeByte(id);
            writeShortArray((short[]) obj, out);
            break;
         case ID_OBJECT_ARRAY:
            out.writeByte(id);
            writeObjectArray((Object[]) obj, out);
            break;
         case NOT_FOUND:
            Externalizer<Object> ext = externalizers.findWriteExternalizer(obj, out);
            if (ext != null)
               ext.writeObject(out, obj);
            else
               external.objectToObjectStream(obj, out);
            break;
         default:
            throw new IOException("Unknown primitive type: " + obj);
      }
   }

   @Override
   public Object objectFromByteBuffer(byte[] buf) throws IOException, ClassNotFoundException {
      ObjectInput in = BytesObjectInput.from(buf, this);
      return objectFromObjectInput(in);
   }

   private Object objectFromObjectInput(ObjectInput in) throws IOException, ClassNotFoundException {
      return readNullableObject(in);
   }

   Object readNullableObject(ObjectInput in) throws IOException, ClassNotFoundException {
      int type = in.readUnsignedByte();
      switch (type) {
         case ID_NULL:
            return null;
         case ID_BYTE_ARRAY:
            return readByteArray(in);
         case ID_STRING:
            return readString(in);
         case ID_BOOLEAN_OBJ:
            return in.readBoolean();
         case ID_BYTE_OBJ:
            return in.readByte();
         case ID_CHAR_OBJ:
            return in.readChar();
         case ID_DOUBLE_OBJ:
            return in.readDouble();
         case ID_FLOAT_OBJ:
            return in.readFloat();
         case ID_INT_OBJ:
            return in.readInt();
         case ID_LONG_OBJ:
            return in.readLong();
         case ID_SHORT_OBJ:
            return in.readShort();
         case ID_BOOLEAN_ARRAY:
            return readBooleanArray(in);
         case ID_CHAR_ARRAY:
            return readCharArray(in);
         case ID_DOUBLE_ARRAY:
            return readDoubleArray(in);
         case ID_FLOAT_ARRAY:
            return readFloatArray(in);
         case ID_INT_ARRAY:
            return readIntArray(in);
         case ID_LONG_ARRAY:
            return readLongArray(in);
         case ID_SHORT_ARRAY:
            return readShortArray(in);
         case ID_OBJECT_ARRAY:
            return readObjectArray(in);
         default:
            Externalizer<Object> ext = externalizers.findReadExternalizer(in, type);
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
   }

   @Override
   public ObjectOutput startObjectOutput(OutputStream os, boolean isReentrant, int estimatedSize) throws IOException {
      BytesObjectOutput out = new BytesObjectOutput(estimatedSize, this);
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
   public Object objectFromByteBuffer(byte[] buf, int offset, int length) throws IOException, ClassNotFoundException {
      // Ignore length since boundary checks are not so useful here where the
      // unmarshalling code knows what to expect specifically. E.g. if reading
      // a byte[] subset within it, it's always appended with length.
      BytesObjectInput in = BytesObjectInput.from(buf, offset, this);
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
            || externalizers.marshallable(o).isMarshallable()
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
      BytesObjectOutput out = writeObjectOutput(o);
      // No triming, just take position as length
      return new ByteBufferImpl(out.bytes, 0, out.pos);
   }

   @Override
   public byte[] objectToByteBuffer(Object obj, int estimatedSize) throws IOException, InterruptedException {
      BytesObjectOutput out = writeObjectOutput(obj, estimatedSize);
      return out.toBytes(); // trim out unused bytes
   }

   @Override
   public ObjectInput startObjectInput(InputStream is, boolean isReentrant) throws IOException {
      throw new RuntimeException("NYI");
   }

   @Override
   public void finishObjectInput(ObjectInput oi) {
      throw new RuntimeException("NYI");
   }

   @Override
   public Object objectFromObjectStream(ObjectInput in) throws IOException, ClassNotFoundException, InterruptedException {
      throw new RuntimeException("NYI");
   }

   @SuppressWarnings("unchecked")
   private void writeString(ObjectOutput out, String obj) {
      // Instead of out.writeUTF() to be able to write smaller String payloads
      enc.encodeString(obj, out);
   }

   private void writeByteArray(byte[] obj, ObjectOutput out) throws IOException {
      final int len = obj.length;
      if (len == 0) {
         out.writeByte(ID_ARRAY_EMPTY);
      } else if (len <= 256) {
         out.writeByte(ID_ARRAY_SMALL);
         out.writeByte(len);
         out.write(obj, 0, len);
      } else if (len <= 65536) {
         out.writeByte(ID_ARRAY_MEDIUM);
         out.writeShort(len);
         out.write(obj, 0, len);
      } else {
         out.writeByte(ID_ARRAY_LARGE);
         out.writeInt(len);
         out.write(obj, 0, len);
      }
   }

   private void writeBooleanArray(boolean[] obj, ObjectOutput out) throws IOException {
      final int len = obj.length;
      if (len == 0) {
         out.writeByte(ID_ARRAY_EMPTY);
      } else if (len <= 256) {
         out.writeByte(ID_ARRAY_SMALL);
         out.writeByte(len);
         writeBooleans(obj, out);
      } else if (len <= 65536) {
         out.writeByte(ID_ARRAY_MEDIUM);
         out.writeShort(len);
         writeBooleans(obj, out);
      } else {
         out.writeByte(ID_ARRAY_LARGE);
         out.writeInt(len);
         writeBooleans(obj, out);
      }
   }

   private void writeBooleans(boolean[] obj, ObjectOutput out) throws IOException {
      final int len = obj.length;
      final int bc = len & ~7;
      for (int i = 0; i < bc;) {
         out.write(
               (obj[i++] ? 1 : 0)
                     | (obj[i++] ? 2 : 0)
                     | (obj[i++] ? 4 : 0)
                     | (obj[i++] ? 8 : 0)
                     | (obj[i++] ? 16 : 0)
                     | (obj[i++] ? 32 : 0)
                     | (obj[i++] ? 64 : 0)
                     | (obj[i++] ? 128 : 0)
         );
      }
      if (bc < len) {
         int o = 0;
         int bit = 1;
         for (int i = bc; i < len; i++) {
            if (obj[i]) o |= bit;
            bit <<= 1;
         }
         out.writeByte(o);
      }
   }

   private void writeCharArray(char[] obj, ObjectOutput out) throws IOException {
      final int len = obj.length;
      if (len == 0) {
         out.writeByte(ID_ARRAY_EMPTY);
      } else if (len <= 256) {
         out.writeByte(ID_ARRAY_SMALL);
         out.writeByte(len);
         for (char v : obj) out.writeChar(v);
      } else if (len <= 65536) {
         out.writeByte(ID_ARRAY_MEDIUM);
         out.writeShort(len);
         for (char v : obj) out.writeChar(v);
      } else {
         out.writeByte(ID_ARRAY_LARGE);
         out.writeInt(len);
         for (char v : obj) out.writeChar(v);
      }
   }

   private void writeDoubleArray(double[] obj, ObjectOutput out) throws IOException {
      final int len = obj.length;
      if (len == 0) {
         out.writeByte(ID_ARRAY_EMPTY);
      } else if (len <= 256) {
         out.writeByte(ID_ARRAY_SMALL);
         out.writeByte(len);
         for (double v : obj) out.writeDouble(v);
      } else if (len <= 65536) {
         out.writeByte(ID_ARRAY_MEDIUM);
         out.writeShort(len);
         for (double v : obj) out.writeDouble(v);
      } else {
         out.writeByte(ID_ARRAY_LARGE);
         out.writeInt(len);
         for (double v : obj) out.writeDouble(v);
      }
   }

   private void writeFloatArray(float[] obj, ObjectOutput out) throws IOException {
      final int len = obj.length;
      if (len == 0) {
         out.writeByte(ID_ARRAY_EMPTY);
      } else if (len <= 256) {
         out.writeByte(ID_ARRAY_SMALL);
         out.writeByte(len);
         for (float v : obj) out.writeFloat(v);
      } else if (len <= 65536) {
         out.writeByte(ID_ARRAY_MEDIUM);
         out.writeShort(len);
         for (float v : obj) out.writeFloat(v);
      } else {
         out.writeByte(ID_ARRAY_LARGE);
         out.writeInt(len);
         for (float v : obj) out.writeFloat(v);
      }
   }

   private void writeIntArray(int[] obj, ObjectOutput out) throws IOException {
      final int len = obj.length;
      if (len == 0) {
         out.writeByte(ID_ARRAY_EMPTY);
      } else if (len <= 256) {
         out.writeByte(ID_ARRAY_SMALL);
         out.writeByte(len);
         for (int v : obj) out.writeInt(v);
      } else if (len <= 65536) {
         out.writeByte(ID_ARRAY_MEDIUM);
         out.writeShort(len);
         for (int v : obj) out.writeInt(v);
      } else {
         out.writeByte(ID_ARRAY_LARGE);
         out.writeInt(len);
         for (int v : obj) out.writeInt(v);
      }
   }

   private void writeLongArray(long[] obj, ObjectOutput out) throws IOException {
      final int len = obj.length;
      if (len == 0) {
         out.writeByte(ID_ARRAY_EMPTY);
      } else if (len <= 256) {
         out.writeByte(ID_ARRAY_SMALL);
         out.writeByte(len);
         for (long v : obj) out.writeLong(v);
      } else if (len <= 65536) {
         out.writeByte(ID_ARRAY_MEDIUM);
         out.writeShort(len);
         for (long v : obj) out.writeLong(v);
      } else {
         out.writeByte(ID_ARRAY_LARGE);
         out.writeInt(len);
         for (long v : obj) out.writeLong(v);
      }
   }

   private void writeShortArray(short[] obj, ObjectOutput out) throws IOException {
      final int len = obj.length;
      if (len == 0) {
         out.writeByte(ID_ARRAY_EMPTY);
      } else if (len <= 256) {
         out.writeByte(ID_ARRAY_SMALL);
         out.writeByte(len);
         for (short v : obj) out.writeShort(v);
      } else if (len <= 65536) {
         out.writeByte(ID_ARRAY_MEDIUM);
         out.writeShort(len);
         for (short v : obj) out.writeShort(v);
      } else {
         out.writeByte(ID_ARRAY_LARGE);
         out.writeInt(len);
         for (short v : obj) out.writeShort(v);
      }
   }

   private void writeObjectArray(Object[] obj, ObjectOutput out) throws IOException {
      final int len = obj.length;
      if (len == 0) {
         out.writeByte(ID_ARRAY_EMPTY);
      } else if (len <= 256) {
         out.writeByte(ID_ARRAY_SMALL);
         out.writeByte(len);
         for (Object v : obj) out.writeObject(v);
      } else if (len <= 65536) {
         out.writeByte(ID_ARRAY_MEDIUM);
         out.writeShort(len);
         for (Object v : obj) out.writeObject(v);
      } else {
         out.writeByte(ID_ARRAY_LARGE);
         out.writeInt(len);
         for (Object v : obj) out.writeObject(v);
      }
   }

   @SuppressWarnings("unchecked")
   private String readString(ObjectInput in) {
      return enc.decodeString(in); // Counterpart to Encoding.encodeString()
   }

   private byte[] readByteArray(ObjectInput in) throws IOException {
      byte type = in.readByte();
      switch (type) {
         case ID_ARRAY_EMPTY:
            return new byte[]{};
         case ID_ARRAY_SMALL:
            return readFully(mkByteArray(in.readUnsignedByte(), ID_SMALL), in);
         case ID_ARRAY_MEDIUM:
            return readFully(mkByteArray(in.readUnsignedShort(), ID_MEDIUM), in);
         case ID_ARRAY_LARGE:
            return readFully(new byte[in.readInt()], in);
         default:
            throw new IOException("Unknown array type: " + Integer.toHexString(type));
      }
   }

   private byte[] mkByteArray(int len, int limit) {
      return new byte[len == 0 ? limit : len];
   }

   private byte[] readFully(byte[] arr, ObjectInput in) throws IOException {
      in.readFully(arr);
      return arr;
   }

   private boolean[] readBooleanArray(ObjectInput in) throws IOException {
      byte type = in.readByte();
      int len;
      switch (type) {
         case ID_ARRAY_EMPTY:
            return new boolean[]{};
         case ID_ARRAY_SMALL:
            return readBooleans(mkBooleanArray(in.readUnsignedByte(), ID_SMALL), in);
         case ID_ARRAY_MEDIUM:
            return readBooleans(mkBooleanArray(in.readUnsignedShort(), ID_MEDIUM), in);
         case ID_ARRAY_LARGE:
            return readBooleans(new boolean[in.readInt()], in);
         default:
            throw new IOException("Unknown array type: " + Integer.toHexString(type));
      }
   }

   private boolean[] mkBooleanArray(int len, int limit) {
      return new boolean[len == 0 ? limit : len];
   }

   private boolean[] readBooleans(boolean[] arr, ObjectInput in) throws IOException {
      final int len = arr.length;
      int v;
      int bc = len & ~7;
      for (int i = 0; i < bc; ) {
         v = in.readByte();
         arr[i++] = (v & 1) != 0;
         arr[i++] = (v & 2) != 0;
         arr[i++] = (v & 4) != 0;
         arr[i++] = (v & 8) != 0;
         arr[i++] = (v & 16) != 0;
         arr[i++] = (v & 32) != 0;
         arr[i++] = (v & 64) != 0;
         arr[i++] = (v & 128) != 0;
      }
      if (bc < len) {
         v = in.readByte();
         switch (len & 7) {
            case 7:
               arr[bc + 6] = (v & 64) != 0;
            case 6:
               arr[bc + 5] = (v & 32) != 0;
            case 5:
               arr[bc + 4] = (v & 16) != 0;
            case 4:
               arr[bc + 3] = (v & 8) != 0;
            case 3:
               arr[bc + 2] = (v & 4) != 0;
            case 2:
               arr[bc + 1] = (v & 2) != 0;
            case 1:
               arr[bc] = (v & 1) != 0;
         }
      }
      return arr;
   }

   private char[] readCharArray(ObjectInput in) throws IOException {
      byte type = in.readByte();
      switch (type) {
         case ID_ARRAY_EMPTY:
            return new char[]{};
         case ID_ARRAY_SMALL:
            return readChars(mkCharArray(in.readUnsignedByte(), ID_SMALL), in);
         case ID_ARRAY_MEDIUM:
            return readChars(mkCharArray(in.readUnsignedShort(), ID_MEDIUM), in);
         case ID_ARRAY_LARGE:
            return readChars(new char[in.readInt()], in);
         default:
            throw new IOException("Unknown array type: " + Integer.toHexString(type));
      }
   }

   private char[] mkCharArray(int len, int limit) {
      return new char[len == 0 ? limit : len];
   }

   private char[] readChars(char[] arr, ObjectInput in) throws IOException {
      final int len = arr.length;
      for (int i = 0; i < len; i ++) arr[i] = in.readChar();
      return arr;
   }

   private double[] readDoubleArray(ObjectInput in) throws IOException {
      byte type = in.readByte();
      switch (type) {
         case ID_ARRAY_EMPTY:
            return new double[]{};
         case ID_ARRAY_SMALL:
            return readDoubles(mkDoubleArray(in.readUnsignedByte(), ID_SMALL), in);
         case ID_ARRAY_MEDIUM:
            return readDoubles(mkDoubleArray(in.readUnsignedShort(), ID_MEDIUM), in);
         case ID_ARRAY_LARGE:
            return readDoubles(new double[in.readInt()], in);
         default:
            throw new IOException("Unknown array type: " + Integer.toHexString(type));
      }
   }

   private double[] mkDoubleArray(int len, int limit) {
      return new double[len == 0 ? limit : len];
   }

   private double[] readDoubles(double[] arr, ObjectInput in) throws IOException {
      final int len = arr.length;
      for (int i = 0; i < len; i ++) arr[i] = in.readDouble();
      return arr;
   }

   private float[] readFloatArray(ObjectInput in) throws IOException {
      byte type = in.readByte();
      switch (type) {
         case ID_ARRAY_EMPTY:
            return new float[]{};
         case ID_ARRAY_SMALL:
            return readFloats(mkFloatArray(in.readUnsignedByte(), ID_SMALL), in);
         case ID_ARRAY_MEDIUM:
            return readFloats(mkFloatArray(in.readUnsignedShort(), ID_MEDIUM), in);
         case ID_ARRAY_LARGE:
            return readFloats(new float[in.readInt()], in);
         default:
            throw new IOException("Unknown array type: " + Integer.toHexString(type));
      }
   }

   private float[] mkFloatArray(int len, int limit) {
      return new float[len == 0 ? limit : len];
   }

   private float[] readFloats(float[] arr, ObjectInput in) throws IOException {
      final int len = arr.length;
      for (int i = 0; i < len; i ++) arr[i] = in.readFloat();
      return arr;
   }

   private int[] readIntArray(ObjectInput in) throws IOException {
      byte type = in.readByte();
      switch (type) {
         case ID_ARRAY_EMPTY:
            return new int[]{};
         case ID_ARRAY_SMALL:
            return readInts(mkIntArray(in.readUnsignedByte(), ID_SMALL), in);
         case ID_ARRAY_MEDIUM:
            return readInts(mkIntArray(in.readUnsignedShort(), ID_MEDIUM), in);
         case ID_ARRAY_LARGE:
            return readInts(new int[in.readInt()], in);
         default:
            throw new IOException("Unknown array type: " + Integer.toHexString(type));
      }
   }

   private int[] mkIntArray(int len, int limit) {
      return new int[len == 0 ? limit : len];
   }

   private int[] readInts(int[] arr, ObjectInput in) throws IOException {
      final int len = arr.length;
      for (int i = 0; i < len; i ++) arr[i] = in.readInt();
      return arr;
   }

   private long[] readLongArray(ObjectInput in) throws IOException {
      byte type = in.readByte();
      switch (type) {
         case ID_ARRAY_EMPTY:
            return new long[]{};
         case ID_ARRAY_SMALL:
            return readLongs(mkLongArray(in.readUnsignedByte(), ID_SMALL), in);
         case ID_ARRAY_MEDIUM:
            return readLongs(mkLongArray(in.readUnsignedShort(), ID_MEDIUM), in);
         case ID_ARRAY_LARGE:
            return readLongs(new long[in.readInt()], in);
         default:
            throw new IOException("Unknown array type: " + Integer.toHexString(type));
      }
   }

   private long[] mkLongArray(int len, int limit) {
      return new long[len == 0 ? limit : len];
   }

   private long[] readLongs(long[] arr, ObjectInput in) throws IOException {
      final int len = arr.length;
      for (int i = 0; i < len; i ++) arr[i] = in.readLong();
      return arr;
   }

   private short[] readShortArray(ObjectInput in) throws IOException {
      byte type = in.readByte();
      switch (type) {
         case ID_ARRAY_EMPTY:
            return new short[]{};
         case ID_ARRAY_SMALL:
            return readShorts(mkShortArray(in.readUnsignedByte(), ID_SMALL), in);
         case ID_ARRAY_MEDIUM:
            return readShorts(mkShortArray(in.readUnsignedShort(), ID_MEDIUM), in);
         case ID_ARRAY_LARGE:
            return readShorts(new short[in.readInt()], in);
         default:
            throw new IOException("Unknown array type: " + Integer.toHexString(type));
      }
   }

   private short[] mkShortArray(int len, int limit) {
      return new short[len == 0 ? limit : len];
   }

   private short[] readShorts(short[] arr, ObjectInput in) throws IOException {
      final int len = arr.length;
      for (int i = 0; i < len; i ++) arr[i] = in.readShort();
      return arr;
   }

   private Object[] readObjectArray(ObjectInput in) throws IOException, ClassNotFoundException {
      byte type = in.readByte();
      switch (type) {
         case ID_ARRAY_EMPTY:
            return new Object[]{};
         case ID_ARRAY_SMALL:
            return readObjects(mkObjectArray(in.readUnsignedByte(), ID_SMALL), in);
         case ID_ARRAY_MEDIUM:
            return readObjects(mkObjectArray(in.readUnsignedShort(), ID_MEDIUM), in);
         case ID_ARRAY_LARGE:
            return readObjects(new Object[in.readInt()], in);
         default:
            throw new IOException("Unknown array type: " + Integer.toHexString(type));
      }
   }

   private Object[] mkObjectArray(int len, int limit) {
      return new Object[len == 0 ? limit : len];
   }

   private Object[] readObjects(Object[] arr, ObjectInput in) throws IOException, ClassNotFoundException {
      final int len = arr.length;
      for (int i = 0; i < len; i ++) arr[i] = in.readObject();
      return arr;
   }


}
