package com.krillsson.sysapi.core.history.db

import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID
import jakarta.persistence.Embeddable
import jakarta.persistence.Enumerated
import jakarta.persistence.EnumType
import jakarta.persistence.Convert
import com.krillsson.sysapi.core.history.db.converters.StringListJsonConverter

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
    @Embedded
    val smart: SmartDataEmbedded? = null,
    @Embedded
    val health: DeviceHealth? = null
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



@Embeddable
data class SmartDataEmbedded(
    @Enumerated(EnumType.STRING)
    val deviceType: SmartType? = null,

    // Common
    val temperatureCelsius: Int? = null,
    val powerOnHours: Long? = null,
    val powerCycleCount: Long? = null,

    // HDD
    val hddReallocatedSectors: Long? = null,
    val hddPendingSectors: Long? = null,
    val hddUncorrectableSectors: Long? = null,
    val hddOfflineUncorrectable: Long? = null,
    val hddSpinRetryCount: Long? = null,
    val hddSeekErrorRate: Long? = null,
    val hddUdmaCrcErrors: Long? = null,

    // SATA SSD
    val ssdPercentageUsed: Int? = null,
    val ssdWearLevelingCount: Int? = null,
    val ssdAvailableReservedSpace: Int? = null,
    val ssdTotalWriteGiB: Long? = null,
    val ssdTotalReadGiB: Long? = null,
    val ssdMediaErrors: Long? = null,
    val ssdUncorrectableErrors: Long? = null,
    val ssdUdmaCrcErrors: Long? = null,

    // NVMe
    val nvmePercentageUsed: Int? = null,
    val nvmeDataUnitsRead: Long? = null,
    val nvmeDataUnitsWritten: Long? = null,
    val nvmeMediaErrors: Long? = null,
    val nvmeNumErrLogEntries: Long? = null,
    val nvmeUnsafeShutdowns: Long? = null,
    val nvmeControllerBusyTimeMinutes: Long? = null
) {
    enum class SmartType { HDD, SATA_SSD, NVME }
}

@Embeddable
data class DeviceHealth(
    val healthStatus: String? = null,
    @Convert(converter = StringListJsonConverter::class)
    val healthMessages: List<String>? = null
)


@Repository
interface DiskLoadDAO : JpaRepository<DiskLoad, UUID>{
    fun findAllByHistoryId(id: UUID): List<DiskLoad>
}