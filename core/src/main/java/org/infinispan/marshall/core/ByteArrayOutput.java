package org.infinispan.marshall.core;

import org.infinispan.commons.marshall.ObjectOutput;
import org.infinispan.commons.marshall.PluggableMarshaller;

/**
 * Object output implementation backed by a byte[].
 */
public final class ByteArrayOutput implements ObjectOutput {

   public static final int DEFAULT_DOUBLING_SIZE = 4 * 1024 * 1024; // 4MB

   final PluggableMarshaller marshaller;

   byte bytes[];
   int count;

//   // TODO: Avoid this instance variable, e.g. beforeExternal
//   int beforeExternalPos;

   public ByteArrayOutput(int size, PluggableMarshaller marshaller) {
      this.bytes = new byte[size];
      this.marshaller = marshaller;
   }

   @Override
   public void write(byte[] b) {
      write(bytes, 0, bytes.length);
   }

   @Override
   public void write(byte[] b, int off, int len) {
      if ((off < 0) || (off > b.length) || (len < 0) ||
            ((off + len) > b.length) || ((off + len) < 0)) {
         throw new IndexOutOfBoundsException();
      } else if (len == 0) {
         return;
      }

      int newcount = checkCapacity(len);
      System.arraycopy(b, off, bytes, count, len);
      count = newcount;
   }

   private int checkCapacity(int len) {
      int newcount = count + len;
      if (newcount > bytes.length) {
         byte newbuf[] = new byte[getNewBufferSize(bytes.length, newcount)];
         System.arraycopy(bytes, 0, newbuf, 0, count);
         bytes = newbuf;
      }
      return newcount;
   }

   /**
    * Gets the number of bytes to which the internal buffer should be resized.
    *
    * @param curSize    the current number of bytes
    * @param minNewSize the minimum number of bytes required
    * @return the size to which the internal buffer should be resized
    */
   private int getNewBufferSize(int curSize, int minNewSize) {
      if (curSize <= DEFAULT_DOUBLING_SIZE)
         return Math.max(curSize << 1, minNewSize);
      else
         return Math.max(curSize + (curSize >> 2), minNewSize);
   }

   @Override
   public void writeBoolean(boolean v) {
      writeByte((byte) (v ? 1 : 0));
   }

   @Override
   public void writeByte(int v) {
      int newcount = checkCapacity(1);
      bytes[count] = (byte) v;
      count = newcount;
   }

   @Override
   public void writeShort(int v) {
      int newcount = checkCapacity(2);
      final int s = count;
      bytes[s] = (byte) (v >> 8);
      bytes[s+1] = (byte) v;
      count = newcount;
   }

   @Override
   public void writeChar(int v) {
      int newcount = checkCapacity(2);
      final int s = count;
      bytes[s] = (byte) (v >> 8);
      bytes[s+1] = (byte) v;
      count = newcount;
   }

   @Override
   public void writeInt(int v) {
      int newcount = checkCapacity(4);
      final int s = count;
      bytes[s] = (byte) (v >> 24);
      bytes[s+1] = (byte) (v >> 16);
      bytes[s+2] = (byte) (v >> 8);
      bytes[s+3] = (byte) v;
      count = newcount;
   }

   @Override
   public void writeLong(long v) {
      int newcount = checkCapacity(8);
      final int s = count;
      bytes[s] = (byte) (v >> 56L);
      bytes[s+1] = (byte) (v >> 48L);
      bytes[s+2] = (byte) (v >> 40L);
      bytes[s+3] = (byte) (v >> 32L);
      bytes[s+4] = (byte) (v >> 24L);
      bytes[s+5] = (byte) (v >> 16L);
      bytes[s+6] = (byte) (v >> 8L);
      bytes[s+7] = (byte) v;
      count = newcount;
   }

   @Override
   public void writeFloat(float v) {
      final int bits = Float.floatToIntBits(v);
      int newcount = checkCapacity(4);
      final int s = count;
      bytes[s] = (byte) (bits >> 24);
      bytes[s+1] = (byte) (bits >> 16);
      bytes[s+2] = (byte) (bits >> 8);
      bytes[s+3] = (byte) bits;
      count = newcount;
   }

   @Override
   public void writeDouble(double v) {
      final long bits = Double.doubleToLongBits(v);
      int newcount = checkCapacity(8);
      final int s = count;
      bytes[s] = (byte) (bits >> 56L);
      bytes[s+1] = (byte) (bits >> 48L);
      bytes[s+2] = (byte) (bits >> 40L);
      bytes[s+3] = (byte) (bits >> 32L);
      bytes[s+4] = (byte) (bits >> 24L);
      bytes[s+5] = (byte) (bits >> 16L);
      bytes[s+6] = (byte) (bits >> 8L);
      bytes[s+7] = (byte) bits;
      count = newcount;
   }

   @Override
   public void writeStringUTF(String s) {
      // TODO: Customise this generated block
   }

   @Override
   public void writeString(String s) {
      // TODO: Customise this generated block
   }

   @Override
   public void writeObjectReusable(Object o) {
      // TODO: Customise this generated block
   }

   @Override
   public void writeObject(Object o) {
      // TODO: Customise this generated block
   }

}
