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

import au.com.cba.omnia.thermometer.core.{Thermometer, ThermometerSource, ThermometerSpec}, Thermometer._
import au.com.cba.omnia.thermometer.fact.PathFactoid
import au.com.cba.omnia.thermometer.fact.PathFactoids._
import au.com.cba.omnia.thermometer.hive.HiveSupport

import au.com.cba.omnia.ebenezer.ParquetLogging
import au.com.cba.omnia.ebenezer.test.ParquetThermometerRecordReader

import au.com.cba.omnia.maestro.core.data.Field
import au.com.cba.omnia.maestro.core.hive.{HiveTable, UnpartitionedHiveTable}
import au.com.cba.omnia.maestro.core.partition.Partition

import au.com.cba.omnia.maestro.core.thrift.scrooge.StringPair

private object ViewExec extends ViewExecution

object ViewExecutionSpec extends ThermometerSpec with HiveSupport  with ParquetLogging { def is = s2"""

View execution properties
=========================

  partitioned:
    can write using execution monad                         $normal
    view executions can be composed with zip                $zipped

    can write to a hive table using execution monad         $normalHive
    can overwrite to a hive table using execution monad     $normalHiveOverwrite
    can append to a hive table using execution monad        $normalHiveAppend
    view hive executions can be composed with flatMap       $flatMappedHive
    view hive executions can be composed with zip           $zippedHive

  unpartitioned:
    can write to a hive table using execution monad         $normalHiveUnpartitioned
    can append to a hive table using execution monad        $normalHiveUnpartitionedAppend
    view hive executions can be composed with flatMap       $flatMappedHiveUnpartitioned
    view hive executions can be composed with zip           $zippedHiveUnpartitioned
"""

  def normal = {
    val exec = ViewExec.view(ViewConfig(byFirst, s"$dir/normal"), source)
    executesSuccessfully(exec) must_== 4
    facts(
      dir </> "normal" </> "A" </> "part-*.parquet" ==> matchesFile,
      dir </> "normal" </> "B" </> "part-*.parquet" ==> matchesFile
    )
  }

  def zipped = {
    val exec = ViewExec.view(ViewConfig(byFirst, s"$dir/zipped/by_first"), source)
      .zip(ViewExec.view(ViewConfig(bySecond, s"$dir/zipped/by_second"), source))
    executesSuccessfully(exec) must_== ((4, 4))
    facts(
      dir </> "zipped" </> "by_first"  </> "A" </> "part-*.parquet" ==> matchesFile,
      dir </> "zipped" </> "by_first"  </> "B" </> "part-*.parquet" ==> matchesFile,
      dir </> "zipped" </> "by_second" </> "1" </> "part-*.parquet" ==> matchesFile,
      dir </> "zipped" </> "by_second" </> "2" </> "part-*.parquet" ==> matchesFile
    )
  }

  def normalHive = {
    val exec = ViewExec.viewHive(tableByFirst("normalHive"), source)
    executesSuccessfully(exec) must_== 4
    facts(
      hiveWarehouse </> "normalhive.db" </> "by_first" </> "partition_first=A" </> "part-*.parquet" ==> matchesFile,
      hiveWarehouse </> "normalhive.db" </> "by_first" </> "partition_first=B" </> "part-*.parquet" ==> matchesFile
    )
  }

  def normalHiveOverwrite = {
    val exec = for {
      c1 <- ViewExec.viewHive(tableByFirst("normalHive"), source)
      c2 <- ViewExec.viewHive(tableByFirst("normalHive"), source2, false)
    } yield (c1, c2)

    executesSuccessfully(exec) must_== ((4, 2))
    facts(
      hiveWarehouse </> "normalhive.db" </> "by_first" </> "partition_first=A" </> "part-*.parquet" ==> noMatch,
      hiveWarehouse </> "normalhive.db" </> "by_first" </> "partition_first=B" </> "part-*.parquet" ==> recordCount(ParquetThermometerRecordReader[StringPair], 2)
    )
  }

  def normalHiveAppend = {
    val exec = for {
      c1 <- ViewExec.viewHive(tableByFirst("normalHive"), source)
      c2 <- ViewExec.viewHive(tableByFirst("normalHive"), source)
    } yield (c1, c2)

    executesSuccessfully(exec) must_== ((4, 4))
    facts(
      hiveWarehouse </> "normalhive.db" </> "by_first" </> "partition_first=A" </> "part-*.parquet" ==> recordCount(ParquetThermometerRecordReader[StringPair], 4),
      hiveWarehouse </> "normalhive.db" </> "by_first" </> "partition_first=B" </> "part-*.parquet" ==> recordCount(ParquetThermometerRecordReader[StringPair], 4)
    )
  }

