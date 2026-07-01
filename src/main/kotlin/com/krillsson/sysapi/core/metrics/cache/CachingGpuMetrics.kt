package com.krillsson.sysapi.core.metrics.cache

import com.google.common.base.Supplier
import com.google.common.base.Suppliers
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.krillsson.sysapi.config.CacheConfiguration
import com.krillsson.sysapi.core.domain.gpu.Gpu
import com.krillsson.sysapi.core.domain.gpu.GpuLoad
import com.krillsson.sysapi.core.metrics.GpuMetrics
import oshi.hardware.Display
import reactor.core.publisher.Flux

class CachingGpuMetrics(
    private val gpuMetrics: GpuMetrics,
    cacheConfiguration: CacheConfiguration
) : GpuMetrics {
    private val gpusCache: Supplier<List<Gpu>> = Suppliers.memoizeWithExpiration(
        Suppliers.synchronizedSupplier { gpuMetrics.gpus() },
        cacheConfiguration.duration, cacheConfiguration.unit
    )
    private val displaysCache: Supplier<List<Display>> = Suppliers.memoizeWithExpiration(
        Suppliers.synchronizedSupplier { gpuMetrics.displays() },
        cacheConfiguration.duration, cacheConfiguration.unit
    )
    private val gpuLoadsCache: Supplier<List<GpuLoad>> = Suppliers.memoizeWithExpiration(
        Suppliers.synchronizedSupplier { gpuMetrics.gpuLoads() },
        cacheConfiguration.duration, cacheConfiguration.unit
    )
    private val gpuLoadsQueryCache: LoadingCache<String, GpuLoad?> = CacheBuilder.newBuilder()
        .expireAfterWrite(cacheConfiguration.duration, cacheConfiguration.unit)
        .build(object : CacheLoader<String, GpuLoad?>() {
            @Throws(Exception::class)
            override fun load(key: String): GpuLoad? {
                return gpuMetrics.gpuLoadById(key)
            }
        })

    override fun gpus(): List<Gpu> {
        return gpusCache.get()
    }

    override fun displays(): List<Display> {
        return displaysCache.get()
    }

    override fun gpuLoads(): List<GpuLoad> {
        return gpuLoadsCache.get()
    }

    override fun gpuLoadById(id: String): GpuLoad? {
        return gpuLoadsQueryCache.getUnchecked(id)
    }

    override fun gpuLoadEvents(): Flux<List<GpuLoad>> {
        return gpuMetrics.gpuLoadEvents()
    }

    override fun gpuLoadEventsById(id: String): Flux<GpuLoad> {
        return gpuMetrics.gpuLoadEventsById(id)
    }
}
