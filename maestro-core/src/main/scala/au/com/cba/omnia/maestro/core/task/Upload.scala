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
package task

import java.io.File

import scala.util.matching.Regex

import org.apache.hadoop.conf.Configuration

import org.apache.log4j.Logger

import scalaz._, Scalaz._

import au.com.cba.omnia.omnitool.Result

import au.com.cba.omnia.permafrost.hdfs.Hdfs

import au.com.cba.omnia.maestro.core.upload._

/**
  * Push source files to HDFS using [[upload]] and archive them.
  *
  * See the example at `au.com.cba.omnia.maestro.example.CustomerUploadExample`.
  *
  * In order to run map-reduce jobs, we first need to get our data onto HDFS.
  * [[upload]] copies data files from the local machine onto HDFS and archives
  * the files.
  *
  * For a given `domain` and `tableName`, [[upload]] copies data files from
  * the standard local location: `\$localIngestDir/dataFeed/\$domain`, to the
  * standard HDFS location: `\$hdfsRoot/source/\$domain/\$tableName`.
  *
  * Only use [[customUpload]] if we are required to use non-standard locations.
  */
trait Upload {

  /**
    * Pushes source files onto HDFS and archives them locally.
    *
    * `upload` expects data files intended for HDFS to be placed in
    * the local folder `\$localIngestDir/dataFeed/\$source/\$domain`. Different
    * source systems will use different values for `source` and `domain`.
    * `upload` processes all data files that match a given file pattern.
    * The file pattern format is explained below.
    *
    * Each data file will be copied onto HDFS as the following file:
    * `\$hdfsRoot/source/\$source/\$domain/\$tableName/<year>/<month>/<day>/<originalFileName>`.
    *
    * Data files are also compressed and archived on the local machine and on HDFS.
    * Each data file is archived as:
    *  - `\$localArchiveDir/\$source/\$domain/\$tableName/<year>/<month>/<day>/<originalFileName>.bz2`
    *  - `\$hdfsRoot/archive/\$source/\$domain/\$tableName/<year>/<month>/<day>/<originalFileName>.bz2`
    *
    * (These destination paths may change slightly depending on the fields in the file pattern.)
    *
    * Some files placed on the local machine are control files. These files
    * are not intended for HDFS and are ignored by `upload`. `upload` will log
    * a message whenever it ignores a control file.
    *
    * When an error occurs, `upload` stops copying files immediately. Once the
    * cause of the error has been addressed, `upload` can be run again to copy
    * any remaining files to HDFS. `upload` will refuse to copy a file if that
    * file or it's control flags are already present in HDFS. If you
    * need to overwrite a file that already exists in HDFS, you will need to
    * delete the file and it's control flags before `upload` will replace it.
    *
    * The file pattern is a string containing the following elements:
    *  - Literals:
    *    - Any character other than `{`, `}`, `*`, `?`, or `\` represents itself.
    *    - `\{`, `\}`, `\*`, `\?`, or `\\` represents `{`, `}`, `*`, `?`, and `\`, respectively.
    *    - The string `{table}` represents the table name.
    *  - Wildcards:
    *    - The character `*` represents an arbitrary number of arbitrary characters.
    *    - The character `?` represents one arbitrary character.
    *  - Date times:
    *    - The string `{<timestamp-pattern>}` represents a JodaTime timestamp pattern.
    *    - `upload` only supports certain date time fields:
    *      - year (y),
    *      - month of year (M),
    *      - day of month (d),
    *      - hour of day (H),
    *      - minute of hour (m), and
    *      - second of minute (s).
    *
    * Some example file patterns:
    *  - `{table}{yyyyMMdd}.DAT`
    *  - `{table}_{yyyyMMdd_HHss}.TXT.*.{yyyyMMddHHss}`
    *  - `??_{table}-{ddMMyy}*`
    *
    * @param source: Source system
    * @param domain: Database or project within source
    * @param tableName: Table name or file name in database or project
    * @param filePattern: File name pattern
    * @param localIngestDir: Root directory of incoming data files
    * @param localArchiveDir: Root directory of the local archive
    * @param hdfsRoot: Root directory of HDFS
    * @param conf: Hadoop configuration
    * @param controlPattern: The regex which identifies control files. Optional.
    * @return The list of copied hdfs files if successful, or any error occuring when uploading files
    */
  def upload(
    source: String, domain: String, tableName: String, filePattern: String,
    localIngestDir: String, localArchiveDir: String, hdfsRoot: String,
    conf: Configuration, controlPattern: Regex = ControlPattern.default
  ): Result[List[String]] = {
    val logger = Logger.getLogger("Upload")

    logger.info("Start of upload")
    logger.info(s"source          = $source")
    logger.info(s"domain          = $domain")
    logger.info(s"tableName       = $tableName")
    logger.info(s"filePattern     = $filePattern")
    logger.info(s"localIngestDir  = $localIngestDir")
    logger.info(s"localArchiveDir = $localArchiveDir")
    logger.info(s"hdfsRoot        = $hdfsRoot")

    val locSourceDir   = List(localIngestDir,  "dataFeed", source, domain)            mkString File.separator
    val archiveDir     = List(localArchiveDir,             source, domain, tableName) mkString File.separator
    val hdfsArchiveDir = List(hdfsRoot,        "archive",  source, domain, tableName) mkString File.separator
    val hdfsLandingDir = List(hdfsRoot,        "source",   source, domain, tableName) mkString File.separator

    val result = Upload.uploadImpl(
      tableName, filePattern, locSourceDir, archiveDir, hdfsArchiveDir, hdfsLandingDir, controlPattern
    ).run(conf)

    val args = s"$source/$domain/$tableName"
    result.foldAll(
      _         => logger.info(s"Upload ended for $args"),
      msg       => logger.error(s"Upload failed for $args: $msg"),
      ex        => logger.error(s"Upload failed for $args", ex),
      (msg, ex) => logger.error(s"Upload failed for $args: $msg", ex)
    )

    result
  }