  def flatMappedHive = {
    val exec = for {
      count1 <- ViewExec.viewHive(tableByFirst("flatMappedHive"), source)
      count2 <- ViewExec.viewHive(tableBySecond("flatMappedHive"), source)
    } yield (count1, count2)
    executesSuccessfully(exec) must_== ((4, 4))
    facts(
      hiveWarehouse </> "flatmappedhive.db" </> "by_first"  </> "partition_first=A"  </> "part-*.parquet" ==> matchesFile,
      hiveWarehouse </> "flatmappedhive.db" </> "by_first"  </> "partition_first=B"  </> "part-*.parquet" ==> matchesFile,
      hiveWarehouse </> "flatmappedhive.db" </> "by_second" </> "partition_second=1" </> "part-*.parquet" ==> matchesFile,
      hiveWarehouse </> "flatmappedhive.db" </> "by_second" </> "partition_second=2" </> "part-*.parquet" ==> matchesFile
    )
  }

  def zippedHive = {
    val exec = ViewExec.viewHive(tableByFirst("zippedHive"), source)
      .zip(ViewExec.viewHive(tableBySecond("zippedHive"), source))
    executesSuccessfully(exec) must_== ((4, 4))
    facts(
      hiveWarehouse </> "zippedhive.db" </> "by_first"  </> "partition_first=A"  </> "part-*.parquet" ==> matchesFile,
      hiveWarehouse </> "zippedhive.db" </> "by_first"  </> "partition_first=B"  </> "part-*.parquet" ==> matchesFile,
      hiveWarehouse </> "zippedhive.db" </> "by_second" </> "partition_second=1" </> "part-*.parquet" ==> matchesFile,
      hiveWarehouse </> "zippedhive.db" </> "by_second" </> "partition_second=2" </> "part-*.parquet" ==> matchesFile
    )
  }

  def normalHiveUnpartitioned = {
    val exec = ViewExec.viewHive(tableUnpartitioned("unpart"), source)
    executesSuccessfully(exec) must_== 4
    facts(
      hiveWarehouse </> "unpart.db" </> "unpart_table" </> "part-*.parquet" ==> matchesFile
    )
  }

  def normalHiveUnpartitionedAppend = {
    val exec = for {
      c1 <- ViewExec.viewHive(tableUnpartitioned("unpart"), source)
      c2 <- ViewExec.viewHive(tableUnpartitioned("unpart"), source)
    } yield (c1, c2)
    executesSuccessfully(exec) must_== ((4, 4))
    facts(
      hiveWarehouse </> "unpart.db" </> "unpart_table" </> "part-*.parquet" ==> recordCount(ParquetThermometerRecordReader[StringPair], 8)
    )
  }

  def flatMappedHiveUnpartitioned = {
    val exec = for {
      count1 <- ViewExec.viewHive(tableUnpartitioned("flatMappedHiveUnpart1"), source)
      count2 <- ViewExec.viewHive(tableUnpartitioned("flatMappedHiveUnpart2"), source)
    } yield (count1, count2)
    executesSuccessfully(exec) must_== ((4, 4))
    facts(
      hiveWarehouse </> "flatmappedhiveunpart1.db" </> "unpart_table" </> "part-*.parquet" ==> matchesFile,
      hiveWarehouse </> "flatmappedhiveunpart2.db" </> "unpart_table" </> "part-*.parquet" ==> matchesFile
    )
  }

  def zippedHiveUnpartitioned = {
    val zipData = List(
      StringPair("C", "3"),
      StringPair("C", "4"),
      StringPair("D", "3"),
      StringPair("D", "4")
    )
    val zipSource = ThermometerSource(zipData)

    val exec = ViewExec.viewHive(tableUnpartitioned("zippedHiveUnpart"), source)
      .zip(ViewExec.viewHive(tableUnpartitioned("zippedHiveUnpart2"), zipSource))
    executesSuccessfully(exec) must_== ((4, 4))
    facts(
      hiveWarehouse </> "zippedhiveunpart.db" </> "unpart_table" </> "part-*.parquet" ==> matchesFile,
      hiveWarehouse </> "zippedhiveunpart2.db" </> "unpart_table" </> "part-*.parquet" ==> matchesFile
    )
  }

  def tableUnpartitioned(database: String) =
    HiveTable[StringPair](database, "unpart_table", None)

  def tableByFirst(database: String) =
    HiveTable(database, "by_first", byFirst, None)

  def tableBySecond(database: String) =
    HiveTable(database, "by_second", bySecond, None)

  def byFirst  = Partition.byField(Field[StringPair, String]("first", _.first))
  def bySecond = Partition.byField(Field[StringPair, String]("second", _.second))

  def source = ThermometerSource(data)
  def data = List(
    StringPair("A", "1"),
    StringPair("A", "2"),
    StringPair("B", "1"),
    StringPair("B", "2")
  )

  def source2 = ThermometerSource(data2)
  def data2 = List(
    StringPair("B", "11"),
    StringPair("B", "22")
  )

  def matchesFile = PathFactoid((context, path) => !context.glob(path).isEmpty)
  def noMatch = PathFactoid((context, path) => context.glob(path).isEmpty)
}
