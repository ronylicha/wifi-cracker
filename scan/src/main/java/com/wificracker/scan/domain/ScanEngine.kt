package com.wificracker.scan.domain

import com.wificracker.core.logging.AuditEntry
import com.wificracker.core.logging.AuditLogger
import com.wificracker.core.root.ShellExecutor
import com.wificracker.core.util.MacVendorLookup
import com.wificracker.core.wifi.ChipsetMonitorHelper
import com.wificracker.core.wifi.MonitorModeManager
import com.wificracker.core.wifi.WifiChipVendor
import com.wificracker.core.wifi.monitor.Ieee80211Frame
import com.wificracker.core.wifi.monitor.MtkMonitorCapture
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
    private var icsJob: Job? = null
    private var currentInterface: String? = null
    private var mtkCapture: MtkMonitorCapture? = null

    suspend fun startScan(interfaceName: String) {
        if (_scanState.value.status == ScanStatus.SCANNING) return

        currentInterface = interfaceName
        _scanState.value = ScanResult(
            interfaceName = interfaceName,
            status = ScanStatus.SCANNING,
            timestamp = System.currentTimeMillis(),
        )

        auditLogger.log(AuditEntry(action = "SCAN_START", module = "scan", target = interfaceName))

        // Start Android system scan (primary — always works)
        startAndroidScan()

        // Also start ICS capture if MTK patched driver is available (passive monitor)
        val chipInfo = chipsetMonitorHelper.detectChipVendor()
        if (chipInfo.vendor == WifiChipVendor.MEDIATEK && chipInfo.patchInstalled) {
            startIcsCapture()
        }
    }

    private suspend fun startAndroidScan() {
        coroutineScope {
            scanJob = launch {
                androidScanFlow()
                    .onEach { networks ->
                        _scanState.value = _scanState.value.copy(
                            networks = mergeNetworks(_scanState.value.networks, networks),
                            duration = System.currentTimeMillis() - _scanState.value.timestamp,
                        )
                    }
                    .catch { e ->
                        auditLogger.log(AuditEntry(action = "SCAN_ERROR", module = "scan", details = e.message ?: "Error"))
                    }
                    .collect {}
            }
        }
    }

    private suspend fun startIcsCapture() {
        val capture = MtkMonitorCapture(shellExecutor, auditLogger)
        mtkCapture = capture

        val enabled = capture.enableCapture()
        if (!enabled) {
            auditLogger.log(AuditEntry(action = "ICS_ENABLE_FAILED", module = "scan"))
            return
        }

        coroutineScope {
            icsJob = launch(Dispatchers.IO) {
                capture.startCapture { frame ->
                    // Convert ICS frames to Network objects and merge
                    val network = frameToNetwork(frame)
                    if (network != null) {
                        _scanState.value = _scanState.value.copy(
                            networks = mergeNetworks(_scanState.value.networks, listOf(network)),
                        )
                    }
                }.collect { }
            }
        }
    }

    private fun frameToNetwork(frame: Ieee80211Frame): Network? {
        // addr3 = BSSID for management frames (beacon, probe resp)
        val bssid = frame.addr3
        if (bssid.isBlank() || bssid == "00:00:00:00:00:00" || bssid == "FF:FF:FF:FF:FF:FF") return null
        if (!frame.isBeacon && !frame.isProbeResp) return null
        return Network(
            bssid = bssid,
            ssid = frame.ssid ?: "",
            channel = frame.channel ?: 0,
            signalStrength = frame.rssi ?: -50,
            encryption = EncryptionType.UNKNOWN,
        )
    }

    private fun androidScanFlow() = flow {
        while (true) {
            shellExecutor.executeAsRoot("cmd wifi start-scan")
            delay(3000)

            val result = shellExecutor.executeAsRoot("cmd wifi list-scan-results")
            if (result.isSuccess && result.stdout.isNotBlank()) {
                val networks = parseAndroidScanResults(result.stdout)
                if (networks.isNotEmpty()) emit(networks)
            }

            delay(5000)
        }
    }.flowOn(Dispatchers.IO)

    private fun parseAndroidScanResults(output: String): List<Network> {
        val networks = mutableListOf<Network>()

        for (line in output.lines().drop(1)) {
            val trimmed = line.trim()
            if (trimmed.isBlank() || !trimmed.contains(":")) continue

            try {
                val parts = trimmed.split("\\s+".toRegex(), limit = 6)
                if (parts.size < 5) continue

                val bssid = parts[0].trim()
                if (!bssid.matches(Regex("[0-9a-fA-F:]{17}"))) continue

                val frequency = parts[1].trim().toIntOrNull() ?: 0
                val rssi = parts[2].trim().toIntOrNull() ?: -100

                val afterAge = trimmed.substringAfter(parts[3]).trim()
                val flagsStart = afterAge.indexOf("[")
                val ssid = if (flagsStart > 0) afterAge.substring(0, flagsStart).trim() else afterAge
                val flags = if (trimmed.contains("[")) trimmed.substringAfter("[").substringBeforeLast("]") else ""

                val encryption = parseEncryptionFromFlags(flags)
                val channel = frequencyToChannel(frequency)
                val wps = flags.contains("WPS")

                networks.add(Network(
                    bssid = bssid,
                    ssid = ssid,
                    channel = channel,
                    frequency = frequency,
                    signalStrength = rssi,
                    encryption = encryption,
                    wps = wps,
                    cipher = when {
                        flags.contains("CCMP-256") -> "CCMP-256"
                        flags.contains("CCMP") -> "CCMP"
                        flags.contains("TKIP") -> "TKIP"
                        else -> ""
                    },
                    authentication = when {
                        flags.contains("SAE") -> "SAE"
                        flags.contains("PSK") -> "PSK"
                        flags.contains("EAP") -> "EAP"
                        else -> ""
                    },
                ))
            } catch (_: Exception) { }
        }

        return networks
            .groupBy { it.bssid }
            .map { (_, nets) -> nets.maxByOrNull { it.signalStrength } ?: nets.first() }
            .sortedByDescending { it.signalStrength }
    }

    private fun parseEncryptionFromFlags(flags: String): EncryptionType = when {
        flags.contains("SAE") -> EncryptionType.WPA3
        flags.contains("RSN") || flags.contains("WPA2") -> EncryptionType.WPA2
        flags.contains("WPA") -> EncryptionType.WPA
        flags.contains("WEP") -> EncryptionType.WEP
        !flags.contains("WPA") && !flags.contains("RSN") && !flags.contains("WEP") -> EncryptionType.OPEN
        else -> EncryptionType.UNKNOWN
    }

    private fun frequencyToChannel(freq: Int): Int = when {
        freq in 2412..2484 -> (freq - 2407) / 5
        freq in 5170..5825 -> (freq - 5000) / 5
        freq in 5955..7115 -> (freq - 5950) / 5
        else -> 0
    }

    private fun mergeNetworks(existing: List<Network>, incoming: List<Network>): List<Network> {
        val merged = existing.toMutableList()
        for (net in incoming) {
            val idx = merged.indexOfFirst { it.bssid == net.bssid }
            if (idx >= 0) {
                val old = merged[idx]
                merged[idx] = old.copy(
                    ssid = if (net.ssid.isNotBlank()) net.ssid else old.ssid,
                    signalStrength = if (net.signalStrength > old.signalStrength) net.signalStrength else old.signalStrength,
                    encryption = if (net.encryption != EncryptionType.UNKNOWN) net.encryption else old.encryption,
                    channel = if (net.channel > 0) net.channel else old.channel,
                    lastSeen = System.currentTimeMillis(),
                )
            } else {
                merged.add(net)
            }
        }
        return merged.sortedByDescending { it.signalStrength }
    }

    suspend fun stopScan() {
        val iface = currentInterface ?: return
        scanJob?.cancel()
        scanJob = null
        icsJob?.cancel()
        icsJob = null

        mtkCapture?.disableCapture()
        mtkCapture = null
        commandRunner.stopScan(iface)

        _scanState.value = _scanState.value.copy(
            status = ScanStatus.COMPLETED,
            duration = System.currentTimeMillis() - _scanState.value.timestamp,
        )

        auditLogger.log(AuditEntry(action = "SCAN_STOP", module = "scan", target = iface, result = "${_scanState.value.networks.size} networks found"))
        currentInterface = null
    }
}
