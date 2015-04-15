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

package au.com.cba.omnia.maestro

import com.twitter.scrooge.ThriftStruct

package object api {
  type MaestroJob      = au.com.cba.omnia.maestro.core.scalding.MaestroJob
  type MacroSupport    = au.com.cba.omnia.maestro.macros.MacroSupport

  type JobStatus       = au.com.cba.omnia.maestro.core.scalding.JobStatus
  type JobFinished     = au.com.cba.omnia.maestro.core.scalding.JobFinished.type
  type JobNotReady     = au.com.cba.omnia.maestro.core.scalding.JobNotReady.type
  type JobNeverReady   = au.com.cba.omnia.maestro.core.scalding.JobNeverReady.type
  type JobFailure      = au.com.cba.omnia.maestro.core.scalding.JobFailure
  val  JobFinished     = au.com.cba.omnia.maestro.core.scalding.JobFinished
  val  JobNotReady     = au.com.cba.omnia.maestro.core.scalding.JobNotReady
  val  JobNeverReady   = au.com.cba.omnia.maestro.core.scalding.JobNeverReady
  val  JobFailure      = au.com.cba.omnia.maestro.core.scalding.JobFailure
  val MaestroExecution = au.com.cba.omnia.maestro.core.scalding.MaestroExecution
  val MX               = au.com.cba.omnia.maestro.core.scalding.MaestroExecution

  type HiveTable[A <: ThriftStruct, B] = au.com.cba.omnia.maestro.core.hive.HiveTable[A, B]
  type Partition[A, B] = au.com.cba.omnia.maestro.core.partition.Partition[A, B]
  val  HiveTable       = au.com.cba.omnia.maestro.core.hive.HiveTable
  val  Partition       = au.com.cba.omnia.maestro.core.partition.Partition
  val  HivePartition   = au.com.cba.omnia.maestro.core.partition.HivePartition

  type TimeSource      = au.com.cba.omnia.maestro.core.time.TimeSource
  val  TimeSource      = au.com.cba.omnia.maestro.core.time.TimeSource

  type Clean           = au.com.cba.omnia.maestro.core.clean.Clean
  val  Clean           = au.com.cba.omnia.maestro.core.clean.Clean

  type Validator[A]    = au.com.cba.omnia.maestro.core.validate.Validator[A]
  val  Validator       = au.com.cba.omnia.maestro.core.validate.Validator
  val  Check           = au.com.cba.omnia.maestro.core.validate.Check

  type RowFilter       = au.com.cba.omnia.maestro.core.filter.RowFilter
  val  RowFilter       = au.com.cba.omnia.maestro.core.filter.RowFilter

  type Splitter        = au.com.cba.omnia.maestro.core.split.Splitter
  val  Splitter        = au.com.cba.omnia.maestro.core.split.Splitter

  type Decode[A]       = au.com.cba.omnia.maestro.core.codec.Decode[A]
  type Transform[A, B] = au.com.cba.omnia.maestro.core.transform.Transform[A, B]
  type Tag[A]          = au.com.cba.omnia.maestro.core.codec.Tag[A]
  type Field[A, B]     = au.com.cba.omnia.maestro.core.data.Field[A, B]
  val  Macros          = au.com.cba.omnia.maestro.macros.Macros

  val  MaestroHdfs     = au.com.cba.omnia.maestro.core.hdfs.MaestroHdfs
  type GuardFilter     = au.com.cba.omnia.maestro.core.hdfs.GuardFilter
  @deprecated("Use MaestroHdfs instead (same method names, but return type is Hdfs)", "2.10.0")
  val  Guard           = au.com.cba.omnia.maestro.core.hdfs.Guard

  type Header          = au.com.cba.omnia.maestro.core.upload.Header
  type Trailer         = au.com.cba.omnia.maestro.core.upload.Trailer
  type DataFile        = au.com.cba.omnia.maestro.core.upload.DataFile
  val  HeaderParsers   = au.com.cba.omnia.maestro.core.upload.HeaderParsers
  val  TrailerParsers  = au.com.cba.omnia.maestro.core.upload.TrailerParsers
  val  ControlPattern  = au.com.cba.omnia.maestro.core.upload.ControlPattern

  type MaestroConfig   = au.com.cba.omnia.maestro.core.task.MaestroConfig
  val  MaestroConfig   = au.com.cba.omnia.maestro.core.task.MaestroConfig

  type UploadConfig    = au.com.cba.omnia.maestro.core.task.UploadConfig
  type UploadInfo      = au.com.cba.omnia.maestro.core.task.UploadInfo
  val  UploadConfig    = au.com.cba.omnia.maestro.core.task.UploadConfig
  val  UploadInfo      = au.com.cba.omnia.maestro.core.task.UploadInfo

  type LoadConfig[A]   = au.com.cba.omnia.maestro.core.task.LoadConfig[A]
  type LoadInfo        = au.com.cba.omnia.maestro.core.task.LoadInfo
  type LoadSuccess     = au.com.cba.omnia.maestro.core.task.LoadSuccess
  type LoadFailure     = au.com.cba.omnia.maestro.core.task.LoadFailure
  val  LoadConfig      = au.com.cba.omnia.maestro.core.task.LoadConfig
  val  LoadSuccess     = au.com.cba.omnia.maestro.core.task.LoadSuccess
  val  LoadFailure     = au.com.cba.omnia.maestro.core.task.LoadFailure
  val  EmptyLoad       = au.com.cba.omnia.maestro.core.task.EmptyLoad

  type ViewConfig[A <: ThriftStruct, B] = au.com.cba.omnia.maestro.core.task.ViewConfig[A, B]
  val  ViewConfig      = au.com.cba.omnia.maestro.core.task.ViewConfig

  type Hdfs[A]         = au.com.cba.omnia.permafrost.hdfs.Hdfs[A]
  val  Hdfs            = au.com.cba.omnia.permafrost.hdfs.Hdfs

  type Hive[A]         = au.com.cba.omnia.ebenezer.scrooge.hive.Hive[A]
  val  Hive            = au.com.cba.omnia.ebenezer.scrooge.hive.Hive

  type ThriftStruct    = com.twitter.scrooge.ThriftStruct

  type ParlourImportDsl         = au.com.cba.omnia.parlour.SqoopSyntax.ParlourImportDsl
  type ParlourExportDsl         = au.com.cba.omnia.parlour.SqoopSyntax.ParlourExportDsl
  type TeradataParlourImportDsl = au.com.cba.omnia.parlour.SqoopSyntax.TeradataParlourImportDsl
  type TeradataParlourExportDsl = au.com.cba.omnia.parlour.SqoopSyntax.TeradataParlourExportDsl
  val  ParlourImportDsl         = au.com.cba.omnia.parlour.SqoopSyntax.ParlourImportDsl
  val  ParlourExportDsl         = au.com.cba.omnia.parlour.SqoopSyntax.ParlourExportDsl
  val  TeradataParlourImportDsl = au.com.cba.omnia.parlour.SqoopSyntax.TeradataParlourImportDsl
  val  TeradataParlourExportDsl = au.com.cba.omnia.parlour.SqoopSyntax.TeradataParlourExportDsl
  type ParlourImportOptions[+T <: ParlourImportOptions[_]] = au.com.cba.omnia.parlour.ParlourImportOptions[T]
  type ParlourExportOptions[+T <: ParlourExportOptions[_]] = au.com.cba.omnia.parlour.ParlourExportOptions[T]
  type SqoopImportConfig[T <: ParlourImportOptions[T]]     = au.com.cba.omnia.maestro.core.task.SqoopImportConfig[T]
  type SqoopExportConfig[T <: ParlourExportOptions[T]]     = au.com.cba.omnia.maestro.core.task.SqoopExportConfig[T]
  val  SqoopImportConfig                                   = au.com.cba.omnia.maestro.core.task.SqoopImportConfig
  val  SqoopExportConfig                                   = au.com.cba.omnia.maestro.core.task.SqoopExportConfig
}
