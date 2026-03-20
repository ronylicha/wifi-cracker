package com.wificracker.core.logging

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuditLogger @Inject constructor(private val logDirPath: String) {
    private val logFile: File get() = File(logDirPath, "audit.jsonl").also { it.parentFile?.mkdirs() }
    private val json = Json { prettyPrint = false }
    private val mutex = Mutex()

    suspend fun log(entry: AuditEntry) { mutex.withLock { logFile.appendText("${json.encodeToString(entry)}\n") } }
    suspend fun getEntries(): List<AuditEntry> { mutex.withLock { if (!logFile.exists()) return emptyList(); return logFile.readLines().filter { it.isNotBlank() }.map { json.decodeFromString<AuditEntry>(it) } } }
    suspend fun exportJson(): String = Json { prettyPrint = true }.encodeToString(getEntries())
    suspend fun purge() { mutex.withLock { if (logFile.exists()) logFile.delete() } }
}
