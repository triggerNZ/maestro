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

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{FileSystem, Path}

import au.com.cba.omnia.permafrost.hdfs.Hdfs

case class GuardFilter(filter: (FileSystem, Path) => Boolean) {
  def &&&(that: GuardFilter): GuardFilter =
    GuardFilter((fs, p) => filter(fs, p) && that.filter(fs, p))
}

/**
 * (DEPRECATED) Utililty functions that operate on the Hadoop filesystem
 *
 * <i>Use</i> [[MaestroHdfs]] <i>instead (same method names, but return type is Hdfs).</i>
 */
object Guard {
  /** Filter out any directories that HAVE a _PROCESSED file. */
  val NotProcessed = GuardFilter((fs, p) => !fs.exists(new Path(p, "_PROCESSED")))
  /** Filter out any directories that DO NOT HAVE a _INGESTION_COMPLETE file. */
  val IngestionComplete = GuardFilter((fs, p) => fs.exists(new Path(p, "_INGESTION_COMPLETE")))
  /** Create the file system */
  val fs = FileSystem.get(new Configuration)

  /** Expands the globs in the provided path and only keeps those directories that pass the filter. */
  def expandPaths(path: String, filter: GuardFilter = NotProcessed): List[String] = {
    fs.globStatus(new Path(path))
      .toList
      .filter(s => fs.isDirectory(s.getPath))
      .map(_.getPath)
      .filter(filter.filter(fs, _))
      .map(_.toString)
  }

  /** Expand the complete file paths from the expandPaths, filtering out directories and 0 byte files */
  def listNonEmptyFiles(paths: List[String]): List[String] = {
    for {
      eachPath <- paths
      status   <- fs.listStatus(new Path(eachPath))
      if(!status.isDirectory && status.getLen>0)
    } yield status.getPath.toString
  }

  /** As `expandPath` but the filter is `NotProcessed` and `IngestionComplete`. */
  def expandTransferredPaths(path: String): List[String] =
    expandPaths(path, NotProcessed &&& IngestionComplete)

  /** Creates the _PROCESSED flag to indicate completion of processing in given list of paths */
  def createFlagFile(directoryPath : List[String]) {
    directoryPath foreach ((x)=> Hdfs.create(Hdfs.path(s"$x/_PROCESSED")).run(new Configuration))
  }
}
