package datainsider.schema.module

import com.google.inject.Provides
import com.twitter.inject.TwitterModule
import datainsider.client.util.{HikariClient, JdbcClient, JsonParser, ZConfig}
import datainsider.schema.controller.http.requests.CreateDBRequest
import datainsider.schema.domain.{ClickhouseConnectionSetting, CsvUploadInfo}
import datainsider.schema.misc._
import datainsider.schema.repository._
import datainsider.schema.service._
import education.x.commons.SsdbKVS
import org.nutz.ssdb4j.SSDBs
import org.nutz.ssdb4j.spi.SSDB

import javax.inject.{Named, Singleton}
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import scala.io.{BufferedSource, Source}
import scala.util.Try

object MainModule extends TwitterModule {

  override def configure(): Unit = {
    super.configure()

    bind[String].annotatedWithName("admin_secret_key").toInstance(ZConfig.getString("admin_secret_key"))
    bind[String].annotatedWithName("service_key").toInstance(ZConfig.getString("service_key"))

    bind[SchemaService].to[SchemaServiceImpl].asEagerSingleton()
    bind[IngestionService].to[IngestionServiceImpl].asEagerSingleton()
    bind[OldCsvUploadService].to[OldCsvUploadServiceImpl].asEagerSingleton()
    bind[CsvIngestionService].to[CsvIngestionServiceImpl].asEagerSingleton()

    // services for upload files to data lake
    bind[FileSyncInfoService].to[SyncInfoServiceImpl].asEagerSingleton()
    bind[FileSyncHistoryService].to[SyncHistoryServiceImpl].asEagerSingleton()
  }

  @Singleton
  @Provides
  def providesSSDB(): SSDB = {
    SSDBs.pool(
      ZConfig.getString("db.ssdb.host"),
      ZConfig.getInt("db.ssdb.port"),
      ZConfig.getInt("db.ssdb.timeout_in_ms"),
      null
    )
  }

  @Singleton
  @Provides
  @Named("clickhouse")
  def providesClickHouseClient(clickhouseConnSetting: Option[ClickhouseConnectionSetting]): JdbcClient = {
    if (clickhouseConnSetting.isDefined) {
      info(s"""
              |Clickhouse datasource from file:
              |URL: ${clickhouseConnSetting.get.toJdbcUrl}
              |User: ${clickhouseConnSetting.get.username}
              |Password: ${clickhouseConnSetting.get.password}
              |Cluster: ${clickhouseConnSetting.get.clusterName}
              |""".stripMargin)

      HikariClient(
        clickhouseConnSetting.get.toJdbcUrl,
        clickhouseConnSetting.get.username,
        clickhouseConnSetting.get.password
      )
    } else {
      val driverClass: String = ZConfig.getString("db.clickhouse.driver_class")
      val jdbcUrl: String = ZConfig.getString("db.clickhouse.url")
      val user: String = ZConfig.getString("db.clickhouse.user")
      val password: String = ZConfig.getString("db.clickhouse.password")

      info(s"""
              |Clickhouse datasource:
              |Driver: $driverClass
              |URL: $jdbcUrl
              |User: $user
              |Password: $password
              |""".stripMargin)

      HikariClient(jdbcUrl, user, password)
    }
  }

  @Provides
  @Singleton
  def provideClickhouseConnectionSetting(): Option[ClickhouseConnectionSetting] = {
    Try {
      val source: BufferedSource = Source.fromFile("clickhouse_connection_settings.json")

      val settingJson: String = {
        try source.getLines().mkString
        finally source.close()
      }

      val settingsMap = JsonParser.fromJson[Map[String, String]](settingJson)

      ClickhouseConnectionSetting(
        host = settingsMap.getOrElse("host", ""),
        username = settingsMap.getOrElse("username", ""),
        password = settingsMap.getOrElse("password", ""),
        httpPort = settingsMap.getOrElse("http_port", "0").toInt,
        tcpPort = settingsMap.getOrElse("tcp_port", "0").toInt,
        clusterName = settingsMap.getOrElse("cluster_name", "")
      )
    }.toOption
  }

  @Provides
  @Singleton
  @Named("mysql-ingestion")
  def providesMySQLClient(): JdbcClient = {
    val host: String = ZConfig.getString("db.mysql.host")
    val port = ZConfig.getInt("db.mysql.port")
    val username: String = ZConfig.getString("db.mysql.username")
    val password: String = ZConfig.getString("db.mysql.password")

    HikariClient(
      s"jdbc:mysql://$host:$port?useLegacyDatetimeCode=false&serverTimezone=Asia/Ho_Chi_Minh",
      username,
      password
    )
  }

  @Singleton
  @Provides
  def providesDDLExecutor(@Named("clickhouse") client: JdbcClient): DDLExecutor = {
    val clusterName: String = ZConfig.getString("db.clickhouse.cluster_name")
    DDLExecutorImpl(client, ClickHouseDDLConverter(), clusterName)
  }

  @Singleton
  @Provides
  def providesSchemaMetadataStorage(client: SSDB): SchemaMetadataStorage = {
    val allDbName = ZConfig.getString("ssdb_key.database.all_database", "test.di.databases")
    val prefixKey = ZConfig.getString("ssdb_key.database.prefix_db_key", "test.di")
    SchemaMetadataStorageImpl(client, SsdbKVS(allDbName, client), prefixKey)
  }

  @Singleton
  @Provides
  def providesSchemaRepository(
      ddlExecutor: DDLExecutor,
      schemaRepository: SchemaMetadataStorage
  ): SchemaRepository = {
    SchemaRepositoryImpl(ddlExecutor, schemaRepository)
  }

  @Singleton
  @Provides
  def providesCreateDbValidator(repository: SchemaRepository): Validator[CreateDBRequest] = {
    CreateDBValidator(repository)
  }

  @Singleton
  @Provides
  def providesDataRepository(@Named("clickhouse") client: JdbcClient): DataRepository = {
    ClickHouseDataRepository(client)
  }

  @Singleton
  @Provides
  def provideCsvInfoRepository(client: SSDB): CsvInfoRepository = {
    implicit val ec: ExecutionContextExecutor = ExecutionContext.global
    val dbKVS = SsdbKVS[String, CsvUploadInfo](s"di.csv_upload_info", client)

    new SsdbCsvRepository(dbKVS)
  }

  @Singleton
  @Provides
  def provideMigrateDataTool(
      executor: DDLExecutor,
      schemaRepository: SchemaMetadataStorage,
      @Named("clickhouse") client: JdbcClient
  ): MigrateDataTool = {
    new MigrateDataTool(executor, schemaRepository, client)
  }

  @Singleton
  @Provides
  def provideSyncInfoRepository(
      @Named("mysql-ingestion") client: JdbcClient
  ): FileSyncInfoRepository = {
    val dbName = ZConfig.getString("db.mysql.dbname")
    val tblName = ZConfig.getString("db.mysql.sync_info_tbl")
    new MySqlSyncInfoRepository(client, dbName, tblName)
  }

  @Singleton
  @Provides
  def provideSyncHistoryRepository(
      @Named("mysql-ingestion") client: JdbcClient
  ): FileSyncHistoryRepository = {
    val dbName = ZConfig.getString("db.mysql.dbname")
    val tblName = ZConfig.getString("db.mysql.sync_history_tbl")
    new MySqlSyncHistoryRepository(client, dbName, tblName)
  }
}