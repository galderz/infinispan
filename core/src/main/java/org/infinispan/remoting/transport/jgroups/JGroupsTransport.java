/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2009, Red Hat, Inc. and/or its affiliates, and
 * individual contributors as indicated by the @author tags. See the
 * copyright.txt file in the distribution for a full listing of
 * individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.infinispan.remoting.transport.jgroups;

import org.infinispan.CacheException;
import org.infinispan.commands.ReplicableCommand;
import org.infinispan.config.ConfigurationException;
import org.infinispan.config.GlobalConfiguration;
import org.infinispan.config.parsing.XmlConfigHelper;
import org.infinispan.marshall.Marshaller;
import org.infinispan.notifications.cachemanagerlistener.CacheManagerNotifier;
import org.infinispan.remoting.InboundInvocationHandler;
import org.infinispan.remoting.ReplicationException;
import org.infinispan.remoting.responses.ExceptionResponse;
import org.infinispan.remoting.responses.Response;
import org.infinispan.remoting.rpc.ResponseFilter;
import org.infinispan.remoting.rpc.ResponseMode;
import org.infinispan.remoting.transport.Address;
import org.infinispan.remoting.transport.DistributedSync;
import org.infinispan.remoting.transport.Transport;
import org.infinispan.statetransfer.StateTransferException;
import org.infinispan.util.FileLookup;
import org.infinispan.util.TypedProperties;
import org.infinispan.util.Util;
import org.infinispan.util.concurrent.TimeoutException;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;
import org.jgroups.Channel;
import org.jgroups.ChannelException;
import org.jgroups.Event;
import org.jgroups.ExtendedMembershipListener;
import org.jgroups.ExtendedMessageListener;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.View;
import org.jgroups.blocks.GroupRequest;
import org.jgroups.blocks.RspFilter;
import org.jgroups.protocols.pbcast.FLUSH;
import org.jgroups.protocols.pbcast.STREAMING_STATE_TRANSFER;
import org.jgroups.stack.ProtocolStack;
import org.jgroups.util.Rsp;
import org.jgroups.util.RspList;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * An encapsulation of a JGroups transport.  JGroups transports can be configured using a variety of methods, usually
 * by passing in one of the following properties:
 * <ul>
 * <li><tt>configurationString</tt> - a JGroups configuration String</li>
 * <li><tt>configurationXml</tt> - JGroups configuration XML as a String</li>
 * <li><tt>configurationFile</tt> - String pointing to a JGroups XML configuration file</li>
 * <li><tt>channelLookup</tt> - Fully qualified class name of a {@link org.infinispan.remoting.transport.jgroups.JGroupsChannelLookup} instance</li>
 * </ul>
 * These are normally passed in as Properties in {@link org.infinispan.config.GlobalConfiguration#setTransportProperties(java.util.Properties)}
 * or in the Infinispan XML configuration file.
 * 
 * @author Manik Surtani
 * @author Galder Zamarreño
 * @since 4.0
 */
public class JGroupsTransport implements Transport, ExtendedMembershipListener, ExtendedMessageListener {
   public static final String CONFIGURATION_STRING = "configurationString";
   public static final String CONFIGURATION_XML = "configurationXml";
   public static final String CONFIGURATION_FILE = "configurationFile";
   public static final String CHANNEL_LOOKUP = "channelLookup";
   protected static final String DEFAULT_JGROUPS_CONFIGURATION_FILE = "config-samples/jgroups-udp.xml";
   protected boolean startChannel = true, stopChannel = true;

   protected Channel channel;
   protected boolean createdChannel = false;
   protected Address address;
   protected Address physicalAddress;
   protected volatile List<Address> members = Collections.emptyList();
   protected volatile boolean coordinator = false;
   protected final Object membersListLock = new Object(); // guards members
   CommandAwareRpcDispatcher dispatcher;
   static final Log log = LogFactory.getLog(JGroupsTransport.class);
   static final boolean trace = log.isTraceEnabled();
   protected GlobalConfiguration c;
   protected TypedProperties props;
   protected InboundInvocationHandler inboundInvocationHandler;
   protected Marshaller marshaller;
   protected ExecutorService asyncExecutor;
   protected CacheManagerNotifier notifier;
   final ConcurrentMap<String, StateTransferMonitor> stateTransfersInProgress = new ConcurrentHashMap<String, StateTransferMonitor>();
   private final JGroupsDistSync flushTracker = new JGroupsDistSync();
   long distributedSyncTimeout;

   /**
    * This form is used when the transport is created by an external source and passed in to the GlobalConfiguration.
    *
    * @param channel created and running channel to use
    */
   public JGroupsTransport(Channel channel) {
      this.channel = channel;
      if (channel == null) throw new IllegalArgumentException("Cannot deal with a null channel!");
      if (channel.isConnected()) throw new IllegalArgumentException("Channel passed in cannot already be connected!");
   }

   public JGroupsTransport() {
   }

   // ------------------------------------------------------------------------------------------------------------------
   // Lifecycle and setup stuff
   // ------------------------------------------------------------------------------------------------------------------

   public void initialize(GlobalConfiguration c, Marshaller marshaller, ExecutorService asyncExecutor,
                          InboundInvocationHandler inboundInvocationHandler, CacheManagerNotifier notifier) {
      this.c = c;
      this.marshaller = marshaller;
      this.asyncExecutor = asyncExecutor;
      this.inboundInvocationHandler = inboundInvocationHandler;
      this.notifier = notifier;
   }

   public void start() {
      props = TypedProperties.toTypedProperties(c.getTransportProperties());
      distributedSyncTimeout = c.getDistributedSyncTimeout();

      if (log.isInfoEnabled()) log.info("Starting JGroups Channel");

      initChannelAndRPCDispatcher();
      startJGroupsChannelIfNeeded();

      // ensure that the channel has FLUSH enabled.
      // see ISPN-83 for details.
      if ( channel.getProtocolStack()!= null && channel.getProtocolStack().findProtocol(FLUSH.class) == null)
         throw new ConfigurationException("Flush should be enabled. This is related to https://jira.jboss.org/jira/browse/ISPN-83");
   }

   protected void startJGroupsChannelIfNeeded() {
      if (startChannel) {
         try {
            channel.connect(c.getClusterName());
         } catch (ChannelException e) {
            throw new CacheException("Unable to start JGroups Channel", e);
         }
      }
      address = new JGroupsAddress(channel.getAddress());
      if (log.isInfoEnabled()) log.info("Cache local address is {0}, physical addresses are {1}", getAddress(), getPhysicalAddresses());
   }

   public int getViewId() {
      return (int) channel.getView().getVid().getId();
   }

   public void stop() {
      try {
         if (stopChannel && channel != null && channel.isOpen()) {
            log.info("Disconnecting and closing JGroups Channel");
            channel.disconnect();
            channel.close();
         }
      } catch (Exception toLog) {
         log.error("Problem closing channel; setting it to null", toLog);
      }

      channel = null;
      if (dispatcher != null) {
         log.info("Stopping the RpcDispatcher");
         dispatcher.stop();
      }

      if (members != null) members = null;
      coordinator = false;
      dispatcher = null;
   }


   protected void initChannel() {
      if (channel == null) {
         createdChannel = true;
         buildChannel();
         // Channel.LOCAL *must* be set to false so we don't see our own messages - otherwise invalidations targeted at
         // remote instances will be received by self.
         String transportNodeName = c.getTransportNodeName();
         if (transportNodeName != null && transportNodeName.length() > 0) {
            long range = Short.MAX_VALUE * 2;
            long randomInRange = (long) ((Math.random() * range) % range) + 1;
            transportNodeName = transportNodeName + "-" + randomInRange;
            channel.setName(transportNodeName);
         }
      }

      channel.setOpt(Channel.LOCAL, false);
   }

   private void initChannelAndRPCDispatcher() throws CacheException {
      initChannel();
      dispatcher = new CommandAwareRpcDispatcher(channel, this,
                                                 asyncExecutor, inboundInvocationHandler, flushTracker, distributedSyncTimeout);
      MarshallerAdapter adapter = new MarshallerAdapter(marshaller);
      dispatcher.setRequestMarshaller(adapter);
      dispatcher.setResponseMarshaller(adapter);
   }

   private void buildChannel() {
      // in order of preference - we first look for an external JGroups file, then a set of XML properties, and
      // finally the legacy JGroups String properties.
      String cfg;
      if (props != null) {
         if (props.containsKey(CHANNEL_LOOKUP)) {
            String channelLookupClassName = props.getProperty(CHANNEL_LOOKUP);

            try {
               JGroupsChannelLookup lookup = (JGroupsChannelLookup) Util.getInstance(channelLookupClassName);
               channel = lookup.getJGroupsChannel();
               startChannel = lookup.shouldStartAndConnect();
               stopChannel = lookup.shouldStopAndDisconnect();
            } catch (ClassCastException e) {
               log.error("Class [" + channelLookupClassName + "] cannot be cast to JGroupsChannelLookup!  Not using a channel lookup.");
            } catch (Exception e) {
               log.error("Errors instantiating [" + channelLookupClassName + "]!  Not using a channel lookup.");
            }
         }

         if (channel == null && props.containsKey(CONFIGURATION_FILE)) {
            cfg = props.getProperty(CONFIGURATION_FILE);
            try {
               channel = new JChannel(new FileLookup().lookupFileLocation(cfg));
            } catch (Exception e) {
               log.error("Error while trying to create a channel using config files: " + cfg);
               throw new CacheException(e);
            }
         }

         if (channel == null && props.containsKey(CONFIGURATION_XML)) {
            cfg = props.getProperty(CONFIGURATION_XML);
            try {
               channel = new JChannel(XmlConfigHelper.stringToElement(cfg));
            } catch (Exception e) {
               log.error("Error while trying to create a channel using config XML: " + cfg);
               throw new CacheException(e);
            }
         }

         if (channel == null && props.containsKey(CONFIGURATION_STRING)) {
            cfg = props.getProperty(CONFIGURATION_STRING);
            try {
               channel = new JChannel(cfg);
            } catch (Exception e) {
               log.error("Error while trying to create a channel using config string: " + cfg);
               throw new CacheException(e);
            }
         }
      }

      if (channel == null) {
         log.info("Unable to use any JGroups configuration mechanisms provided in properties {0}.  Using default JGroups configuration!", props);
         try {
            channel = new JChannel(new FileLookup().lookupFileLocation(DEFAULT_JGROUPS_CONFIGURATION_FILE));
         } catch (ChannelException e) {
            throw new CacheException("Unable to start JGroups channel", e);
         }
      }
   }

   // ------------------------------------------------------------------------------------------------------------------
   // querying cluster status
   // ------------------------------------------------------------------------------------------------------------------

   public boolean isCoordinator() {
      return coordinator;
   }

   public Address getCoordinator() {
      if (channel == null) return null;
      synchronized (membersListLock) {
         while (members == null || members.isEmpty()) {
            log.debug("Waiting on view being accepted");
            try {
               membersListLock.wait();
            } catch (InterruptedException e) {
               log.error("getCoordinator(): Interrupted while waiting for members to be set", e);
               break;
            }
         }
         return members == null || members.isEmpty() ? null : members.get(0);
      }
   }

   public List<Address> getMembers() {
      return members;
   }

   public boolean retrieveState(String cacheName, Address address, long timeout) throws StateTransferException {
      boolean cleanup = false;
      try {
         StateTransferMonitor mon = new StateTransferMonitor();
         if (stateTransfersInProgress.putIfAbsent(cacheName, mon) != null)
            throw new StateTransferException("There already appears to be a state transfer in progress for the cache named " + cacheName);

         cleanup = true;
         ((JChannel) channel).getState(toJGroupsAddress(address), cacheName, timeout, false);
         mon.waitForState();
         return mon.getSetStateException() == null;
      } catch (StateTransferException ste) {
         throw ste;
      } catch (Exception e) {
         if (log.isInfoEnabled()) log.info("Unable to retrieve state from member " + address, e);
         return false;
      } finally {
         if (cleanup) stateTransfersInProgress.remove(cacheName);
      }
   }

   public DistributedSync getDistributedSync() {
      return flushTracker;
   }


   public boolean isSupportStateTransfer() {
      // tests whether state transfer is supported.  We *need* STREAMING_STATE_TRANSFER.
      ProtocolStack stack;
      if (channel != null && (stack = channel.getProtocolStack()) != null) {
         if (stack.findProtocol(STREAMING_STATE_TRANSFER.class) == null) {
            log.error("Channel does not contain STREAMING_STATE_TRANSFER.  Cannot support state transfers!");
            return false;
         }
      } else {
         log.warn("Channel not set up properly!");
         return false;
      }

      return true;
   }

   public Address getAddress() {
      if (address == null && channel != null) {
         address = new JGroupsAddress(channel.getAddress());
//         address = new JGroupsAddress(channel.getLocalAddress());
      }
      return address;
   }

   public List<Address> getPhysicalAddresses() {
      if (physicalAddress == null && channel != null) {
         org.jgroups.Address addr = (org.jgroups.Address) channel.downcall(new Event(Event.GET_PHYSICAL_ADDRESS, channel.getAddress()));
         physicalAddress = new JGroupsAddress(addr);
      }
      return Collections.singletonList(physicalAddress);
   }

   // ------------------------------------------------------------------------------------------------------------------
   // outbound RPC
   // ------------------------------------------------------------------------------------------------------------------

   public List<Response> invokeRemotely(Collection<Address> recipients, ReplicableCommand rpcCommand, ResponseMode mode, long timeout,
                                        boolean usePriorityQueue, ResponseFilter responseFilter, boolean supportReplay)
         throws Exception {

      if (recipients != null && recipients.isEmpty()) {
         // don't send if dest list is empty
         log.trace("Destination list is empty: no need to send message");
         return Collections.emptyList();
      }

      if (trace) log.trace("dests={0}, command={1}, mode={2}, timeout={3}", recipients, rpcCommand, mode, timeout);

      // Acquire a "processing" lock so that any other code is made aware of a network call in progress
      // make sure this is non-exclusive since concurrent network calls are valid for most situations.
      flushTracker.acquireProcessingLock(false, distributedSyncTimeout, MILLISECONDS);
      boolean unlock = true;
      // if there is a FLUSH in progress, block till it completes
      flushTracker.blockUntilReleased(distributedSyncTimeout, MILLISECONDS);
      boolean asyncMarshalling = mode == ResponseMode.ASYNCHRONOUS;
      if (!usePriorityQueue && ResponseMode.SYNCHRONOUS == mode) usePriorityQueue = true;
      try {
         RspList rsps = dispatcher.invokeRemoteCommands(toJGroupsAddressVector(recipients), rpcCommand, toJGroupsMode(mode),
                                                        timeout, recipients != null, usePriorityQueue,
                                                        toJGroupsFilter(responseFilter), supportReplay, asyncMarshalling, recipients == null || recipients.size() == members.size());

         if (mode.isAsynchronous()) return Collections.emptyList();// async case

//         if (trace)
//            log.trace("Cache [{0}], is caller thread interupted? {3}: responses for command {1}:\n{2}", getAddress(), rpcCommand.getClass().getSimpleName(), rsps, Thread.currentThread().isInterrupted());

         // short-circuit no-return-value calls.
         if (rsps == null) return Collections.emptyList();
         List<Response> retval = new ArrayList<Response>(rsps.size());

         boolean noValidResponses = true;
         for (Rsp rsp : rsps.values()) {
            if (rsp.wasSuspected() || !rsp.wasReceived()) {
               if (rsp.wasSuspected()) {
                  throw new SuspectException("Suspected member: " + rsp.getSender());
               } else {
                  // if we have a response filter then we may not have waited for some nodes!
                  if (responseFilter == null) throw new TimeoutException("Replication timeout for " + rsp.getSender());
               }
            } else {
               noValidResponses = false;
               Object value = rsp.getValue();
               if (value instanceof Response) {
                  Response response = (Response) value;
                  if (response instanceof ExceptionResponse) {
                     Exception e = ((ExceptionResponse) value).getException();
                     if (!(e instanceof ReplicationException)) {
                        // if we have any application-level exceptions make sure we throw them!!
                        if (trace) log.trace("Received exception from " + rsp.getSender(), e);
                        throw e;
                     }
                  }
                  retval.add(response);
               } else if (value instanceof Exception) {
                  Exception e = (Exception) value;
                  if (trace) log.trace("Unexpected exception from " + rsp.getSender(), e);
                  throw e;
               } else if (value instanceof Throwable) {
                  Throwable t = (Throwable) value;
                  if (trace) log.trace("Unexpected throwable from " + rsp.getSender(), t);
                  throw new CacheException("Remote (" + rsp.getSender() + ") failed unexpectedly", t);
               }
            }
         }

         if (noValidResponses) throw new TimeoutException("Timed out waiting for valid responses!");
         return retval;
      } finally {
         // release the "processing" lock so that other threads are aware of the network call having completed
         if (unlock) flushTracker.releaseProcessingLock();
      }
   }

   private int toJGroupsMode(ResponseMode mode) {
      switch (mode) {
         case ASYNCHRONOUS:
         case ASYNCHRONOUS_WITH_SYNC_MARSHALLING:
            return GroupRequest.GET_NONE;
         case SYNCHRONOUS:
            return GroupRequest.GET_ALL;
         case WAIT_FOR_VALID_RESPONSE:
            return GroupRequest.GET_MAJORITY;
      }
      throw new CacheException("Unknown response mode " + mode);
   }

   private RspFilter toJGroupsFilter(ResponseFilter responseFilter) {
      return responseFilter == null ? null : new JGroupsResponseFilterAdapter(responseFilter);
   }

   // ------------------------------------------------------------------------------------------------------------------
   // Implementations of JGroups interfaces
   // ------------------------------------------------------------------------------------------------------------------

   public void viewAccepted(View newView) {
      Vector<org.jgroups.Address> newMembers = newView.getMembers();
      List<Address> oldMembers = null;
      if (log.isInfoEnabled()) log.info("Received new cluster view: {0}", newView);
      synchronized (membersListLock) {
         boolean needNotification = false;
         if (newMembers != null) {
            oldMembers = members;
            // we need a defensive copy anyway            
            members = fromJGroupsAddressList(newMembers);
            needNotification = true;
         }
         // Now that we have a view, figure out if we are the coordinator
         coordinator = (members != null && !members.isEmpty() && members.get(0).equals(getAddress()));

         // now notify listeners - *after* updating the coordinator. - JBCACHE-662
         if (needNotification && notifier != null) {
            notifier.notifyViewChange(members, oldMembers, getAddress(), (int) newView.getVid().getId());
         }

         // Wake up any threads that are waiting to know about who the coordinator is
         membersListLock.notifyAll();
      }
   }

   public void suspect(org.jgroups.Address suspected_mbr) {
      // no-op
   }

   public void block() {
      // Set the DistSync to indicate that a JOIN is in progress
      // See ISPN-83 for more details.  Once ISPN-83 is addressed, this may no longer be needed.
      flushTracker.signalJoinInProgress();
   }

   public void unblock() {
      // Set the DistSync to indicate that a JOIN is in progress
      // See ISPN-83 for more details.  Once ISPN-83 is addressed, this may no longer be needed.
      flushTracker.signalJoinCompleted();
   }

   public void receive(Message msg) {
      // no-op
   }

   public byte[] getState() {
      throw new UnsupportedOperationException("Retrieving state for the entire cache system is not supported!");
   }

   public void setState(byte[] state) {
      throw new UnsupportedOperationException("Setting state for the entire cache system is not supported!");
   }

   public byte[] getState(String state_id) {
      throw new UnsupportedOperationException("Non-stream-based state retrieval is not supported!  Make sure you use the JGroups STREAMING_STATE_TRANSFER protocol!");
   }

   public void setState(String state_id, byte[] state) {
      throw new UnsupportedOperationException("Non-stream-based state retrieval is not supported!  Make sure you use the JGroups STREAMING_STATE_TRANSFER protocol!");
   }

   public void getState(OutputStream ostream) {
      throw new UnsupportedOperationException("Retrieving state for the entire cache system is not supported!");
   }

   public void getState(String cacheName, OutputStream ostream) {
      if (trace)
         log.trace("Received request to generate state for cache named '{0}'.  Attempting to generate state.", cacheName);
      try {
         inboundInvocationHandler.generateState(cacheName, ostream);
      } catch (StateTransferException e) {
         log.error("Caught while responding to state transfer request", e);
      } finally {
         Util.flushAndCloseStream(ostream);
      }
   }

   public void setState(InputStream istream) {
      throw new UnsupportedOperationException("Setting state for the entire cache system is not supported!");
   }

   public void setState(String cacheName, InputStream istream) {
      StateTransferMonitor mon = null;
      try {
         if (trace) log.trace("Received state for cache named '{0}'.  Attempting to apply state.", cacheName);
         mon = stateTransfersInProgress.get(cacheName);
         inboundInvocationHandler.applyState(cacheName, istream);
         mon.notifyStateReceiptSucceeded();
      } catch (Exception e) {
         mon.notifyStateReceiptFailed(e instanceof StateTransferException ? (StateTransferException) e : new StateTransferException(e));
         log.error("Caught while requesting or applying state", e);
      } finally {
         Util.closeStream(istream);
      }
   }


   // ------------------------------------------------------------------------------------------------------------------
   // Helpers to convert between Address types
   // ------------------------------------------------------------------------------------------------------------------

   private Vector<org.jgroups.Address> toJGroupsAddressVector(Collection<Address> list) {
      if (list == null) return null;
      if (list.isEmpty()) return new Vector<org.jgroups.Address>();

      // optimize for the single node case
      Vector<org.jgroups.Address> retval = new Vector<org.jgroups.Address>(list.size());
      for (Address a : list) retval.add(toJGroupsAddress(a));

      return retval;
   }

   private org.jgroups.Address toJGroupsAddress(Address a) {
      return ((JGroupsAddress) a).address;
   }

   private List<Address> fromJGroupsAddressList(List<org.jgroups.Address> list) {
      if (list == null || list.isEmpty()) return Collections.emptyList();
      // optimize for the single node case
      int sz = list.size();
      List<Address> retval = new ArrayList<Address>(sz);
      if (sz == 1) {
         retval.add(new JGroupsAddress(list.get(0)));
      } else {
         for (org.jgroups.Address a : list) retval.add(new JGroupsAddress(a));
      }
      return retval;
   }

   // mainly for unit testing
   public CommandAwareRpcDispatcher getCommandAwareRpcDispatcher() {
      return dispatcher;
   }

   public Channel getChannel() {
      return channel;
   }
}
