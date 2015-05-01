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

package au.com.cba.omnia.maestro.example

import com.twitter.scalding.{Config, Execution}

import au.com.cba.omnia.maestro.api._, Maestro._
import au.com.cba.omnia.maestro.example.thrift.{Account, Customer}

/** Configuration for the customer to account transformation example */
case class TransformConfig(config: Config) {
  val maestro   = MaestroConfig(
    conf        = config,
    source      = "customer",
    domain      = "customer",
    tablename   = "customer"
  )

  val inputGlob = s"${maestro.hdfsRoot}/source/${maestro.domain}/*"

  val load      = maestro.load[Customer](
    none        = "null",
    timeSource  = TimeSource.fromPath(""".*([0-9]{4})-([0-9]{2})-([0-9]{2}).*""".r),
    validator   = Validator.all(
      Validator.of(Fields[Customer].SubCat, Check.oneOf("M", "F")),
      Validator.by[Customer](_.acct.length == 4, "Customer accounts should always be a length of 4")
    )
  )
  val customerView = ViewConfig(
    partition   = Partition.byDate(Fields[Customer].EffectiveDate),
    output      = s"${maestro.hdfsRoot}/view/warehouse/${maestro.domain}/customer"
  )

  val accountView = ViewConfig(
    partition   = Partition.byDate(Fields[Account].EffectiveDate),
    output      = s"${maestro.hdfsRoot}/view/warehouse/${maestro.domain}/account"
  )
}

/** Customer to account transformation eample */
object TransformExecution {
  /** Create an example execution using an automatically generated transform */
  def execute: Execution[(LoadSuccess, Long, Long)] = {
    val customerToAccount = Macros.mkTransform[Customer, Account](
      ('id,           (c: Customer) => c.acct),
      ('customer,     (c: Customer) => c.id ),
      ('balance,      (c: Customer) => c.balance / 100),
      ('balanceCents, (c: Customer) => c.balance % 100)
    )
    for {
      conf                <- Execution.getConfig.map(TransformConfig(_))
      files               <- Execution.fromHdfs(MaestroHdfs.expandPaths(conf.inputGlob))
      (customers, ldInfo) <- load[Customer](conf.load, files)
      accounts             = customers.map(customerToAccount.run)
      loadSuccess         <- ldInfo.withSuccess
      custCount           <- view(conf.customerView, customers)
      acctCount           <- view(conf.accountView, accounts)
    } yield (loadSuccess, custCount, acctCount)
  }
}
