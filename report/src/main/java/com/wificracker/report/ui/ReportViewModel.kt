package com.wificracker.report.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wificracker.report.domain.ExportManager
import com.wificracker.report.domain.ReportGenerator
import com.wificracker.report.model.*
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
)

enum class ReportStep { MISSION_INFO, FINDINGS, PREVIEW, EXPORT }

@HiltViewModel
class ReportViewModel @Inject constructor(
    private val reportGenerator: ReportGenerator,
    private val exportManager: ExportManager,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReportUiState())
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()

    fun updateCompanyProfile(profile: CompanyProfile) { _uiState.value = _uiState.value.copy(companyProfile = profile) }
    fun updateClientProfile(profile: ClientProfile) { _uiState.value = _uiState.value.copy(clientProfile = profile) }
    fun updateMission(title: String, scope: String) { _uiState.value = _uiState.value.copy(missionTitle = title, missionScope = scope) }
    fun addFinding(finding: Finding) { _uiState.value = _uiState.value.copy(findings = _uiState.value.findings + finding) }
    fun removeFinding(id: String) { _uiState.value = _uiState.value.copy(findings = _uiState.value.findings.filter { it.id != id }) }
    fun nextStep() { val next = ReportStep.entries.getOrNull((_uiState.value.step.ordinal + 1))  ?: return; _uiState.value = _uiState.value.copy(step = next) }
    fun prevStep() { val prev = ReportStep.entries.getOrNull((_uiState.value.step.ordinal - 1))  ?: return; _uiState.value = _uiState.value.copy(step = prev) }

    fun generateReport() {
        _uiState.value = _uiState.value.copy(isGenerating = true)
        val state = _uiState.value
        val mission = MissionInfo(title = state.missionTitle, scope = state.missionScope, clientProfile = state.clientProfile)
        val report = reportGenerator.generateReport(mission, state.companyProfile, state.findings)
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
