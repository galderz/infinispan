package org.infinispan.commands;

import static org.infinispan.xsite.XSiteAdminCommand.AdminOperation;
import static org.infinispan.xsite.statetransfer.XSiteStateTransferControlCommand.StateTransferControl;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.transaction.xa.Xid;

import org.infinispan.commands.control.LockControlCommand;
import org.infinispan.commands.functional.Mutation;
import org.infinispan.commands.functional.ReadOnlyKeyCommand;
import org.infinispan.commands.functional.ReadOnlyManyCommand;
import org.infinispan.commands.functional.ReadWriteKeyCommand;
import org.infinispan.commands.functional.ReadWriteKeyValueCommand;
import org.infinispan.commands.functional.ReadWriteManyCommand;
import org.infinispan.commands.functional.ReadWriteManyEntriesCommand;
import org.infinispan.commands.functional.TxReadOnlyKeyCommand;
import org.infinispan.commands.functional.TxReadOnlyManyCommand;
import org.infinispan.commands.functional.WriteOnlyKeyCommand;
import org.infinispan.commands.functional.WriteOnlyKeyValueCommand;
import org.infinispan.commands.functional.WriteOnlyManyCommand;
import org.infinispan.commands.functional.WriteOnlyManyEntriesCommand;
import org.infinispan.commands.read.EntrySetCommand;
import org.infinispan.commands.read.GetAllCommand;
import org.infinispan.commands.read.GetCacheEntryCommand;
import org.infinispan.commands.read.GetKeyValueCommand;
import org.infinispan.commands.read.KeySetCommand;
import org.infinispan.commands.read.SizeCommand;
import org.infinispan.commands.remote.CheckTransactionRpcCommand;
import org.infinispan.commands.remote.ClusteredGetAllCommand;
import org.infinispan.commands.remote.ClusteredGetCommand;
import org.infinispan.commands.remote.GetKeysInGroupCommand;
import org.infinispan.commands.remote.RenewBiasCommand;
import org.infinispan.commands.remote.RevokeBiasCommand;
import org.infinispan.commands.remote.SingleRpcCommand;
import org.infinispan.commands.remote.expiration.RetrieveLastAccessCommand;
import org.infinispan.commands.remote.expiration.UpdateLastAccessCommand;
import org.infinispan.commands.remote.recovery.CompleteTransactionCommand;
import org.infinispan.commands.remote.recovery.GetInDoubtTransactionsCommand;
import org.infinispan.commands.remote.recovery.GetInDoubtTxInfoCommand;
import org.infinispan.commands.remote.recovery.TxCompletionNotificationCommand;
import org.infinispan.commands.triangle.MultiEntriesFunctionalBackupWriteCommand;
import org.infinispan.commands.triangle.MultiKeyFunctionalBackupWriteCommand;
import org.infinispan.commands.triangle.PutMapBackupWriteCommand;
import org.infinispan.commands.triangle.SingleKeyBackupWriteCommand;
import org.infinispan.commands.triangle.SingleKeyFunctionalBackupWriteCommand;
import org.infinispan.commands.tx.CommitCommand;
import org.infinispan.commands.tx.PrepareCommand;
import org.infinispan.commands.tx.RollbackCommand;
import org.infinispan.commands.tx.VersionedCommitCommand;
import org.infinispan.commands.tx.VersionedPrepareCommand;
import org.infinispan.commands.write.BackupAckCommand;
import org.infinispan.commands.write.BackupMultiKeyAckCommand;
import org.infinispan.commands.write.ClearCommand;
import org.infinispan.commands.write.ComputeCommand;
import org.infinispan.commands.write.ComputeIfAbsentCommand;
import org.infinispan.commands.write.EvictCommand;
import org.infinispan.commands.write.ExceptionAckCommand;
import org.infinispan.commands.write.InvalidateCommand;
import org.infinispan.commands.write.InvalidateVersionsCommand;
import org.infinispan.commands.write.PutKeyValueCommand;
import org.infinispan.commands.write.PutMapCommand;
import org.infinispan.commands.write.RemoveCommand;
import org.infinispan.commands.write.RemoveExpiredCommand;
import org.infinispan.commands.write.ReplaceCommand;
import org.infinispan.commands.write.WriteCommand;
import org.infinispan.commons.util.IntSet;
import org.infinispan.container.entries.CacheEntry;
import org.infinispan.encoding.DataConversion;
import org.infinispan.factories.ComponentRegistry;
import org.infinispan.factories.scopes.Scope;
import org.infinispan.factories.scopes.Scopes;
import org.infinispan.functional.EntryView.ReadEntryView;
import org.infinispan.functional.EntryView.ReadWriteEntryView;
import org.infinispan.functional.EntryView.WriteEntryView;
import org.infinispan.functional.impl.Params;
import org.infinispan.metadata.Metadata;
import org.infinispan.notifications.cachelistener.cluster.ClusterEvent;
import org.infinispan.notifications.cachelistener.cluster.MultiClusterEventCommand;
import org.infinispan.reactive.publisher.impl.DeliveryGuarantee;
import org.infinispan.reactive.publisher.impl.commands.batch.CancelPublisherCommand;
import org.infinispan.reactive.publisher.impl.commands.batch.InitialPublisherCommand;
import org.infinispan.reactive.publisher.impl.commands.batch.NextPublisherCommand;
import org.infinispan.reactive.publisher.impl.commands.reduction.ReductionPublisherRequestCommand;
import org.infinispan.remoting.transport.Address;
import org.infinispan.statetransfer.StateChunk;
import org.infinispan.statetransfer.StateRequestCommand;
import org.infinispan.statetransfer.StateResponseCommand;
import org.infinispan.stream.impl.StreamIteratorCloseCommand;
import org.infinispan.stream.impl.StreamIteratorNextCommand;
import org.infinispan.stream.impl.StreamIteratorRequestCommand;
import org.infinispan.stream.impl.StreamRequestCommand;
import org.infinispan.stream.impl.StreamResponseCommand;
import org.infinispan.stream.impl.intops.IntermediateOperation;
import org.infinispan.transaction.xa.GlobalTransaction;
import org.infinispan.xsite.SingleXSiteRpcCommand;
import org.infinispan.xsite.XSiteAdminCommand;
import org.infinispan.xsite.statetransfer.XSiteState;
import org.infinispan.xsite.statetransfer.XSiteStatePushCommand;
import org.infinispan.xsite.statetransfer.XSiteStateTransferControlCommand;
import org.reactivestreams.Publisher;

