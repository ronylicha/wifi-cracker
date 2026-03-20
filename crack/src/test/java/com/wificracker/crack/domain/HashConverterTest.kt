package com.wificracker.crack.domain

import com.wificracker.core.root.ShellExecutor
import com.wificracker.core.root.ShellResult
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

class HashConverterTest {
    private val shellExecutor = mockk<ShellExecutor>(relaxed = true)
    private val converter = HashConverter(shellExecutor)

    @Test fun `convertCapToHc22000 returns success with output path`() {
        every { shellExecutor.executeAsRoot(match { it.contains("hcxpcapngtool") }) } returns ShellResult(0, "Networks detected: 1", "")
        val result = converter.convertCapToHc22000("/tmp/capture.cap")
        assertTrue(result.success)
        assertTrue(result.outputPath.endsWith(".hc22000"))
        assertEquals("hc22000", result.format)
    }

    @Test fun `convertCapToHc22000 returns failure on error`() {
        every { shellExecutor.executeAsRoot(match { it.contains("hcxpcapngtool") }) } returns ShellResult(1, "", "file not found")
        val result = converter.convertCapToHc22000("/tmp/bad.cap")
        assertFalse(result.success)
        assertTrue(result.error.contains("not found"))
    }

    @Test fun `detectCaptureType identifies pcap files`() {
        every { shellExecutor.executeAsRoot("file /tmp/test.cap") } returns ShellResult(0, "pcap capture file", "")
        assertEquals("pcap", converter.detectCaptureType("/tmp/test.cap"))
    }
}
