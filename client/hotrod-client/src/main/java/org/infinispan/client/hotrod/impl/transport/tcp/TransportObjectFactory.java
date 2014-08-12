package org.infinispan.client.hotrod.impl.transport.tcp;

import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.pool.BaseKeyedPoolableObjectFactory;
import org.infinispan.client.hotrod.impl.operations.PingOperation;
import org.infinispan.client.hotrod.impl.protocol.Codec;
import org.infinispan.client.hotrod.impl.transport.Transport;
import org.infinispan.client.hotrod.logging.Log;
import org.infinispan.client.hotrod.logging.LogFactory;

/**
 * @author Mircea.Markus@jboss.com
 * @since 4.1
 */
public class TransportObjectFactory
      extends BaseKeyedPoolableObjectFactory<SocketAddress, TcpTransport> {

   private static final Log log = LogFactory.getLog(TransportObjectFactory.class);
   protected final TcpTransportFactory tcpTransportFactory;
   protected final AtomicInteger topologyId;
   protected final boolean pingOnStartup;
   protected volatile boolean firstPingExecuted = false;
   protected final Codec codec;

   public TransportObjectFactory(Codec codec, TcpTransportFactory tcpTransportFactory, AtomicInteger topologyId, boolean pingOnStartup) {
      this.tcpTransportFactory = tcpTransportFactory;
      this.topologyId = topologyId;
      this.pingOnStartup = pingOnStartup;
      this.codec = codec;
   }

   @Override
   public TcpTransport makeObject(SocketAddress address) throws Exception {
      TcpTransport transport;
      if (tcpTransportFactory.getSSLContext() == null) {
         transport = new TcpTransport(address, tcpTransportFactory);
      } else {
         transport = new SSLTransport(address, tcpTransportFactory);
      }
      if (log.isTraceEnabled()) {
         log.tracef("%08x Created tcp transport: %s", this.hashCode(), transport);
      }
      if (pingOnStartup && !firstPingExecuted) {
         log.trace("Executing first ping!");
         firstPingExecuted = true;

         // Don't ignore exceptions from ping() command, since
         // they indicate that the transport instance is invalid.
         ping(transport, topologyId);
      }
      return transport;
   }

   protected PingOperation.PingResult ping(Transport transport, AtomicInteger topologyId) {
      PingOperation po = new PingOperation(codec, topologyId, transport);
      return po.executeSync();
   }

   /**
    * This will be called by the test thread when testWhileIdle==true.
    */
   @Override
   public boolean validateObject(SocketAddress address, TcpTransport transport) {
      try {
         boolean valid = ping(transport, topologyId) == PingOperation.PingResult.SUCCESS;
         log.tracef("Is connection %s valid? %s", transport, valid);
         return valid;
      } catch (Throwable e) {
         log.tracef(e, "Error validating the connection %s. Marking it as invalid.", transport);
         return false;
      }
   }

   @Override
   public void destroyObject(SocketAddress address, TcpTransport transport) throws Exception {
//      if (log.isTraceEnabled()) {
//         try {
//            throw new Exception("Stacktrace");
//         } catch (Exception e) {
//            log.tracef(e, "%08x, About to destroy tcp transport: %s", this.hashCode(), transport);
//         }
//      }
      transport.destroy();
   }

   @Override
   public void activateObject(SocketAddress address, TcpTransport transport) throws Exception {
      super.activateObject(address, transport);
//      if (log.isTraceEnabled()) {
//         try {
//            throw new Exception("Stacktrace");
//         } catch (Exception e) {
//            log.tracef(e, "Fetching from pool: %s", transport);
//         }
//      }
   }

   @Override
   public void passivateObject(SocketAddress address, TcpTransport transport) throws Exception {
      super.passivateObject(address, transport);
//      if (log.isTraceEnabled()) {
//         try {
//            throw new Exception("Stacktrace");
//         } catch (Exception e) {
//            log.tracef(e, "Returning to pool: %s", transport);
//         }
//      }
   }
}
