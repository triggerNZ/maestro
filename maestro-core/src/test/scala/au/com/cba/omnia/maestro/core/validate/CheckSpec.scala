//   Copyright 2014 Commonwealth Bank of Australia
//
//   Licensed under the Apache License, Version 2.0 (the "License");
//   you may not use this file except in compliance with the License.
//   You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//   See the License for the specific language governing permissions and
//   limitations under the License.

package au.com.cba.omnia.maestro.core
package validate

import scalaz._, Scalaz._

import org.scalacheck.{Arbitrary, Gen}

import au.com.cba.omnia.maestro.core.test.Spec

object CheckSpec extends Spec { def is = s2"""

Check properties
================

  Check.oneOf                 $oneOf
  Check.oneOf failure         $oneOfFailure
  Check.nonempty              $nonempty
  Check.nonempty failure      $nonemptyFailure

"""
  def categories = List("A", "B", "C", "D")
  case class Category(value: String)

  def oneOf = prop((c: Category) =>
    Check.oneOf(categories:_*).run(c.value) must_== c.value.success)

  def oneOfFailure = prop((c: Category) =>
    Check.oneOf(categories:_*).run(c.value + "bad").isFailure)

  def nonempty = prop((s: String) => !s.isEmpty ==> {
    Check.nonempty.run(s) == s.success })

  def nonemptyFailure =
    Check.nonempty.run("").isFailure

  implicit def CategoryArbitrary: Arbitrary[Category] =
    Arbitrary(Gen.oneOf(categories) map Category)

}
