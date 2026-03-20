package com.wificracker.crack.domain.strategies

import com.wificracker.core.root.BinaryInstaller
import com.wificracker.core.root.ShellExecutor
import com.wificracker.crack.model.CrackJob
import com.wificracker.crack.model.CrackProgress
import com.wificracker.crack.model.CrackStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DictionaryAttack @Inject constructor(
    private val shellExecutor: ShellExecutor,
    private val binaryInstaller: BinaryInstaller,
) : CrackStrategyImpl {
    override fun execute(job: CrackJob): Flow<CrackProgress> = flow {
        val binary = binaryInstaller.getBinaryPath("aircrack-ng")
        val hashFile = job.hashPath.ifBlank { job.capturePath }
        emit(CrackProgress(jobId = job.id, status = CrackStatus.RUNNING, message = "Starting dictionary attack..."))
        val result = shellExecutor.executeAsRoot("$binary -w ${job.wordlistPath} -b ${job.targetBssid} $hashFile", timeoutSeconds = 3600)
        if (result.isSuccess && result.stdout.contains("KEY FOUND!")) {
            val password = Regex("KEY FOUND! \\[ (.+?) ]").find(result.stdout)?.groupValues?.get(1) ?: ""
            emit(CrackProgress(jobId = job.id, status = CrackStatus.COMPLETED, progress = 1f, currentKey = password, message = "Password found: $password"))
        } else {
            emit(CrackProgress(jobId = job.id, status = CrackStatus.COMPLETED, progress = 1f, message = "Password not found in wordlist"))
        }
    }.flowOn(Dispatchers.IO)

    override fun stop() { shellExecutor.executeAsRoot("pkill -f aircrack-ng") }
}
