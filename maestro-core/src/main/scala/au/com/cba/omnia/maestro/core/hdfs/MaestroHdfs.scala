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
 * Utility functions for Hdfs monad (the au.com.cba.omnia.permafrost.hdfs.Hdfs), with a maestro flavour.
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
