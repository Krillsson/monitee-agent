package com.krillsson.sysapi.persistence

import com.codahale.metrics.MetricRegistry
import com.krillsson.sysapi.config.SysAPIConfiguration
import com.krillsson.sysapi.core.history.HistoryStore
import com.krillsson.sysapi.core.history.asEntity
import com.krillsson.sysapi.core.history.db.HistorySystemLoadDAO
import com.krillsson.sysapi.core.history.db.HistorySystemLoadEntity
import com.krillsson.sysapi.util.logger
import io.dropwizard.flyway.FlywayBundle
import io.dropwizard.hibernate.UnitOfWork
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.FlywayException

open class PersistenceMigrator(
    private val config: SysAPIConfiguration,
    private val flywayBundle: FlywayBundle<SysAPIConfiguration>,
    private val historyDao: HistorySystemLoadDAO,
    private val store: HistoryStore,
    private val metrics: MetricRegistry
) {

    val logger by logger()

    fun migrate() {
        getFlyway()?.let { migrateSql(it) }
        migrateJsonToSql()
    }

    @UnitOfWork
    open fun migrateJsonToSql() {
        val items: List<HistorySystemLoadEntity> = store.read()
            .orEmpty()
            .map { it.asEntity() }
        val itemsInserted = historyDao.insert(items).size
        if (itemsInserted > 0) {
            logger.info("Database migration - $itemsInserted entries from JSON to SQLite")
            store.deleteOnExit()
        }
    }

    private fun migrateSql(flyway: Flyway) {
        val result = flyway.migrate()
        if (result.migrationsExecuted > 0) {
            logger.info("Database migration - migrated ${result.initialSchemaVersion} to ${result.targetSchemaVersion}")
        }
    }

    private fun getFlyway(): Flyway? {
        return try {
            flywayBundle.getFlywayFactory(config)
                .build(
                    config.database
                        .build(metrics, "Flyway")
                )
        } catch (e: FlywayException) {
            logger.error("Error while migrating", e)
            null
        }
    }
}