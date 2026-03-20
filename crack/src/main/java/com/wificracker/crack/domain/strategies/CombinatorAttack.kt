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
class CombinatorAttack @Inject constructor(
    private val shellExecutor: ShellExecutor,
    private val binaryInstaller: BinaryInstaller,
) : CrackStrategyImpl {
    override fun execute(job: CrackJob): Flow<CrackProgress> = flow {
        val binary = binaryInstaller.getBinaryPath("hashcat")
        emit(CrackProgress(jobId = job.id, status = CrackStatus.RUNNING, message = "Starting combinator attack..."))
        val cmd = "$binary -m 22000 -a 1 ${job.hashPath} ${job.wordlistPath} ${job.secondWordlistPath}"
        val result = shellExecutor.executeAsRoot(cmd, timeoutSeconds = 7200)
        val cracked = result.stdout.contains("Cracked")
        val password = if (cracked) result.stdout.lines().firstOrNull { it.contains(":") }?.substringAfterLast(":")?.trim() ?: "" else ""
        emit(CrackProgress(jobId = job.id, status = CrackStatus.COMPLETED, progress = 1f, currentKey = password, message = if (cracked) "Found!" else "Not found"))
    }.flowOn(Dispatchers.IO)

    override fun stop() { shellExecutor.executeAsRoot("pkill hashcat") }
}
