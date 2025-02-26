package com.krillsson.sysapi.core.metrics.defaultimpl

import com.krillsson.sysapi.core.domain.disk.*
import com.krillsson.sysapi.core.metrics.DiskMetrics
import com.krillsson.sysapi.core.speed.SpeedMeasurementManager
import org.apache.commons.lang3.StringUtils
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import oshi.hardware.HWDiskStore
import oshi.hardware.HWPartition
import oshi.hardware.HardwareAbstractionLayer
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.util.*
import java.util.concurrent.TimeUnit

@Component
open class DefaultDiskMetrics(
    private val hal: HardwareAbstractionLayer,
    private val speedMeasurementManager: DiskReadWriteRateMeasurementManager,
    @Qualifier("defaultDiskSensors") private val diskSensors: DefaultDiskSensors
) : DiskMetrics {

    private val diskMetric = Sinks.many()
        .replay()
        .latest<List<DiskLoad>>()

    fun register() {
        speedMeasurementManager.registerStores()
    }

    @Scheduled(fixedRate = 15, timeUnit = TimeUnit.SECONDS)
    fun runMeasurement() {
        speedMeasurementManager.run()
        diskMetric.tryEmitNext(diskLoads())
    }

    override fun disks(): List<Disk> {
        return hal.diskStores.map { store ->
            store.asDisk()
        }
    }

    override fun diskLoads(): List<DiskLoad> {
        return hal.diskStores.map { d: HWDiskStore -> createDiskLoad(d) }
    }

    override fun diskLoadByName(name: String): DiskLoad? {
        return hal.diskStores
            .filter { n: HWDiskStore -> n.name.equals(name, ignoreCase = true) }
            .map { d: HWDiskStore -> createDiskLoad(d) }
            .firstOrNull()
    }

    override fun diskLoadEvents(): Flux<List<DiskLoad>> {
        return diskMetric.asFlux()
    }

    override fun diskLoadEventsByName(name: String): Flux<DiskLoad> {
        return diskMetric.asFlux()
            .mapNotNull { list: List<DiskLoad> ->
                list.firstOrNull { n -> n.name.equals(name, ignoreCase = true) }
            }
    }

    private fun diskMetrics(
        disk: HWDiskStore,
    ): DiskValues {
        return DiskValues(
            disk.reads,
            disk.readBytes,
            disk.writes,
            disk.writeBytes
        )
    }

    override fun diskByName(name: String): Disk? {
        return hal.diskStores
            .filter { n: HWDiskStore -> n.name.equals(name, ignoreCase = true) }
            .map { store: HWDiskStore ->
                store.asDisk()
            }.firstOrNull()
    }

    protected open fun diskSpeedForStore(
        diskStore: HWDiskStore,
    ): Optional<DiskSpeed?> {
        val currentSpeedForName = speedMeasurementManager.getCurrentSpeedForName(
            diskStore.name
        )
        return currentSpeedForName.map { s: SpeedMeasurementManager.CurrentSpeed ->
            DiskSpeed(
                s.readPerSeconds,
                s.writePerSeconds
            )
        }
    }

    private fun createDiskLoad(diskStore: HWDiskStore): DiskLoad {
        val metrics = diskMetrics(diskStore)
        val speed: DiskSpeed = requireNotNull(diskSpeedForStore(diskStore).orElse(DiskSpeed(-1, -1)))
        val temperature = diskSensors.getDiskTemperature(diskStore)
        return DiskLoad(diskStore.name, getSerial(diskStore), temperature, metrics, speed)
    }

    private fun getSerial(d: HWDiskStore): String {
        return if (StringUtils.isEmpty(d.serial)) "n/a" else d.serial
    }

    private fun HWDiskStore.asDisk() = Disk(
        model,
        name,
        serial.takeIf { !it.isNullOrBlank() } ?: "N/A",
        size,
        partitions.asPartitions()
    )

    private fun List<HWPartition>.asPartitions() = map { it.asPartition() }

    private fun HWPartition.asPartition() = Partition(
        identification,
        name,
        type,
        uuid,
        size,
        major,
        minor,
        mountPoint
    )
}