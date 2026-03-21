package com.wificracker.core.wifi.monitor

import com.wificracker.core.logging.AuditEntry
import com.wificracker.core.logging.AuditLogger
import com.wificracker.core.root.ShellExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException

class MtkMonitorCapture(
    private val shellExecutor: ShellExecutor,
    private val auditLogger: AuditLogger,
) {
    private var captureJob: Job? = null
    private val icsDevice = File("/dev/fw_log_ics")

    suspend fun enableCapture(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Step 0: SELinux permissive (required for wpa_supplicant comms)
                shellExecutor.executeAsRoot("setenforce 0")

                // Step 1: Send SNIFFER via ioctl 0x8BE5 (bypasses dispatch table + KCFI)
                shellExecutor.executeAsRoot(
                    "/data/local/tmp/wificracker/sniffer_direct wlan0 \"SNIFFER 2 0 0 0 0 0 0 0 0 0\"",
                )

                // Step 2: Enable ICS via ics_enable with raw integer args
                // SET_LEVEL=2 (capture all), ON_OFF=1 (enable)
                // Expected result: IcsLog[Lv:OnOff]=[2:1]
                val icsResult = shellExecutor.executeAsRoot(
                    "/data/local/tmp/wificracker/ics_enable 2 1",
                )

                if (!icsResult.isSuccess) {
                    auditLogger.log(AuditEntry(action = "ICS_ENABLE_FAILED", module = "MtkMonitorCapture", result = "FAIL", details = icsResult.stderr))
                    return@withContext false
                }

                auditLogger.log(AuditEntry(action = "MTK_CAPTURE_ENABLED", module = "MtkMonitorCapture", result = "SUCCESS"))
                true
            } catch (e: Exception) {
                auditLogger.log(AuditEntry(action = "MTK_CAPTURE_ENABLE_ERROR", module = "MtkMonitorCapture", result = "ERROR", details = e.message ?: "Unknown error"))
                false
            }
        }
    }

    suspend fun disableCapture(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                stopCapture()
                // SET_LEVEL=0, ON_OFF=0
                shellExecutor.executeAsRoot("/data/local/tmp/wificracker/ics_enable 0 0")
                auditLogger.log(AuditEntry(action = "MTK_CAPTURE_DISABLED", module = "MtkMonitorCapture", result = "SUCCESS"))
                true
            } catch (e: Exception) {
                auditLogger.log(AuditEntry(action = "MTK_CAPTURE_DISABLE_ERROR", module = "MtkMonitorCapture", result = "ERROR", details = e.message ?: "Unknown error"))
                false
            }
        }
    }

    fun startCapture(onFrame: suspend (Ieee80211Frame) -> Unit): Flow<Ieee80211Frame> = channelFlow {
        if (!icsDevice.exists()) {
            close(IOException("ICS device /dev/fw_log_ics not found — is the patched driver installed?"))
            return@channelFlow
        }

        try {
            // Open ICS device directly via su for root access
            val process = ProcessBuilder("su").start()
            process.outputStream.bufferedWriter().use { writer ->
                writer.write("cat /dev/fw_log_ics")
                writer.newLine()
                writer.flush()
            }

            val inputStream = process.inputStream
            val buffer = ByteArray(8192)
            var bufferPos = 0

            while (isActive) {
                val bytesRead = try {
                    inputStream.read(buffer, bufferPos, buffer.size - bufferPos)
                } catch (e: IOException) {
                    if (isActive) {
                        auditLogger.log(AuditEntry(action = "MTK_CAPTURE_READ_ERROR", module = "MtkMonitorCapture", details = e.message ?: "Read failed"))
                    }
                    break
                }

                if (bytesRead <= 0) break
                bufferPos += bytesRead

                var offset = 0
                while (offset < bufferPos) {
                    val nextOffset = IcsPacketParser.findNextPacketOffset(buffer, offset) ?: break
                    if (nextOffset >= bufferPos) break

                    val packet = IcsPacketParser.parsePacket(buffer, nextOffset)
                    if (packet != null && !packet.isTimesync) {
                        val frame = Ieee80211Parser.parseFrame(packet.rawFrame)
                        if (frame != null) {
                            send(frame)
                            onFrame(frame)
                        }
                    }

                    offset = nextOffset + 16
                }

                if (offset < bufferPos) {
                    System.arraycopy(buffer, offset, buffer, 0, bufferPos - offset)
                    bufferPos -= offset
                } else {
                    bufferPos = 0
                }
            }

            process.destroyForcibly()
        } catch (e: Exception) {
            auditLogger.log(AuditEntry(action = "MTK_CAPTURE_FATAL", module = "MtkMonitorCapture", details = e.message ?: "Fatal error"))
            close(e)
        }
    }

    suspend fun stopCapture() {
        captureJob?.cancel()
        captureJob = null
    }
}
