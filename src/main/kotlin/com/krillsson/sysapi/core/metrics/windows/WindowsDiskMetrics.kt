package com.krillsson.sysapi.core.metrics.windows

import com.krillsson.sysapi.core.metrics.defaultimpl.DefaultDiskMetrics
import com.krillsson.sysapi.core.metrics.defaultimpl.DiskReadWriteRateMeasurementManager
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import oshi.hardware.HardwareAbstractionLayer

@Lazy
@Component
class WindowsDiskMetrics(
    hal: HardwareAbstractionLayer,
    speedMeasurementManager: DiskReadWriteRateMeasurementManager,
    diskSensors: WindowsDiskSensors
) : DefaultDiskMetrics(hal, speedMeasurementManager, diskSensors)