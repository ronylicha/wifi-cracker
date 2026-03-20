package com.wificracker.core.logging

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class AuditLoggerTest {
    private lateinit var logDir: File
    private lateinit var logger: AuditLogger

    @Before fun setup() {
        logDir = File(System.getProperty("java.io.tmpdir"), "wificracker_test_logs")
        logDir.deleteRecursively(); logDir.mkdirs()
        logger = AuditLogger(logDir.absolutePath)
    }

    @Test fun `log writes entry`() = runTest {
        logger.log(AuditEntry(action = "SCAN_START", module = "scan"))
        assertEquals(1, logger.getEntries().size)
    }

    @Test fun `getEntries returns all in order`() = runTest {
        logger.log(AuditEntry(action = "FIRST", module = "scan"))
        logger.log(AuditEntry(action = "SECOND", module = "attack"))
        assertEquals(2, logger.getEntries().size); assertEquals("FIRST", logger.getEntries()[0].action)
    }

    @Test fun `exportJson returns valid JSON`() = runTest {
        logger.log(AuditEntry(action = "TEST", module = "core"))
        assertTrue(logger.exportJson().contains("TEST"))
    }

    @Test fun `purge clears all`() = runTest {
        logger.log(AuditEntry(action = "TEST", module = "core"))
        logger.purge(); assertTrue(logger.getEntries().isEmpty())
    }
}