  /**
    * Pushes source files onto HDFS and archives them locally, using non-standard file locations.
    *
    * As per [[upload]], except the user has more control where to find data
    * files, where to copy them, and where to archive them.
    *
    * Data files are found in the local folder `\$locSourceDir`. They are copied
    * to `\$hdfsLandingDir/<year>/<month>/<originalFileName>`,
    * and archived at `\$archiveDir/<year>/<month>/<originalFileName>.gz`. (These
    * directories may change slightly depending on the timestamp format.)
    *
    * In all other respects `customUpload` behaves the same as [[upload]].
    *
    * @param tableName: Table name or file name in database or project
    * @param filePattern: File name pattern
    * @param localIngestPath: Local ingest directory
    * @param localArchivePath: Local archive directory
    * @param hdfsArchivePath: HDFS archive directory
    * @param hdfsLandingPath: HDFS landing directory
    * @param conf: Hadoop configuration
    * @param controlPattern: The regex which identifies control files. Optional.
    * @return The list of copied hdfs files if successful, or any error occuring when uploading files
    */
  def customUpload(
    tableName: String, filePattern: String, localIngestPath: String,
    localArchivePath: String, hdfsArchivePath: String, hdfsLandingPath: String,
    conf: Configuration, controlPattern: Regex = ControlPattern.default
  ): Result[List[String]] = {
    val logger = Logger.getLogger("Upload")

    logger.info("Start of custom upload")
    logger.info(s"tableName        = $tableName")
    logger.info(s"filePattern      = $filePattern")
    logger.info(s"localIngestPath  = $localIngestPath")
    logger.info(s"localArchivePath = $localArchivePath")
    logger.info(s"hdfsArchivePath  = $hdfsArchivePath")
    logger.info(s"hdfsLandingPath  = $hdfsLandingPath")

    val result =
      Upload.uploadImpl(tableName, filePattern, localIngestPath,
        localArchivePath, hdfsArchivePath, hdfsLandingPath,
        controlPattern).run(conf)

    result.foldAll(
      _         => logger.info(s"Custom upload ended from $localIngestPath"),
      msg       => logger.error(s"Custom upload failed from $localIngestPath: $msg"),
      ex        => logger.error(s"Custom upload failed from $localIngestPath", ex),
      (msg, ex) => logger.error(s"Custom upload failed from $localIngestPath: $msg", ex)
    )

    result
  }
}

/**
  * Contains implementation for `upload` methods in [[Upload]] trait.
  *
  * WARNING: The methods on this object are not considered part of the public
  * maestro API, and may change without warning. Use the methods in the maestro
  * API instead, unless you know what you are doing.
  */
object Upload {
  val logger = Logger.getLogger("Upload")

  /** Implementation of `upload` methods in [[Upload]] trait */
  def uploadImpl(
    tableName: String, filePattern: String, localIngestPath: String,
    localArchivePath: String, hdfsArchivePath: String, hdfsLandingPath: String,
    controlPattern: Regex
  ): Hdfs[List[String]] = for {
    temp          <- Hdfs.result(Input.findFiles(new File(localIngestPath), tableName, filePattern, controlPattern))
    (ctrls, datas) = temp
    _              = ctrls foreach  (ctrl =>
      logger.info(s"skipping control file ${ctrl.file.getName}")
    )
    hdfsFiles     <- datas traverse (data =>
      Push.push(data, hdfsLandingPath, localArchivePath, hdfsArchivePath).map(record => {
        logger.info(s"copied ${record.source.getName} to ${record.dest}")
        record.dest.toString
      })
    )
  } yield hdfsFiles
}
