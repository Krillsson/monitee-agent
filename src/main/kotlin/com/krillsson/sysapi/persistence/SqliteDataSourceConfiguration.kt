package com.krillsson.sysapi.persistence

import com.zaxxer.hikari.HikariDataSource
import org.hibernate.cfg.AvailableSettings
import org.hibernate.resource.jdbc.spi.PhysicalConnectionHandlingMode
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource
import org.springframework.transaction.support.TransactionSynchronizationManager
import javax.sql.DataSource

@Configuration
class SqliteDataSourceConfiguration {

    @Bean
    fun dataSource(properties: DataSourceProperties): DataSource {
        val url = properties.determineUrl()
        return LazyConnectionDataSourceProxy(
            ReadOnlyTransactionRoutingDataSource(
                writer = SqlitePools.writer(url),
                reader = SqlitePools.reader(url)
            ).apply { afterPropertiesSet() }
        )
    }

    // Holding the connection until the EntityManager closes would pin a whole web request to the pool
    // its first transaction was routed to, so a write after a read would land on a read-only connection.
    @Bean
    fun releaseConnectionsAfterTransaction() = HibernatePropertiesCustomizer { properties ->
        properties[AvailableSettings.CONNECTION_HANDLING] =
            PhysicalConnectionHandlingMode.DELAYED_ACQUISITION_AND_RELEASE_AFTER_TRANSACTION
    }
}

object SqlitePools {
    private const val DRIVER = "org.sqlite.JDBC"
    private const val BUSY_TIMEOUT_MS = "10000"
    private const val READER_POOL_SIZE = 3

    fun writer(url: String) = HikariDataSource().apply {
        poolName = "sqlite-writer"
        jdbcUrl = url
        driverClassName = DRIVER
        maximumPoolSize = 1
        addDataSourceProperty("journal_mode", "WAL")
        addDataSourceProperty("synchronous", "NORMAL")
        addDataSourceProperty("busy_timeout", BUSY_TIMEOUT_MS)
    }

    fun reader(url: String) = HikariDataSource().apply {
        poolName = "sqlite-reader"
        jdbcUrl = url
        driverClassName = DRIVER
        maximumPoolSize = READER_POOL_SIZE
        minimumIdle = 1
        addDataSourceProperty("busy_timeout", BUSY_TIMEOUT_MS)
        connectionInitSql = "PRAGMA query_only = true"
    }
}

enum class DataSourceRoute {
    READ,
    WRITE
}

class ReadOnlyTransactionRoutingDataSource(writer: DataSource, reader: DataSource) : AbstractRoutingDataSource() {

    init {
        setTargetDataSources(mapOf(DataSourceRoute.WRITE to writer, DataSourceRoute.READ to reader))
        setDefaultTargetDataSource(writer)
    }

    public override fun determineCurrentLookupKey(): DataSourceRoute = when {
        TransactionSynchronizationManager.isActualTransactionActive() &&
            TransactionSynchronizationManager.isCurrentTransactionReadOnly() -> DataSourceRoute.READ

        else -> DataSourceRoute.WRITE
    }
}
