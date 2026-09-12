package com.krillsson.sysapi.core.history.series

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.Optional
import java.util.UUID

data class MetricSeriesKey(
    val metric: MetricId,
    val itemId: String
)

fun MetricSeriesBucketRepository.findDistinctSeries(resolution: MetricResolution): List<MetricSeriesKey> =
    findDistinctSeriesTuples(resolution).map { (metric, itemId) ->
        MetricSeriesKey(metric as MetricId, itemId as String)
    }

@Repository
interface MetricSeriesBucketRepository : JpaRepository<MetricSeriesBucketEntity, UUID> {

    @Query("select distinct b.metric, b.itemId from MetricSeriesBucketEntity b where b.resolution = :resolution")
    fun findDistinctSeriesTuples(@Param("resolution") resolution: MetricResolution): List<Array<Any>>

    fun findByMetricAndItemIdAndResolutionAndBucketStartGreaterThanEqualAndBucketStartLessThanOrderByBucketStartAsc(
        metric: MetricId,
        itemId: String,
        resolution: MetricResolution,
        from: Instant,
        to: Instant
    ): List<MetricSeriesBucketEntity>

    fun findByMetricAndItemIdAndResolutionAndBucketStartIn(
        metric: MetricId,
        itemId: String,
        resolution: MetricResolution,
        bucketStarts: Collection<Instant>
    ): List<MetricSeriesBucketEntity>

    fun findFirstByMetricAndItemIdAndResolutionOrderByBucketStartDesc(
        metric: MetricId,
        itemId: String,
        resolution: MetricResolution
    ): Optional<MetricSeriesBucketEntity>

    fun findFirstByMetricAndItemIdAndResolutionOrderByBucketStartAsc(
        metric: MetricId,
        itemId: String,
        resolution: MetricResolution
    ): Optional<MetricSeriesBucketEntity>

    @Modifying
    @Query("delete from MetricSeriesBucketEntity b where b.resolution = :resolution and b.bucketStart < :before")
    fun deleteOlderThan(
        @Param("resolution") resolution: MetricResolution,
        @Param("before") before: Instant
    ): Int
}
