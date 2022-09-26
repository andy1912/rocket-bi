package datainsider.data_cook.service.metadata

import com.twitter.inject.Test
import com.twitter.util.logging.Logging
import datainsider.client.util.ZConfig
import datainsider.data_cook.domain.EtlJob.ActionConfiguration2Operator
import datainsider.data_cook.domain.persist.{MsSQLJdbcPersistConfiguration, MySQLJdbcPersistConfiguration, PersistentType}
import datainsider.data_cook.domain.response.{ThirdPartyDatabaseInfo, ThirdPartyTableInfo}
import datainsider.ingestion.domain.PageResult

/**
  * @author tvc12 - Thien Vi
  * @created 03/03/2022 - 11:33 PM
  */
class MsSQLMetaDataHandlerTest extends Test with Logging {
  val host: String = ZConfig.getString("data_cook.jdbc_test.mssql.host")
  val port: Int = ZConfig.getInt("data_cook.jdbc_test.mssql.port")
  val username: String = ZConfig.getString("data_cook.jdbc_test.mssql.username")
  val password: String = ZConfig.getString("data_cook.jdbc_test.mssql.password")
  val catalogName: String = ZConfig.getString("data_cook.jdbc_test.mssql.catalog")
  val dbName = "dbo"
  val config = MsSQLJdbcPersistConfiguration(host, port, username, password, catalogName = catalogName, databaseName ="", tableName = "", persistType = PersistentType.Append)
  val handler = ThirdPartyMetaDataHandler(config.toOperator(0))


  test("List Database") {
    val result: PageResult[ThirdPartyDatabaseInfo] = await(handler.listDatabases())
    info(s"database:: ${result}")
    assertResult(result != null)(true)
    assertResult(result.total > 0)(true)
  }
  test("List Table") {
    val result: PageResult[ThirdPartyTableInfo] = await(handler.listTables(dbName))
    info(s"table:: ${result}")
    assertResult(result != null)(true)
  }
}