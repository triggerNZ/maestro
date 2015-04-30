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

import scala.util.Random

import java.io.File

import org.apache.commons.lang.StringUtils

import org.slf4j.{Logger, LoggerFactory}

import scalaz.Monoid

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.io.compress.{GzipCodec, CompressionCodec}

//import com.cloudera.sqoop.SqoopOptions

import cascading.flow.FlowDef

import com.twitter.scalding._

import com.cba.omnia.edge.source.compressible.CompressibleTypedTsv

// import au.com.cba.omnia.parlour._
import au.com.cba.omnia.parlour.SqoopSyntax.{ParlourExportDsl, ParlourImportDsl}
import au.com.cba.omnia.parlour.{SqoopExecution => ParlourExecution, ParlourExportOptions, ParlourImportOptions, ParlourOptions}

import au.com.cba.omnia.permafrost.hdfs.Hdfs

import au.com.cba.omnia.maestro.core.scalding.StatKeys
import au.com.cba.omnia.maestro.core.codec.Encode
import au.com.cba.omnia.maestro.core.split.Splitter
import au.com.cba.omnia.maestro.core.scalding.ExecutionOps._


/**
  * Configuration options for sqoop import
  *
  * Use [[SqoopExecution.createSqoopImportOptions]] to populate the `options` parameter.
  *
  * @param hdfsLandingPath: The HDFS landing directory where files are imported to.
  * @param hdfsArchivePath: The HDFS directory in which to archive files.
  * @param timePath:        The sub-folders representing the effective date, where we place files.
  * @param options:         A DSL for building options to pass to Sqoop
  */
case class SqoopImportConfig[T <: ParlourImportOptions[T]](
  hdfsLandingPath: String,
  hdfsArchivePath: String,
  timePath: String,
  options: T
)

/** Factory functions for [[SqoopImportConfig]] */
object SqoopImportConfig {
  /**
    * Convenience method to populate a parlour import option instance.
    *
    * @param connectionString: Database connection string
    * @param username:         Database username
    * @param password:         Database password
    * @param dbTablename:      Table name in database
    * @param outputFieldsTerminatedBy: Output field terminating character.
    *                          Defaults to '|'
    * @param nullString:       The string to be written for a null value in columns.
    *                          Defaults to the empty string.
    * @param whereCondition:   Where condition. Defaults to no condition.
    * @param initialOptions:   Optional initial parlour options. Not provided by default.
    *
    * @return : Populated parlour option
    */
  def options[T <: ParlourImportOptions[T] : Monoid](
    connectionString: String,
    username: String,
    password: String,
    dbTablename: String,
    outputFieldsTerminatedBy: Char = '|',
    nullString: String             = "",
    whereCondition: Option[String] = None,
    initialOptions: Option[T]      = None
  ): T = SqoopEx.createSqoopImportOptions[T](
    connectionString, username, password, outputFieldsTerminatedBy, nullString,
    TableSrc(dbTablename, whereCondition), initialOptions
  )

  /**
   * Convenience method to populate a parlour import option instance.
   * Use it when you want to use SQL Select query to fetch data.
   * If you do not provide `splitBy`, then `numberOfMappers` is set to 1.
   *
   * @param connectionString: database connection string
   * @param username: database username
   * @param password: database password
   * @param query: SQL Select query
   * @param outputFieldsTerminatedBy: output field terminating character
   * @param nullString: The string to be written for a null value in columns
   * @param splitBy: splitting column; if None, then `numberOfMappers` is set to 1
   * @param options: parlour option to populate
   *
   * @return : Populated parlour option
   */
  def optionsWithQuery[T <: ParlourImportOptions[T] : Monoid](
    connectionString: String,
    username: String,
    password: String,
    query: String,
    splitBy: Option[String],
    outputFieldsTerminatedBy: Char = '|',
    nullString: String             = "",
    initialOptions: Option[T]      = None
  ): T = SqoopEx.createSqoopImportOptions[T](
    connectionString, username, password, outputFieldsTerminatedBy, nullString,
    QuerySrc(query, splitBy), initialOptions
  )
}

/**
  * Configuration options for sqoop export
  *
  * Use [[SqoopExecution.createSqoopExportOptions]] to populate the `options` parameter.
  *
  * @param options:         A DSL for building options to pass to Sqoop
  * @param deleteFromTable: If true than delete all data from destination table
  *                         before exporting. Defaults to false.
  */
case class SqoopExportConfig[T <: ParlourExportOptions[T]](
  // the only reason to have this class at all instead of adding
  // deleteFromTable to T is that we want to move processing of deleteFromTable
  // as late as possible so it can detect when adding the option is illegal
  // would be nicer to do sanity checking on T or handle currently illegal Ts
  options: T,
  deleteFromTable: Boolean = false
)

