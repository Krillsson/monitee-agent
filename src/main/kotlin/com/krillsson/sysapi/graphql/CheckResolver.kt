package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.core.check.Check
import com.krillsson.sysapi.core.check.CheckCertificate
import com.krillsson.sysapi.core.check.CheckHistory
import com.krillsson.sysapi.core.check.CheckHistoryService
import com.krillsson.sysapi.core.check.CheckResolution
import com.krillsson.sysapi.core.check.CheckResult
import com.krillsson.sysapi.core.check.CheckService
import com.krillsson.sysapi.core.check.CheckUptime
import com.krillsson.sysapi.core.check.HttpCheck
import com.krillsson.sysapi.core.check.PingCheck
import com.krillsson.sysapi.core.check.PingCheckProbe
import com.krillsson.sysapi.core.check.certificate
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller
import java.time.Duration
import java.time.Instant

abstract class CheckFieldResolver(
    protected val checkService: CheckService,
    private val historyService: CheckHistoryService
) {
    companion object {
        private val DEFAULT_UPTIME_WINDOW: Duration = Duration.ofDays(30)
    }

    @SchemaMapping
    fun status(check: Check): CheckResult? = checkService.latestResult(check.id)

    @SchemaMapping
    fun uptimeMetrics(check: Check, @Argument from: Instant?, @Argument to: Instant?): CheckUptime {
        val end = to ?: Instant.now()
        return historyService.uptime(check.id, from ?: end.minus(DEFAULT_UPTIME_WINDOW), end)
    }

    @SchemaMapping
    fun history(
        check: Check,
        @Argument from: Instant,
        @Argument to: Instant,
        @Argument resolution: CheckResolution?
    ): CheckHistory = historyService.history(check.id, from, to, resolution)

    @SchemaMapping
    fun results(
        check: Check,
        @Argument from: Instant,
        @Argument to: Instant,
        @Argument limit: Int?
    ): List<CheckResult> = historyService.resultsBetween(check.id, from, to, limit)
}

@Controller
@SchemaMapping(typeName = "HttpCheck")
class HttpCheckResolver(
    checkService: CheckService,
    historyService: CheckHistoryService
) : CheckFieldResolver(checkService, historyService) {

    @SchemaMapping
    fun certificate(check: HttpCheck): CheckCertificate? = checkService.latestResultWithCertificate(check.id)?.certificate()
}

@Controller
@SchemaMapping(typeName = "CheckCertificate")
class CheckCertificateResolver {

    @SchemaMapping
    fun daysUntilExpiry(certificate: CheckCertificate): Int = certificate.daysUntilExpiry(Instant.now())
}

@Controller
@SchemaMapping(typeName = "TcpCheck")
class TcpCheckResolver(
    checkService: CheckService,
    historyService: CheckHistoryService
) : CheckFieldResolver(checkService, historyService)

@Controller
@SchemaMapping(typeName = "DnsCheck")
class DnsCheckResolver(
    checkService: CheckService,
    historyService: CheckHistoryService
) : CheckFieldResolver(checkService, historyService)

@Controller
@SchemaMapping(typeName = "PingCheck")
class PingCheckResolver(
    checkService: CheckService,
    historyService: CheckHistoryService,
    private val pingProbe: PingCheckProbe
) : CheckFieldResolver(checkService, historyService) {

    @SchemaMapping
    fun pingAvailable(check: PingCheck): Boolean = pingProbe.available
}
