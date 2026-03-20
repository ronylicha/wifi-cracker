package com.wificracker.crack.domain

import com.wificracker.core.logging.AuditEntry
import com.wificracker.core.logging.AuditLogger
import com.wificracker.crack.domain.strategies.*
import com.wificracker.crack.model.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CrackOrchestrator @Inject constructor(
    private val dictionaryAttack: DictionaryAttack,
    private val bruteForceAttack: BruteForceAttack,
    private val ruleBasedAttack: RuleBasedAttack,
    private val combinatorAttack: CombinatorAttack,
    private val hashConverter: HashConverter,
    private val auditLogger: AuditLogger,
) {
    private val _progress = MutableStateFlow(CrackProgress())
    val progress: StateFlow<CrackProgress> = _progress.asStateFlow()
    private val _result = MutableStateFlow<CrackResult?>(null)
    val result: StateFlow<CrackResult?> = _result.asStateFlow()
    private var crackJob: Job? = null

    private fun getStrategy(strategy: CrackStrategy): CrackStrategyImpl = when (strategy) {
        CrackStrategy.DICTIONARY -> dictionaryAttack
        CrackStrategy.BRUTE_FORCE -> bruteForceAttack
        CrackStrategy.RULE_BASED -> ruleBasedAttack
        CrackStrategy.COMBINATOR -> combinatorAttack
    }

    suspend fun startCrack(job: CrackJob) {
        var activeJob = job
        if (job.hashPath.isBlank()) {
            _progress.value = CrackProgress(jobId = job.id, status = CrackStatus.CONVERTING, message = "Converting capture file...")
            val conversion = hashConverter.convertCapToHc22000(job.capturePath)
            if (!conversion.success) {
                _progress.value = CrackProgress(jobId = job.id, status = CrackStatus.FAILED, message = "Conversion failed: ${conversion.error}")
                return
            }
            activeJob = job.copy(hashPath = conversion.outputPath)
        }

        auditLogger.log(AuditEntry(action = "CRACK_START", module = "crack", target = "${activeJob.strategy.name}:${activeJob.targetBssid}"))
        val strategy = getStrategy(activeJob.strategy)
        val startTime = System.currentTimeMillis()

        coroutineScope {
            crackJob = launch {
                strategy.execute(activeJob)
                    .catch { e -> _progress.value = CrackProgress(jobId = activeJob.id, status = CrackStatus.FAILED, message = e.message ?: "Error") }
                    .collect { progress ->
                        _progress.value = progress
                        if (progress.status == CrackStatus.COMPLETED) {
                            _result.value = CrackResult(jobId = activeJob.id, success = progress.currentKey.isNotBlank(), password = progress.currentKey, duration = System.currentTimeMillis() - startTime)
                            auditLogger.log(AuditEntry(action = "CRACK_DONE", module = "crack", target = activeJob.targetBssid, result = if (progress.currentKey.isNotBlank()) "FOUND" else "NOT_FOUND"))
                        }
                    }
            }
        }
    }

    suspend fun stopCrack() {
        crackJob?.cancel()
        val current = _progress.value
        getStrategy(CrackStrategy.DICTIONARY).stop()
        _progress.value = current.copy(status = CrackStatus.CANCELLED)
        auditLogger.log(AuditEntry(action = "CRACK_STOP", module = "crack"))
    }
}
