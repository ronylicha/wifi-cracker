package com.wificracker.scan.data

import com.wificracker.core.root.ShellExecutor
import com.wificracker.core.root.ShellResult
import com.wificracker.scan.model.Client
import com.wificracker.scan.model.EncryptionType
import com.wificracker.scan.model.Network
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WifiCommandRunner @Inject constructor(
    private val shellExecutor: ShellExecutor,
) {

    companion object {
        private const val SCAN_OUTPUT_DIR = "/data/local/tmp/wificracker/scans"
        private const val POLL_INTERVAL_MS = 2000L
    }

    fun startScan(interfaceName: String): Flow<ScanUpdate> = flow {
        shellExecutor.executeAsRoot("mkdir -p $SCAN_OUTPUT_DIR")
        val outputPrefix = "$SCAN_OUTPUT_DIR/scan_${System.currentTimeMillis()}"

        // Start airodump-ng in background, writing CSV output
        shellExecutor.executeAsRoot(
            "airodump-ng $interfaceName --write $outputPrefix --output-format csv --background 1"
        )

        // Poll the CSV file for updates
        var running = true
        while (running) {
            delay(POLL_INTERVAL_MS)
            val csvFile = "${outputPrefix}-01.csv"
            val result = shellExecutor.executeAsRoot("cat $csvFile")
            if (result.isSuccess && result.stdout.isNotBlank()) {
                val update = parseCsvOutput(result.stdout)
                emit(update)
            }
        }
    }.flowOn(Dispatchers.IO)

    fun stopScan(interfaceName: String): ShellResult {
        return shellExecutor.executeAsRoot("pkill -f 'airodump-ng $interfaceName'")
    }

    fun parseCsvOutput(csvContent: String): ScanUpdate {
        val lines = csvContent.lines()
        val networks = mutableListOf<Network>()
        val clients = mutableListOf<Client>()

        var section = Section.NONE

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) continue

            when {
                trimmed.startsWith("BSSID") && section == Section.NONE -> {
                    section = Section.NETWORKS
                    continue
                }
                trimmed.startsWith("Station MAC") -> {
                    section = Section.CLIENTS
                    continue
                }
            }

            when (section) {
                Section.NETWORKS -> parseNetworkLine(trimmed)?.let { networks.add(it) }
                Section.CLIENTS -> parseClientLine(trimmed)?.let { clients.add(it) }
                Section.NONE -> {}
            }
        }

        return ScanUpdate(networks = networks, clients = clients)
    }

    private fun parseNetworkLine(line: String): Network? {
        val parts = line.split(",").map { it.trim() }
        if (parts.size < 14) return null

        val bssid = parts[0]
        if (!bssid.matches(Regex("[0-9A-Fa-f:]{17}"))) return null

        val channel = parts[3].toIntOrNull() ?: 0
        val signal = parts[8].toIntOrNull() ?: -100
        val encryption = parseEncryption(parts[5], parts[6])
        val ssid = parts[13]

        return Network(
            bssid = bssid,
            ssid = ssid,
            channel = channel,
            signalStrength = signal,
            encryption = encryption,
            cipher = parts[6],
            authentication = parts[7],
        )
    }

    private fun parseClientLine(line: String): Client? {
        val parts = line.split(",").map { it.trim() }
        if (parts.size < 6) return null

        val mac = parts[0]
        if (!mac.matches(Regex("[0-9A-Fa-f:]{17}"))) return null

        val bssid = parts[5].takeIf { it.matches(Regex("[0-9A-Fa-f:]{17}")) } ?: ""
        val signal = parts[3].toIntOrNull() ?: -100
        val packets = parts[4].toIntOrNull() ?: 0
        val probes = if (parts.size > 6) parts[6].split(" ").filter { it.isNotBlank() } else emptyList()

        return Client(
            macAddress = mac,
            bssid = bssid,
            signalStrength = signal,
            packets = packets,
            probeRequests = probes,
        )
    }

    private fun parseEncryption(encField: String, cipherField: String): EncryptionType {
        return when {
            encField.contains("WPA3", ignoreCase = true) -> EncryptionType.WPA3
            encField.contains("WPA2", ignoreCase = true) -> EncryptionType.WPA2
            encField.contains("WPA", ignoreCase = true) -> EncryptionType.WPA
            encField.contains("WEP", ignoreCase = true) -> EncryptionType.WEP
            encField.contains("OPN", ignoreCase = true) -> EncryptionType.OPEN
            else -> EncryptionType.UNKNOWN
        }
    }

    private enum class Section { NONE, NETWORKS, CLIENTS }
}

data class ScanUpdate(
    val networks: List<Network>,
    val clients: List<Client>,
)
