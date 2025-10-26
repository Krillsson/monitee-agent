package com.krillsson.sysapi.core.history.db

import jakarta.persistence.EntityManager
import jakarta.persistence.Id
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Repository
class UpsLoadDAO {
    @PersistenceContext
    lateinit var em: EntityManager
    @Transactional
    fun insert(entity: UpsLoadEntity): UUID {
        em.persist(entity)
        return entity.id
    }

    @Transactional
    fun insertAll(entities: List<UpsLoadEntity>): List<UUID> {
        return entities.map {
            val createdId = insert(it)
            createdId
        }
    }
}

data class UpsLoadEntity(
    @Id
    val id: UUID,
    val timestamp: Instant,
    val name: String,
    val batteryCharge: Int?,
    val batteryRuntime: Int?,
    val inputVoltage: Float?,
    val outputVoltage: Float?,
    val upsStatus: String?
)