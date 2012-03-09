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

package org.infinispan.server.hotrod.test

import org.scalatest.Assertions
import org.testng.annotations.Test

/**
 * // TODO: Document this
 * @author Galder Zamarreño
 * @since // TODO
 */
object ScalaTestAssertions extends Assertions {

//   @Test(enabled = false)
   override def assert(condition: Boolean) = super.assert(condition)

//   @Test(enabled = false)
   override def assert(condition: Boolean, clue: Any) =
      super.assert(condition, clue)

//   @Test(enabled = false)
   override def assert(o: Option[String]) = super.assert(o)

//   @Test(enabled = false)
   override def assert(o: Option[String], clue: Any) = super.assert(o, clue)

//   @Test(enabled = false)
   override def expect(expected: Any)(actual: Any) =
      super.expect(expected)(actual)

//   @Test(enabled = false)
   override def expect(expected: Any, clue: Any)(actual: Any) =
      super.expect(expected, clue)(actual)


//   @Test(enabled = false)
   override def withClue(clue: Any)(fun: => Unit) = super.withClue(clue)(fun)

}
