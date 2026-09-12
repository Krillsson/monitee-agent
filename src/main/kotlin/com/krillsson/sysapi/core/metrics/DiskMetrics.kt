package com.krillsson.sysapi.core.metrics

import com.krillsson.sysapi.core.domain.disk.Disk
import com.krillsson.sysapi.core.domain.disk.DiskLoad
import reactor.core.publisher.Flux

interface DiskMetrics {
    fun disks(): List<Disk>
    fun diskLoads(): List<DiskLoad>
    fun diskLoadsExcludingSmartData(): List<DiskLoad>
    fun diskLoadByName(name: String): DiskLoad?
    fun diskLoadEvents(): Flux<List<DiskLoad>>
    fun diskLoadEventsByName(name: String): Flux<DiskLoad>
    fun diskByName(name: String): Disk?
}