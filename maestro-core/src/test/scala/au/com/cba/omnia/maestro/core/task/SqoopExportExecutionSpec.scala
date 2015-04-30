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

package au.com.cba.omnia.maestro.core.task

import java.util.UUID

import scala.util.Failure
import scala.io.Source

import scalikejdbc.{SQL, ConnectionPool, AutoSession}

import com.twitter.scalding.{TextLine, TypedPipe}

import au.com.cba.omnia.parlour.SqoopSyntax.{ParlourExportDsl,TeradataParlourExportDsl}

import au.com.cba.omnia.thermometer.core.Thermometer._
import au.com.cba.omnia.thermometer.core.ThermometerSpec

import au.com.cba.omnia.maestro.core.split.Splitter
import au.com.cba.omnia.maestro.core.codec.Encode
import au.com.cba.omnia.maestro.core.thrift.humbug.Exhaustive

object SqoopExportExecutionSpec
  extends ThermometerSpec
  with SqoopExecution { def is = s2"""
  Sqoop Export Execution test
  ===========================

  Exporting data from HDFS to DB appending to existing rows  $endToEndExportWithAppend
  Export data from HDFS to DB deleting all existing rows     $endToEndExportWithDeleteTest
  Fails if sqlQuery set and need to delete all existing rows $endToEndExportWithQuery
  Fails with Teradata connection manager                     $endToEndExportWithTeradataConnMan
  Succeeds with Teradata after connection manager reset      $endToEndExportWithTeradataResetConnMan
  Export pipe                                                $endToEndExportPipe

"""
  val connectionString = "jdbc:hsqldb:mem:sqoopdb"
  val username         = "sa"
  val password         = ""
  val mapRedHome       = s"${System.getProperty("user.home")}/.ivy2/cache"
  val exportDir        = s"$dir/user/sales/books/customers/export"
  val resourceUrl      = getClass.getResource("/sqoop")
  val newCustomers     = Source.fromFile(s"${resourceUrl.getPath}/sales/books/customers/export/new-customers.txt").getLines().toList
  val oldCustomers     = Source.fromFile(s"${resourceUrl.getPath}/sales/books/customers/old-customers.txt").getLines().toList

  def options(table: String) = SqoopExportConfig.options[ParlourExportDsl](
    connectionString, username, password, table
  )

  Class.forName("org.hsqldb.jdbcDriver")

  SqoopExecutionTest.setupEnv()

  def endToEndExportWithAppend = {
    val table = s"customer_export_${UUID.randomUUID.toString.replace('-', '_')}"
    tableSetup(connectionString, username, password, table, Option(oldCustomers))

    withEnvironment(path(resourceUrl.toString)) {
      val config = SqoopExportConfig(options(table))
      executesOk(sqoopExport(config, exportDir))
      tableData(connectionString, username, password, table) must containTheSameElementsAs(newCustomers ++ oldCustomers)
    }
  }

  def endToEndExportPipe = {
    val table = s"testobj_export_${UUID.randomUUID.toString.replace('-', '_')}"
    testObjTableSetup(connectionString, username, password, table)

    val config = SqoopExportConfig(options(table))
    val pipe = TypedPipe.from[Exhaustive](testObjects)
    executesOk(sqoopExport(config, pipe))
    testObjTableData(connectionString, username, password, table) must containTheSameElementsAs(testObjects.map(Encode.encode("", _).mkString("|")))
  }

  def endToEndExportWithDeleteTest = {
    val table = s"customer_export_${UUID.randomUUID.toString.replace('-', '_')}"
    tableSetup(connectionString, username, password, table, Option(oldCustomers))

    withEnvironment(path(resourceUrl.toString)) {
      val config = SqoopExportConfig(options(table), deleteFromTable = true)
      executesOk(sqoopExport(config, exportDir))
      tableData(connectionString, username, password, table) must containTheSameElementsAs(newCustomers)
    }
  }

  def endToEndExportWithQuery = {
    val table = s"customer_export_${UUID.randomUUID.toString.replace('-', '_')}"
    tableSetup(connectionString, username, password, table, Option(oldCustomers))

    withEnvironment(path(resourceUrl.toString)) {
      val config = SqoopExportConfig(
        options(table).sqlQuery(s"select * from $table"), deleteFromTable = true
      )
      execute(sqoopExport(config, exportDir)) must throwA[RuntimeException](
        message = "SqoopOptions.getSqlQuery must be empty on Sqoop Export with delete from table"
      )
    }
  }

  def TeradataOptions(table: String) = SqoopExportConfig.options[TeradataParlourExportDsl](
    connectionString, username, password, table
  )

  def endToEndExportWithTeradataConnMan = {
    SqoopExecutionTest.setupEnv()
    val table = s"customer_export_${UUID.randomUUID.toString.replace('-', '_')}"
    tableSetup(connectionString, username, password, table, Option(List()))

    withEnvironment(path(resourceUrl.toString)) {
      val config = SqoopExportConfig(TeradataOptions(table))
      execute(sqoopExport(config, exportDir)) must beLike { case Failure(_) => ok }
    }
  }

  def endToEndExportWithTeradataResetConnMan = {
    SqoopExecutionTest.setupEnv(customConnMan=Some(""))
    val table = s"customer_export_${UUID.randomUUID.toString.replace('-', '_')}"
    tableSetup(connectionString, username, password, table, Option(List()))

    withEnvironment(path(resourceUrl.toString)) {
      val config = SqoopExportConfig(TeradataOptions(table))
      executesOk(sqoopExport(config, exportDir))
      tableData(connectionString, username, password, table) must containTheSameElementsAs(newCustomers)
    }
  }

  def tableSetup(
    connectionString: String,
    username: String,
    password: String,
    table: String = "customer_export",
    data: Option[List[String]] = None
  ): Unit = {
    ConnectionPool.singleton(connectionString, username, password)
    implicit val session = AutoSession
    SQL(s"""
      create table $table (
        id integer,
        name varchar(20),
        accr varchar(20),
        cat varchar(20),
        sub_cat varchar(20),
        balance integer
      )
    """).execute.apply()

    data.foreach(lines =>
      lines.foreach(line => {
        val row = line.split('|')
        SQL(s"""insert into ${table}(id, name, accr, cat, sub_cat, balance)
          values ('${row(0)}', '${row(1)}', '${row(2)}', '${row(3)}', '${row(4)}', '${row(5)}')"""
        ).update().apply()
      })
    )
  }

  def tableData(
    connectionString: String,
    username: String,
    password: String,
    table: String = "customer_export"
  ): List[String] = {
    ConnectionPool.singleton(connectionString, username, password)
    implicit val session = AutoSession
    SQL(s"select * from $table").map(rs => List(rs.int("id"), rs.string("name"), rs.string("accr"),
      rs.string("cat"), rs.string("sub_cat"), rs.int("balance")) mkString "|").list.apply()
  }
  
  def testObjTableSetup(
    connectionString: String,
    username: String,
    password: String,
    table: String
  ): Unit = {
    ConnectionPool.singleton(connectionString, username, password)
    implicit val session = AutoSession
    SQL(s"""
      create table $table (
        stringField varchar(255) not null,
        booleanField boolean not null,
        intField integer not null,
        longField bigint not null,
        doubleField double not null,
        optIntField integer,
        optStringField varchar(255)
      )
    """).execute.apply()

  }

  def testObjTableData(
    connectionString: String,
    username:         String,
    password:         String,
    table:            String
  ): List[String] = {
    ConnectionPool.singleton(connectionString, username, password)
    implicit val session = AutoSession
    SQL(s"select * from $table").map(rs => List(rs.string("stringField"), rs.boolean("booleanField"), rs.int("intField"),
      rs.long("longField"), rs.double("doubleField"), rs.intOpt("optIntField").getOrElse(""), rs.stringOpt("optStringField").getOrElse("")) mkString "|").list.apply()
  }
  
  def testObjects = {
    def create (
      str:    String,
      bool:   Boolean,
      int:    Int, 
      long:   Long, 
      double: Double, 
      optInt: Option[Int],
      optStr: Option[String]
    ): Exhaustive = { 
      val x = new Exhaustive
      x.myString    = str
      x.myBoolean   = bool
      x.myInt       = int
      x.myLong      = long
      x.myDouble    = double
      x.myOptInt    = optInt
      x.myOptString = optStr
      x
    }
    val x1 = create("Foo", false, 1, Math.pow(2,32).toLong, 3.14, Some(1), Some("bar"))
    val x2 = create("Bar", true, 2, Math.pow(3,32).toLong, 2.71, None, None)
    val x3 = create("FooBar", true, 3, Math.pow(3,32).toLong, 2.71, None, Some("foobar"))
    x1 :: x2 :: x3 :: Nil
  }

  implicit def encoder: Encode[Exhaustive] = {
    Encode((none, a) => 
      List(
        Encode.encode[String](none, a._1), 
        Encode.encode[Boolean](none, a._2),
        Encode.encode[Int](none, a._3),
        Encode.encode[Long](none, a._4),
        Encode.encode[Double](none, a._5),
        Encode.encode[Option[Int]](none, a._6),
        Encode.encode[Option[String]](none, a._7)
      ).flatten
    )
  }

}
