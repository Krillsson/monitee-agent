package com.krillsson.sysapi.core.check

import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.util.logger
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.*

@Component
class CheckHistoryAggregator(
    private val resultRepository: CheckResultRepository,
    private val bucketRepository: CheckResultBucketRepository,
    yamlConfigFile: YAMLConfigFile
) {
    private val logger by logger()
    private val retention = yamlConfigFile.metricsConfig.history.checks

    @EventListener(ApplicationReadyEvent::class)
    @Transactional
    fun rollUpExistingHistory() {
        aggregate()
    }

    @Scheduled(cron = "0 5 * * * *")
    @Transactional
    fun rollUpClosedPeriods() {
        aggregate()
    }

    private fun aggregate() {
        val now = Instant.now()
        val hourlyBuckets = rollUpRawResults(now)
        val dailyBuckets = rollUpHourlyBuckets(now)
        val purged = purge(now)
        if (hourlyBuckets > 0 || dailyBuckets > 0 || purged > 0) {
            logger.info(
                "Condensed check history into {} hourly and {} daily buckets, purged {} rows",
                hourlyBuckets,
                dailyBuckets,
                purged
            )
        }
    }

    private fun rollUpRawResults(now: Instant): Int {
        val cutoff = CheckBuckets.startOfHour(now)
        var written = 0
        resultRepository.findDistinctCheckIds().forEach { checkId ->
            val earliest = resultRepository.findFirstByCheckIdOrderByTimestampAsc(checkId).orElse(null)
                ?: return@forEach
            val from = bucketRepository
                .findFirstByCheckIdAndResolutionOrderByBucketStartDesc(checkId, BucketResolution.HOURLY)
                .map { CheckBuckets.endOf(it.bucketStart, BucketResolution.HOURLY) }
                .orElse(CheckBuckets.startOfHour(earliest.timestamp))
            val results = resultRepository
                .findByCheckIdAndTimestampGreaterThanEqualAndTimestampLessThanOrderByTimestampAsc(
                    checkId,
                    from,
                    cutoff
                )
            val preceding = resultRepository
                .findFirstByCheckIdAndTimestampLessThanOrderByTimestampDesc(checkId, from)
                .orElse(null)
            written += upsertAll(checkId, BucketResolution.HOURLY, summarizeByHour(results, preceding, now))
        }
        return written
    }

    private fun summarizeByHour(
        results: List<CheckResultEntity>,
        precedingResult: CheckResultEntity?,
        now: Instant
    ): Map<Instant, BucketSummary> {
        var preceding = precedingResult
        return results.groupBy { CheckBuckets.startOfHour(it.timestamp) }
            .toSortedMap()
            .mapValues { (bucketStart, rows) ->
                val adjacent = preceding?.let {
                    CheckBuckets.endOf(
                        CheckBuckets.startOfHour(it.timestamp),
                        BucketResolution.HOURLY
                    ) == bucketStart
                } == true
                CheckBuckets.summarize(
                    rows,
                    bucketStart,
                    CheckBuckets.endOf(bucketStart, BucketResolution.HOURLY),
                    now,
                    preceding.takeIf { adjacent }
                ).also { preceding = rows.last() }
            }
    }

    private fun rollUpHourlyBuckets(now: Instant): Int {
        val cutoff = CheckBuckets.startOfDay(now)
        var written = 0
        bucketRepository.findDistinctCheckIds(BucketResolution.HOURLY).forEach { checkId ->
            val from = bucketRepository
                .findFirstByCheckIdAndResolutionOrderByBucketStartDesc(checkId, BucketResolution.DAILY)
                .map { CheckBuckets.endOf(it.bucketStart, BucketResolution.DAILY) }
                .orElse(Instant.EPOCH)
            val hourly = bucketRepository
                .findByCheckIdAndResolutionAndBucketStartGreaterThanEqualAndBucketStartLessThanOrderByBucketStartAsc(
                    checkId,
                    BucketResolution.HOURLY,
                    from,
                    cutoff
                )
            written += upsertAll(
                checkId,
                BucketResolution.DAILY,
                hourly.groupBy { CheckBuckets.startOfDay(it.bucketStart) }
                    .mapValues { (_, buckets) -> CheckBuckets.merge(buckets) }
            )
        }
        return written
    }

    private fun upsertAll(
        checkId: UUID,
        resolution: BucketResolution,
        summaries: Map<Instant, BucketSummary>
    ): Int {
        if (summaries.isEmpty()) {
            return 0
        }
        val existing = bucketRepository
            .findByCheckIdAndResolutionAndBucketStartIn(checkId, resolution, summaries.keys)
            .associateBy { it.bucketStart }
        val entities = summaries.map { (bucketStart, summary) ->
            CheckResultBucketEntity(
                id = existing[bucketStart]?.id ?: UUID.randomUUID(),
                checkId = checkId,
                resolution = resolution,
                bucketStart = bucketStart,
                samples = summary.samples,
                successful = summary.successful,
                failed = summary.failed,
                downtimeSeconds = summary.downtimeSeconds,
                minLatencyMs = summary.minLatencyMs,
                avgLatencyMs = summary.avgLatencyMs,
                maxLatencyMs = summary.maxLatencyMs,
                p95LatencyMs = summary.p95LatencyMs,
                lastMessage = summary.lastMessage
            )
        }
        bucketRepository.saveAll(entities)
        return entities.size
    }

    private fun purge(now: Instant): Int {
        val raw = resultRepository.deleteOlderThan(now.minus(retention.raw.olderThan, retention.raw.unit))
        val hourly = bucketRepository.deleteOlderThan(
            BucketResolution.HOURLY,
            CheckBuckets.startOfHour(now.minus(retention.hourly.olderThan, retention.hourly.unit))
        )
        val daily = bucketRepository.deleteOlderThan(
            BucketResolution.DAILY,
            CheckBuckets.startOfDay(now.minus(retention.daily.olderThan, retention.daily.unit))
        )
        return raw + hourly + daily
    }
}
