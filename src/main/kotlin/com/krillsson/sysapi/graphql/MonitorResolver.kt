package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.core.domain.event.Event
import com.krillsson.sysapi.core.domain.event.OngoingEvent
import com.krillsson.sysapi.core.domain.event.PastEvent
import com.krillsson.sysapi.core.monitoring.toConditionalValue
import com.krillsson.sysapi.core.monitoring.toFractionalValue
import com.krillsson.sysapi.core.monitoring.toNumericalValue
import com.krillsson.sysapi.core.history.HistoryRepository
import com.krillsson.sysapi.core.history.series.HistoryResolution
import com.krillsson.sysapi.core.history.series.MetricHistory
import com.krillsson.sysapi.core.history.series.MetricHistoryService
import com.krillsson.sysapi.core.history.series.MonitorMetrics
import com.krillsson.sysapi.core.history.series.asHistoryResolution
import com.krillsson.sysapi.core.history.db.BasicHistorySystemLoadEntity
import com.krillsson.sysapi.core.metrics.Metrics
import com.krillsson.sysapi.core.monitoring.MonitorManager
import com.krillsson.sysapi.core.monitoring.MonitorableItem
import com.krillsson.sysapi.core.monitoring.event.EventManager
import com.krillsson.sysapi.core.monitoring.monitors.*
import com.krillsson.sysapi.core.check.CheckHistoryService
import com.krillsson.sysapi.docker.ContainersHistoryRepository
import com.krillsson.sysapi.graphql.domain.*
import com.krillsson.sysapi.ups.UpsMetricsHistoryRepository
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.BatchMapping
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller
import java.time.Instant
import java.time.OffsetDateTime
import java.util.*

@Controller
@SchemaMapping(typeName = "Monitor")
class MonitorResolver(
    val historyRepository: HistoryRepository,
    val containersHistoryRepository: ContainersHistoryRepository,
    val eventManager: EventManager,
    val monitorManager: MonitorManager,
    val upsMetricsHistoryRepository: UpsMetricsHistoryRepository,
    val checkHistoryService: CheckHistoryService,
    val metricHistoryService: MetricHistoryService,
    val metrics: Metrics,
    val monitorInputCreator: com.krillsson.sysapi.core.monitoring.MonitorInputCreator
) {

    @SchemaMapping
    fun history(monitor: Monitor): List<MonitoredValueHistoryEntry> {
        val to = Instant.now()
        return historyEntries(monitor, metricHistoryService.rawWindowStart(to), to)
    }

    @SchemaMapping
    fun historyBetweenTimestamps(
        monitor: Monitor,
        @Argument from: Instant,
        @Argument to: Instant
    ): List<MonitoredValueHistoryEntry> = historyEntries(monitor, from, to)

    @SchemaMapping
    fun metricHistory(
        monitor: Monitor,
        @Argument from: Instant,
        @Argument to: Instant,
        @Argument resolution: HistoryResolution?
    ): MetricHistory {
        val series = MonitorMetrics.seriesFor(monitor.type, monitor.monitoredItemId)
            ?: return MetricHistory(
                metricHistoryService.resolutionFor(from, to).asHistoryResolution(),
                from,
                to,
                emptyList()
            )
        return metricHistoryService.history(series.metric, series.itemId, from, to, resolution)
    }

    private fun historyEntries(
        monitor: Monitor,
        from: Instant,
        to: Instant
    ): List<MonitoredValueHistoryEntry> = when (monitor.type) {
        com.krillsson.sysapi.core.monitoring.Monitor.Type.WEBSERVER_UP ->
            checkResults(monitor, from, to).map {
                MonitoredValueHistoryEntry(it.timestamp, it.successful.toConditionalValue().asMonitoredValue())
            }

        com.krillsson.sysapi.core.monitoring.Monitor.Type.CHECK_LATENCY ->
            checkResults(monitor, from, to).filter { it.successful }.map {
                MonitoredValueHistoryEntry(it.timestamp, it.latencyMs.toNumericalValue().asMonitoredValue())
            }

        com.krillsson.sysapi.core.monitoring.Monitor.Type.DISK_SMART_HEALTH -> smartHealthEntries(monitor, from, to)

        else -> seriesEntries(monitor, from, to)
    }

    private fun checkResults(monitor: Monitor, from: Instant, to: Instant) =
        checkHistoryService.resultsBetween(UUID.fromString(monitor.monitoredItemId), from, to, null)

    private fun seriesEntries(monitor: Monitor, from: Instant, to: Instant): List<MonitoredValueHistoryEntry> {
        val series = MonitorMetrics.seriesFor(monitor.type, monitor.monitoredItemId) ?: return emptyList()
        return metricHistoryService.tieredPoints(series.metric, series.itemId, from, to).map { point ->
            MonitoredValueHistoryEntry(
                point.timestamp,
                MonitorMetrics.collapse(point, series.metric).asMonitoredValue()
            )
        }
    }

    private fun smartHealthEntries(
        monitor: Monitor,
        from: Instant,
        to: Instant
    ): List<MonitoredValueHistoryEntry> = historyRepository.getHistoryLimitedToDates(from, to).mapNotNull { entry ->
        DiskSmartHealthMonitor
            .value(historyRepository.getDiskLoadsById(entry.id), monitor.monitoredItemId)
            ?.asMonitoredValue()
            ?.let { MonitoredValueHistoryEntry(entry.date, it) }
    }

    @SchemaMapping
    fun events(monitor: Monitor): List<Event> {
        return eventManager.eventsForMonitorId(monitor.id)
    }

    @BatchMapping(field = "monitoredItem", typeName = "Monitor")
    fun monitoredItem(monitors: Collection<Monitor>): Map<Monitor, MonitorableItem?> {
        val itemIds = monitors.map { it.id }
        val items = monitorManager.getMonitorableItemsForMonitors(itemIds).associateBy { it.first }
        return monitors.associateWith { items[it.id]?.second }
    }

    @SchemaMapping
    fun pastEvents(monitor: Monitor) =
        eventManager.eventsForMonitorId(monitor.id).filterIsInstance(PastEvent::class.java)

    @SchemaMapping
    fun ongoingEvents(monitor: Monitor) =
        eventManager.eventsForMonitorId(monitor.id).filterIsInstance(OngoingEvent::class.java)

    @SchemaMapping
    fun maxValue(monitor: Monitor): MonitoredValue? {
        return monitorManager.getById(monitor.id)?.maxValue(monitorInputCreator.createMaxValueInput())
            ?.asMonitoredValue()
    }

    @BatchMapping(field = "currentValue", typeName = "Monitor")
    fun currentValue(monitors: Collection<Monitor>): Map<Monitor, MonitoredValue?> {
        val itemIds = monitors.map { it.id }
        val items = monitorManager.getMonitorableItemsForMonitors(itemIds).associateBy { it.first }
        return monitors.associateWith { items[it.id]?.second?.currentValue?.asMonitoredValue() }
    }
}