/** Factory functions for [[SqoopExportConfig]] */
object SqoopExportConfig {
  /**
    * Convienience method to populate parlour export options.
    *
    * @param connectionString: Database connection string
    * @param username:         Database username
    * @param password:         Database password
    * @param dbTablename:      Table name in database
    * @param exportDir:        Optional directory to export from, if known in advance.
    *                          Not specified by default.
    * @param inputFieldsTerminatedBy: Output field terminating character.
    *                          Defaults to '|'
    * @param inputNullString:  The string to be written for a null value in columns.
    *                          Defaults to the empty string.
    * @param initialOptions:   Optional initial parlour options. Not provided by default.
    *
    * @return : Populated parlour option
    */
  def options[T <: ParlourExportOptions[T] : Monoid](
    connectionString: String,
    username: String,
    password: String,
    dbTablename: String,
    inputFieldsTerminatedBy: Char = '|',
    inputNullString: String       = "",
    initialOptions: Option[T]     = None
  ): T =
    SqoopEx.createSqoopExportOptions[T](
      connectionString, username, password, dbTablename, inputFieldsTerminatedBy,
      inputNullString, initialOptions
    )
}

/** Methods to import and export from sqoop */
trait SqoopExecution {
  /**
    * Runs a sqoop import from a database table to HDFS.
    *
    * @return Tuple of import directory and number of rows imported
    */
  def sqoopImport[T <: ParlourImportOptions[T]](
    config: SqoopImportConfig[T], condition: String
  ): Execution[(String, Long)] =
    sqoopImport(config.copy(options = config.options.where(condition)))

  /**
    * Runs a sqoop import from a database table to HDFS.
    *
    * @return Tuple of import directory and number of rows imported
    */
  def sqoopImport[T <: ParlourImportOptions[T]](
    config: SqoopImportConfig[T]
  ): Execution[(String, Long)] =
    SqoopEx.importExecution(config)

  /** Exports data from HDFS to a DB via Sqoop. */
  def sqoopExport[T <: ParlourExportOptions[T]](
    config: SqoopExportConfig[T], exportDir: String
  ): Execution[Unit] =
    SqoopEx.exportExecution(config.copy(options = config.options.exportDir(exportDir)))
  
  /** Exports data from a TypedPipe to a DB via Sqoop.
    *
    * The provided TypedPipe is flushed to a temporary location on-disk before writing.
    */
  def sqoopExport[T <: ParlourExportOptions[T], A : Encode](
    config: SqoopExportConfig[T],
    pipe:   TypedPipe[A]
  ): Execution[Unit] = {
      val sep = config.options.getInputFieldsTerminatedBy.getOrElse('|').toString
      val nul = config.options.getInputNullString.getOrElse("null").toString

      for {
        dir <- Execution.fromHdfs{ Hdfs.createTempDir(config.options.getTableName.mkString) }
        _   <- pipe.map(Encode.encode(nul, _))
                 .map(_.mkString(sep))
                 .writeExecution(TypedPsv(dir.toString))
        _   <- sqoopExport(config, dir.toString)
        _   <- Execution.fromHdfs{ Hdfs.delete(dir, true) }
      } yield ()
  }
}

/** Methods to support testing Sqoop execution */
object SqoopExecutionTest {
  /**  Set up environment variables so that sqoop knows how to run jobs in testing environment
    *
    *  @param customMRHome:  The testing map-reduce home.  Defaults to `~/.ivy2/cache`.
    *  @param customConnMan: Optionally set the connection manager class name.
    *                        `Some("")` resets it to null.  Default is not to set it (`None`).
    */
  def setupEnv(
    customMRHome: String = s"${System.getProperty("user.home")}/.ivy2/cache",
    customConnMan: Option[String] = None
  ) {
    // very dodgy way of configuring sqoop for testing,
    // but these should only ever have one value each in a single testing run
    // and the configuration becomes an implementation details hidden from our API
    System.setProperty(SqoopEx.mrHomeKey, customMRHome)
    customConnMan.fold(System.clearProperty(SqoopEx.connManKey))(cm => System.setProperty(SqoopEx.connManKey, cm))
  }
}

/**
  * Different sources for parlour imports.
  *
  * WARNING: not considered part of the maestro api.
  * We may change this without considering backwards compatibility.
  */
sealed trait ImportSource
case class TableSrc(table: String, where: Option[String]) extends ImportSource
case class QuerySrc(query: String, splitBy: Option[String]) extends ImportSource

/**
  * WARNING: not considered part of the maestro api.
  * We may change this without considering backwards compatibility.
  */
object SqoopEx {
  // system property key for setting custom hadoop map reduce dir
  // this property is a hack to get testing working without impacting our API
  val mrHomeKey  = "MAESTRO_HADOOP_MAPRED_HOME"
  val connManKey = "MAESTRO_PARLOUR_CONNMAN_CLASSNAME"

