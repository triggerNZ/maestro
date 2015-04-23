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
package partition

import org.joda.time.format.DateTimeFormat

import au.com.cba.omnia.maestro.core.data._

/**
  * Partitioning schema to create partition `B`, usually a string or tuple of strings
  * from `A`.
  *
  * It has the names of the partition columns, a way to get the partition values from an instance
  * of `A` and a pattern to create from the values, e.g `"id=%s"`.
  */
case class Partition[A, B](fieldNames: List[String], extract: A => B, pattern: String)

/** Various partitioning schemes. */
object Partition {
  /** Partitions by the specified field. */
  def byField[A, B](a: Field[A, B]): Partition[A, B] =
    Partition(toPartitionName(List(a.name)), a.get, "%s")

  /** Partitions by the two specified fields. */
  def byFields2[A, B, C](a: Field[A, B], b: Field[A, C]): Partition[A, (B, C)] =
    Partition(toPartitionName(List(a.name, b.name)), v => (a.get(v), b.get(v)), "%s/%s")

  /** Partitions by the three specified fields. */
  def byFields3[A, B, C, D](a: Field[A, B], b: Field[A, C], c: Field[A, D]): Partition[A, (B, C, D)] =
    Partition(
      toPartitionName(List(a.name, b.name, c.name)),
      v => (a.get(v), b.get(v), c.get(v)),
      "%s/%s/%s"
    )

  /** Partition by year, month, day for a given dateFormat. */
  def byDate[A](date: Field[A, String], dateFormat: String = "y-M-d"): Partition[A, (String, String, String)] =
    Partition(List("year", "month", "day"), v => {
      val dt = DateTimeFormat.forPattern(dateFormat).parseDateTime(date.get(v))
      (dt.getYear.toString, f"${dt.getMonthOfYear}%02d", f"${dt.getDayOfMonth}%02d")
    }, "%s/%s/%s")

  /** Partition by year, month, day, hour for a given dateFormat. */
  def byHour[A](date: Field[A, String], dateFormat: String = "y-M-d-H"): Partition[A, (String, String, String, String)] =
    Partition(List("year", "month", "day", "hour"), v => {
      val dt = DateTimeFormat.forPattern(dateFormat).parseDateTime(date.get(v))
      (dt.getYear.toString, f"${dt.getMonthOfYear}%02d", f"${dt.getDayOfMonth}%02d", f"${dt.getHourOfDay}%02d")
    }, "%s/%s/%s/%s")

  /** Partition by static column, year, month, day for a given dateFormat. */
  def byFieldPlusDate[A](staticField: (String, String), date: Field[A, String], dateFormat: String = "y-M-d"): Partition[A, (String, String, String, String)] =
    Partition(List(staticField._1, "year", "month", "day"), v => {
      val dt = DateTimeFormat.forPattern(dateFormat).parseDateTime(date.get(v))
      (staticField._2, dt.getYear.toString, f"${dt.getMonthOfYear}%02d", f"${dt.getDayOfMonth}%02d")
    }, "%s/%s/%s/%s")

  /**
    * Prepends `partition_` to column names to avoid clashes between core columns and members of the
    * thrift struct.
    */
  def toPartitionName(columns: List[String]): List[String] = columns.map("partition_" + _)
}

/**
  * Various partitioning schemes using a hive style output path such as
  * `field_name=value`.
  */
object HivePartition {
  /** Hive style partition by the specified field. */
  def byField[A, B](a: Field[A, B]): Partition[A, B] =
    Partition(Partition.toPartitionName(List(a.name)), a.get, s"p${a.name}=%s")

  /** Hive style partition by the two specified fields. */
  def byFields2[A, B, C](a: Field[A, B], b: Field[A, C]): Partition[A, (B, C)] =
    Partition(
      Partition.toPartitionName(List(a.name, b.name)),
      v => (a.get(v), b.get(v)),
      s"p${a.name}=%s/p${b.name}=%s"
    )

  /** Hive style partition by the three specified fields. */
  def byFields3[A, B, C, D](a: Field[A, B], b: Field[A, C], c: Field[A, D]): Partition[A, (B, C, D)] =
    Partition(
      Partition.toPartitionName(List(a.name, b.name, c.name)),
      v => (a.get(v), b.get(v), c.get(v)),
      s"p${a.name}=%s/p${b.name}=%s/p${c.name}=%s"
    )

  /** Hive style partition by year for a given dateFormat. */
  def byYear[A](date: Field[A, String], dateFormat: String): Partition[A, String] =
    Partition(List("year"), v => {
      val dt = DateTimeFormat.forPattern(dateFormat).parseDateTime(date.get(v))
      dt.getYear.toString
    }, "year=%s")

  /** Hive style partition by year, month for a given dateFormat. */
  def byMonth[A](date: Field[A, String], dateFormat: String): Partition[A, (String, String)] =
    Partition(List("year", "month"), v => {
      val dt = DateTimeFormat.forPattern(dateFormat).parseDateTime(date.get(v))
      (dt.getYear.toString, f"${dt.getMonthOfYear}%02d")
    }, "year=%s/month=%s")

  /** Hive style partition by year, month, day for a given dateFormat. */
  def byDay[A](date: Field[A, String], dateFormat: String): Partition[A, (String, String, String)] =
    Partition(List("year", "month", "day"), v => {
      val dt = DateTimeFormat.forPattern(dateFormat).parseDateTime(date.get(v))
      (dt.getYear.toString, f"${dt.getMonthOfYear}%02d", f"${dt.getDayOfMonth}%02d")
    }, "year=%s/month=%s/day=%s")

  /** Hive style partition by year, month, day, hour for a given dateFormat. */
  def byHour[A](date: Field[A, String], dateFormat: String): Partition[A, (String, String, String, String)] =
    Partition(List("year", "month", "day", "hour"), v => {
      val dt = DateTimeFormat.forPattern(dateFormat).parseDateTime(date.get(v))
      (dt.getYear.toString, f"${dt.getMonthOfYear}%02d", f"${dt.getDayOfMonth}%02d", f"${dt.getHourOfDay}%02d")
    }, "year=%s/month=%s/day=%s/hour=%s")
}