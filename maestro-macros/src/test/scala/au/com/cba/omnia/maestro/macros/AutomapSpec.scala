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

package au.com.cba.omnia.maestro.macros

import au.com.cba.omnia.maestro.test.Spec
import au.com.cba.omnia.maestro.test.Arbitraries._
import au.com.cba.omnia.maestro.test.thrift.humbug.{Types => HTypes, _}
import au.com.cba.omnia.maestro.test.thrift.scrooge._


import com.twitter.util.Eval

object AutomapSpec extends Spec { def is = s2"""
AutomapSpec
==================

  Transforms
    can transform one struct to another without manual operations        $autoTransform
    can transform one struct to another with manual operations           $manualTransform

  Humbug generated Thrift structures
    can be joined                                                        $joinHumbug
    can handle multiple equal input fields                               $multipleEqualSourcesHumbug
    throws exception when encountering multiple differing input fields   $multipleDifferingSourcesHumbug

  Scrooge generated Thrift structures
    can be joined                                                        $joinScrooge
    can handle multiple equal input fields                               $multipleEqualSourcesScrooge
    throws exception when encountering multiple differing input fields   $multipleDifferingSourcesScrooge

  Combined Humbug and Scrooge Thrift structures
    can join Scrooge and Humbug to Humbug                                $mixedToHumbug
    can join Scrooge and Humbug to Scrooge                               $mixedToScrooge

"""

  implicit val srcFields = Macros.mkFields[HTypes]
  implicit val dstFields = Macros.mkFields[Large]
  val imports =
    """import au.com.cba.omnia.maestro.macros._
       import au.com.cba.omnia.maestro.test.thrift.humbug._
       import au.com.cba.omnia.maestro.test.thrift.scrooge._
    """

  def autoTransform = prop { (types: HTypes) =>
    val s1 = new SubOne()
    s1.stringField  = types.stringField
    s1.booleanField = types.booleanField

    val s2 = new SubTwo()
    s2.intField    = types.intField
    s2.longField   = types.longField
    s2.doubleField = types.doubleField

    val s3 = new SubThree()
    s3.longField = types.longField
    s3.intField  = types.intField

    @automap def hToOne(x: HTypes): SubOne = {}
    hToOne(types) must_== s1
    @automap def hToTwo(x: HTypes): SubTwo = {}
    hToTwo(types) must_== s2
    @automap def hToThree(x: HTypes): SubThree = {}
    hToThree(types) must_== s3
  }

  def manualTransform = prop { (types: HTypes) =>
    val s1 = new SubOne()
    s1.stringField  = types.stringField
    s1.booleanField = !types.booleanField

    val s2 = new SubTwo()
    s2.intField    = types.intField + 1
    s2.longField   = types.longField - 1
    s2.doubleField = types.doubleField

    @automap def hToOne(x: HTypes): SubOne = {
      booleanField := !x.booleanField
    }
    @automap def hToTwo(x: HTypes): SubTwo = {
      intField  := x.intField + 1
      longField := x.longField - 1
    }

    hToOne(types) must_== s1
    hToTwo(types) must_== s2
  }

  val scroogeStructOne = JoinOneScrooge("some-string", true, 42)
  val scroogeStructTwo = JoinTwoScrooge("some-string", 1000L, 2d)

  val scroogeDuplicateOk  = JoinOneDuplicateScrooge(42)
  val scroogeDuplicateBad = JoinOneDuplicateScrooge(13)

  val scroogeIncompatible = JoinOneIncompatibleScrooge("not int")

  val humbugStructOne = {
    val one = new JoinOneHumbug
    one.stringField  = "some-string"
    one.booleanField = true
    one.someField    = 42
    one
  }

  val humbugStructTwo = {
    val two = new JoinTwoHumbug
    two.stringField = "some-string"
    two.longField   = 1000L
    two.doubleField = 2d
    two
  }

  val humbugDuplicateOk  = {
    val dup = new JoinOneDuplicateHumbug
    dup.someField = 42

    dup
  }

  val humbugDuplicateBad = {
    val dup = new JoinOneDuplicateHumbug
    dup.someField = 13
    dup
  }


  def joinHumbug = {
    @automap def join(x: JoinOneHumbug, y: JoinTwoHumbug): JoinableHumbug = {}

    val resultJoined = new JoinableHumbug
    resultJoined.booleanField = true
    resultJoined.longField    = 1000L
    resultJoined.someField    = 42
    join((humbugStructOne, humbugStructTwo)) must beEqualTo(resultJoined)
  }

  def joinScrooge = {
    @automap def join(x: JoinOneScrooge, y: JoinTwoScrooge): JoinableScrooge = {}

    val resultJoined = JoinableScrooge(true, 1000, 42)
    join((scroogeStructOne, scroogeStructTwo)) must beEqualTo(resultJoined)
  }

  def multipleEqualSourcesHumbug = {
    @automap def join(x: JoinOneHumbug, y: JoinTwoHumbug, z: JoinOneDuplicateHumbug): JoinableHumbug = {}

    val resultJoined          = new JoinableHumbug
    resultJoined.booleanField = true
    resultJoined.longField    = 1000L
    resultJoined.someField    = 42
    join((humbugStructOne, humbugStructTwo, humbugDuplicateOk)) must_== resultJoined
  }

  def multipleEqualSourcesScrooge = {
    @automap def join(x: JoinOneScrooge, y: JoinTwoScrooge, z: JoinOneDuplicateScrooge): JoinableScrooge = {}

    val resultJoined = JoinableScrooge(true, 1000L, 42)
    join((scroogeStructOne, scroogeStructTwo, scroogeDuplicateOk)) must_== resultJoined
  }

  def multipleDifferingSourcesHumbug = {
    @automap def join (x: JoinOneHumbug, y: JoinTwoHumbug, z: JoinOneDuplicateHumbug): JoinableHumbug = {}

    join((humbugStructOne, humbugStructTwo, humbugDuplicateBad)) must throwAn[IllegalArgumentException]
  }

  def multipleDifferingSourcesScrooge = {
    @automap def join (x: JoinOneScrooge, y: JoinTwoScrooge, z: JoinOneDuplicateScrooge): JoinableScrooge = {}

    join((scroogeStructOne, scroogeStructTwo, scroogeDuplicateBad)) must throwAn[IllegalArgumentException]
  }

  def mixedToHumbug = {
    @automap def join(x: JoinOneScrooge, y: JoinTwoHumbug): JoinableHumbug = {}

    val resultJoined = new JoinableHumbug
    resultJoined.booleanField = true
    resultJoined.longField    = 1000L
    resultJoined.someField    = 42
    join((scroogeStructOne, humbugStructTwo)) must beEqualTo(resultJoined)
  }

  def mixedToScrooge = {
    @automap def join (x: JoinOneHumbug, y: JoinTwoScrooge): JoinableScrooge = {}

    val resultJoined = JoinableScrooge(true, 1000, 42)
    join((humbugStructOne, scroogeStructTwo)) must beEqualTo(resultJoined)
  }
}
