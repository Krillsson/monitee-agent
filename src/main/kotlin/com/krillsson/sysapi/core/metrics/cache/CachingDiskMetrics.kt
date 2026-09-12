package com.krillsson.sysapi.core.metrics.cache

import com.google.common.base.Supplier
import com.google.common.base.Suppliers
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.krillsson.sysapi.config.CacheConfiguration
import com.krillsson.sysapi.core.domain.disk.Disk
import com.krillsson.sysapi.core.domain.disk.DiskLoad
import com.krillsson.sysapi.core.metrics.DiskMetrics
import reactor.core.publisher.Flux

class CachingDiskMetrics(private val diskMetrics: DiskMetrics, cacheConfiguration: CacheConfiguration) :
    DiskMetrics {
    private val disksCache: Supplier<List<Disk>> = Suppliers.memoizeWithExpiration(
        { diskMetrics.disks() },
        cacheConfiguration.duration, cacheConfiguration.unit
    )
    private val diskLoadsCache: Supplier<List<DiskLoad>> = Suppliers.memoizeWithExpiration(
        { diskMetrics.diskLoads() },
        cacheConfiguration.duration, cacheConfiguration.unit
    )
    private val diskLoadsExcludingSmartDataCache: Supplier<List<DiskLoad>> = Suppliers.memoizeWithExpiration(
        { diskMetrics.diskLoadsExcludingSmartData() },
        cacheConfiguration.duration, cacheConfiguration.unit
    )
    private val diskQueryCache: LoadingCache<String, Disk?> = CacheBuilder.newBuilder()
        .expireAfterWrite(cacheConfiguration.duration, cacheConfiguration.unit)
        .build(object : CacheLoader<String, Disk>() {
            @Throws(Exception::class)
            override fun load(s: String): Disk? {
                return diskMetrics.diskByName(s)
            }
        })
    private val diskLoadsQueryCache: LoadingCache<String, DiskLoad?> =
        CacheBuilder.newBuilder()
            .expireAfterWrite(cacheConfiguration.duration, cacheConfiguration.unit)
            .build(object : CacheLoader<String, DiskLoad?>() {
                @Throws(Exception::class)
                override fun load(s: String): DiskLoad? {
                    return diskMetrics.diskLoadByName(s)
                }
            })

    override fun disks(): List<Disk> {
        return disksCache.get()
    }

    override fun diskLoads(): List<DiskLoad> {
        return diskLoadsCache.get()
    }

    override fun diskLoadsExcludingSmartData(): List<DiskLoad> {
        return diskLoadsExcludingSmartDataCache.get()
    }

    override fun diskByName(name: String): Disk? {
        return diskQueryCache.getUnchecked(name)
    }

    override fun diskLoadByName(name: String): DiskLoad? {
        return diskLoadsQueryCache.getUnchecked(name)
    }

    override fun diskLoadEvents(): Flux<List<DiskLoad>> {
        return diskMetrics.diskLoadEvents()
    }

    override fun diskLoadEventsByName(name: String): Flux<DiskLoad> {
        return diskMetrics.diskLoadEventsByName(name)
    }
}