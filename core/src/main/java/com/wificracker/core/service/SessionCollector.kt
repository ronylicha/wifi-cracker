package com.wificracker.core.service

import com.wificracker.core.model.SelectedNetwork
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Collecte les résultats clés de la session en cours pour la génération de rapport.
 * Chaque module (scan, attack, crack) pousse ses résultats ici au fur et à mesure.
 */
@Singleton
class SessionCollector @Inject constructor() {

    data class SessionData(
        val scannedNetworkCount: Int = 0,
        val scanDuration: Long = 0,
        val scanInterfaceName: String = "",
        val attacksPerformed: List<AttackRecord> = emptyList(),
        val crackAttempts: List<CrackRecord> = emptyList(),
    )

    data class AttackRecord(
        val type: String,
        val targetBssid: String,
        val targetSsid: String,
        val status: String,
        val startTime: Long = 0,
        val endTime: Long = 0,
    )

    data class CrackRecord(
        val targetBssid: String,
        val targetSsid: String,
        val strategy: String,
        val success: Boolean,
        val password: String = "",
        val keysTested: Long = 0,
        val duration: Long = 0,
    )

    private val _sessionData = MutableStateFlow(SessionData())
    val sessionData: StateFlow<SessionData> = _sessionData.asStateFlow()

    fun updateScanStats(networkCount: Int, duration: Long, interfaceName: String) {
        _sessionData.value = _sessionData.value.copy(
            scannedNetworkCount = networkCount,
            scanDuration = duration,
            scanInterfaceName = interfaceName,
        )
    }

    fun recordAttack(record: AttackRecord) {
        _sessionData.value = _sessionData.value.copy(
            attacksPerformed = _sessionData.value.attacksPerformed + record,
        )
    }

    fun recordCrack(record: CrackRecord) {
        _sessionData.value = _sessionData.value.copy(
            crackAttempts = _sessionData.value.crackAttempts + record,
        )
    }

    fun reset() {
        _sessionData.value = SessionData()
    }
}
