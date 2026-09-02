package com.krillsson.sysapi.storagepool.unraid

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class UnraidMdStatParserTest {

    @ParameterizedTest
    @ValueSource(strings = ["STARTED", "STOPPED", "NEW_ARRAY", "DISABLE_DISK", "RECON_DISK", "SWAP_DSBL"])
    fun `parses every mdState value`(state: String) {
        // Given
        val text = "mdState=$state\nmdNumDisks=4\n"

        // When
        val array = UnraidMdStatParser.parse(text)

        // Then
        array.shouldNotBeNull()
        array.mdState shouldBe state
    }

    @Test
    fun `an idle array has no resync position`() {
        // Given
        val text = """
            mdState=STARTED
            mdNumDisks=4
            mdResyncAction=
            mdResyncPos=0
            mdResync=0
            sbSynced=1700000000
            sbSynced2=1700003600
            sbSyncErrs=0
            sbSyncExit=0
        """.trimIndent()

        // When
        val array = UnraidMdStatParser.parse(text).shouldNotBeNull()

        // Then
        array.resyncPosK shouldBe 0L
        array.syncCompletedAtEpochSeconds shouldBe 1700003600L
    }

    @Test
    fun `a running parity check reports progress`() {
        // Given
        val text = """
            mdState=STARTED
            mdNumDisks=4
            mdResyncAction=check P
            mdResyncSize=1000000
            mdResyncPos=500000
            mdResync=1000000
            sbSynced=1700000000
            sbSynced2=0
            sbSyncErrs=0
            sbSyncExit=0
        """.trimIndent()

        // When
        val array = UnraidMdStatParser.parse(text).shouldNotBeNull()

        // Then
        array.resyncAction shouldBe "check P"
        array.resyncPosK shouldBe 500000L
        array.resyncTotalK shouldBe 1000000L
        array.syncCompletedAtEpochSeconds shouldBe 0L
    }

    @Test
    fun `a completed check reports its error count`() {
        // Given
        val text = """
            mdState=STARTED
            mdResyncPos=0
            sbSynced=1700000000
            sbSynced2=1700003600
            sbSyncErrs=5
            sbSyncExit=0
        """.trimIndent()

        // When
        val array = UnraidMdStatParser.parse(text).shouldNotBeNull()

        // Then
        array.syncErrors shouldBe 5L
        array.syncExitCode shouldBe 0
    }

    @Test
    fun `a cancelled check has a negative exit code`() {
        // Given
        val text = """
            mdState=STARTED
            sbSynced=1700000000
            sbSynced2=1700001000
            sbSyncExit=-4
        """.trimIndent()

        // When
        val array = UnraidMdStatParser.parse(text).shouldNotBeNull()

        // Then
        array.syncExitCode shouldBe -4
    }

    @Test
    fun `a disk status other than DISK_OK is preserved per slot`() {
        // Given
        val text = """
            mdState=STARTED
            mdNumDisks=2
            rdevStatus.0=DISK_OK
            rdevNumErrors.0=0
            rdevStatus.1=DISK_DSBL
            rdevNumErrors.1=12
        """.trimIndent()

        // When
        val array = UnraidMdStatParser.parse(text).shouldNotBeNull()

        // Then
        array.disks shouldBe listOf(
            UnraidDisk(slot = 0, status = "DISK_OK", numErrors = 0L),
            UnraidDisk(slot = 1, status = "DISK_DSBL", numErrors = 12L)
        )
    }

    @Test
    fun `text without mdState is not an unraid array`() {
        // Given
        val text = "Personalities : [raid1]\nunused devices: <none>"

        // When
        val array = UnraidMdStatParser.parse(text)

        // Then
        array.shouldBeNull()
    }
}
