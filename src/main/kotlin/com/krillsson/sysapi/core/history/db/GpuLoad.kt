package com.krillsson.sysapi.core.history.db

import jakarta.persistence.Embeddable
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Entity
data class GpuLoad(
    @Id
    val id: UUID,
    @JoinColumn(name = "historyId", insertable = false, updatable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    val history: HistorySystemLoadEntity? = null,
    val historyId: UUID,
    val deviceId: String,
    val name: String,
    val coreLoad: Double,
    val vramUsedBytes: Long,
    val vramTotalBytes: Long,
    @Embedded
    val health: GpuHealth
)

@Embeddable
data class GpuHealth(
    val temperature: Double,
    val fanPercent: Double,
    val powerDraw: Double,
    val coreClockMhz: Long,
    val memoryClockMhz: Long
)

@Repository
interface GpuLoadDAO : JpaRepository<GpuLoad, UUID> {
    fun findAllByHistoryId(id: UUID): List<GpuLoad>
}
