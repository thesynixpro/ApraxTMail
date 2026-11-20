package dev.aprax.tmaii.backend.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import dev.aprax.tmaii.backend.config.AppConfig
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseFactory {
    private var dataSource: HikariDataSource? = null

    fun init(config: AppConfig): Database {
        val hikari = HikariConfig().apply {
            jdbcUrl = config.databaseUrl
            username = config.databaseUser
            password = config.databasePassword
            maximumPoolSize = config.databaseMaxPoolSize
            driverClassName = "org.postgresql.Driver"
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_READ_COMMITTED"
            connectionTimeout = 10_000
            validationTimeout = 5_000
            addDataSourceProperty("socketTimeout", "30")
            addDataSourceProperty("applicationName", "aprax-tmaii-backend")
        }
        val ds = HikariDataSource(hikari)
        dataSource = ds
        val db = Database.connect(ds)
        transaction(db) {
            SchemaUtils.createMissingTablesAndColumns(
                UsersTable,
                SessionsTable,
                MailboxesTable,
                MessageCacheTable,
                UsageLogsTable,
                AuditLogsTable,
                RateLimitBucketsTable,
            )
        }
        return db
    }

    fun close() {
        dataSource?.close()
        dataSource = null
    }
}