/**
 * A factory to build commands, initializing and injecting dependencies accordingly.  Commands built for a specific,
 * named cache instance cannot be reused on a different cache instance since most commands contain the cache name it was
 * built for along with references to other named-cache scoped components.
 * <p>
 * Commands returned by the various build*Command methods should be initialised sufficiently for local execution via the
 * interceptor chain, with no calls to  {@link #initializeReplicableCommand(ReplicableCommand, boolean)} required.
 * However, for remote execution, it's assumed that a command will be initialized via {@link
 * #initializeReplicableCommand(ReplicableCommand, boolean)} before being invoked.
 * <p>
 * Note, {@link InitializableCommand} implementations should not rely on access to the {@link
 * org.infinispan.factories.ComponentRegistry} in their constructors for local execution initialization as this leads to
 * duplicated code. Instead implementations of this interface should call {@link InitializableCommand#init(ComponentRegistry,
 * boolean)} before returning the created command.
 *
 * @author Manik Surtani
 * @author Mircea.Markus@jboss.com
 * @author Galder Zamarreño
 * @since 4.0
 */
@Scope(Scopes.NAMED_CACHE)
public interface CommandsFactory {

   /**
    * Builds a PutKeyValueCommand
    * @param key key to put
    * @param value value to put
    * @param segment the segment of the given key
    * @param metadata metadata of entry
    * @param flagsBitSet Command flags provided by cache
    * @return a PutKeyValueCommand
    */
   PutKeyValueCommand buildPutKeyValueCommand(Object key, Object value, int segment, Metadata metadata, long flagsBitSet);

   /**
    * Builds a RemoveCommand
    * @param key key to remove
    * @param value value to check for ina  conditional remove, or null for an unconditional remove.
    * @param segment the segment of the given key
    * @param flagsBitSet Command flags provided by cache
    * @return a RemoveCommand
    */
   RemoveCommand buildRemoveCommand(Object key, Object value, int segment, long flagsBitSet);

   /**
    * Builds an InvalidateCommand
    * @param flagsBitSet Command flags provided by cache
    * @param keys keys to invalidate
    * @return an InvalidateCommand
    */
   InvalidateCommand buildInvalidateCommand(long flagsBitSet, Object... keys);

