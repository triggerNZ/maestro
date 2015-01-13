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
package codec

import scalaz._, Scalaz._
import scalaz.scalacheck.ScalazProperties.monad

import org.scalacheck.Arbitrary

import au.com.cba.omnia.maestro.core.test.Spec
import au.com.cba.omnia.maestro.core.test.Arbitraries._

object DecodeSpec extends Spec { def is = s2"""

Decode laws
===========

  lawful monad                                                                                           ${monad.laws[Decode]}

Decode properties
=================

  boolean decoding                                                                                       ${primitive[Boolean]}
  int decoding                                                                                           ${primitive[Int]}
  long decoding                                                                                          ${primitive[Long]}
  double decoding                                                                                        ${primitive[Double]}
  string decoding                                                                                        ${primitive[String]}
  tuple decoding                                                                                         ${tuples}
  option decoding for non strings                                                                        ${option}
  option decoding for strings                                                                            ${optionString}
  empty values for optional string fields are decoded as empty string                                    ${emptyString}
  otherwise empty values are decoded as None                                                             ${emptyOther}
  Encoding and then decoding a string with the same value as the missing value indicator decodes to None ${exception}

Decode witness
==============

  If this spec compiles then we now we know we can derive primitive and product Decode instances  ${ok}

"""

  val noneVal = "\0"

  def primitive[A : Arbitrary : Decode : Encode] = prop { (v: A) =>
    Decode.decode(noneVal, Encode.encode(noneVal, v)) must_== DecodeOk(v)
  }

  def tuples = prop { (b: Boolean, i: Int, l: Long, d: Double, s: String) =>
    val tuple = (b, i, l, d, s, (b, i, l, d, s))
    Decode.decode[(Boolean, Int, Long, Double, String, (Boolean, Int, Long, Double, String))](
      noneVal, Encode.encode(noneVal, tuple)
    ) must_== DecodeOk(tuple)
  }

  def option = prop { (v: Option[Int]) =>
    Decode.decode[Option[Int]](noneVal, Encode.encode(noneVal, v)) must_== DecodeOk(v)
  }

  def optionString = prop { (v: Option[String]) => (v != Some(noneVal)) ==>
    (Decode.decode[Option[String]](noneVal, Encode.encode(noneVal, v)) must_== DecodeOk(v))
  }

  def emptyString = Decode.decode[Option[String]](noneVal, List("")) must_== DecodeOk(Some(""))

  def emptyOther = Decode.decode[Option[Int]](noneVal, List("")) must_== DecodeOk(None)

  def exception =
    Decode.decode[Option[String]](noneVal, Encode.encode(noneVal, Some(noneVal))) must_== DecodeOk(None)

  /* witness these codecs, compilation is the test */
  Decode.of[String]
  Decode.of[Int]
  Decode.of[Long]
  Decode.of[Double]
  Decode.of[Boolean]
  Decode.of[List[Int]]
  Decode.of[Vector[Int]]
  Decode.of[(String, Int)]
  Decode.of[(String, (Long, Int), (Double, Boolean))]
  Decode.of[Example]
  Decode.of[Option[String]]
  Decode.of[Option[Int]]
  Decode.of[Option[Long]]
  Decode.of[Option[Double]]
  Decode.of[Option[Boolean]]

  case class Nested(i: Int, b: Boolean)
  case class Example(s: String, l: Long, n: Nested)

  implicit def DecodeIntEqual: Equal[Decode[Int]] =
    Equal.equal { (a, b) =>
      val ns = (1 to 100).toList.map(_.toString)
      (a.run(noneVal, ns, 0) == a.run(noneVal, ns, 0))
    }
}
