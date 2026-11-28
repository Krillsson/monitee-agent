package com.krillsson.sysapi.core.forecast

import com.krillsson.sysapi.core.domain.filesystem.FileSystemSpaceForecast
import com.krillsson.sysapi.core.domain.filesystem.FileSystemSpaceForecastHistoryPoint
import com.krillsson.sysapi.core.domain.filesystem.FileSystemSpaceTrend
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@RegisterReflectionForBinding(
    FileSystemSpaceForecast::class,
    FileSystemSpaceForecastHistoryPoint::class,
    FileSystemSpaceTrend::class,
)
class ForecastNativeHints
