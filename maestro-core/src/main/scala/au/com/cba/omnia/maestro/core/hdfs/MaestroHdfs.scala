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
package hdfs

import org.apache.hadoop.fs.{FileSystem, Path}
import scalaz._, Scalaz._

import au.com.cba.omnia.permafrost.hdfs.Hdfs

import Guard.{NotProcessed, IngestionComplete}

/**
 * Utility functions for Hdfs monad
 * (<a href="https://commbank.github.io/permafrost/latest/api/index.html#au.com.cba.omnia.permafrost.hdfs.Hdfs">
 * au.com.cba.omnia.permafrost.hdfs.Hdfs</a>), with a Maestro flavour.
 *
 * This object was introduced in Maestro 2.10 to replace the [[Guard]] object, which served a similar purpose but
 * predated the Hdfs monad. [[Guard]] is now deprecated and <i>will be removed</i> in a future version. Users of
 * Maestro should update any code which uses [[Guard]] to use [[MaestroHdfs]] instead.
 *
 * <strong>Migration guide from Guard to MaestroHdfs</strong>
 *
 * [[MaestroHdfs]] provides all the same functions as [[Guard]], with the same parameters, but instead of performing
 * the work immediately it returns an
 * <a href="https://commbank.github.io/permafrost/latest/api/index.html#au.com.cba.omnia.permafrost.hdfs.Hdfs">
 * Hdfs</a> action. This can be composed with other Hdfs actions into a more complex workflow. Alternatively, it
 * can be composed with other <a href="http://twitter.github.io/scalding/index.html#com.twitter.scalding.Execution">
 * Execution</a> actions, with the help of `Execution.fromHdfs`.
 *
 * Let's look at the `etl.gcps-gcps` project as an example.
 *
 * {{{
 * // old code
 * case class GcpsConfig[T <: ThriftStruct : Decode : Tag : Manifest](...)
 * {
 *   ...
 *   val inputs = Guard.expandTransferredPaths(s"${maestro.hdfsRoot}/source/${maestro.source}/${maestro.tablename}/*/*/*")
 * }}}
 *
 * Notice that `Guard.expandTransferredPaths` executes immediately <i>when the class is instantiated</i>, which is a
 * hidden side-effect. The benefit of the `Hdfs` monad is that it allows us to reason more easily about the order in
 * which operations are performed.
 *
 * We can rewrite this line simply by changing `Guard` to `MaestroHdfs`, and changing the name of the `val` to
 * make it clear that this is now an <i>action</i> rather than a simple value:
 *
 * {{{
 * // new code
 * case class GcpsConfig[T <: ThriftStruct : Decode : Tag : Manifest](...)
 * {
 *   ...
 *   val findInputs = MaestroHdfs.expandTransferredPaths(s"${maestro.hdfsRoot}/source/${maestro.source}/${maestro.tablename}/*/*/*")
 * }}}
 *
 * Later in the file, we see where `inputs` was being used:
 *
 * {{{
 * // old code
 * def run[T <: ThriftStruct : Decode : Tag : Manifest](
 *   tableName      : String,
 *   partitionField : Field[T, String],
 *   loadWithKey    : Boolean = false
 * ): Execution[JobStatus] = for {
 *     conf             <- Execution.getConfig.map(GcpsConfig(_, tableName, partitionField, loadWithKey))
 *     (pipe, loadInfo) <- load[T](conf.load, conf.inputs)                                                                    // here
 *     _                <- viewHive(conf.hiveTable, pipe)
 *     _                <- Execution.from(Guard.createFlagFile(conf.inputs))                                                  // and here
 *     _                <- Execution.fromHdfs { Hdfs.write(Hdfs.path(conf.processingPathFile), conf.inputs.mkString("\n")) }  // and here too
 *   } yield JobFinished
 * }}}
 *
 * The new `findInputs` action can be run as part of this `Execution` block, by using `Execution.fromHdfs`. The same is
 * true for the call to `Guard.createFlagFile`.
 *
 * {{{
 * // new code (alternative 1)
 * def run[T <: ThriftStruct : Decode : Tag : Manifest](
 *   tableName      : String,
 *   partitionField : Field[T, String],
 *   loadWithKey    : Boolean = false
 * ): Execution[JobStatus] = for {
 *     conf             <- Execution.getConfig.map(GcpsConfig(_, tableName, partitionField, loadWithKey))
 *     inputs           <- Execution.fromHdfs(conf.findInputs)                                                                // new line
 *     (pipe, loadInfo) <- load[T](conf.load, inputs)                                                                         // edited
 *     _                <- viewHive(conf.hiveTable, pipe)
 *     _                <- Execution.fromHdfs { MaestroHdfs.createFlagFile(inputs) }                                          // edited
 *     _                <- Execution.fromHdfs { Hdfs.write(Hdfs.path(conf.processingPathFile), inputs.mkString("\n")) }       // edited
 *   } yield JobFinished
 * }}}
 *
 * Equivalently, we could sequence the last two actions inside the `Hdfs` monad, as follows:
 *
 * {{{
 * // new code (alternative 2)
 * def run[T <: ThriftStruct : Decode : Tag : Manifest](
 *   tableName      : String,
 *   partitionField : Field[T, String],
 *   loadWithKey    : Boolean = false
 * ): Execution[JobStatus] = for {
 *     conf             <- Execution.getConfig.map(GcpsConfig(_, tableName, partitionField, loadWithKey))
 *     inputs           <- Execution.fromHdfs(conf.findInputs)
 *     (pipe, loadInfo) <- load[T](conf.load, inputs)
 *     _                <- viewHive(conf.hiveTable, pipe)
 *     _                <- Execution.fromHdfs {                                                                               // edited
 *                           MaestroHdfs.createFlagFile(inputs) >>                                                            // ...
 *                           Hdfs.write(Hdfs.path(conf.processingPathFile), inputs.mkString("\n"))                            // ...
 *                         }                                                                                                  // ...
 *   } yield JobFinished
 * }}}
 *
 * For more advice on refactoring code to use [[MaestroHdfs]], speak to Todd Owen at desk W.04.106.
 */
