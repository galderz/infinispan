package org.infinispan.commons.marshall;

public interface PluggableMarshaller {

   void writeStringUTF(ObjectOutput out, String s);
   void writeString(ObjectOutput out, String s);
   void writeObjectReusable(ObjectOutput out, Object o);
   void writeObject(ObjectOutput out, Object o);

}
