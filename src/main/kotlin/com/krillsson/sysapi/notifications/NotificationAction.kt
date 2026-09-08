package com.krillsson.sysapi.notifications

sealed interface NotificationAction {
    val label: String
    val clear: Boolean

    data class View(
        override val label: String,
        val url: String,
        override val clear: Boolean = false
    ) : NotificationAction
}
