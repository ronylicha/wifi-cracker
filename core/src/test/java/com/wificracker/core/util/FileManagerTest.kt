package com.wificracker.core.util

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class FileManagerTest {
    private val fileManager = FileManager()
    private lateinit var tempDir: File
    @Before fun setup() { tempDir = File(System.getProperty("java.io.tmpdir"), "wificracker_fm_test"); tempDir.deleteRecursively(); tempDir.mkdirs() }
    @Test fun `ensureDirectory creates dir`() { val dir = fileManager.ensureDirectory("${tempDir.absolutePath}/newdir"); assertTrue(dir.exists()) }
    @Test fun `listFiles filters by extension`() { File(tempDir, "test.cap").createNewFile(); File(tempDir, "test.txt").createNewFile(); assertEquals(1, fileManager.listFiles(tempDir.absolutePath, "cap").size) }
    @Test fun `listFiles empty for nonexistent`() { assertTrue(fileManager.listFiles("/nonexistent").isEmpty()) }
    @Test fun `fileSizeFormatted correct`() { val f = File(tempDir, "t.bin"); f.writeBytes(ByteArray(2048)); assertEquals("2 KB", fileManager.fileSizeFormatted(f)) }
}