   /**
    * Builds an InvalidateFromL1Command
    *
    * @param flagsBitSet Command flags provided by cache
    * @param keys keys to invalidate
    * @return an InvalidateFromL1Command
    */
   InvalidateCommand buildInvalidateFromL1Command(long flagsBitSet, Collection<Object> keys);

   /**
    * @see #buildInvalidateFromL1Command(long, Collection)
    */
   InvalidateCommand buildInvalidateFromL1Command(Address origin, long flagsBitSet, Collection<Object> keys);

   /**
    * Builds an expired remove command that is used to remove only a specific entry when it expires via lifespan
    * @param key the key of the expired entry
    * @param value the value of the entry when it was expired
    * @param segment the segment of the given key
    * @param lifespan the lifespan that expired from the command
    * @param flagsBitSet Command flags provided by cache
    * @return a RemovedExpiredCommand
    */
   RemoveExpiredCommand buildRemoveExpiredCommand(Object key, Object value, int segment, Long lifespan, long flagsBitSet);

   /**
    * Builds an expired remove command that is used to remove only a specific entry when it expires via maxIdle
    * @param key the key of the expired entry
    * @param value the value of the entry when it was expired
    * @param segment the segment of the given key
    * @param flagsBitSet Command flags provided by cache
    * @return a RemovedExpiredCommand
    */
   RemoveExpiredCommand buildRemoveExpiredCommand(Object key, Object value, int segment, long flagsBitSet);

   /**
    * Builds a retrieve max idle command that is used to get the last access time for a given key.
    * @param key the key of the entry to get the last access time of
    * @return a RetrieveLastAccessCommand
    */
   RetrieveLastAccessCommand buildRetrieveLastAccessCommand(Object key, Object value, int segment);

   /**
    * Builds an update last access command that is used to update the last access time for a given key.
    * @param key the key of the entry to update the last access time of
    * @param accessTime the time to set the access time to
    * @return a UpdateLastAccessCommand
    */
   UpdateLastAccessCommand buildUpdateLastAccessCommand(Object key, int segment, long accessTime);

   /**
    * Builds a ReplaceCommand
    * @param key key to replace
    * @param oldValue existing value to check for if conditional, null if unconditional.
    * @param newValue value to replace with
    * @param segment the segment of the given key
    * @param metadata metadata of entry
    * @param flagsBitSet Command flags provided by cache
    * @return a ReplaceCommand
    */
   ReplaceCommand buildReplaceCommand(Object key, Object oldValue, Object newValue, int segment, Metadata metadata,
         long flagsBitSet);


   /**
    * Builds a ComputeCommand
    * @param key key to compute if this key is absent
    * @param mappingFunction BiFunction for the key and the value
    * @param computeIfPresent flag to apply as computeIfPresent mode
    * @param segment the segment of the given key
    * @param metadata metadata of entry
    * @param flagsBitSet Command flags provided by cache
    * @return a ComputeCommand
    */
   ComputeCommand buildComputeCommand(Object key, BiFunction mappingFunction, boolean computeIfPresent, int segment,
         Metadata metadata, long flagsBitSet);

   /**
    * Builds a ComputeIfAbsentCommand
    * @param key key to compute if this key is absent
    * @param mappingFunction mappingFunction for the key
    * @param segment the segment of the given key
    * @param metadata metadata of entry
    * @param flagsBitSet Command flags provided by cache
    * @return a ComputeCommand
    */
   ComputeIfAbsentCommand buildComputeIfAbsentCommand(Object key, Function mappingFunction, int segment,
         Metadata metadata, long flagsBitSet);

   /**
    * Builds a SizeCommand
    * @param flagsBitSet Command flags provided by cache
    * @return a SizeCommand
    */
   SizeCommand buildSizeCommand(long flagsBitSet);

   /**
    * Builds a GetKeyValueCommand
    * @param key key to get
    * @param segment the segment of the given key
    * @param flagsBitSet Command flags provided by cache
    * @return a GetKeyValueCommand
    */
   GetKeyValueCommand buildGetKeyValueCommand(Object key, int segment, long flagsBitSet);

   /**
    * Builds a GetCacheEntryCommand
    * @param key key to get
    * @param segment the segment for the key
    * @param flagsBitSet Command flags provided by cache
    * @return a GetCacheEntryCommand
    */
   GetCacheEntryCommand buildGetCacheEntryCommand(Object key, int segment, long flagsBitSet);

