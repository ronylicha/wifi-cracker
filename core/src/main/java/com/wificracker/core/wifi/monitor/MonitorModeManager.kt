package com.wificracker.core.wifi.monitor

import com.wificracker.core.logging.AuditEntry
import com.wificracker.core.logging.AuditLogger
import com.wificracker.core.root.ShellExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MonitorModeManager @Inject constructor(
    private val shellExecutor: ShellExecutor,
    private val auditLogger: AuditLogger,
) {
    private var mtkCapture: MtkMonitorCapture? = null
    private val mediaTexKernelDriver = "/vendor/lib/modules/wlan_drv_gen4m_6878.ko"
    private val expectedDriverHash = "aaead92bedc1e69f2642aaa54cb0a314ec3d28fec8781bf2263f4e203310534a"

    suspend fun isPatched(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val driverFile = File(mediaTexKernelDriver)
                if (!driverFile.exists()) return@withContext false

                val fileHash = computeSha256(driverFile)
                val isPatchedDriver = fileHash.equals(expectedDriverHash, ignoreCase = true)

                if (isPatchedDriver) {
                    auditLogger.log(AuditEntry(action = "MEDIATEK_DRIVER_VERIFIED", module = "MonitorModeManager", target = mediaTexKernelDriver, result = "SUCCESS", details = "Patched kernel driver detected"))
                }

                isPatchedDriver
            } catch (e: Exception) {
                auditLogger.log(AuditEntry(action = "MEDIATEK_DRIVER_VERIFY_ERROR", module = "MonitorModeManager", result = "ERROR", details = e.message ?: "Unknown error"))
                false
            }
        }
    }

    suspend fun enableMonitorMode(interfaceName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                auditLogger.log(AuditEntry(action = "MONITOR_MODE_ENABLE_ATTEMPT", module = "MonitorModeManager", target = interfaceName, details = "Starting MediaTek monitor mode"))

                if (mtkCapture == null) {
                    mtkCapture = MtkMonitorCapture(shellExecutor, auditLogger)
                }

                val enabled = mtkCapture!!.enableCapture()
                if (enabled) {
                    auditLogger.log(AuditEntry(action = "MONITOR_MODE_ENABLED", module = "MonitorModeManager", target = interfaceName, result = "SUCCESS"))
                }
                enabled
            } catch (e: Exception) {
                auditLogger.log(AuditEntry(action = "MONITOR_MODE_ENABLE_ERROR", module = "MonitorModeManager", target = interfaceName, result = "ERROR", details = e.message ?: "Unknown error"))
                false
            }
        }
    }

    suspend fun disableMonitorMode(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val disabled = mtkCapture?.disableCapture() ?: true
                if (disabled) {
                    auditLogger.log(AuditEntry(action = "MONITOR_MODE_DISABLED", module = "MonitorModeManager", result = "SUCCESS"))
                    mtkCapture = null
                }
                disabled
            } catch (e: Exception) {
                auditLogger.log(AuditEntry(action = "MONITOR_MODE_DISABLE_ERROR", module = "MonitorModeManager", result = "ERROR", details = e.message ?: "Unknown error"))
                false
            }
        }
    }

    fun scanNetworks(
        durationMs: Long,
        onNetwork: suspend (Ieee80211Frame) -> Unit,
    ): Flow<Ieee80211Frame> {
        if (mtkCapture == null) {
            mtkCapture = MtkMonitorCapture(shellExecutor, auditLogger)
        }
        return mtkCapture!!.startCapture(onNetwork)
    }

    private fun computeSha256(file: File): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }
}
