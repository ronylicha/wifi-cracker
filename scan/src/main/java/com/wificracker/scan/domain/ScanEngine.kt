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

        // Primary method: Android system scan (works everywhere, no monitor mode needed)
        startAndroidScan(interfaceName)
    }

    private suspend fun startAndroidScan(interfaceName: String) {
        coroutineScope {
            scanJob = launch {
                androidScanFlow()
                    .onEach { networks ->
                        _scanState.value = _scanState.value.copy(
                            networks = networks,
                            clients = emptyList(),
                            duration = System.currentTimeMillis() - _scanState.value.timestamp,
                        )
                    }
                    .catch { e ->
                        _scanState.value = _scanState.value.copy(status = ScanStatus.FAILED)
                        auditLogger.log(AuditEntry(action = "SCAN_ERROR", module = "scan", details = e.message ?: "Error"))
                    }
                    .collect {}
            }
        }
    }

    private fun androidScanFlow() = flow {
        while (true) {
            // Trigger a fresh system scan
            shellExecutor.executeAsRoot("cmd wifi start-scan")
            delay(3000)

            // Read scan results
            val result = shellExecutor.executeAsRoot("cmd wifi list-scan-results")
            if (result.isSuccess && result.stdout.isNotBlank()) {
                val networks = parseAndroidScanResults(result.stdout)
                if (networks.isNotEmpty()) {
                    emit(networks)
                }
            }

            delay(5000) // Scan every 8 seconds total
        }
    }.flowOn(Dispatchers.IO)

    private fun parseAndroidScanResults(output: String): List<Network> {
        val networks = mutableListOf<Network>()

        for (line in output.lines().drop(1)) { // Skip header line
            val trimmed = line.trim()
            if (trimmed.isBlank() || !trimmed.contains(":")) continue

            try {
                // Format: BSSID  Frequency  RSSI  Age  SSID  Flags
                val parts = trimmed.split("\\s+".toRegex(), limit = 6)
                if (parts.size < 5) continue

                val bssid = parts[0].trim()
                if (!bssid.matches(Regex("[0-9a-fA-F:]{17}"))) continue

                val frequency = parts[1].trim().toIntOrNull() ?: 0
                val rssi = parts[2].trim().toIntOrNull() ?: -100
                val ssid = if (parts.size >= 5) {
                    // SSID is after Age column, Flags is last
                    val afterAge = trimmed.substringAfter(parts[3]).trim()
                    // Split SSID and Flags (flags start with [)
                    val flagsStart = afterAge.indexOf("[")
                    if (flagsStart > 0) afterAge.substring(0, flagsStart).trim() else afterAge
                } else ""
                val flags = if (trimmed.contains("[")) trimmed.substringAfter("[").substringBeforeLast("]") else ""

                val encryption = parseEncryptionFromFlags(flags)
                val channel = frequencyToChannel(frequency)
                val wps = flags.contains("WPS")

                val vendor = macVendorLookup.resolve(bssid)

                networks.add(Network(
                    bssid = bssid,
                    ssid = ssid,
                    channel = channel,
                    frequency = frequency,
                    signalStrength = rssi,
                    encryption = encryption,
                    wps = wps,
                    cipher = if (flags.contains("CCMP-256")) "CCMP-256" else if (flags.contains("CCMP")) "CCMP" else if (flags.contains("TKIP")) "TKIP" else "",
                    authentication = if (flags.contains("SAE")) "SAE" else if (flags.contains("PSK")) "PSK" else if (flags.contains("EAP")) "EAP" else "",
                ))
            } catch (_: Exception) { }
        }

        // Deduplicate by BSSID, keep strongest signal
        return networks
            .groupBy { it.bssid }
            .map { (_, nets) -> nets.maxByOrNull { it.signalStrength } ?: nets.first() }
            .sortedByDescending { it.signalStrength }
    }

    private fun parseEncryptionFromFlags(flags: String): EncryptionType {
        return when {
            flags.contains("SAE") || flags.contains("WPA3") -> EncryptionType.WPA3
            flags.contains("RSN") || flags.contains("WPA2") -> EncryptionType.WPA2
            flags.contains("WPA") -> EncryptionType.WPA
            flags.contains("WEP") -> EncryptionType.WEP
            flags.isBlank() || flags.contains("ESS") && !flags.contains("WPA") && !flags.contains("RSN") -> EncryptionType.OPEN
            else -> EncryptionType.UNKNOWN
        }
    }

    private fun frequencyToChannel(freq: Int): Int {
        return when {
            freq in 2412..2484 -> (freq - 2407) / 5
            freq in 5170..5825 -> (freq - 5000) / 5
            freq in 5955..7115 -> (freq - 5950) / 5 // WiFi 6E
            else -> 0
        }
    }

    suspend fun stopScan() {
        val iface = currentInterface ?: return
        scanJob?.cancel()
        scanJob = null

        shellExecutor.executeAsRoot("/data/local/tmp/ics_enable 0 2>/dev/null")
        commandRunner.stopScan(iface)

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
