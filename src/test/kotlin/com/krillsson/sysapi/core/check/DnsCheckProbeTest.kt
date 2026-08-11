package com.krillsson.sysapi.core.check

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.xbill.DNS.ARecord
import org.xbill.DNS.DClass
import org.xbill.DNS.Flags
import org.xbill.DNS.Message
import org.xbill.DNS.Name
import org.xbill.DNS.Rcode
import org.xbill.DNS.Record
import org.xbill.DNS.Section
import org.xbill.DNS.TXTRecord
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class DnsCheckProbeTest {

    private val probe = DnsCheckProbe()

    private lateinit var socket: DatagramSocket
    private var answers: List<Record> = emptyList()
    private var rcode = Rcode.NOERROR

    @BeforeEach
    fun startResolver() {
        socket = DatagramSocket(0, InetAddress.getLoopbackAddress())
        Thread { serve() }.apply { isDaemon = true }.start()
    }

    @AfterEach
    fun stopResolver() {
        socket.close()
    }

    private fun serve() {
        while (!socket.isClosed) {
            val request = DatagramPacket(ByteArray(512), 512)
            try {
                socket.receive(request)
            } catch (e: Exception) {
                return
            }
            val query = Message(request.data.copyOf(request.length))
            val response = Message(query.header.id)
            response.header.setFlag(Flags.QR.toInt())
            response.header.rcode = rcode
            response.addRecord(query.question, Section.QUESTION)
            answers.forEach { response.addRecord(it, Section.ANSWER) }
            val wire = response.toWire(Message.MAXLENGTH)
            socket.send(DatagramPacket(wire, wire.size, request.address, request.port))
        }
    }

    private fun spec(
        hostname: String = "nas.lan",
        recordType: DnsRecordType = DnsRecordType.A,
        expectedValues: List<String> = emptyList()
    ) = DnsCheckSpec(
        name = "Resolver",
        enabled = true,
        intervalSeconds = 60,
        timeoutSeconds = 5,
        hostname = hostname,
        resolver = "127.0.0.1:${socket.localPort}",
        recordType = recordType,
        expectedValues = expectedValues
    )

    private fun aRecord(address: String) = ARecord(
        Name.fromString("nas.lan."),
        DClass.IN,
        60,
        InetAddress.getByName(address)
    )

    @Test
    fun `passes and reports what the resolver answered`() {
        // Given
        answers = listOf(aRecord("192.168.1.10"))

        // When
        val result = probe.probe(spec())

        // Then
        result.successful shouldBe true
        result.checkType shouldBe CheckType.DNS
        result.resolvedValues shouldContainExactly listOf("192.168.1.10")
    }

    @Test
    fun `passes when every expected value is in the answer`() {
        // Given
        answers = listOf(aRecord("192.168.1.10"), aRecord("192.168.1.11"))

        // When
        val result = probe.probe(spec(expectedValues = listOf("192.168.1.11")))

        // Then
        result.successful shouldBe true
    }

    @Test
    fun `fails when an expected value is missing from the answer`() {
        // Given
        answers = listOf(aRecord("192.168.1.10"))

        // When
        val result = probe.probe(spec(expectedValues = listOf("192.168.1.99")))

        // Then
        result.successful shouldBe false
        result.message shouldContain "192.168.1.99"
        result.resolvedValues shouldContainExactly listOf("192.168.1.10")
    }

    @Test
    fun `fails when the name does not resolve`() {
        // Given
        answers = emptyList()
        rcode = Rcode.NXDOMAIN

        // When
        val result = probe.probe(spec())

        // Then
        result.successful shouldBe false
        result.checkType shouldBe CheckType.DNS
        result.resolvedValues shouldContainExactly emptyList()
    }

    @Test
    fun `matches a record against the text it prints as`() {
        // Given
        answers = listOf(TXTRecord(Name.fromString("nas.lan."), DClass.IN, 60, "hello"))

        // When
        val result = probe.probe(spec(recordType = DnsRecordType.TXT, expectedValues = listOf("\"hello\"")))

        // Then
        result.successful shouldBe true
    }
}
