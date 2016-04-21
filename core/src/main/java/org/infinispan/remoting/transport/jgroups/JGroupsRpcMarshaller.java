package org.infinispan.remoting.transport.jgroups;

import org.infinispan.topology.CacheTopologyControlCommand;
import org.jgroups.blocks.RpcDispatcher;
import org.jgroups.util.Buffer;

public class JGroupsRpcMarshaller implements RpcDispatcher.Marshaller {

   @Override
   public Buffer objectToBuffer(Object obj) throws Exception {
      if (obj instanceof CacheTopologyControlCommand) {

      }
      return null;  // TODO: Customise this generated block
   }

   @Override
   public Object objectFromBuffer(byte[] buf, int offset, int length) throws Exception {
      return null;  // TODO: Customise this generated block
   }

}