   /**
    * Builds a GetAllCommand
    * @param keys keys to get
    * @param flagsBitSet Command flags provided by cache
    * @param returnEntries boolean indicating whether entire cache entries are
    *                      returned, otherwise return just the value parts
    * @return a GetKeyValueCommand
    */
   GetAllCommand buildGetAllCommand(Collection<?> keys, long flagsBitSet, boolean returnEntries);

   /**
    * Builds a KeySetCommand
    * @param flagsBitSet Command flags provided by cache
    * @return a KeySetCommand
    */
   KeySetCommand buildKeySetCommand(long flagsBitSet);

   /**
    * Builds a EntrySetCommand
    * @param flagsBitSet Command flags provided by cache
    * @return a EntrySetCommand
    */
   EntrySetCommand buildEntrySetCommand(long flagsBitSet);

   /**
    * Builds a PutMapCommand
    * @param map map containing key/value entries to put
    * @param metadata metadata of entry
    * @param flagsBitSet Command flags provided by cache
    * @return a PutMapCommand
    */
   PutMapCommand buildPutMapCommand(Map<?, ?> map, Metadata metadata, long flagsBitSet);

   /**
    * Builds a ClearCommand
    * @param flagsBitSet Command flags provided by cache
    * @return a ClearCommand
    */
   ClearCommand buildClearCommand(long flagsBitSet);

   /**
    * Builds an EvictCommand
    * @param key key to evict
    * @param segment the segment for the key
    * @param flagsBitSet Command flags provided by cache
    * @return an EvictCommand
    */
   EvictCommand buildEvictCommand(Object key, int segment, long flagsBitSet);

   /**
    * Builds a PrepareCommand
    * @param gtx global transaction associated with the prepare
    * @param modifications list of modifications
    * @param onePhaseCommit is this a one-phase or two-phase transaction?
    * @return a PrepareCommand
    */
   PrepareCommand buildPrepareCommand(GlobalTransaction gtx, List<WriteCommand> modifications, boolean onePhaseCommit);

   /**
    * Builds a VersionedPrepareCommand
    *
    * @param gtx global transaction associated with the prepare
    * @param modifications list of modifications
    * @param onePhase
    * @return a VersionedPrepareCommand
    */
   VersionedPrepareCommand buildVersionedPrepareCommand(GlobalTransaction gtx, List<WriteCommand> modifications, boolean onePhase);

   /**
    * Builds a CommitCommand
    * @param gtx global transaction associated with the commit
    * @return a CommitCommand
    */
   CommitCommand buildCommitCommand(GlobalTransaction gtx);

   /**
    * Builds a VersionedCommitCommand
    * @param gtx global transaction associated with the commit
    * @return a VersionedCommitCommand
    */
   VersionedCommitCommand buildVersionedCommitCommand(GlobalTransaction gtx);

   /**
    * Builds a RollbackCommand
    * @param gtx global transaction associated with the rollback
    * @return a RollbackCommand
    */
   RollbackCommand buildRollbackCommand(GlobalTransaction gtx);

   /**
    * Initializes a {@link org.infinispan.commands.ReplicableCommand} read from a data stream with components specific
    * to the target cache instance.
    * <p/>
    * Implementations should also be deep, in that if the command contains other commands, these should be recursed
    * into.
    * <p/>
    *
    * @param command command to initialize.  Cannot be null.
    * @param isRemote
    */
   void initializeReplicableCommand(ReplicableCommand command, boolean isRemote);

   /**
    * Builds a SingleRpcCommand "envelope" containing a single ReplicableCommand
    * @param call ReplicableCommand to include in the envelope
    * @return a SingleRpcCommand
    */
   SingleRpcCommand buildSingleRpcCommand(ReplicableCommand call);

   /**
    * Builds a ClusteredGetCommand, which is a remote lookup command
    * @param key key to look up
    * @param segment the segment for the key
    * @param flagsBitSet Command flags provided by cache
    * @return a ClusteredGetCommand
    */
   ClusteredGetCommand buildClusteredGetCommand(Object key, int segment, long flagsBitSet);