object MaestroHdfs {
  /** Expands the globs in the provided path and only keeps those directories that pass the filter. */
  def expandPaths(path: String, filter: GuardFilter = NotProcessed): Hdfs[List[String]] = for {
    paths   <- Hdfs.glob(Hdfs.path(path))
    dirs    <- paths.filterM(Hdfs.isDirectory)
    passed  <- dirs.filterM(dir => Hdfs.withFilesystem(fs => filter.filter(fs, dir)))
  } yield passed.map(_.toString)

  /** Expand the complete file paths from the expandPaths, filtering out directories and 0 byte files */
  def listNonEmptyFiles(paths: List[String]): Hdfs[List[String]] = {
    val isNonEmpty = (filePath: Path) => Hdfs.withFilesystem(_.getFileStatus(filePath).getLen > 0)
    for {
      children <- paths.map(Hdfs.path).map(Hdfs.files(_)).sequence
      files    <- children.flatten.filterM(Hdfs.isFile)
      nonEmpty <- files.filterM(isNonEmpty)
    } yield nonEmpty.map(_.toString)
  }

  /** As `expandPath` but the filter is `NotProcessed` and `IngestionComplete`. */
  def expandTransferredPaths(path: String): Hdfs[List[String]] =
    expandPaths(path, NotProcessed &&& IngestionComplete)

  /** Creates the _PROCESSED flag to indicate completion of processing in given list of paths */
  def createFlagFile(directoryPaths: List[String]): Hdfs[Unit] = {
    directoryPaths
      .map(new Path(_, "_PROCESSED"))
      .map(Hdfs.create(_))
      .sequence_
  }
}
