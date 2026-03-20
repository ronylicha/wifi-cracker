package com.wificracker.crack.domain.strategies

import com.wificracker.core.root.BinaryInstaller
import com.wificracker.core.root.ShellExecutor
import com.wificracker.crack.model.CrackJob
import com.wificracker.crack.model.CrackProgress
import com.wificracker.crack.model.CrackStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BruteForceAttack @Inject constructor(
    private val shellExecutor: ShellExecutor,
    private val binaryInstaller: BinaryInstaller,
) : CrackStrategyImpl {
    override fun execute(job: CrackJob): Flow<CrackProgress> = flow {
        val binary = binaryInstaller.getBinaryPath("hashcat")
        val charsetFlag = if (job.charset.isNotBlank()) "-1 ${job.charset}" else ""
        emit(CrackProgress(jobId = job.id, status = CrackStatus.RUNNING, message = "Starting brute-force (len ${job.minLength}-${job.maxLength})..."))
        val mask = "?1".repeat(job.maxLength)
        val cmd = "$binary -m 22000 -a 3 $charsetFlag --increment --increment-min=${job.minLength} --increment-max=${job.maxLength} ${job.hashPath} $mask"
        val result = shellExecutor.executeAsRoot(cmd, timeoutSeconds = 7200)
        val cracked = result.stdout.contains("Cracked") || result.stdout.contains("Status...........: Cracked")
        if (cracked) {
            val password = result.stdout.lines().firstOrNull { it.contains(":") && !it.startsWith("Status") }?.substringAfterLast(":")?.trim() ?: ""
            emit(CrackProgress(jobId = job.id, status = CrackStatus.COMPLETED, progress = 1f, currentKey = password))
        } else {
            emit(CrackProgress(jobId = job.id, status = CrackStatus.COMPLETED, progress = 1f, message = "Password not found"))
        }
    }.flowOn(Dispatchers.IO)

    override fun stop() { shellExecutor.executeAsRoot("pkill hashcat") }
}