   /**
    * Builds a ClusteredGetAllCommand, which is a remote lookup command
    * @param keys key to look up
    * @param flagsBitSet Command flags provided by cache
    * @return a ClusteredGetAllCommand
    */
   ClusteredGetAllCommand buildClusteredGetAllCommand(List<?> keys, long flagsBitSet, GlobalTransaction gtx);

   /**
    * Builds a LockControlCommand to control explicit remote locking
    *
    * @param keys keys to lock
    * @param flagsBitSet Command flags provided by cache
    * @param gtx
    * @return a LockControlCommand
    */
   LockControlCommand buildLockControlCommand(Collection<?> keys, long flagsBitSet, GlobalTransaction gtx);

   /**
    * Same as {@link #buildLockControlCommand(Object, long, GlobalTransaction)}
    * but for locking a single key vs a collection of keys.
    */
   LockControlCommand buildLockControlCommand(Object key, long flagsBitSet, GlobalTransaction gtx);


   LockControlCommand buildLockControlCommand(Collection<?> keys, long flagsBitSet);

   /**
    * Builds a StateRequestCommand used for requesting transactions and locks and for starting or canceling transfer of cache entries.
    */
   StateRequestCommand buildStateRequestCommand(StateRequestCommand.Type subtype, Address sender, int topologyId, IntSet segments);

   /**
    * Builds a StateResponseCommand used for pushing cache entries to another node in response to a StateRequestCommand.
    */
   StateResponseCommand buildStateResponseCommand(Address sender, int viewId, Collection<StateChunk> stateChunks, boolean applyState, boolean pushTransfer);

   /**
    * Retrieves the cache name this CommandFactory is set up to construct commands for.
    * @return the name of the cache this CommandFactory is set up to construct commands for.
    */
   String getCacheName();

   /**
    * Builds a {@link org.infinispan.commands.remote.recovery.GetInDoubtTransactionsCommand}.
    */
   GetInDoubtTransactionsCommand buildGetInDoubtTransactionsCommand();

   /**
    * Builds a {@link org.infinispan.commands.remote.recovery.TxCompletionNotificationCommand}.
    */
   TxCompletionNotificationCommand buildTxCompletionNotificationCommand(Xid xid, GlobalTransaction globalTransaction);

   /**
    * @see GetInDoubtTxInfoCommand
    */
   GetInDoubtTxInfoCommand buildGetInDoubtTxInfoCommand();

   /**
    * Builds a CompleteTransactionCommand command.
    * @param xid the xid identifying the transaction we want to complete.
    * @param commit commit(true) or rollback(false)?
    */
   CompleteTransactionCommand buildCompleteTransactionCommand(Xid xid, boolean commit);

   /**
    * @param internalId the internal id identifying the transaction to be removed.
    * @see org.infinispan.commands.remote.recovery.TxCompletionNotificationCommand
    */
   TxCompletionNotificationCommand buildTxCompletionNotificationCommand(long internalId);


   /**
    * Same as {@code buildCreateCacheCommand(cacheName, cacheConfigurationName, false, 0)}.
    */
   CreateCacheCommand buildCreateCacheCommand(String cacheName, String cacheConfigurationName);

   /**
    * Builds a CreateCacheCommand used to create/start cache around Infinispan cluster
    *
    * @param size If {@code size > 0}, the command will wait until the cache runs on at least {@code size} nodes.
    */
   CreateCacheCommand buildCreateCacheCommand(String tmpCacheName, String defaultTmpCacheConfigurationName, int size);

   /**
    * Builds CancelCommandCommand used to cancel other commands executing on Infinispan cluster
    *
    * @param commandUUID UUID for command to cancel
    * @return created CancelCommandCommand
    */
   CancelCommand buildCancelCommandCommand(UUID commandUUID);

   /**
    * Builds XSiteStateTransferControlCommand used to control the-cross site state transfer.
    *
    * @param control  the control operation
    * @param siteName the site name, needed for some control operations.
    * @return the XSiteStateTransferControlCommand created
    */
   XSiteStateTransferControlCommand buildXSiteStateTransferControlCommand(StateTransferControl control, String siteName);

   /**
    * Builds XSiteAdminCommand used to perform system administrator operations.
    *
    * @return the XSiteAdminCommand created
    */
   XSiteAdminCommand buildXSiteAdminCommand(String siteName, AdminOperation op, Integer afterFailures, Long minTimeToWait);

