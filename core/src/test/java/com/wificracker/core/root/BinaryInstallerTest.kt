package com.wificracker.core.root

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertTrue
import org.junit.Test

class BinaryInstallerTest {
    private val shellExecutor = mockk<ShellExecutor>(relaxed = true)
    private val installer = BinaryInstaller(shellExecutor)

    @Test fun `installBinary sets permissions with chmod 755`() {
        every { shellExecutor.executeAsRoot(any(), any()) } returns ShellResult(0, "", "")
        val result = installer.installBinary("aircrack-ng", "/data/data/com.wificracker.app/files")
        assertTrue(result.isSuccess)
        verify { shellExecutor.executeAsRoot(match { it.contains("chmod 755") }) }
    }

    @Test fun `isBinaryInstalled returns true when binary exists`() {
        every { shellExecutor.executeAsRoot("test -x /data/local/tmp/wificracker/aircrack-ng && echo exists") } returns ShellResult(0, "exists", "")
        assertTrue(installer.isBinaryInstalled("aircrack-ng"))
    }

    @Test fun `listInstalledBinaries returns correct list`() {
        every { shellExecutor.executeAsRoot("ls /data/local/tmp/wificracker/") } returns ShellResult(0, "aircrack-ng\nhcxdumptool\nhcxpcapngtool", "")
        val binaries = installer.listInstalledBinaries()
        assertTrue(binaries.contains("aircrack-ng"))
        assertTrue(binaries.size == 3)
    }
}
