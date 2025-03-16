package com.krillsson.sysapi.core.monitoring

import com.krillsson.sysapi.core.domain.monitor.MonitorConfig
import com.krillsson.sysapi.core.domain.monitor.MonitoredValue
import com.krillsson.sysapi.core.metrics.Metrics
import com.krillsson.sysapi.core.monitoring.MonitorFactory.createMonitor
import com.krillsson.sysapi.core.monitoring.event.EventManager
import com.krillsson.sysapi.util.logger
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.util.*
import java.util.concurrent.TimeUnit

@Component
class MonitorManager(
    private val metrics: Metrics,
    private val eventManager: EventManager,
    private val repository: MonitorRepository,
    private val monitoredItemMissingChecker: MonitoredItemMissingChecker,
    private val clock: Clock,
    private val monitorInputCreator: MonitorInputCreator
) {

    val logger by logger()

    @Scheduled(fixedRate = 15, timeUnit = TimeUnit.SECONDS)
    fun run() {
        val input = monitorInputCreator.createInput(
            activeMonitors.values.map { it.second }
        )

        checkMonitors(
            input
        )
    }

    companion object {
        val logger by logger()
    }

    private lateinit var activeMonitors: MutableMap<UUID, Pair<MonitorMechanism, Monitor<MonitoredValue>>>

    private fun checkMonitors(metricQueryEvent: MonitorInput) {
        activeMonitors.values.forEach { (mechanism, monitor) ->
            val value = monitor.selectValue(metricQueryEvent)
            if (value != null) {
                val isOverThreshold = monitor.isPastThreshold(value)
                val event = mechanism.check(
                    monitor,
                    monitor.config,
                    value,
                    isOverThreshold
                )
                event?.let {
                    eventManager.add(it)
                }
            }
            reportItemMissingForMonitor(monitor, value)
        }
    }

    @PostConstruct
    fun start() {
        restore()
    }


    @PreDestroy
    fun stop() {
        persist()
    }

    fun getAll() = activeMonitors.map { it.value.second }

    fun getById(id: UUID): Monitor<MonitoredValue>? {
        return activeMonitors[id]?.second
    }

    fun getMonitorableItemForType(type: Monitor.Type): List<MonitorableItem> {
        return monitorInputCreator.getMonitorableItemForType(type)
    }

    fun getMonitorableItemForMonitor(id: UUID): MonitorableItem {
        return requireNotNull(getById(id)?.let { monitor -> monitorInputCreator.getMonitorableItemForMonitor(monitor) })
    }

    fun monitorOfTypeByMonitoredItemId(type: Monitor.Type, monitoredItemId: String): Monitor<MonitoredValue>? {
        return activeMonitors.values.firstOrNull { it.second.type == type && it.second.config.monitoredItemId == monitoredItemId }?.second
    }

    fun add(inertia: Duration, type: Monitor.Type, threshold: MonitoredValue, itemId: String?): UUID {
        val config = MonitorConfig(itemId, threshold, inertia)
        val monitor = createMonitor(type, UUID.randomUUID(), config)
        return if (validate(monitor)) {
            register(monitor)
            persist()
            monitor.id
        } else {
            throw IllegalArgumentException("Not mappable to device: $type with $itemId using ${metrics::class.java.simpleName}")
        }
    }

    fun update(monitorId: UUID, inertia: Duration?, threshold: MonitoredValue?): UUID {
        val oldMonitor = getById(monitorId)
        checkNotNull(oldMonitor) { "No monitor with id $monitorId was found" }
        check(inertia != null || threshold != null) { "Either inertia or threshold has to be provided" }
        val updatedConfig = MonitorConfig(
            oldMonitor.config.monitoredItemId,
            threshold ?: oldMonitor.config.threshold,
            inertia ?: oldMonitor.config.inertia
        )
        val updatedMonitor = createMonitor(oldMonitor.type, oldMonitor.id, updatedConfig)
        return if (validate(updatedMonitor)) {
            register(updatedMonitor)
            persist()
            updatedMonitor.id
        } else {
            throw IllegalArgumentException("Not mappable to device: ${oldMonitor.type} with ${oldMonitor.config.monitoredItemId}")
        }
    }

    fun remove(id: UUID): Boolean {
        val removed = activeMonitors.remove(id)

        removed?.let {
            persist()
            eventManager.removeEventsForMonitorId(id)
        }
        return removed != null
    }

    private fun reportItemMissingForMonitor(monitor: Monitor<MonitoredValue>, value: MonitoredValue?) {
        if (value == null) {
            logger.warn("Removing monitor ${monitor.id} as its item ${monitor.type.name}:${monitor.config.monitoredItemId} is missing")
            monitoredItemMissingChecker.reportItemMissing(monitor)
            remove(monitor.id)
        }
    }

    private fun persist() {
        val monitors = activeMonitors.map { it.value.second }.toList()
        repository.write(monitors)
    }

    private fun restore() {
        activeMonitors = mutableMapOf()
        repository.read().forEach { monitor ->
            register(monitor)
        }
    }

    private fun register(monitor: Monitor<MonitoredValue>) {
        val mechanism = MonitorMechanism(clock)
        activeMonitors[monitor.id] = mechanism to monitor
    }

    private fun validate(monitor: Monitor<MonitoredValue>): Boolean {
        val input = monitorInputCreator.createInput(listOf(monitor))
        return monitor.selectValue(
            input
        ) != null
    }
}