   /**
    * Builds XSiteStatePushCommand used to transfer a single chunk of data between sites.
    *
    * @param chunk         the data chunk
    * @param timeoutMillis timeout in milliseconds, for the retries in the receiver site.
    * @return the XSiteStatePushCommand created
    */
   XSiteStatePushCommand buildXSiteStatePushCommand(XSiteState[] chunk, long timeoutMillis);

   /**
    * Builds SingleRpcCommand used to perform {@link org.infinispan.commands.VisitableCommand} on the backup site,
    * @param command the visitable command.
    * @return the SingleXSiteRpcCommand created
    */
   SingleXSiteRpcCommand buildSingleXSiteRpcCommand(VisitableCommand command);

   /**
    * Builds {@link org.infinispan.commands.remote.GetKeysInGroupCommand} used to fetch all the keys belonging to a group.
    *
    * @param flagsBitSet Command flags provided by cache
    * @param groupName the group name.
    * @return the GetKeysInGroup created.
    */
   GetKeysInGroupCommand buildGetKeysInGroupCommand(long flagsBitSet, Object groupName);

   <K> StreamRequestCommand<K> buildStreamRequestCommand(Object id, boolean parallelStream, StreamRequestCommand.Type type,
           IntSet segments, Set<K> keys, Set<K> excludedKeys, boolean includeLoader, boolean entryStream,
         Object terminalOperation);

   /**
    * Builds {@link StreamResponseCommand} used to send back a response either intermediate or complete to the
    * originating node with the information for the stream request.
    * @param identifier the unique identifier for the stream request
    * @param complete whether or not this is an intermediate or final response from this node for the given id
    * @param lostSegments what segments that were lost during processing
    * @param response the actual response
    * @param <R> type of response
    * @return the command to send back the response
    */
   <R> StreamResponseCommand<R> buildStreamResponseCommand(Object identifier, boolean complete, IntSet lostSegments,
           R response);

   <K> StreamIteratorRequestCommand<K> buildStreamIteratorRequestCommand(Object id, boolean parallelStream,
         IntSet segments, Set<K> keys, Set<K> excludedKeys, boolean includeLoader, boolean entryStream,
         Iterable<IntermediateOperation> intOps, long batchSize);

   StreamIteratorNextCommand buildStreamIteratorNextCommand(Object id, long batchSize);

   StreamIteratorCloseCommand buildStreamIteratorCloseCommand(Object id);

   <K, V, R> ReadOnlyKeyCommand<K, V, R> buildReadOnlyKeyCommand(Object key, Function<ReadEntryView<K, V>, R> f,
         int segment, Params params, DataConversion keyDataConversion, DataConversion valueDataConversion);

   <K, V, R> ReadOnlyManyCommand<K, V, R> buildReadOnlyManyCommand(Collection<?> keys, Function<ReadEntryView<K, V>, R> f, Params params, DataConversion keyDataConversion, DataConversion valueDataConversion);

   <K, V> WriteOnlyKeyCommand<K, V> buildWriteOnlyKeyCommand(
         Object key, Consumer<WriteEntryView<K, V>> f, int segment, Params params, DataConversion keyDataConversion, DataConversion valueDataConversion);

   <K, V, T, R> ReadWriteKeyValueCommand<K, V, T, R> buildReadWriteKeyValueCommand(
         Object key, Object argument, BiFunction<T, ReadWriteEntryView<K, V>, R> f, int segment, Params params, DataConversion keyDataConversion, DataConversion valueDataConversion);

   <K, V, R> ReadWriteKeyCommand<K, V, R> buildReadWriteKeyCommand(
         Object key, Function<ReadWriteEntryView<K, V>, R> f, int segment, Params params, DataConversion keyDataConversion, DataConversion valueDataConversion);

   <K, V, T> WriteOnlyManyEntriesCommand<K, V, T> buildWriteOnlyManyEntriesCommand(
         Map<?, ?> arguments, BiConsumer<T, WriteEntryView<K, V>> f, Params params, DataConversion keyDataConversion, DataConversion valueDataConversion);

   <K, V, T> WriteOnlyKeyValueCommand<K, V, T> buildWriteOnlyKeyValueCommand(Object key, Object argument,
         BiConsumer<T, WriteEntryView<K, V>> f, int segment, Params params, DataConversion keyDataConversion, DataConversion valueDataConversion);

