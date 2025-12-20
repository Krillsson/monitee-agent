package com.krillsson.sysapi.core.monitoring.monitors

import com.krillsson.sysapi.core.domain.docker.Container
import com.krillsson.sysapi.core.domain.docker.ContainerMetrics
import com.krillsson.sysapi.core.monitoring.MonitoredValue
import com.krillsson.sysapi.core.domain.system.SystemInfo
import com.krillsson.sysapi.core.domain.system.SystemLoad

// To prevent repeating the logic for selecting a monitored value from a SystemLoad as it can cause bugs
typealias NumericalValueSelector = (SystemLoad, String?) -> MonitoredValue.NumericalValue?
typealias FractionalValueSelector = (SystemLoad, String?) -> MonitoredValue.FractionalValue?
typealias ConditionalValueSelector = (SystemLoad, String?) -> MonitoredValue.ConditionalValue?
typealias EnumValueSelector<E> = (SystemLoad, String?) -> MonitoredValue.EnumValue<E>?
typealias ContainerConditionalValueSelector = (List<Container>, List<ContainerMetrics>, String?) -> MonitoredValue.ConditionalValue?
typealias ContainerNumericalValueSelector = (List<Container>, List<ContainerMetrics>, String?) -> MonitoredValue.NumericalValue?
typealias ContainerFractionalValueSelector = (List<Container>, List<ContainerMetrics>, String?) -> MonitoredValue.FractionalValue?

typealias MaxValueNumericalSelector = (SystemInfo, String?) -> MonitoredValue.NumericalValue?
typealias MaxValueFractionalSelector = (SystemInfo, String?) -> MonitoredValue.FractionalValue?