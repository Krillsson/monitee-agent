package com.krillsson.sysapi.core.metrics.linux

import com.krillsson.sysapi.core.metrics.defaultimpl.DefaultDiskMetrics
import com.krillsson.sysapi.core.metrics.defaultimpl.DiskReadWriteRateMeasurementManager
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import oshi.hardware.HardwareAbstractionLayer

@Lazy
@Component
class LinuxDiskMetrics(
    hal: HardwareAbstractionLayer,
    speedMeasurementManager: DiskReadWriteRateMeasurementManager,
    diskSensors: LinuxDiskSensors
) : DefaultDiskMetrics(hal, speedMeasurementManager, diskSensors)