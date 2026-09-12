package com.krillsson.sysapi.core.history.series

import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.util.logger
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Component
class MetricHistoryAggregator(
    private val repository: MetricSeriesBucketRepository,
    yamlConfigFile: YAMLConfigFile
) {
    private val logger by logger()
    private val retention = yamlConfigFile.metricsConfig.history.series

    @EventListener(ApplicationReadyEvent::class)
    @Transactional
    fun rollUpExistingHistory() {
        aggregate()
    }

    @Scheduled(cron = "0 10 * * * *")
    @Transactional
    fun rollUpClosedPeriods() {
        aggregate()
    }

    private fun aggregate() {
        val now = Instant.now()
        val written = MERGE_TARGETS.sumOf { rollUp(it, now) }
        val purged = purge(now)
        if (written > 0 || purged > 0) {
            logger.info("Condensed metric history into {} buckets, purged {} rows", written, purged)
        }
    }

    private fun rollUp(target: MetricResolution, now: Instant): Int {
        val source = requireNotNull(MetricSeriesBuckets.below(target))
        val cutoff = MetricSeriesBuckets.startOf(now, target)
        return repository.findDistinctSeries(source).sumOf { series ->
            rollUpSeries(series, source, target, cutoff)
        }
    }

    private fun rollUpSeries(
        series: MetricSeriesKey,
        source: MetricResolution,
        target: MetricResolution,
        cutoff: Instant
    ): Int {
        val from = repository
            .findFirstByMetricAndItemIdAndResolutionOrderByBucketStartDesc(series.metric, series.itemId, target)
            .map { MetricSeriesBuckets.endOf(it.bucketStart, target) }
            .orElseGet {
                repository
                    .findFirstByMetricAndItemIdAndResolutionOrderByBucketStartAsc(
                        series.metric,
                        series.itemId,
                        source
                    )
                    .map { MetricSeriesBuckets.startOf(it.bucketStart, target) }
                    .orElse(null)
            } ?: return 0
        if (!from.isBefore(cutoff)) {
            return 0
        }
        val sourceBuckets = repository
            .findByMetricAndItemIdAndResolutionAndBucketStartGreaterThanEqualAndBucketStartLessThanOrderByBucketStartAsc(
                series.metric,
                series.itemId,
                source,
                from,
                cutoff
            )
        if (sourceBuckets.isEmpty()) {
            return 0
        }
        return upsertAll(
            series,
            target,
            sourceBuckets
                .groupBy { MetricSeriesBuckets.startOf(it.bucketStart, target) }
                .mapValues { (_, buckets) -> MetricSeriesBuckets.merge(buckets) }
        )
    }

    private fun upsertAll(
        series: MetricSeriesKey,
        resolution: MetricResolution,
        summaries: Map<Instant, MetricSeriesSummary>
    ): Int {
        if (summaries.isEmpty()) {
            return 0
        }
        val existing = repository
            .findByMetricAndItemIdAndResolutionAndBucketStartIn(
                series.metric,
                series.itemId,
                resolution,
                summaries.keys
            )
            .associateBy { it.bucketStart }
        val entities = summaries.map { (bucketStart, summary) ->
            MetricSeriesBucketEntity(
                id = existing[bucketStart]?.id ?: UUID.randomUUID(),
                metric = series.metric,
                itemId = series.itemId,
                resolution = resolution,
                bucketStart = bucketStart,
                samples = summary.samples,
                minValue = summary.minValue,
                avgValue = summary.avgValue,
                maxValue = summary.maxValue,
                lastValue = summary.lastValue
            )
        }
        repository.saveAll(entities)
        return entities.size
    }

    private fun purge(now: Instant): Int {
        val raw = repository.deleteOlderThan(
            MetricResolution.RAW,
            now.minus(retention.raw.olderThan, retention.raw.unit)
        )
        val fiveMinute = repository.deleteOlderThan(
            MetricResolution.FIVE_MINUTE,
            MetricSeriesBuckets.startOf(
                now.minus(retention.fiveMinute.olderThan, retention.fiveMinute.unit),
                MetricResolution.FIVE_MINUTE
            )
        )
        val hourly = repository.deleteOlderThan(
            MetricResolution.HOURLY,
            MetricSeriesBuckets.startOf(
                now.minus(retention.hourly.olderThan, retention.hourly.unit),
                MetricResolution.HOURLY
            )
        )
        val daily = repository.deleteOlderThan(
            MetricResolution.DAILY,
            MetricSeriesBuckets.startOf(
                now.minus(retention.daily.olderThan, retention.daily.unit),
                MetricResolution.DAILY
            )
        )
        return raw + fiveMinute + hourly + daily
    }

    companion object {
        private val MERGE_TARGETS = listOf(
            MetricResolution.FIVE_MINUTE,
            MetricResolution.HOURLY,
            MetricResolution.DAILY
        )
    }
}
