package com.wificracker.report.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wificracker.core.service.SessionCollector
import com.wificracker.report.data.dao.ClientProfileDao
import com.wificracker.report.data.dao.CompanyProfileDao
import com.wificracker.report.data.entity.ClientProfileEntity
import com.wificracker.report.domain.DataAggregator
import com.wificracker.report.domain.ExportManager
import com.wificracker.report.domain.ReportGenerator
import com.wificracker.report.model.*
import com.wificracker.scan.domain.ScanEngine
import com.wificracker.scan.domain.VulnMatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReportUiState(
    val companyProfile: CompanyProfile = CompanyProfile(),
    val clientProfile: ClientProfile = ClientProfile(),
    val missionTitle: String = "",
    val missionScope: String = "",
    val findings: List<Finding> = emptyList(),
    val generatedReport: Report? = null,
    val exportedPath: String = "",
    val isGenerating: Boolean = false,
    val step: ReportStep = ReportStep.MISSION_INFO,
    val savedClients: List<ClientProfileEntity> = emptyList(),
    val selectedClientId: Long = 0,
    val sessionStats: SessionStats = SessionStats(),
    val sessionCollected: Boolean = false,
)

data class SessionStats(
    val networksScanned: Int = 0,
    val attacksPerformed: Int = 0,
    val attacksSuccessful: Int = 0,
    val cracksAttempted: Int = 0,
    val cracksSuccessful: Int = 0,
    val passwordsFound: List<String> = emptyList(),
)

enum class ReportStep { MISSION_INFO, FINDINGS, PREVIEW, EXPORT }

@HiltViewModel
class ReportViewModel @Inject constructor(
    private val reportGenerator: ReportGenerator,
    private val exportManager: ExportManager,
    private val dataAggregator: DataAggregator,
    private val clientProfileDao: ClientProfileDao,
    private val companyProfileDao: CompanyProfileDao,
    private val scanEngine: ScanEngine,
    private val vulnMatcher: VulnMatcher,
    private val sessionCollector: SessionCollector,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReportUiState())
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()

    init {
        // Load saved clients
        viewModelScope.launch {
            clientProfileDao.getAllClients().collect { clients ->
                _uiState.value = _uiState.value.copy(savedClients = clients)
            }
        }
        // Load saved company profile
        viewModelScope.launch {
            companyProfileDao.getCompanyProfile().collect { entity ->
                entity?.let {
                    _uiState.value = _uiState.value.copy(
                        companyProfile = CompanyProfile(
                            id = it.id,
                            name = it.name,
                            address = it.address,
                            siret = it.siret,
                            contactName = it.contactName,
                            contactEmail = it.contactEmail,
                            contactPhone = it.contactPhone,
                            certifications = it.certifications.split(",").filter { c -> c.isNotBlank() },
                            legalMention = it.legalMention,
                            logoPath = it.logoPath,
                        ),
                    )
                }
            }
        }
    }

    fun updateCompanyProfile(profile: CompanyProfile) { _uiState.value = _uiState.value.copy(companyProfile = profile) }
    fun updateClientProfile(profile: ClientProfile) { _uiState.value = _uiState.value.copy(clientProfile = profile) }
    fun updateMission(title: String, scope: String) { _uiState.value = _uiState.value.copy(missionTitle = title, missionScope = scope) }
    fun addFinding(finding: Finding) { _uiState.value = _uiState.value.copy(findings = _uiState.value.findings + finding) }
    fun removeFinding(id: String) { _uiState.value = _uiState.value.copy(findings = _uiState.value.findings.filter { it.id != id }) }
    fun nextStep() { val next = ReportStep.entries.getOrNull((_uiState.value.step.ordinal + 1))  ?: return; _uiState.value = _uiState.value.copy(step = next) }
    fun prevStep() { val prev = ReportStep.entries.getOrNull((_uiState.value.step.ordinal - 1))  ?: return; _uiState.value = _uiState.value.copy(step = prev) }

    fun selectClient(client: ClientProfileEntity) {
        _uiState.value = _uiState.value.copy(
            selectedClientId = client.id,
            clientProfile = ClientProfile(
                companyName = client.companyName,
                address = client.address,
                contactName = client.contactName,
                contactTitle = client.contactTitle,
                contactEmail = client.contactEmail,
                contractReference = client.contractReference,
                logoPath = client.logoPath,
            ),
        )
    }

    fun collectSessionResults() {
        viewModelScope.launch(Dispatchers.IO) {
            val findings = mutableListOf<Finding>()
            val session = sessionCollector.sessionData.value

            // 1. Scan findings — convert all scanned networks to findings
            val scanResult = scanEngine.scanState.value
            if (scanResult.networks.isNotEmpty()) {
                val vulnMatches = vulnMatcher.matchAllNetworks(scanResult.networks)
                for (network in scanResult.networks) {
                    findings.addAll(
                        dataAggregator.networkToFindings(network, vulnMatches[network.bssid] ?: emptyList()),
                    )
                }
            }

            // 2. Attack findings — convert completed attacks to findings
            for (attackRecord in session.attacksPerformed) {
                findings.addAll(dataAggregator.attackRecordToFindings(attackRecord))
            }

            // 3. Crack findings — add cracked passwords
            for (crackRecord in session.crackAttempts) {
                if (crackRecord.success && crackRecord.password.isNotBlank()) {
                    findings.add(
                        dataAggregator.crackedPasswordFinding(
                            bssid = crackRecord.targetBssid,
                            ssid = crackRecord.targetSsid,
                            password = crackRecord.password,
                        ),
                    )
                }
            }

            // Deduplicate by title + network
            val deduplicated = findings.distinctBy { "${it.title}:${it.networkBssid}" }

            val stats = SessionStats(
                networksScanned = session.scannedNetworkCount,
                attacksPerformed = session.attacksPerformed.size,
                attacksSuccessful = session.attacksPerformed.count { it.status == "COMPLETED" },
                cracksAttempted = session.crackAttempts.size,
                cracksSuccessful = session.crackAttempts.count { it.success },
                passwordsFound = session.crackAttempts.filter { it.success }.map { it.targetSsid },
            )

            _uiState.value = _uiState.value.copy(
                findings = deduplicated.sortedByDescending { it.cvssScore },
                sessionStats = stats,
                sessionCollected = true,
            )
        }
    }

    fun generateReport() {
        _uiState.value = _uiState.value.copy(isGenerating = true)
        val state = _uiState.value
        val mission = MissionInfo(title = state.missionTitle, scope = state.missionScope, clientProfile = state.clientProfile)
        val report = reportGenerator.generateReport(mission, state.companyProfile, state.findings, sessionStats = state.sessionStats)
        _uiState.value = _uiState.value.copy(generatedReport = report, isGenerating = false, step = ReportStep.PREVIEW)
    }

    fun exportReport(format: ExportFormat) {
        val report = _uiState.value.generatedReport ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val path = exportManager.export(context, report, format)
            _uiState.value = _uiState.value.copy(exportedPath = path, step = ReportStep.EXPORT)
        }
    }
}
