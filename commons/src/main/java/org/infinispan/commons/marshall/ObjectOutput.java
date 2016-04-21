package org.infinispan.commons.marshall;

public interface ObjectOutput {

   void write(byte b[]);
   void write(byte b[], int off, int len);
   void writeBoolean(boolean v);
   void writeByte(int v);
   void writeShort(int v);
   void writeChar(int v);
   void writeInt(int v);
   void writeLong(long v);
   void writeFloat(float v);
   void writeDouble(double v);

   // Methods below will be pluggable via user-defined marshaller
   void writeStringUTF(String s);
   void writeString(String s);
   void writeObjectReusable(Object o);
   void writeObject(Object o);

}
