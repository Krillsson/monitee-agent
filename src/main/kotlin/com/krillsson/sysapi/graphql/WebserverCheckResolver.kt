package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.core.check.CheckHistoryService
import com.krillsson.sysapi.core.check.CheckResult
import com.krillsson.sysapi.core.check.CheckService
import com.krillsson.sysapi.core.check.HttpCheck
import com.krillsson.sysapi.core.check.UptimeMetrics
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller
import java.time.Instant

@Controller
@SchemaMapping(typeName = "WebserverCheck")
class WebserverCheckResolver(
    private val checkService: CheckService,
    private val historyService: CheckHistoryService,
    yamlConfigFile: YAMLConfigFile
) {
    private val legacyWindow = yamlConfigFile.metricsConfig.history.purging

    @SchemaMapping
    fun status(check: HttpCheck): CheckResult? = checkService.latestResult(check.id)

    @SchemaMapping
    fun uptimeMetrics(check: HttpCheck): UptimeMetrics {
        val now = Instant.now()
        return historyService.uptimeMetrics(check.id, now.minus(legacyWindow.olderThan, legacyWindow.unit), now)
    }

    @SchemaMapping
    fun historyBetweenTimestamps(
        check: HttpCheck,
        @Argument from: Instant,
        @Argument to: Instant
    ): List<CheckResult> = historyService.resultsBetween(check.id, from, to, null)
}

@Controller
class LegacyCheckResultResolver {

    @SchemaMapping(typeName = "WebserverCheckHistoryEntry", field = "webserverCheckId")
    fun webserverCheckId(result: CheckResult) = result.checkId

    @SchemaMapping(typeName = "WebserverCheckHistoryEntry", field = "timeStamp")
    fun historyEntryTimeStamp(result: CheckResult) = result.timestamp

    @SchemaMapping(typeName = "WebserverCheckHistoryEntry", field = "responseCode")
    fun historyEntryResponseCode(result: CheckResult) = result.responseCode ?: -1

    @SchemaMapping(typeName = "OneOffWebserverCheck", field = "timeStamp")
    fun oneOffTimeStamp(result: CheckResult) = result.timestamp

    @SchemaMapping(typeName = "OneOffWebserverCheck", field = "responseCode")
    fun oneOffResponseCode(result: CheckResult) = result.responseCode ?: -1
}
