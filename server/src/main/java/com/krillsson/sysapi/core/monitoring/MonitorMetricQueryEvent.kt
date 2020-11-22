package com.krillsson.sysapi.core.monitoring

import com.krillsson.sysapi.core.domain.system.SystemLoad
import com.krillsson.sysapi.core.query.MetricQueryEvent

class MonitorMetricQueryEvent(load: SystemLoad) : MetricQueryEvent(load)