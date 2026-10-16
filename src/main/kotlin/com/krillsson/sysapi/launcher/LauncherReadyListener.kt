package com.krillsson.sysapi.launcher

import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.NativeDetector
import org.springframework.stereotype.Component

@Component
class LauncherReadyListener {

    @EventListener(ApplicationReadyEvent::class)
    fun onReady() {
        LauncherHandshake.fromEnvironment(NativeDetector.inNativeImage()).onReady()
    }
}
