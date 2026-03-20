package com.wificracker.scan.domain

import com.wificracker.core.logging.AuditEntry
import com.wificracker.core.logging.AuditLogger
import com.wificracker.core.root.ShellExecutor
import com.wificracker.core.util.MacVendorLookup
import com.wificracker.core.wifi.ChipsetMonitorHelper
import com.wificracker.core.wifi.MonitorModeManager
import com.wificracker.core.wifi.WifiChipVendor
import com.wificracker.scan.data.WifiCommandRunner
import com.wificracker.scan.model.Client
import com.wificracker.scan.model.EncryptionType
import com.wificracker.scan.model.Network
import com.wificracker.scan.model.ScanResult
import com.wificracker.scan.model.ScanStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScanEngine @Inject constructor(
    private val commandRunner: WifiCommandRunner,
    private val monitorModeManager: MonitorModeManager,
    private val chipsetMonitorHelper: ChipsetMonitorHelper,
    private val shellExecutor: ShellExecutor,
    private val macVendorLookup: MacVendorLookup,
    private val auditLogger: AuditLogger,
) {

    private val _scanState = MutableStateFlow(ScanResult())
    val scanState: StateFlow<ScanResult> = _scanState.asStateFlow()

    private var scanJob: Job? = null
    private var currentInterface: String? = null

    suspend fun startScan(interfaceName: String) {
        if (_scanState.value.status == ScanStatus.SCANNING) return

        currentInterface = interfaceName
        _scanState.value = ScanResult(
            interfaceName = interfaceName,
            status = ScanStatus.SCANNING,
            timestamp = System.currentTimeMillis(),
        )

        auditLogger.log(AuditEntry(action = "SCAN_START", module = "scan", target = interfaceName))

        // Detect chipset and choose scan method
        val chipInfo = chipsetMonitorHelper.detectChipVendor()

        if (chipInfo.vendor == WifiChipVendor.MEDIATEK && chipInfo.patchInstalled) {
            // MTK patched driver: use ICS capture
            startMtkIcsScan(interfaceName)
        } else {
            // Standard: enable monitor mode + airodump-ng
            startAirodumpScan(interfaceName)
        }
    }

    private suspend fun startMtkIcsScan(interfaceName: String) {
        // Enable MTK sniffer via wpa_driver + ics_enable
        val snifferResult = shellExecutor.executeAsRoot("/data/local/tmp/wpa_driver \"SNIFFER 2 0 0 0 0 0 0 0 0 0\"")
        val icsResult = shellExecutor.executeAsRoot("/data/local/tmp/ics_enable 1")

        if (!icsResult.isSuccess) {
            _scanState.value = _scanState.value.copy(status = ScanStatus.FAILED)
            auditLogger.log(AuditEntry(action = "SCAN_ERROR", module = "scan", target = "MTK ICS", details = "Failed to enable ICS: ${icsResult.stderr}"))
            return
        }

        coroutineScope {
            scanJob = launch {
                readIcsPackets()
                    .onEach { networks ->
                        val enriched = networks.map { net ->
                            net.copy(clients = net.clients.map { enrichClient(it) })
                        }
                        _scanState.value = _scanState.value.copy(
                            networks = mergeNetworks(_scanState.value.networks, enriched),
                            duration = System.currentTimeMillis() - _scanState.value.timestamp,
                        )
                    }
                    .catch { e ->
                        _scanState.value = _scanState.value.copy(status = ScanStatus.FAILED)
                        auditLogger.log(AuditEntry(action = "SCAN_ERROR", module = "scan", details = e.message ?: "ICS read error"))
                    }
                    .collect {}
            }
        }
    }

    private fun readIcsPackets() = flow {
        // Read packets from /dev/fw_log_ics and parse 802.11 frames
        while (true) {
            val result = shellExecutor.executeAsRoot(
                "timeout 2 cat /dev/fw_log_ics 2>/dev/null | xxd -p | head -500"
            )

            if (result.isSuccess && result.stdout.isNotBlank()) {
                val networks = parseIcsFrames(result.stdout)
                if (networks.isNotEmpty()) {
                    emit(networks)
                }
            }

            delay(2000)
        }
    }.flowOn(Dispatchers.IO)

    private fun parseIcsFrames(hexDump: String): List<Network> {
        // Parse the raw ICS capture format
        // Magic: 0x44D9C99A, then MTK RX descriptor (120 bytes), then 802.11 frame
        val networks = mutableListOf<Network>()
        val hex = hexDump.replace("\n", "").replace(" ", "")

        // Find beacon frames (type 0x80) in the hex data
        // 802.11 beacon: FC=0x0080, contains SSID and BSSID
        var offset = 0
        while (offset < hex.length - 100) {
            // Look for ICS magic: 9ac9d944 (little-endian of 0x44D9C99A)
            val magicPos = hex.indexOf("9ac9d944", offset)
            if (magicPos < 0) break

            // Skip header (16 bytes = 32 hex chars) + MTK RX descriptor (120 bytes = 240 hex chars)
            val frameStart = magicPos + 32 + 240
            if (frameStart + 60 >= hex.length) break

            try {
                // Read frame control (2 bytes)
                val fcHex = hex.substring(frameStart, frameStart + 4)
                val fc = fcHex.substring(2, 4).toInt(16) shl 8 or fcHex.substring(0, 2).toInt(16)
                val frameType = (fc shr 2) and 0x3
                val frameSubtype = (fc shr 4) and 0xF

                // Beacon frame: type=0 (management), subtype=8
                if (frameType == 0 && frameSubtype == 8) {
                    // BSSID is at offset 16 from frame start (bytes 16-21)
                    val bssidStart = frameStart + 32 // 16 bytes = 32 hex chars
                    if (bssidStart + 12 <= hex.length) {
                        val bssidHex = hex.substring(bssidStart, bssidStart + 12)
                        val bssid = bssidHex.chunked(2).joinToString(":") { it.uppercase() }

                        // Try to extract SSID from tagged parameters
                        // Tagged params start after fixed params (24 bytes of header + 12 bytes fixed)
                        val tagStart = frameStart + 72 // 36 bytes = 72 hex chars
                        var ssid = ""
                        if (tagStart + 4 < hex.length) {
                            val tagId = hex.substring(tagStart, tagStart + 2).toInt(16)
                            val tagLen = hex.substring(tagStart + 2, tagStart + 4).toInt(16)
                            if (tagId == 0 && tagLen > 0 && tagStart + 4 + tagLen * 2 <= hex.length) {
                                val ssidHex = hex.substring(tagStart + 4, tagStart + 4 + tagLen * 2)
                                ssid = ssidHex.chunked(2).map { it.toInt(16).toChar() }.joinToString("")
                            }
                        }

                        if (bssid.length == 17 && !bssid.startsWith("00:00:00")) {
                            networks.add(Network(
                                bssid = bssid,
                                ssid = ssid,
                                channel = 0,
                                signalStrength = -50,
                                encryption = EncryptionType.UNKNOWN,
                            ))
                        }
                    }
                }

                // Probe response: type=0, subtype=5
                if (frameType == 0 && frameSubtype == 5) {
                    val bssidStart = frameStart + 32
                    if (bssidStart + 12 <= hex.length) {
                        val bssidHex = hex.substring(bssidStart, bssidStart + 12)
                        val bssid = bssidHex.chunked(2).joinToString(":") { it.uppercase() }
                        if (bssid.length == 17 && !bssid.startsWith("00:00:00")) {
                            networks.add(Network(bssid = bssid, ssid = "", channel = 0, signalStrength = -60, encryption = EncryptionType.UNKNOWN))
                        }
                    }
                }
            } catch (_: Exception) { }

            offset = magicPos + 32
        }

        return networks.distinctBy { it.bssid }
    }

    private fun mergeNetworks(existing: List<Network>, incoming: List<Network>): List<Network> {
        val merged = existing.toMutableList()
        for (net in incoming) {
            val idx = merged.indexOfFirst { it.bssid == net.bssid }
            if (idx >= 0) {
                // Update existing with new info if better
                val old = merged[idx]
                merged[idx] = old.copy(
                    ssid = if (net.ssid.isNotBlank()) net.ssid else old.ssid,
                    lastSeen = System.currentTimeMillis(),
                )
            } else {
                merged.add(net)
            }
        }
        return merged
    }

    private suspend fun startAirodumpScan(interfaceName: String) {
        // Standard airodump-ng scan (for USB adapters or other chipsets)
        val monitorResult = monitorModeManager.enableMonitorMode(interfaceName)
        if (!monitorResult.isSuccess) {
            _scanState.value = _scanState.value.copy(status = ScanStatus.FAILED)
            return
        }

        coroutineScope {
            scanJob = launch {
                commandRunner.startScan(interfaceName)
                    .onEach { update ->
                        val enrichedNetworks = update.networks.map { network ->
                            network.copy(
                                clients = update.clients
                                    .filter { it.bssid == network.bssid }
                                    .map { enrichClient(it) },
                            )
                        }
                        _scanState.value = _scanState.value.copy(
                            networks = enrichedNetworks,
                            clients = update.clients.map { enrichClient(it) },
                            duration = System.currentTimeMillis() - _scanState.value.timestamp,
                        )
                    }
                    .catch { e ->
                        _scanState.value = _scanState.value.copy(status = ScanStatus.FAILED)
                        auditLogger.log(AuditEntry(action = "SCAN_ERROR", module = "scan", details = e.message ?: "Unknown error"))
                    }
                    .collect {}
            }
        }
    }

    suspend fun stopScan() {
        val iface = currentInterface ?: return
        scanJob?.cancel()
        scanJob = null

        // Stop MTK ICS if active
        shellExecutor.executeAsRoot("/data/local/tmp/ics_enable 0 2>/dev/null")

        // Stop airodump-ng if active
        commandRunner.stopScan(iface)
        monitorModeManager.disableMonitorMode(iface)

        _scanState.value = _scanState.value.copy(
            status = ScanStatus.COMPLETED,
            duration = System.currentTimeMillis() - _scanState.value.timestamp,
        )

        auditLogger.log(AuditEntry(action = "SCAN_STOP", module = "scan", target = iface, result = "${_scanState.value.networks.size} networks found"))
        currentInterface = null
    }

    private fun enrichClient(client: Client): Client {
        return client.copy(vendor = macVendorLookup.resolve(client.macAddress))
    }
}
