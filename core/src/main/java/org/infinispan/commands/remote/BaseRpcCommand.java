package org.infinispan.commands.remote;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Set;

import org.infinispan.commands.TopologyAffectedCommand;
import org.infinispan.commons.marshall.AdvancedExternalizer;
import org.infinispan.marshall.DeltaAwareObjectOutput;
import org.infinispan.marshall.core.Ids;
import org.infinispan.remoting.transport.Address;
import org.infinispan.util.ByteString;

public abstract class BaseRpcCommand implements CacheRpcCommand, AdvancedExternalizer<CacheRpcCommand> {
   protected final ByteString cacheName;

   private Address origin;

   protected BaseRpcCommand(ByteString cacheName) {
      this.cacheName = cacheName;
   }

   @Override
   public ByteString getCacheName() {
      return cacheName;
   }

   @Override
   public String toString() {
      return getClass().getSimpleName() + "{" +
            "cacheName='" + cacheName + '\'' +
            '}';
   }

   @Override
   public Address getOrigin() {
      return origin;
   }

   @Override
   public void setOrigin(Address origin) {
      this.origin = origin;
   }

   @Override
   public boolean canBlock() {
      return false;
   }

   @Override
   public Set<Class<? extends CacheRpcCommand>> getTypeClasses() {
      return null; // Unused
   }

   @Override
   public Integer getId() {
      return Ids.CACHE_RPC_COMMAND;
   }

   @Override
   public void writeObject(ObjectOutput out, CacheRpcCommand obj) throws IOException {
      // TODO: Deal with core vs user commands
      out.writeByte(0);
      out.writeShort(obj.getCommandId());

      ByteString cacheName = obj.getCacheName();
      ByteString.writeObject(out, cacheName);

      DeltaAwareObjectOutput deltaAwareObjectOutput = out instanceof DeltaAwareObjectOutput ?
            (DeltaAwareObjectOutput) out :
            new DeltaAwareObjectOutput(out);
      obj.writeTo(deltaAwareObjectOutput);
      if (obj instanceof TopologyAffectedCommand)
         out.writeInt(((TopologyAffectedCommand) obj).getTopologyId());
   }

   @Override
   public CacheRpcCommand readObject(ObjectInput in) throws IOException, ClassNotFoundException {
      return null; // CacheRpcCommandExternalizer
   }

//   public CacheRpcCommand fromStream(byte id, ByteString cacheName) {
//      CacheRpcCommand command;
//      switch (id) {
//         case LockControlCommand.COMMAND_ID:
//            command = new LockControlCommand(cacheName);
//            break;
//         case PrepareCommand.COMMAND_ID:
//            command = new PrepareCommand(cacheName);
//            break;
//         case VersionedPrepareCommand.COMMAND_ID:
//            command = new VersionedPrepareCommand(cacheName);
//            break;
//         case TotalOrderNonVersionedPrepareCommand.COMMAND_ID:
//            command = new TotalOrderNonVersionedPrepareCommand(cacheName);
//            break;
//         case TotalOrderVersionedPrepareCommand.COMMAND_ID:
//            command = new TotalOrderVersionedPrepareCommand(cacheName);
//            break;
//         case CommitCommand.COMMAND_ID:
//            command = new CommitCommand(cacheName);
//            break;
//         case VersionedCommitCommand.COMMAND_ID:
//            command = new VersionedCommitCommand(cacheName);
//            break;
//         case TotalOrderCommitCommand.COMMAND_ID:
//            command = new TotalOrderCommitCommand(cacheName);
//            break;
//         case TotalOrderVersionedCommitCommand.COMMAND_ID:
//            command = new TotalOrderVersionedCommitCommand(cacheName);
//            break;
//         case RollbackCommand.COMMAND_ID:
//            command = new RollbackCommand(cacheName);
//            break;
//         case TotalOrderRollbackCommand.COMMAND_ID:
//            command = new TotalOrderRollbackCommand(cacheName);
//            break;
//         case SingleRpcCommand.COMMAND_ID:
//            command = new SingleRpcCommand(cacheName);
//            break;
//         case ClusteredGetCommand.COMMAND_ID:
//            command = new ClusteredGetCommand(cacheName);
//            break;
//         case StateRequestCommand.COMMAND_ID:
//            command = new StateRequestCommand(cacheName);
//            break;
//         case StateResponseCommand.COMMAND_ID:
//            command = new StateResponseCommand(cacheName);
//            break;
////            case RemoveCacheCommand.COMMAND_ID:
////               command = new RemoveCacheCommand(cacheName, cacheManager);
////               break;
//         case TxCompletionNotificationCommand.COMMAND_ID:
//            command = new TxCompletionNotificationCommand(cacheName);
//            break;
//         case GetInDoubtTransactionsCommand.COMMAND_ID:
//            command = new GetInDoubtTransactionsCommand(cacheName);
//            break;
//         case DistributedExecuteCommand.COMMAND_ID:
//            command = new DistributedExecuteCommand(cacheName);
//            break;
//         case GetInDoubtTxInfoCommand.COMMAND_ID:
//            command = new GetInDoubtTxInfoCommand(cacheName);
//            break;
//         case CompleteTransactionCommand.COMMAND_ID:
//            command = new CompleteTransactionCommand(cacheName);
//            break;
//         case CreateCacheCommand.COMMAND_ID:
//            command = new CreateCacheCommand(cacheName);
//            break;
//         case XSiteAdminCommand.COMMAND_ID:
//            command = new XSiteAdminCommand(cacheName);
//            break;
//         case CancelCommand.COMMAND_ID:
//            command = new CancelCommand(cacheName);
//            break;
//         case XSiteStateTransferControlCommand.COMMAND_ID:
//            command = new XSiteStateTransferControlCommand(cacheName);
//            break;
//         case XSiteStatePushCommand.COMMAND_ID:
//            command = new XSiteStatePushCommand(cacheName);
//            break;
//         case SingleXSiteRpcCommand.COMMAND_ID:
//            command = new SingleXSiteRpcCommand(cacheName);
//            break;
//         case ClusteredGetAllCommand.COMMAND_ID:
//            command = new ClusteredGetAllCommand(cacheName);
//            break;
//         case StreamRequestCommand.COMMAND_ID:
//            command = new StreamRequestCommand(cacheName);
//            break;
//         case StreamSegmentResponseCommand.COMMAND_ID:
//            command = new StreamSegmentResponseCommand<>(cacheName);
//            break;
//         case StreamResponseCommand.COMMAND_ID:
//            command = new StreamResponseCommand(cacheName);
//            break;
//         default:
//            throw new CacheException("Unknown command id " + id + "!");
//      }
////      }
////      else {
////         ModuleCommandFactory mcf = commandFactories.get(id);
////         if (mcf != null)
////            return mcf.fromStream(id, cacheName);
////         else
////            throw new CacheException("Unknown command id " + id + "!");
////      }
//      return command;
//   }

}
