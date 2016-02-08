package org.infinispan.interceptors.impl;

import org.infinispan.commands.FlagAffectedCommand;
import org.infinispan.commands.tx.CommitCommand;
import org.infinispan.commands.tx.PrepareCommand;
import org.infinispan.commands.tx.VersionedCommitCommand;
import org.infinispan.commands.tx.VersionedPrepareCommand;
import org.infinispan.container.entries.CacheEntry;
import org.infinispan.container.versioning.EntryVersion;
import org.infinispan.container.versioning.EntryVersionsMap;
import org.infinispan.container.versioning.VersionGenerator;
import org.infinispan.context.Flag;
import org.infinispan.context.InvocationContext;
import org.infinispan.context.impl.TxInvocationContext;
import org.infinispan.factories.annotations.Inject;
import org.infinispan.metadata.EmbeddedMetadata;
import org.infinispan.metadata.Metadata;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;

import java.util.concurrent.CompletableFuture;

/**
 * Interceptor in charge with wrapping entries and add them in caller's context.
 *
 * @author Mircea Markus
 * @since 9.0
 */
public class VersionedEntryWrappingInterceptor extends EntryWrappingInterceptor {

   protected VersionGenerator versionGenerator;
   private static final Log log = LogFactory.getLog(VersionedEntryWrappingInterceptor.class);

   @Override
   protected Log getLog() {
      return log;
   }

   @Inject
   public void initialize(VersionGenerator versionGenerator) {
      this.versionGenerator = versionGenerator;
   }

   @Override
   public CompletableFuture<Void> visitPrepareCommand(TxInvocationContext ctx, PrepareCommand command) throws Throwable {
      VersionedPrepareCommand versionedPrepareCommand = (VersionedPrepareCommand) command;
      if (ctx.isOriginLocal()) {
         versionedPrepareCommand.setVersionsSeen(ctx.getCacheTransaction().getVersionsRead());
      }
      wrapEntriesForPrepare(ctx, command);
      EntryVersionsMap originVersionData;
      if (ctx.isOriginLocal() && !ctx.getCacheTransaction().isFromStateTransfer()) {
         originVersionData =
               cdl.createNewVersionsAndCheckForWriteSkews(versionGenerator, ctx, versionedPrepareCommand);
      } else {
         originVersionData = null;
      }

      return ctx.onReturn((ctx1, command1, rv, throwable) -> {
         if (throwable != null) {
            throw throwable;
         }

         EntryVersionsMap newVersionData;
         if (ctx.isOriginLocal()) {
            newVersionData = originVersionData;
         } else {
            newVersionData =
                  cdl.createNewVersionsAndCheckForWriteSkews(versionGenerator, ctx, versionedPrepareCommand);
         }

         if (command.isOnePhaseCommit()) {
            ctx.getCacheTransaction().setUpdatedEntryVersions(versionedPrepareCommand.getVersionsSeen());
         }

         if (command.isOnePhaseCommit()) {
            commitContextEntries(ctx, null, null);
         }
         if (newVersionData != null)
            return CompletableFuture.completedFuture(newVersionData);

         return null;
      });
   }

   @Override
   public CompletableFuture<Void> visitCommitCommand(TxInvocationContext ctx, CommitCommand command) throws Throwable {
      ctx.onReturn((ctx1, command1, rv, throwable) -> {
         if (!ctx.isOriginLocal()) {
            VersionedCommitCommand versionedCommitCommand = (VersionedCommitCommand) command1;
            ((TxInvocationContext<?>) ctx1).getCacheTransaction()
                  .setUpdatedEntryVersions(versionedCommitCommand.getUpdatedVersions());
         }
         commitContextEntries(ctx, null, null);
         return null;
      });

      VersionedCommitCommand versionedCommitCommand = (VersionedCommitCommand) command;
      if (ctx.isOriginLocal()) {
         versionedCommitCommand.setUpdatedVersions(ctx.getCacheTransaction().getUpdatedEntryVersions());
      }

      return ctx.continueInvocation();
   }

   @Override
   protected void commitContextEntry(CacheEntry entry, InvocationContext ctx, FlagAffectedCommand command,
                                     Metadata metadata, Flag stateTransferFlag, boolean l1Invalidation) {
      if (ctx.isInTxScope() && stateTransferFlag == null) {
         EntryVersion updatedEntryVersion = ((TxInvocationContext) ctx)
               .getCacheTransaction().getUpdatedEntryVersions().get(entry.getKey());
         Metadata commitMetadata;
         if (updatedEntryVersion != null) {
            if (metadata == null && entry.getMetadata() == null) {
               commitMetadata = new EmbeddedMetadata.Builder().version(updatedEntryVersion).build();
            } else if (metadata != null) {
               commitMetadata = metadata.builder().version(updatedEntryVersion).build();
            } else {
               commitMetadata = entry.getMetadata().builder().version(updatedEntryVersion).build();
            }
         } else {
            commitMetadata = metadata != null ? metadata : entry.getMetadata();
         }

         cdl.commitEntry(entry, commitMetadata, command, ctx, null, l1Invalidation);
      } else {
         // This could be a state transfer call!
         cdl.commitEntry(entry, entry.getMetadata(), command, ctx, stateTransferFlag, l1Invalidation);
      }
   }

}
