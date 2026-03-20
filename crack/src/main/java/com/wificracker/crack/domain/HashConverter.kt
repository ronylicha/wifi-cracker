package com.wificracker.crack.domain

import com.wificracker.core.root.ShellExecutor
import com.wificracker.core.root.ShellResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HashConverter @Inject constructor(private val shellExecutor: ShellExecutor) {
    companion object {
        private const val OUTPUT_DIR = "/data/local/tmp/wificracker/hashes"
    }

    fun convertCapToHc22000(capFilePath: String): ConversionResult {
        shellExecutor.executeAsRoot("mkdir -p $OUTPUT_DIR")
        val outputPath = "$OUTPUT_DIR/${System.currentTimeMillis()}.hc22000"
        val result = shellExecutor.executeAsRoot("hcxpcapngtool -o $outputPath $capFilePath")
        return if (result.isSuccess) {
            ConversionResult(success = true, outputPath = outputPath, format = "hc22000")
        } else {
            ConversionResult(success = false, error = result.stderr)
        }
    }

    fun detectCaptureType(filePath: String): String {
        val result = shellExecutor.executeAsRoot("file $filePath")
        return when {
            result.stdout.contains("pcap-ng") -> "pcapng"
            result.stdout.contains("pcap") -> "pcap"
            result.stdout.contains("hccapx") -> "hccapx"
            else -> "unknown"
        }
    }
}

data class ConversionResult(
    val success: Boolean,
    val outputPath: String = "",
    val format: String = "",
    val error: String = "",
)
