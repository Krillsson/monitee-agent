package com.krillsson.sysapi.notifications.localization

import org.springframework.stereotype.Component
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.*

@Component
class DurationFormatter {
    fun formatDuration(uptime: Double): String {
        var uptime = uptime
        uptime *= 1000.0
        val fmtI: NumberFormat = DecimalFormat("###,###", DecimalFormatSymbols(Locale.ENGLISH))
        val fmtD: NumberFormat = DecimalFormat(
            "###,##",
            DecimalFormatSymbols(Locale.ENGLISH)
        )
        uptime /= 1000.0
        if (uptime < 60) {
            return fmtD.format(uptime) + " seconds"
        }
        uptime /= 60.0
        if (uptime < 60) {
            val minutes = uptime.toLong()
            return fmtI.format(minutes) + if (minutes > 1) " minutes" else " minutes"
        }
        uptime /= 60.0
        if (uptime < 24) {
            val hours = uptime.toLong()
            val minutes = ((uptime - hours) * 60).toLong()
            var s = fmtI.format(hours) + if (hours > 1) " hours" else " hours"
            if (minutes != 0L) {
                s += " " + fmtI.format(minutes) + if (minutes > 1) " minutes" else " minutes"
            }
            return s
        }
        uptime /= 24.0
        val days = uptime.toLong()
        val hours = ((uptime - days) * 24).toLong()
        var s = fmtI.format(days) + if (days > 1) " days" else " days"
        if (hours != 0L) {
            s += " " + fmtI.format(hours) + if (hours > 1) " hours" else " hours"
        }
        return s
    }
}