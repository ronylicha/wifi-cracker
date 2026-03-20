package com.wificracker.core.wifi.monitor

import com.wificracker.core.logging.AuditEntry
import com.wificracker.core.logging.AuditLogger
import com.wificracker.core.root.ShellExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import kotlin.system.exitProcess

class MtkMonitorCapture(
    private val shellExecutor: ShellExecutor,
    private val auditLogger: AuditLogger,
) {
    private var captureJob: Job? = null
    private val icsDevice = File("/dev/fw_log_ics")
    private val wpaSocket = "/data/vendor/wifi/wpa/sockets/wlan0"

    suspend fun enableCapture(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val snifferCmd = "DRIVER SNIFFER 2 0 0 0 0 0 0 0 0 0"
                sendWpaCommand(snifferCmd)

                if (!icsDevice.exists()) {
                    auditLogger.log(AuditEntry(action = "MTK_CAPTURE_ENABLE_FAILED", module = "MtkMonitorCapture", target = icsDevice.absolutePath, result = "FAIL", details = "ICS device not found"))
                    return@withContext false
                }

                val enableLevel = setIcsLevel(2)
                if (!enableLevel) {
                    auditLogger.log(AuditEntry(action = "MTK_CAPTURE_LEVEL_FAILED", module = "MtkMonitorCapture", target = icsDevice.absolutePath, result = "FAIL", details = "Failed to set ICS level"))
                    return@withContext false
                }

                val enableCapture = setIcsState(1)
                if (!enableCapture) {
                    auditLogger.log(AuditEntry(action = "MTK_CAPTURE_STATE_FAILED", module = "MtkMonitorCapture", target = icsDevice.absolutePath, result = "FAIL", details = "Failed to enable ICS state"))
                    return@withContext false
                }

                auditLogger.log(AuditEntry(action = "MTK_CAPTURE_ENABLED", module = "MtkMonitorCapture", target = icsDevice.absolutePath, result = "SUCCESS"))
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

                val disableState = setIcsState(0)
                if (!disableState) {
                    auditLogger.log(AuditEntry(action = "MTK_CAPTURE_DISABLE_PARTIAL", module = "MtkMonitorCapture", result = "WARN", details = "Failed to disable ICS state"))
                }

                val snifferCmd = "DRIVER SNIFFER 0 0 0 0 0 0 0 0 0 0"
                sendWpaCommand(snifferCmd)

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
            close(IOException("ICS device not found"))
            return@channelFlow
        }

        try {
            FileInputStream(icsDevice).use { inputStream ->
                val buffer = ByteArray(8192)
                var bufferPos = 0

                while (isActive) {
                    val bytesRead = try {
                        inputStream.read(buffer, bufferPos, buffer.size - bufferPos)
                    } catch (e: IOException) {
                        if (isActive) {
                            auditLogger.log(AuditEntry(action = "MTK_CAPTURE_READ_ERROR", module = "MtkMonitorCapture", result = "ERROR", details = e.message ?: "Read failed"))
                        }
                        break
                    }

                    if (bytesRead <= 0) break

                    bufferPos += bytesRead

                    var offset = 0
                    while (offset < bufferPos) {
                        val nextOffset = IcsPacketParser.findNextPacketOffset(buffer, offset)
                            ?: break

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
            }
        } catch (e: Exception) {
            auditLogger.log(AuditEntry(action = "MTK_CAPTURE_FATAL", module = "MtkMonitorCapture", result = "ERROR", details = e.message ?: "Unknown error"))
            close(e)
        }
    }

    suspend fun stopCapture() {
        captureJob?.cancel()
        captureJob = null
    }

    private suspend fun sendWpaCommand(command: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val socatPath = "/system/bin/socat"
                if (!File(socatPath).exists()) {
                    return@withContext false
                }

                val result = shellExecutor.executeAsRoot(
                    "echo '$command' | $socatPath - UNIX-CONNECT:$wpaSocket 2>/dev/null"
                )
                result.isSuccess
            } catch (e: Exception) {
                false
            }
        }
    }

    private suspend fun setIcsLevel(level: Int): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val result = shellExecutor.executeAsRoot(
                    "echo 'ioctl /dev/fw_log_ics 0x4004FC01 $level' | /system/bin/sh 2>/dev/null"
                )
                result.isSuccess
            } catch (e: Exception) {
                false
            }
        }
    }

    private suspend fun setIcsState(state: Int): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val result = shellExecutor.executeAsRoot(
                    "echo 'ioctl /dev/fw_log_ics 0x4004FC00 $state' | /system/bin/sh 2>/dev/null"
                )
                result.isSuccess
            } catch (e: Exception) {
                false
            }
        }
    }
}
