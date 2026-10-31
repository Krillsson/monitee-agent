package com.krillsson.sysapi.storagepool.unraid

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class UnraidDisksIniParserTest {

    @Test
    fun `maps slot sections to friendly disk info`() {
        // Given
        val text = """
            ["parity"]
            device="sdb"
            id="WDC_WD40"
            status="DISK_OK"
            numErrors="0"
            temp="32"

            ["disk1"]
            device="sdc"
            id="WDC_WD20"
            size="11708591084"
            status="DISK_OK"
            numErrors="2"
            temp="34"
            fsSize="11706504172"
            fsFree="2316868280"
            fsUsed="9389635892"
        """.trimIndent()

        // When
        val disks = UnraidDisksIniParser.parse(text)

        // Then
        disks shouldHaveSize 2
        val parity = disks.first { it.slotName == "parity" }
        parity.device shouldBe "sdb"
        parity.id shouldBe "WDC_WD40"
        parity.status shouldBe "DISK_OK"
        parity.numErrors shouldBe 0L
        parity.temperatureCelsius shouldBe 32

        val disk1 = disks.first { it.slotName == "disk1" }
        disk1.device shouldBe "sdc"
        disk1.numErrors shouldBe 2L
        disk1.sizeK shouldBe 11708591084L
        disk1.fsSizeK shouldBe 11706504172L
        disk1.fsFreeK shouldBe 2316868280L
        disk1.fsUsedK shouldBe 9389635892L
        parity.fsSizeK shouldBe null
    }

    @Test
    fun `an empty file yields no disks`() {
        // Given / When
        val disks = UnraidDisksIniParser.parse("")

        // Then
        disks.shouldBeEmpty()
    }
}
