package com.wificracker.core.root

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootCheckerTest {

    private val shellExecutor = mockk<ShellExecutor>()
    private val rootChecker = RootChecker(shellExecutor)

    @Test
    fun `isRooted returns true when su command succeeds`() {
        every { shellExecutor.execute("su -c id") } returns ShellResult(0, "uid=0(root)", "")
        assertTrue(rootChecker.isRooted())
    }

    @Test
    fun `isRooted returns false when su command fails`() {
        every { shellExecutor.execute("su -c id") } returns ShellResult(1, "", "su: not found")
        assertFalse(rootChecker.isRooted())
    }

    @Test
    fun `detectRootType returns MAGISK when magisk binary exists`() {
        every { shellExecutor.execute("su -c id") } returns ShellResult(0, "uid=0(root)", "")
        every { shellExecutor.execute("su -c which magisk") } returns ShellResult(0, "/sbin/magisk", "")
        assertEquals(RootType.MAGISK, rootChecker.detectRootType())
    }

    @Test
    fun `detectRootType returns KERNELSU when ksud exists`() {
        every { shellExecutor.execute("su -c id") } returns ShellResult(0, "uid=0(root)", "")
        every { shellExecutor.execute("su -c which magisk") } returns ShellResult(1, "", "")
        every { shellExecutor.execute("su -c which ksud") } returns ShellResult(0, "/data/adb/ksud", "")
        assertEquals(RootType.KERNELSU, rootChecker.detectRootType())
    }

    @Test
    fun `detectRootType returns NONE when not rooted`() {
        every { shellExecutor.execute("su -c id") } returns ShellResult(1, "", "")
        assertEquals(RootType.NONE, rootChecker.detectRootType())
    }
}
