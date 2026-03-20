package com.wificracker.crack.integration

import com.wificracker.core.root.ShellExecutor
import com.wificracker.core.root.ShellResult
import com.wificracker.crack.domain.HashConverter
import com.wificracker.crack.domain.ConversionResult
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

class CrackFlowTest {

    private val shellExecutor = mockk<ShellExecutor>(relaxed = true)
    private val hashConverter = HashConverter(shellExecutor)

    @Test
    fun `hash conversion pipeline cap to hc22000`() {
        every { shellExecutor.executeAsRoot(match { it.contains("hcxpcapngtool") }) } returns ShellResult(0, "Networks detected: 3", "")
        val result = hashConverter.convertCapToHc22000("/tmp/test.cap")
        assertTrue(result.success)
        assertTrue(result.outputPath.endsWith(".hc22000"))
        assertEquals("hc22000", result.format)
    }

    @Test
    fun `hash conversion fails gracefully on bad file`() {
        every { shellExecutor.executeAsRoot(match { it.contains("hcxpcapngtool") }) } returns ShellResult(1, "", "could not read pcap file")
        val result = hashConverter.convertCapToHc22000("/tmp/bad.cap")
        assertFalse(result.success)
        assertTrue(result.error.contains("could not read"))
    }

    @Test
    fun `detect capture type identifies formats`() {
        every { shellExecutor.executeAsRoot("file /tmp/test.pcap") } returns ShellResult(0, "pcap-ng capture file", "")
        assertEquals("pcapng", hashConverter.detectCaptureType("/tmp/test.pcap"))

        every { shellExecutor.executeAsRoot("file /tmp/test.cap") } returns ShellResult(0, "pcap capture file", "")
        assertEquals("pcap", hashConverter.detectCaptureType("/tmp/test.cap"))

        every { shellExecutor.executeAsRoot("file /tmp/test.bin") } returns ShellResult(0, "data", "")
        assertEquals("unknown", hashConverter.detectCaptureType("/tmp/test.bin"))
    }
}
