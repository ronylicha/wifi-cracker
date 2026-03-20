package com.wificracker.scan.domain

import com.wificracker.core.root.ShellExecutor
import com.wificracker.core.util.FileManager
import com.wificracker.scan.model.ScanResult
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PcapExporter @Inject constructor(
    private val shellExecutor: ShellExecutor,
    private val fileManager: FileManager,
) {

    companion object {
        private const val EXPORT_DIR = "/data/local/tmp/wificracker/exports"
    }

    fun startCapture(interfaceName: String, outputFile: String? = null): String {
        fileManager.ensureDirectory(EXPORT_DIR)
        val filename = outputFile ?: "$EXPORT_DIR/capture_${System.currentTimeMillis()}.pcap"

        // Use tcpdump if available, fallback to airodump-ng
        val tcpdumpCheck = shellExecutor.executeAsRoot("which tcpdump 2>/dev/null || test -x /data/local/tmp/wificracker/tcpdump && echo found")
        val tcpdumpBin = if (tcpdumpCheck.isSuccess) {
            shellExecutor.executeAsRoot("which tcpdump 2>/dev/null").stdout.trim().ifBlank { "/data/local/tmp/wificracker/tcpdump" }
        } else ""

        if (tcpdumpBin.isNotBlank()) {
            shellExecutor.executeAsRoot("$tcpdumpBin -i $interfaceName -w $filename &")
        } else {
            // Use airodump-ng write
            val prefix = filename.removeSuffix(".pcap")
            shellExecutor.executeAsRoot("/data/local/tmp/wificracker/airodump-ng $interfaceName --write $prefix --output-format pcap &")
        }

        return filename
    }

    fun stopCapture() {
        shellExecutor.executeAsRoot("pkill tcpdump 2>/dev/null; pkill airodump-ng 2>/dev/null")
    }

    fun exportScanResultJson(scanResult: ScanResult): String {
        fileManager.ensureDirectory(EXPORT_DIR)
        val filename = "$EXPORT_DIR/scan_${System.currentTimeMillis()}.json"

        val networkData = scanResult.networks.map { network ->
            mapOf(
                "bssid" to network.bssid,
                "ssid" to network.ssid,
                "channel" to network.channel.toString(),
                "signal" to network.signalStrength.toString(),
                "encryption" to network.encryption.label,
                "clients" to network.clients.size.toString(),
            )
        }

        val content = buildString {
            appendLine("{")
            appendLine("  \"timestamp\": ${scanResult.timestamp},")
            appendLine("  \"interface\": \"${scanResult.interfaceName}\",")
            appendLine("  \"duration\": ${scanResult.duration},")
            appendLine("  \"networks_count\": ${scanResult.networks.size},")
            appendLine("  \"clients_count\": ${scanResult.clients.size},")
            appendLine("  \"networks\": [")
            networkData.forEachIndexed { i, net ->
                val comma = if (i < networkData.size - 1) "," else ""
                appendLine("    {\"bssid\":\"${net["bssid"]}\",\"ssid\":\"${net["ssid"]}\",\"channel\":${net["channel"]},\"signal\":${net["signal"]},\"encryption\":\"${net["encryption"]}\",\"clients\":${net["clients"]}}$comma")
            }
            appendLine("  ]")
            appendLine("}")
        }

        File(filename).writeText(content)
        return filename
    }

    fun listCaptures(): List<File> {
        return fileManager.listFiles(EXPORT_DIR, "pcap") + fileManager.listFiles(EXPORT_DIR, "json")
    }
}
