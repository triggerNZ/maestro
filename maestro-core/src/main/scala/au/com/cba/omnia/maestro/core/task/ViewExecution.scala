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

import com.twitter.scalding.{Execution, TupleSetter, TypedPipe}

import com.twitter.scrooge.ThriftStruct

import au.com.cba.omnia.ebenezer.scrooge.PartitionParquetScroogeSource
import au.com.cba.omnia.ebenezer.scrooge.hive.Hive

import au.com.cba.omnia.maestro.core.hive.HiveTable
import au.com.cba.omnia.maestro.core.partition.Partition
import au.com.cba.omnia.maestro.core.scalding.StatKeys
import au.com.cba.omnia.maestro.core.scalding.ExecutionOps._

/**
  * Configuration options for write pipes to HDFS files.
  *
  * @param partition: The partition used to split pipe data.
  * @param output:    The HDFS file in which to store data.
  */
case class ViewConfig[A <: ThriftStruct, B](
  partition: Partition[A, B],
  output: String
)

/** Executions for view tasks */
trait ViewExecution {
  /**
    * Partitions a pipe using the given partition scheme and writes out the data.
    *
    * @return the number of rows written.
    */
  def view[A <: ThriftStruct : Manifest, B : Manifest : TupleSetter](
    config: ViewConfig[A, B], pipe: TypedPipe[A]
  ): Execution[Long] =
    pipe
      .map(v => config.partition.extract(v) -> v)
      .writeExecution(PartitionParquetScroogeSource[B, A](config.partition.pattern, config.output))
      .getAndResetCounters
      .map { case (_, counters) => counters.get(StatKeys.tuplesWritten).getOrElse(0) }

  /**
    * Writes out the data to a hive table.
    *
    * This will create the table if it doesn't already exist. If the existing schema doesn't match
    * the schema expected the job will fail.
    *
    * @return the number of rows written.
    */
  def viewHive[A <: ThriftStruct : Manifest, ST](
    table: HiveTable[A, ST], pipe: TypedPipe[A], append: Boolean = true
  ): Execution[Long] =
    if (append) {
      println("APPEND")
      for {
        /* Creates the database upfront since when Hive is run concurrently uzing `zip` all but the
         * first attempt fails.
         * The Hive monad handles this so that the job doesn't fall over.
         */
        _ <- Execution.fromHive(Hive.createDatabase(table.database))
        n <- table.writeExecution(pipe, append).map(_.get(StatKeys.tuplesWritten).getOrElse(0L))
      } yield n
    } else {
      println("NOT APPEND")
      for {
        /* Creates the database upfront since when Hive is run concurrently uzing `zip` all but the
         * first attempt fails.
         * The Hive monad handles this so that the job doesn't fall over.
         */
        _ <- Execution.fromHive(Hive.createDatabase(table.database))
        n <- table.writeExecution(pipe, append).map(_.get(StatKeys.tuplesWritten).getOrElse(0L))
      } yield n
    }
}
