package com.wificracker.crack.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wificracker.crack.domain.CrackOrchestrator
import com.wificracker.crack.domain.WordlistManager
import com.wificracker.crack.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CrackUiState(
    val capturePath: String = "",
    val targetBssid: String = "",
    val targetSsid: String = "",
    val selectedStrategy: CrackStrategy = CrackStrategy.DICTIONARY,
    val selectedWordlist: Wordlist? = null,
    val wordlists: List<Wordlist> = emptyList(),
    val progress: CrackProgress = CrackProgress(),
    val result: CrackResult? = null,
    val isRunning: Boolean = false,
)

@HiltViewModel
class CrackViewModel @Inject constructor(
    private val orchestrator: CrackOrchestrator,
    private val wordlistManager: WordlistManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CrackUiState())
    val uiState: StateFlow<CrackUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) { _uiState.value = _uiState.value.copy(wordlists = wordlistManager.getAllWordlists()) }
        viewModelScope.launch { orchestrator.progress.collect { p -> _uiState.value = _uiState.value.copy(progress = p, isRunning = p.status == CrackStatus.RUNNING || p.status == CrackStatus.CONVERTING) } }
        viewModelScope.launch { orchestrator.result.collect { r -> _uiState.value = _uiState.value.copy(result = r) } }
    }

    fun setCapture(path: String, bssid: String = "", ssid: String = "") { _uiState.value = _uiState.value.copy(capturePath = path, targetBssid = bssid, targetSsid = ssid) }
    fun selectStrategy(strategy: CrackStrategy) { _uiState.value = _uiState.value.copy(selectedStrategy = strategy) }
    fun selectWordlist(wordlist: Wordlist) { _uiState.value = _uiState.value.copy(selectedWordlist = wordlist) }

    fun startCrack() {
        val state = _uiState.value
        val job = CrackJob(capturePath = state.capturePath, targetBssid = state.targetBssid, targetSsid = state.targetSsid, strategy = state.selectedStrategy, wordlistPath = state.selectedWordlist?.path ?: "")
        viewModelScope.launch(Dispatchers.IO) { orchestrator.startCrack(job) }
    }

    fun stopCrack() { viewModelScope.launch(Dispatchers.IO) { orchestrator.stopCrack() } }
}