  def importExecution[T <: ParlourImportOptions[T]](
    config: SqoopImportConfig[T]
  ): Execution[(String, Long)] = {
    val importPath    = config.hdfsLandingPath + File.separator + config.timePath
    val archivePath   = config.hdfsArchivePath + File.separator + config.timePath
    val logger        = LoggerFactory.getLogger("Sqoop")
    val withDestDir   = config.options.targetDir(importPath)
    val withMRHome    = setCustomMRHome(withDestDir)
    val withClassName =
      withMRHome.getClassName.fold(withMRHome.className(f"SqoopImport_${Random.nextInt(Int.MaxValue)}%010d"))(_ => withMRHome)
    val withConnMan  = getCustomConnMan.fold(withClassName)(withClassName.connectionManager(_))
    val sqoopOptions = withConnMan.toSqoopOptions

    logger.info(s"connectionString  = ${sqoopOptions.getConnectString}")
    logger.info(s"tableName         = ${sqoopOptions.getTableName}")
    logger.info(s"targetDir         = ${sqoopOptions.getTargetDir}")
    logger.info(s"connectionManager = ${sqoopOptions.getConnManagerClassName}")

    for {
      // can't get count from sqoop, but can get it from archive job
      _     <- ParlourExecution.sqoopImport(withConnMan)
      count <- archive[GzipCodec](importPath, archivePath)
    } yield (importPath, count)
  }

  def archive[C <: CompressionCodec : ClassManifest](
    src: String, dest: String
  ): Execution[Long] = {
    def modifyConfig(config: Config) = Config(config.toMap ++ Map(
      "mapred.output.compress"          -> "true",
      "mapred.output.compression.type"  -> "BLOCK",
      "mapred.output.compression.codec" -> implicitly[ClassManifest[C]].erasure.getName
    ))

    val execution = TypedPipe.from(TextLine(src))
      .writeExecution(CompressibleTypedTsv[String](dest))
      .getAndResetCounters
      .map( _._2.get(StatKeys.tuplesWritten).getOrElse(0L) )

    execution.withSubConfig(modifyConfig)
  }

  def exportExecution[T <: ParlourExportOptions[T]](
    config: SqoopExportConfig[T]
  ): Execution[Unit] = {
    val withDelete    =
      if (config.deleteFromTable) trySetDeleteQuery(config.options)
      else config.options
    val withMRHome    = setCustomMRHome(withDelete)
    val withClassName = withMRHome.getClassName.fold(withMRHome.className(f"SqoopExport_${Random.nextInt(Int.MaxValue)}%010d"))(_ => withMRHome)
    val withConnMan   = getCustomConnMan.fold(withClassName)(withClassName.connectionManager(_))
    ParlourExecution.sqoopExport(withConnMan)
  }

  // Sets DELETE sql query. Throws RuntimeException if sql query already set or table name is not set
  def trySetDeleteQuery[T <: ParlourExportOptions[T]](options: T): T = {
    if (options.getSqlQuery.fold(false)(StringUtils.isNotEmpty(_))) {
      throw new RuntimeException("SqoopOptions.getSqlQuery must be empty on Sqoop Export with delete from table")
    }

    val tableName: String = options.getTableName.getOrElse(throw new RuntimeException("Cannot create DELETE query before SqoopExport - table name is not set"))
    options.sqlQuery(s"DELETE FROM $tableName")
  }

  def setCustomMRHome[T <: ParlourOptions[T]](options: T): T =
    Option(System.getProperty(mrHomeKey)).fold(options)(options.hadoopMapRedHome(_))

  def createSqoopImportOptions[T <: ParlourImportOptions[T] : Monoid](
    connectionString: String,
    username: String,
    password: String,
    outputFieldsTerminatedBy: Char,
    nullString: String,
    source: ImportSource,
    initialOptions: Option[T]
  ): T = {
    val initial  = initialOptions.getOrElse(implicitly[Monoid[T]].zero)
    val noSource = initial
      .connectionString(connectionString)
      .username(username)
      .password(password)
      .fieldsTerminatedBy(outputFieldsTerminatedBy)
      .nullString(nullString)
      .nullNonString(nullString)
    source match {
      case TableSrc(table, where) => {
        val withTable = noSource.tableName(table)
        where.fold(withTable)(withTable.where(_))
      }
      case QuerySrc(query, splitBy) => {
        val withQuery = noSource.sqlQuery(query)
        splitBy.fold(withQuery.numberOfMappers(1))(withQuery.splitBy(_))
      }
    }
  }

  def createSqoopExportOptions[T <: ParlourExportOptions[T] : Monoid](
    connectionString: String,
    username: String,
    password: String,
    dbTablename: String,
    inputFieldsTerminatedBy: Char,
    inputNullString: String,
    initialOptions: Option[T]
  ): T = {
    val initial = initialOptions.getOrElse(implicitly[Monoid[T]].zero)
    initial
      .connectionString(connectionString)
      .username(username)
      .password(password)
      .tableName(dbTablename)
      .inputFieldsTerminatedBy(inputFieldsTerminatedBy)
      .inputNull(inputNullString)
  }

  def getCustomConnMan: Option[String] =
    System.getProperty(connManKey) match {
      case null => None
      case "" => Some(null)
      case cm => Some(cm)
    }
}
