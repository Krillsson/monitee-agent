package com.krillsson.sysapi.core.history.db

import jakarta.persistence.*
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.*

@Repository
class UpsMetricsHistoryDAO {
    @PersistenceContext
    lateinit var em: EntityManager

    @Transactional
    fun insert(entity: UpsMetricsHistoryEntity): UUID {
        em.persist(entity)
        return entity.id
    }

    @Transactional
    fun insertAll(entities: List<UpsMetricsHistoryEntity>): List<UUID> {
        return entities.map { insert(it) }
    }

    @Transactional(readOnly = true)
    fun findAllBetween(upsId: String, from: Instant, to: Instant): List<UpsMetricsHistoryEntity> {
        val builder = em.criteriaBuilder
        val query = builder.createQuery(UpsMetricsHistoryEntity::class.java)
        val root = query.from(UpsMetricsHistoryEntity::class.java)
        val between = builder.between(root.get<Instant>("timestamp"), from, to)
        val equal = builder.equal(root.get<String>("upsId"), upsId)
        val condition = builder.and(between, equal)
        return em.createQuery(query.where(condition)).resultList
    }

    @Transactional(readOnly = true)
    fun findLatest(upsId: String): UpsMetricsHistoryEntity? {
        val cb = em.criteriaBuilder
        val query = cb.createQuery(UpsMetricsHistoryEntity::class.java)
        val root = query.from(UpsMetricsHistoryEntity::class.java)
        val idPredicate = cb.equal(root.get<String>("upsId"), upsId)
        query.where(idPredicate)
        query.orderBy(cb.desc(root.get<Instant>("timestamp")))
        val results = em.createQuery(query).setMaxResults(1).resultList
        return results.firstOrNull()
    }

    @Transactional
    fun purge(maxAge: Instant): Int {
        val builder = em.criteriaBuilder
        val delete = builder.createCriteriaDelete(UpsMetricsHistoryEntity::class.java)
        val table = delete.from(UpsMetricsHistoryEntity::class.java)
        val lessThan = builder.lessThan(table.get("timestamp"), maxAge)
        return em.createQuery(delete.where(lessThan)).executeUpdate()
    }
}

@Entity
class UpsMetricsHistoryEntity(
    @Id
    val id: UUID,
    val upsId: String,
    val timestamp: Instant,
    @Embedded
    val metrics: UpsMetricsEntity
)

@Embeddable
data class UpsMetricsEntity(
    @Embedded
    val batteryMetrics: BatteryMetricsEntity?,
    @Embedded
    val inputMetrics: InputMetricsEntity?,
    @Embedded
    val outputMetrics: OutputMetricsEntity?,
    val loadPercent: Int?,
    val realPowerLoadWatts: Int?,
    val powerLoadVA: Int?,
    val upsStatus: String? // comma-separated enum names
)

@Embeddable
data class BatteryMetricsEntity(
    val batteryCapacity: Float?,
    val batteryChargePercent: Int?,
    val batteryRuntimeSeconds: Long?,
    val batteryVoltage: Float?,
    val batteryVoltageNominal: Float?,
    val batteryChargerStatus: String?
)

@Embeddable
data class InputMetricsEntity(
    val inputCurrent: Float?,
    val inputFrequency: Float?,
    val inputVoltage: Float?
)

@Embeddable
data class OutputMetricsEntity(
    val outputCurrent: Float?,
    val outputFrequency: Float?,
    val outputPowerFactor: Float?,
    val outputVoltage: Float?
)

