package com.krillsson.sysapi.util

import oshi.software.os.OSProcess

object OSProcessComparators {
    val CPU_LOAD_COMPARATOR = Comparator
        .comparingDouble { process: OSProcess -> process.processCpuLoadCumulative }.reversed()

    val MEMORY_COMPARATOR =
        Comparator.comparingLong { process: OSProcess -> process.privateResidentMemory }
            .reversed()

    val NEWEST_COMPARATOR =
        Comparator.comparingLong { process: OSProcess -> process.upTime }

    val OLDEST_COMPARATOR = NEWEST_COMPARATOR.reversed()

    val PID_COMPARATOR =
        Comparator.comparingInt { process: OSProcess -> process.processID }

    val PARENT_PID_COMPARATOR = Comparator
        .comparingInt { process: OSProcess -> process.parentProcessID }

    val NAME_COMPARATOR = Comparator.comparing(
        { process: OSProcess -> process.name },
        String.CASE_INSENSITIVE_ORDER
    )
}