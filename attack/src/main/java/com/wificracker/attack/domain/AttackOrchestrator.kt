package com.wificracker.attack.domain

import com.wificracker.attack.domain.attacks.*
import com.wificracker.attack.model.*
import com.wificracker.core.logging.AuditEntry
import com.wificracker.core.logging.AuditLogger
import com.wificracker.core.service.SessionCollector
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
class AttackOrchestrator @Inject constructor(
    private val deauthAttack: DeauthAttack,
    private val handshakeCapture: HandshakeCapture,
    private val pmkidCapture: PmkidCapture,
    private val evilTwinAttack: EvilTwinAttack,
    private val probeSniff: ProbeSniff,
    private val prerequisiteCheck: PrerequisiteCheck,
    private val auditLogger: AuditLogger,
    private val sessionCollector: SessionCollector,
) {
    private val _attackState = MutableStateFlow(Attack(type = AttackType.DEAUTH, targetBssid = "", interfaceName = ""))
    val attackState: StateFlow<Attack> = _attackState.asStateFlow()

    private val _consoleOutput = MutableStateFlow<List<String>>(emptyList())
    val consoleOutput: StateFlow<List<String>> = _consoleOutput.asStateFlow()

    private var attackJob: Job? = null

    fun getAttackImpl(type: AttackType): WifiAttack = when (type) {
        AttackType.DEAUTH -> deauthAttack
        AttackType.HANDSHAKE_CAPTURE -> handshakeCapture
        AttackType.PMKID_CAPTURE -> pmkidCapture
        AttackType.EVIL_TWIN -> evilTwinAttack
        AttackType.PROBE_SNIFF -> probeSniff
    }

    suspend fun launchAttack(attack: Attack) {
        val prereq = prerequisiteCheck.check(attack.type, attack.interfaceName)
        if (!prereq.satisfied) {
            _consoleOutput.value = _consoleOutput.value + "[!] Prerequisites not met: ${prereq.missingPrerequisites.joinToString(", ")}"
            _attackState.value = attack.copy(status = AttackStatus.FAILED)
            return
        }

        _attackState.value = attack.copy(status = AttackStatus.RUNNING, startTime = System.currentTimeMillis())
        _consoleOutput.value = emptyList()

        auditLogger.log(AuditEntry(action = "ATTACK_START", module = "attack", target = "${attack.type.name}:${attack.targetBssid}"))

        val impl = getAttackImpl(attack.type)
        coroutineScope {
            attackJob = launch {
                impl.execute(attack)
                    .catch { e ->
                        _consoleOutput.value = _consoleOutput.value + "[!] Error: ${e.message}"
                        _attackState.value = _attackState.value.copy(status = AttackStatus.FAILED)
                    }
                    .collect { line ->
                        _consoleOutput.value = _consoleOutput.value + line
                    }
                val completedAttack = _attackState.value.copy(status = AttackStatus.COMPLETED, endTime = System.currentTimeMillis())
                _attackState.value = completedAttack
                sessionCollector.recordAttack(
                    SessionCollector.AttackRecord(
                        type = completedAttack.type.name,
                        targetBssid = completedAttack.targetBssid,
                        targetSsid = completedAttack.targetSsid,
                        status = completedAttack.status.name,
                        startTime = completedAttack.startTime,
                        endTime = completedAttack.endTime,
                    ),
                )
            }
        }
    }

    suspend fun stopAttack() {
        val current = _attackState.value
        attackJob?.cancel()
        getAttackImpl(current.type).stop(current)
        _attackState.value = current.copy(status = AttackStatus.CANCELLED, endTime = System.currentTimeMillis())
        _consoleOutput.value = _consoleOutput.value + "[*] Attack stopped"
        auditLogger.log(AuditEntry(action = "ATTACK_STOP", module = "attack", target = "${current.type.name}:${current.targetBssid}"))
    }
}
