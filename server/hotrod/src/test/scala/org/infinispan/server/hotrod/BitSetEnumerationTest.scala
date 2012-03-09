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

import org.testng.annotations.Test
import test.ScalaTestAssertions._
import BitSetEnumeration._

/**
 * // TODO: Document this
 * @author Galder Zamarreño
 * @since // TODO
 */
@Test(groups = Array("functional"), testName = "server.hotrod.BitSetEnumerationTest")
class BitSetEnumerationTest {

   import SampleEnum._
   
   def testBitSetEnumeration {
      assert(fromBitSet(SampleEnum, 0).isEmpty)
      assert(fromBitSet(SampleEnum, 1) === SampleEnum.ValueSet(A))
      assert(fromBitSet(SampleEnum, 2) === SampleEnum.ValueSet(B))
      assert(fromBitSet(SampleEnum, 3) === SampleEnum.ValueSet(A, B))
      assert(fromBitSet(SampleEnum, 4) === SampleEnum.ValueSet(C))
      assert(fromBitSet(SampleEnum, 5) === SampleEnum.ValueSet(A, C))
      assert(fromBitSet(SampleEnum, 6) === SampleEnum.ValueSet(B, C))
      assert(fromBitSet(SampleEnum, 7) === SampleEnum.ValueSet(A, B, C))
   }

}

object SampleEnum extends Enumeration {
   type SampleEnumeration = Value
   val A = Value
   val B = Value
   val C = Value
}
