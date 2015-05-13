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

import scala.annotation.StaticAnnotation
import scala.reflect.macros.{Context, TypecheckException}
import scala.language.experimental.macros
import scala.collection.immutable.StringOps

/** Automapper for Thrift structs.
  *
  * One or more input structs are mapped to a single output struct, using a custom syntax to
  * define mapping rules for each field. Output struct fields for which mapping rules are
  * unspecified are mapped to matching fields with the same name and type in the input structs.
  * If more than one such input candidate exists, all structs with a field by this name must
  * agree on its value, otherwise a runtime exception is raised.
  *
  * To use the automapper, declare a method with the desired signature, assign to it a block of
  * transformation rules and annotate it with @automapper. The macro fills in the method with the
  * required logic. A simple use of the automapper annotation macro might look like this:
  * 
  *   @automap def mkFooBar (foo: Foo, bar: Bar): FooBar = {
  *       thisInt    := foo.thatInt
  *       thisString := bar.thatString
  *   }
  *
  * For a FooBar which has an `x` in common with Foo and a `y` in common with both Foo and Bar, the
  * generated code will take one of two forms, depending on the Thrift implementation of the output
  * type.
  *
  * Scrooge implementations generate constructors in the style of case classes:
  *
  *   def mkFooBar (in: (Foo, Bar)): FooBar = in match {
  *     case (foo, bar) => FooBar (x          = foo.x,
  *                                y          = if (foo.y == bar.y) foo.y
  *                                             else throw new IllegalArgumentException,
  *                                thisInt    = foo.thatInt,
  *                                thisString = bar.thatString)
  *                                                  
  *   }
  *
  * Humbug implementations instantiate the output object and assign values to each field:
  *
  *   def mkFooBar (in: (Foo, Bar)): FooBar = in match {
  *     case (foo, bar) => { val out        = new FooBar
  *                          out.x          = foo.x
  *                          out.y          = if (foo.y == bar.y) foo.y
  *                                           else throw new IllegalArgumentException
  *                          out.thisInt    = foo.thatInt
  *                          out.thisString = foo.thatString
  *                          out
  *                        }
  *   }
  *
  * Note that the signature of the method has changed: the original mkFooBar declaration took
  * two arguments, whereas the generated method takes a tuple. This is merely a syntactic
  * convenience: it is clearer to write mapping rules in terms of named arguments, but Scalding
  * pipes work with tuples. The Automapper is intended to help bridge that gap.
  *
  * Expansion of the macro comprises three overlapping stages: *reflection* on the inputs and
  * outputs, *computation* of field mappings and *generation* of output code.
  */
