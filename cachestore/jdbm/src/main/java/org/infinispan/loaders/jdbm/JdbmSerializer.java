package org.infinispan.loaders.jdbm;

import java.io.IOException;

import org.infinispan.marshall.Marshaller;

import jdbm.helper.Serializer;

/**
 * Uses the configured (runtime) {@link Marshaller} of the cache.
 * This Serializer is thus not really serializiable.
 * 
 * @author Elias Ross
 */
@SuppressWarnings("serial")
public class JdbmSerializer implements Serializer {
    
    private transient Marshaller marshaller;

    /**
     * Constructs a new JdbmSerializer.
     */
    public JdbmSerializer(Marshaller marshaller) {
        if (marshaller == null)
            throw new NullPointerException("marshaller");
        this.marshaller = marshaller;
    }

    public Object deserialize(byte[] buf) throws IOException {
        try {
            return marshaller.objectFromByteBuffer(buf);
        } catch (ClassNotFoundException e) {
            throw (IOException)new IOException().initCause(e);
        }
    }

    public byte[] serialize(Object obj) throws IOException {
        return marshaller.objectToByteBuffer(obj);
    }

}