   <K, V> WriteOnlyManyCommand<K, V> buildWriteOnlyManyCommand(Collection<?> keys, Consumer<WriteEntryView<K, V>> f,
                                                               Params params, DataConversion keyDataConversion, DataConversion valueDataConversion);

   <K, V, R> ReadWriteManyCommand<K, V, R> buildReadWriteManyCommand(Collection<?> keys, Function<ReadWriteEntryView<K, V>, R> f,
         Params params, DataConversion keyDataConversion, DataConversion valueDataConversion);

   <K, V, T, R> ReadWriteManyEntriesCommand<K, V, T, R> buildReadWriteManyEntriesCommand(Map<?, ?> entries, BiFunction<T, ReadWriteEntryView<K, V>, R> f, Params params, DataConversion keyDataConversion, DataConversion valueDataConversion);

   <K, V, R> TxReadOnlyKeyCommand<K, V, R> buildTxReadOnlyKeyCommand(Object key, Function<ReadEntryView<K, V>, R> f,
                                                                    List<Mutation<K, V, ?>> mutations, int segment,
                                                                    Params params, DataConversion keyDataConversion,
                                                                    DataConversion valueDataConversion);

   <K, V, R> TxReadOnlyManyCommand<K, V, R> buildTxReadOnlyManyCommand(Collection<?> keys, List<List<Mutation<K,V,?>>> mutations,
                                                                       Params params, DataConversion keyDataConversion,
                                                                       DataConversion valueDataConversion);

   BackupAckCommand buildBackupAckCommand(long id, int topologyId);

   BackupMultiKeyAckCommand buildBackupMultiKeyAckCommand(long id, int segment, int topologyId);

   ExceptionAckCommand buildExceptionAckCommand(long id, Throwable throwable, int topologyId);

   InvalidateVersionsCommand buildInvalidateVersionsCommand(int topologyId, Object[] keys, int[] topologyIds, long[] versions, boolean removed);

   RevokeBiasCommand buildRevokeBiasCommand(Address ackTarget, long id, int topologyId, Collection<Object> keys);

   RenewBiasCommand buildRenewBiasCommand(Object[] keys);

   SingleKeyBackupWriteCommand buildSingleKeyBackupWriteCommand();

   SingleKeyFunctionalBackupWriteCommand buildSingleKeyFunctionalBackupWriteCommand();

   PutMapBackupWriteCommand buildPutMapBackupWriteCommand();

   MultiEntriesFunctionalBackupWriteCommand buildMultiEntriesFunctionalBackupWriteCommand();

   MultiKeyFunctionalBackupWriteCommand buildMultiKeyFunctionalBackupWriteCommand();

   <K, R> ReductionPublisherRequestCommand<K> buildKeyReductionPublisherCommand(boolean parallelStream, DeliveryGuarantee deliveryGuarantee,
         IntSet segments, Set<K> keys, Set<K> excludedKeys, boolean includeLoader,
         Function<? super Publisher<K>, ? extends CompletionStage<R>> transformer,
         Function<? super Publisher<R>, ? extends CompletionStage<R>> finalizer);

   <K, V, R> ReductionPublisherRequestCommand<K> buildEntryReductionPublisherCommand(boolean parallelStream, DeliveryGuarantee deliveryGuarantee,
         IntSet segments, Set<K> keys, Set<K> excludedKeys, boolean includeLoader,
         Function<? super Publisher<CacheEntry<K, V>>, ? extends CompletionStage<R>> transformer,
         Function<? super Publisher<R>, ? extends CompletionStage<R>> finalizer);

   <K, I, R> InitialPublisherCommand<K, I, R> buildInitialPublisherCommand(Object requestId, DeliveryGuarantee deliveryGuarantee,
         int batchSize, IntSet segments, Set<K> keys, Set<K> excludedKeys, boolean includeLoader, boolean entryStream,
         boolean trackKeys, Function<? super Publisher<I>, ? extends Publisher<R>> transformer);

   NextPublisherCommand buildNextPublisherCommand(Object requestId);

   CancelPublisherCommand buildCancelPublisherCommand(Object requestId);

   <K, V> MultiClusterEventCommand<K, V> buildMultiClusterEventCommand(Map<UUID, Collection<ClusterEvent<K, V>>> events);

   CheckTransactionRpcCommand buildCheckTransactionRpcCommand(Collection<GlobalTransaction> globalTransactions);
}