object automap {
  def impl(c: Context)(annottees: c.Expr[Any]*): c.Expr[Any] = {
    import c.universe._

    type Instance = (TypeName, TermName)
    type Field    = Instance
    type Struct   = Instance

    /** Abort macro expansion with an error message. */
    def fail(reason: String) = c.abort(c.enclosingPosition, reason)

    //
    // Reflection stage
    //

    /** Given a type identifier from the syntax tree, return its type and fields. */
    def inspectTermType(typ: Tree, name: TermName): ((TermName, Type), List[(Field, Struct)]) = {
        // The following obscure construction seems to be the simplest way of reliably forcing
        // resolution of an externally-defined class and its companion class.
        val q"{ class $n[$ts] { type FakeType = $r; () } }" = 
          c.typeCheck( q"{ class Dummy[T] { type FakeType = $typ; () } }" )
        val instance = (r.tpe.typeSymbol.name.toTypeName, name)
        val members  = getFields(r.tpe).map((_, instance))
        
        ((name, r.tpe), members)
    }

    def isHumbug(t: Type): Boolean = 
      t.baseClasses.exists{_.name.toTypeName.toString == "HumbugThriftStruct"}

    /** Enumerate the methods on a type. */
    def methods(t: Type): List[MethodSymbol] =
      t.declarations.filter(_.isMethod).map(_.asMethod).toList

    /** Enumerate the fields on a struct. */
    def getFields(t: Type): List[Field] =
      if (isHumbug(t)) humbugFields(t)
      else             scroogeFields(t.typeSymbol.companionSymbol.typeSignature)

    /** Enumerate the fields on a humbug struct. */
    def humbugFields(t: Type): List[Field] =
      methods(t)
        .filter(_.isGetter)
        .map(m => (m.returnType.typeSymbol.name.toTypeName, m.name.toTermName))

    /** Enumerate the fields on a scrooge struct. */
    def scroogeFields(t: Type): List[Field] =
      methods(t.member(newTypeName("Immutable")).asType.toType) // Scrooge's native representation
       .filter(_.isGetter)
       .filter(_.name.toTermName.toString.charAt(0) != '_') // Ignore meta fields
       .map(m => (m.returnType.typeSymbol.name.toTypeName, m.name.toTermName))

    /** Get the list of user-defined field mappings. */
    def getOverrides(xs: List[Tree]): Map[TermName, Tree] =
      xs.map { case q"$x := $expr" => (newTermName(x.toString), expr) }.toMap

    //
    // Code generation stage
    //

    /** Given an object instance `x:T` and a field `k:A`, show `x.k`. */
    val showId: ((Field, Struct)) => Tree = { case ((_, f),(_, s)) => q"$s.$f" }

    /** Render an equality comparison between a field on two structs. */
    def showEq(x: (Field, Struct), y: (Field, Struct)): Tree = q"${showId(x)} == ${showId(y)}"

    /** Render a runtime equality-checked value expression.
      *
      * Evaluates to `v` if `field` on each item in `structs` equals the value on `ref`, otherwise
      * raises an IllegalArgumentException. Example:
      *
      *  if (r.x == a.x &&  r.x == b.x) v else throw new IllegalArgumentException
      */
    def showChecker(field: Field, structs: List[Struct], ref: Struct, v: Tree): Tree =
      q"""if (${structs.map(x => showEq((field, x), (field, ref))).reduce((a, b) => q"$a && $b")}) $v
          else throw new IllegalArgumentException"""

    /** Render the value to assign an auto-mapped field.
      *
      * Includes a runtime equality check if there exists more than one suitable candidate.
      */
    def showAuto(field: Field, structs: List[Struct]): Tree = (field, structs) match {
      case ((_, k),    (_, x)  :: Nil) => q"$x.$k"
      case ((_, k), (v@(_, x)) :: xs)  => showChecker(field, xs, v, q"$x.$k")
    }

    /** Render an argument-style assignment for an auto-mapped field. */
    def showPassAuto(fld: Field, xs: List[Struct]): Tree =
      q"${fld._2} = ${showAuto(fld, xs)}"

    /** Render a match pattern to extract arguments from a tuple. */
    def showArgMatch(args: List[(TermName, Type)]): Tree =
      pq"(..${args.map(x => pq"${x._1} @ _")})"

    /** Render an argument-style assignment for a user-defined field mapping. */
    val showPassOverride:((TermName, Tree)) => Tree = {
      case (k, v) => q"$k = $v"
    }

    /** Render a humbug-compatible *setter*-style initialiser. */
    def showHumbugSetter(
      to:        Type,
      from:      List[(TermName, Type)], 
      autos:     List[(Field, List[Struct])],
      overrides: Map[TermName, Tree]
    ): Tree = 
       cq"""${showArgMatch(from)} => { 
               val out = new $to
               ..${autos.map     { case (fld, xs) => q"out.${fld._2} = ${showAuto(fld, xs)}" }}
               ..${overrides.map { case (k, v)    => q"out.$k = $v" }}
               out
             }
        """

    /** Render a scrooge-compatible *constructor*-style initaliser. */
    def showScroogeCtor(
      to:        Type,
      from:      List[(TermName, Type)],
      autos:     List[(Field, List[Struct])],
      overrides: Map[TermName, Tree]
    ): Tree = 
      cq"""${showArgMatch(from)} => 
        ${newTermName(to.typeSymbol.name.toString)} (..${autos.map{case (fld, xs) => showPassAuto(fld, xs)} ++ overrides.map(showPassOverride)})"""

    //
    // Computation stage
    //

    /** Map output fields to inputs with the same name, unless an override is defined. */
    def automap(
      inputs:    Map[Field, List[Struct]],
      outputs:   List[(Field, Struct)],
      overrides: Map[TermName, Tree]
    ): List[(Field, List[Struct])] =
      outputs.map(_._1)
             .filterNot( a => overrides.exists( b => a._2 == b._1 ) )
             .map(field => inputs.get(field) match { 
               case Some(matches) => (field, matches)
               case _ => fail (s"Could not resolve `${field._2}`")
             })
        
    /** Pull apart the source object, pad it out, put it back together. */
    def mkMapper(src: Tree): Tree = {
      val q"def $name (..$srcArgs): $srcTo = $srcBody" = src
      val srcRules = srcBody match {
        case Block(xs,x)  => x :: xs
        case _            => Nil
      }

      val (from, inputs) = srcArgs.map{ case q"$m val $x: $t = $v" => inspectTermType(t, x) }.unzip
      val (to, outputs)  = inspectTermType(srcTo, newTermName("out"))
      val overrides      = getOverrides(srcRules)
      val autos          = automap(priorityMap(inputs.flatten), outputs, overrides)

      val fn = if (isHumbug(to._2)) showHumbugSetter (to._2, from, autos, overrides)
               else showScroogeCtor (to._2, from, autos, overrides)
      q" def $name (in: (..${from.map(_._2)})): ${to._2} = in match { case $fn }" 
    }

    annottees
      .map(_.tree)
      .map{case (x: DefDef) => c.Expr[Any](mkMapper(x))
           case _           => fail("Automap annottee must be method accepting thrift structs and returning one.")}.head
  }
  
  /** Create a priority map of keys to list of values. */
  def priorityMap[K, V](xs: List[(K, V)]): Map[K, List[V]] =
    (xs :\ Map[K, List[V]]())
    { case ((k, v), acc) => acc + ((k, v :: acc.getOrElse(k, List[V]()))) }
 
}

class automap(xs: Any*) extends StaticAnnotation {
  def macroTransform(annottees: Any*) = macro automap.impl
}

