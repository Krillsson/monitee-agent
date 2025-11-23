package com.krillsson.sysapi.core.history.db

import jakarta.persistence.*
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Root
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.*

@Repository
class ContainerStatisticsDAO {

    @PersistenceContext
    lateinit var em: EntityManager

    @Transactional
    fun insert(entity: ContainerStatisticsEntity): UUID {
        em.persist(entity)
        return entity.id
    }

    @Transactional
    fun insertAll(entities: List<ContainerStatisticsEntity>): List<UUID> {
        return entities.map {
            val createdId = insert(it)
            createdId
        }
    }

    @Transactional(readOnly = true)
    fun findAllBetween(containerId: String, from: Instant, to: Instant): List<ContainerStatisticsEntity> {
        val builder = em.criteriaBuilder
        val query: CriteriaQuery<ContainerStatisticsEntity> = builder.createQuery(ContainerStatisticsEntity::class.java)
        val root: Root<ContainerStatisticsEntity> = query.from(ContainerStatisticsEntity::class.java)
        val between = builder
                .between(root.get("timestamp"), from, to)
        val equal = builder.equal(root.get<String>("containerId"), containerId)
        val condition = builder.and(between, equal)
        return em.createQuery(query.where(condition)).resultList
    }


    @Transactional(readOnly = true)
    fun findLatest(containerId: String): ContainerStatisticsEntity? {
        val cb = em.criteriaBuilder
        val query: CriteriaQuery<ContainerStatisticsEntity> = cb.createQuery(ContainerStatisticsEntity::class.java)
        val root: Root<ContainerStatisticsEntity> = query.from(ContainerStatisticsEntity::class.java)
        val idPredicate = cb.equal(root.get<String>("containerId"), containerId)
        query.where(idPredicate)
        query.orderBy(cb.desc(root.get<Instant>("timestamp")))
        val results = em.createQuery(query)
                .setMaxResults(1) // Limit to only the first result
                .resultList
        return results.firstOrNull()
    }

    @Transactional
    fun purge(maxAge: Instant): Int {
        val builder = em.criteriaBuilder
        val delete = builder.createCriteriaDelete(ContainerStatisticsEntity::class.java)
        val table = delete.from(ContainerStatisticsEntity::class.java)
        val lessThan = builder.lessThan(table.get("timestamp"), maxAge)
        return em
                .createQuery(delete.where(lessThan))
                .executeUpdate()
    }
}

@Entity
class ContainerStatisticsEntity(
        @Id
        val id: UUID,
        val containerId: String,
        val timestamp: Instant,
        val currentPid: Long,
        @Embedded
        val cpuUsage: CpuUsage,
        @Embedded
        val memoryUsage: MemoryUsage,
        @Embedded
        val networkUsage: NetworkUsage,
        @Embedded
        val blockIOUsage: BlockIOUsage
)

@Embeddable
data class CpuUsage(
        val usagePercentPerCore: Double,
        val usagePercentTotal: Double,
        val throttlingData: ThrottlingData
)

@Embeddable
data class ThrottlingData(
        val periods: Long,
        val throttledPeriods: Long,
        val throttledTime: Long
)

@Embeddable
data class NetworkUsage(
        val bytesReceived: Long,
        val bytesTransferred: Long,
)

@Embeddable
data class BlockIOUsage(
        val bytesWritten: Long,
        val bytesRead: Long
)

@Embeddable
data class MemoryUsage(
        val usageBytes: Long,
        val usagePercent: Double,
        val limitBytes: Long
)
