package org.infinispan.server.core

/**
 * Externalizer ids used by Server module {@link AdvancedExternalizer} implementations.
 *
 * Information about the valid id range can be found <a href="http://community.jboss.org/docs/DOC-16198">here</a>
 *
 * @author Galder Zamarreño
 * @since 5.0
 */
object ExternalizerIds {

   val SERVER_ENTRY_VERSION = 1100
   val MEMCACHED_METADATA = 1101
   val TOPOLOGY_ADDRESS = 1102
   val TOPOLOGY_VIEW = 1103
   val SERVER_ADDRESS = 1104
   val MIME_METADATA = 1105
   val BINARY_FILTER = 1106
   val BINARY_CONVERTER = 1107
   val KEY_VALUE_VERSION_CONVERTER = 1108
   val BINARY_FILTER_CONVERTER = 1109
   val KEY_VALUE_WITH_PREVIOUS_CONVERTER = 1110
   val ITERATION_FILTER = 1111
   val SLIMMING_CONVERTER = 1112

}