/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009 Red Hat Inc. and/or its affiliates and other
 * contributors as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a full listing of
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
package org.infinispan.marshall;

/**
 * Indexes for object types. These are currently limited to being unsigned ints, so valid values are considered those
 * in the range of 0 to 254. Please note that the use of 255 is forbidden since this is reserved for foreign, or user
 * defined, externalizers.
 *
 * @author Galder Zamarreño
 * @since 4.0
 */
public interface Ids {
   // No internal externalizer should use this upper limit Id or anything higher than that.
   int MAX_ID = 255;

   int ARRAY_LIST = 0;
   int LINKED_LIST = 1;
   int MAPS = 2;
   int JDK_SETS = 3;
   int SINGLETON_LIST = 4;
   // responses
   int SUCCESSFUL_RESPONSE = 5;
   int EXTENDED_RESPONSE = 6;
   int EXCEPTION_RESPONSE = 7;
   int UNSUCCESSFUL_RESPONSE = 8;
   int REQUEST_IGNORED_RESPONSE = 9;
   // entries and values
   int IMMORTAL_ENTRY = 10;
   int MORTAL_ENTRY = 11;
   int TRANSIENT_ENTRY = 12;
   int TRANSIENT_MORTAL_ENTRY = 13;
   int IMMORTAL_VALUE = 14;
   int MORTAL_VALUE = 15;
   int TRANSIENT_VALUE = 16;
   int TRANSIENT_MORTAL_VALUE = 17;
   // internal collections
   @Deprecated int FASTCOPY_HASH_MAP = 18; // unused, legacy versions
   int IMMUTABLE_MAP = 19;
   int ATOMIC_HASH_MAP = 20;

   // commands (unused, but left here to handle legacy versions)
   @Deprecated static final byte STATE_TRANSFER_CONTROL_COMMAND = 21;
   @Deprecated static final byte CLUSTERED_GET_COMMAND = 22;
   @Deprecated static final byte MULTIPLE_RPC_COMMAND = 23;
   @Deprecated static final byte SINGLE_RPC_COMMAND = 24;
   @Deprecated static final byte GET_KEY_VALUE_COMMAND = 25;
   @Deprecated static final byte PUT_KEY_VALUE_COMMAND = 26;
   @Deprecated static final byte REMOVE_COMMAND = 27;
   @Deprecated static final byte INVALIDATE_COMMAND = 28;
   @Deprecated static final byte REPLACE_COMMAND = 29;
   @Deprecated static final byte CLEAR_COMMAND = 30;
   @Deprecated static final byte PUT_MAP_COMMAND = 31;
   @Deprecated static final byte PREPARE_COMMAND = 32;
   @Deprecated static final byte COMMIT_COMMAND = 33;
   @Deprecated static final byte ROLLBACK_COMMAND = 34;
   @Deprecated static final byte INVALIDATE_L1_COMMAND = 35;
   @Deprecated static final byte LOCK_CONTROL_COMMAND = 36;
   @Deprecated static final byte EVICT_COMMAND = 37; // never used, evict command is local

   // others
   int GLOBAL_TRANSACTION = 38;
   int JGROUPS_ADDRESS = 39;
   int MARSHALLED_VALUE = 40;
   int TRANSACTION_LOG_ENTRY = 41;
   int BUCKET = 42;
   int DEADLOCK_DETECTING_GLOBAL_TRANSACTION = 43;

   // 44 and 45 no longer in use, used to belong to tree module
   int ATOMIC_HASH_MAP_DELTA = 46;
   int ATOMIC_PUT_OPERATION = 47;
   int ATOMIC_REMOVE_OPERATION = 48;
   int ATOMIC_CLEAR_OPERATION = 49;
   @Deprecated int REHASH_CONTROL_COMMAND = 50; // unused, legacy versions
   int DEFAULT_CONSISTENT_HASH = 51;
   int UNION_CONSISTENT_HASH = 52;
   @Deprecated int JOIN_COMPLETE_COMMAND = 53; // unused, legacy versions
   int UNSURE_RESPONSE = 54;
   // 55 - 56 no longer in use since server modules can now register their own externalizers
   int BYTE_ARRAY_KEY = 57;
   // 58 - 59 no longer in use since server modules can now register their own externalizers
   int JGROUPS_TOPOLOGY_AWARE_ADDRESS = 60;
   int TOPOLOGY_AWARE_CH = 61;
   // commands (ids between 21 and 37 both inclusive and 50 and 53, are no longer in use, might get reused at a later stage)
   int REPLICABLE_COMMAND = 62;

   int REMOTE_TX_LOG_DETAILS = 63;

   int XID = 66;
   int XID_DEADLOCK_DETECTING_GLOBAL_TRANSACTION = 67;
   int XID_GLOBAL_TRANSACTION = 68;

   int IN_DOUBT_TX_INFO = 70;
}
