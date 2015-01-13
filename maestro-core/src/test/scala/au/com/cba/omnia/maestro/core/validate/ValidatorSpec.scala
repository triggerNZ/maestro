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

import org.scalacheck.Arbitrary

import au.com.cba.omnia.maestro.core.data.Field
import au.com.cba.omnia.maestro.core.test.Spec

object ValidatorSpec extends Spec { def is = s2"""

Validator properties
====================

  validate on entity                     $entity
  validate on field                      $field
  fail validate                          $fail
  validators compose                     $composition
  validators accumulate error            $accumulation

"""

  case class Person(name: String, age: Int)
  val name = Field[Person, String]("name", _.name)
  val age = Field[Person, Int]("int", _.age)

  def entity = prop((p: Person) => {
    val validator = Validator.by[Person](_.name == p.name, "this shouldn't happen")
    validator.run(p) must_== (p.success) })

  def field = prop((p: Person) => {
    val check = Validator.by[String](_ == p.name, "this shouldn't happen")
    val validator = Validator.of(name, check)
    validator.run(p) must_== (p.success) })

  def fail = prop((p: Person) => {
    val validator = Validator.by[Person](_.name == (p.name + "x"), "this should happen")
    validator.run(p) must_== ("this should happen".failNel) })

  def composition = prop((p: Person) => {
    val validator = Validator.all(
      Validator.by[Person](_.name == p.name, "[name]"),
      Validator.by[Person](_.age == p.age, "[age]")
    )
    validator.run(p) must_== p.success })

  def accumulation = prop((p: Person) => {
    val validator = Validator.all(
      Validator.by[Person](_.name == (p.name + "x"), "[name]"),
      Validator.by[Person](_.age == (p.age + 1), "[age]")
    )
    validator.run(p) must_== NonEmptyList("[name]", "[age]").failure })

  implicit def PersonArbitrary: Arbitrary[Person] =
    Arbitrary(Arbitrary.arbitrary[(String, Int)] map Person.tupled)
}
