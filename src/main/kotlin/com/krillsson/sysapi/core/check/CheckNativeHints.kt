package com.krillsson.sysapi.core.check

import com.krillsson.sysapi.graphql.mutations.CreateCheckFailed
import com.krillsson.sysapi.graphql.mutations.CreateCheckSuccess
import com.krillsson.sysapi.graphql.mutations.CreateDnsCheckInput
import com.krillsson.sysapi.graphql.mutations.CreateHttpCheckInput
import com.krillsson.sysapi.graphql.mutations.CreatePingCheckInput
import com.krillsson.sysapi.graphql.mutations.CreateTcpCheckInput
import com.krillsson.sysapi.graphql.mutations.DeleteCheckInput
import com.krillsson.sysapi.graphql.mutations.DeleteCheckOutput
import com.krillsson.sysapi.graphql.mutations.HttpHeaderInput
import com.krillsson.sysapi.graphql.mutations.OneOffDnsCheckInput
import com.krillsson.sysapi.graphql.mutations.OneOffHttpCheckInput
import com.krillsson.sysapi.graphql.mutations.OneOffPingCheckInput
import com.krillsson.sysapi.graphql.mutations.OneOffTcpCheckInput
import com.krillsson.sysapi.graphql.mutations.RunCheckNowFailed
import com.krillsson.sysapi.graphql.mutations.RunCheckNowInput
import com.krillsson.sysapi.graphql.mutations.RunCheckNowSuccess
import com.krillsson.sysapi.graphql.mutations.SetCheckEnabledInput
import com.krillsson.sysapi.graphql.mutations.UpdateCheckFailed
import com.krillsson.sysapi.graphql.mutations.UpdateCheckSuccess
import com.krillsson.sysapi.graphql.mutations.UpdateDnsCheckInput
import com.krillsson.sysapi.graphql.mutations.UpdateHttpCheckInput
import com.krillsson.sysapi.graphql.mutations.UpdatePingCheckInput
import com.krillsson.sysapi.graphql.mutations.UpdateTcpCheckInput
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@RegisterReflectionForBinding(
    CheckType::class,
    HttpMethod::class,
    HttpHeader::class,
    HttpCheck::class,
    TcpCheck::class,
    PingCheck::class,
    PingAvailability::class,
    DnsCheck::class,
    DnsRecordType::class,
    CheckResult::class,
    CheckResolution::class,
    CheckHistory::class,
    CheckHistoryPoint::class,
    CheckUptime::class,
    UptimeMetrics::class,
    UptimePeriod::class,
    UptimeDay::class,
    CreateHttpCheckInput::class,
    UpdateHttpCheckInput::class,
    OneOffHttpCheckInput::class,
    CreateTcpCheckInput::class,
    UpdateTcpCheckInput::class,
    OneOffTcpCheckInput::class,
    CreatePingCheckInput::class,
    UpdatePingCheckInput::class,
    OneOffPingCheckInput::class,
    CreateDnsCheckInput::class,
    UpdateDnsCheckInput::class,
    OneOffDnsCheckInput::class,
    HttpHeaderInput::class,
    DeleteCheckInput::class,
    SetCheckEnabledInput::class,
    RunCheckNowInput::class,
    CreateCheckSuccess::class,
    CreateCheckFailed::class,
    UpdateCheckSuccess::class,
    UpdateCheckFailed::class,
    DeleteCheckOutput::class,
    RunCheckNowSuccess::class,
    RunCheckNowFailed::class,
)
class CheckNativeHints
