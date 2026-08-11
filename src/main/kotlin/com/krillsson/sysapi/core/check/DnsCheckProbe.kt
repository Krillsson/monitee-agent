package com.krillsson.sysapi.core.check

import com.krillsson.sysapi.util.logger
import org.springframework.stereotype.Component
import org.xbill.DNS.Cache
import org.xbill.DNS.DClass
import org.xbill.DNS.Lookup
import org.xbill.DNS.Name
import org.xbill.DNS.Record
import org.xbill.DNS.SimpleResolver
import org.xbill.DNS.Type
import java.time.Duration
import java.time.Instant

@Component
class DnsCheckProbe {

    private val logger by logger()

    fun probe(spec: DnsCheckSpec): CheckResult {
        val start = Instant.now()
        return try {
            val lookup = Lookup(Name.fromString(spec.hostname, Name.root), spec.recordType.asDnsType())
            lookup.setResolver(resolverFor(spec))
            lookup.setCache(Cache(DClass.IN))
            val records = lookup.run()
            val values = records.orEmpty().map { it.asValue() }
            evaluate(spec, start, lookup, values)
        } catch (e: Exception) {
            result(spec, start, false, e.message ?: e::class.java.simpleName, null)
        }
    }

    private fun evaluate(spec: DnsCheckSpec, start: Instant, lookup: Lookup, values: List<String>): CheckResult {
        val missing = spec.expectedValues.filterNot { expected ->
            values.any { it.equals(expected, ignoreCase = true) }
        }
        return when {
            lookup.result != Lookup.SUCCESSFUL -> result(spec, start, false, lookup.errorString, values)
            values.isEmpty() -> result(spec, start, false, "No ${spec.recordType} record for ${spec.hostname}", values)
            missing.isNotEmpty() -> result(
                spec,
                start,
                false,
                "Answer ${values.joinToString()} does not contain ${missing.joinToString()}",
                values
            )

            else -> result(spec, start, true, values.joinToString(), values)
        }
    }

    private fun resolverFor(spec: DnsCheckSpec): SimpleResolver {
        val resolver = spec.resolver?.let { address ->
            val separator = address.lastIndexOf(':')
            val port = address.substring(separator + 1).toIntOrNull()
            if (separator > 0 && port != null && address.count { it == ':' } == 1) {
                SimpleResolver(address.substring(0, separator)).apply { setPort(port) }
            } else {
                SimpleResolver(address)
            }
        } ?: SimpleResolver()
        resolver.timeout = Duration.ofSeconds(spec.timeoutSeconds.toLong())
        return resolver
    }

    private fun result(
        spec: DnsCheckSpec,
        start: Instant,
        successful: Boolean,
        message: String,
        resolvedValues: List<String>?
    ): CheckResult {
        val latencyMs = Duration.between(start, Instant.now()).toMillis()
        logger.debug(
            "Check {} {} {}: {} ({}ms)",
            spec.recordType,
            spec.hostname,
            if (successful) "SUCCESS" else "FAIL",
            message,
            latencyMs
        )
        return CheckResult(
            id = null,
            checkId = null,
            checkType = CheckType.DNS,
            timestamp = Instant.now(),
            successful = successful,
            latencyMs = latencyMs,
            message = message,
            responseCode = null,
            errorBody = null,
            resolvedValues = resolvedValues
        )
    }

    private fun DnsRecordType.asDnsType() = when (this) {
        DnsRecordType.A -> Type.A
        DnsRecordType.AAAA -> Type.AAAA
        DnsRecordType.CNAME -> Type.CNAME
        DnsRecordType.MX -> Type.MX
        DnsRecordType.TXT -> Type.TXT
        DnsRecordType.NS -> Type.NS
    }

    private fun Record.asValue() = rdataToString().removeSuffix(".")
}
