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
data class DiskLoad(
    @Id
    val id: UUID,
    @JoinColumn(name = "historyId", insertable = false, updatable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    val history: HistorySystemLoadEntity? = null,
    val historyId: UUID,
    val name: String,
    val serial: String,
    val temperature: Double?,
    @Embedded
    val values: DiskValues,
    @Embedded
    val speed: DiskSpeed,
)

@Embeddable
data class DiskValues(
    val reads: Long,
    val readBytes: Long,
    val writes: Long,
    val writeBytes: Long
)

@Embeddable
class DiskSpeed(
    val readBytesPerSecond: Long,
    val writeBytesPerSecond: Long
)

@Repository
interface DiskLoadDAO : JpaRepository<DiskLoad, UUID>{
    fun findAllByHistoryId(id: UUID): List<DiskLoad>
}