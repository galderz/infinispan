/*
 * Copyright 2012 Red Hat, Inc. and/or its affiliates.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA
 */

package org.infinispan.server.hotrod

import collection.immutable.BitSet.BitSet1

/**
 * // TODO: Document this
 * @author Galder Zamarreño
 * @since // TODO
 */
object BitSetEnumeration {

   def toBitSet(vs: Enumeration#ValueSet): Long =
      vs.foldLeft(0L){ (z,n) => (2L << n.id) + z }

   def fromBitSet[T <: Enumeration](e: T, l: Long): T#ValueSet =
      e.ValueSet.empty ++ (new BitSet1(l)).map(i => e(i))

}
