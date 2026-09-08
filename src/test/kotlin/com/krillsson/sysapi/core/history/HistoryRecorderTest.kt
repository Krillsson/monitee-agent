package com.krillsson.sysapi.core.history

import com.krillsson.sysapi.config.ConnectivityCheckConfiguration
import com.krillsson.sysapi.config.DockerConfiguration
import com.krillsson.sysapi.config.HistoryConfiguration
import com.krillsson.sysapi.config.MetricsConfiguration
import com.krillsson.sysapi.config.SelfSignedCertificateConfiguration
import com.krillsson.sysapi.config.UserConfiguration
import com.krillsson.sysapi.config.WindowsConfiguration
import com.krillsson.sysapi.config.YAMLConfigFile
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.scheduling.TaskScheduler
import java.time.Duration
import java.util.concurrent.TimeUnit

class HistoryRecorderTest {

    @Test
    fun `schedules recording at the interval configured under metricsConfig history`() {
        // Given
        val config = configWithHistoryInterval(interval = 45, unit = TimeUnit.SECONDS)
        val taskScheduler = mockk<TaskScheduler>(relaxed = true)
        val period = slot<Duration>()

        // When
        HistoryRecorder(config, mockk(relaxed = true), mockk(relaxed = true), taskScheduler).start()

        // Then
        verify { taskScheduler.scheduleAtFixedRate(any(), capture(period)) }
        period.captured shouldBe Duration.ofSeconds(45)
    }

    @Test
    fun `normalises a minutes-configured interval to seconds`() {
        // Given
        val config = configWithHistoryInterval(interval = 30, unit = TimeUnit.MINUTES)
        val taskScheduler = mockk<TaskScheduler>(relaxed = true)
        val period = slot<Duration>()

        // When
        HistoryRecorder(config, mockk(relaxed = true), mockk(relaxed = true), taskScheduler).start()

        // Then
        verify { taskScheduler.scheduleAtFixedRate(any(), capture(period)) }
        period.captured shouldBe Duration.ofMinutes(30)
    }

    private fun configWithHistoryInterval(interval: Long, unit: TimeUnit) = YAMLConfigFile(
        user = UserConfiguration(username = "user", password = "password"),
        metricsConfig = MetricsConfiguration(history = HistoryConfiguration(interval = interval, unit = unit)),
        windows = WindowsConfiguration(),
        connectivityCheck = ConnectivityCheckConfiguration(enabled = true, address = "https://ifconfig.me"),
        docker = DockerConfiguration(),
        forwardHttpToHttps = false,
        selfSignedCertificates = SelfSignedCertificateConfiguration(enabled = true, populateCN = true, populateSAN = true)
    )
}
