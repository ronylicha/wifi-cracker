package com.wificracker.attack.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wificracker.attack.domain.AttackOrchestrator
import com.wificracker.attack.domain.PrerequisiteCheck
import com.wificracker.attack.model.*
import com.wificracker.core.service.SelectedNetworkRepository
import com.wificracker.core.wifi.InterfaceManager
import com.wificracker.core.wifi.WifiInterface
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AttackUiState(
    val selectedAttackType: AttackType = AttackType.DEAUTH,
    val targetBssid: String = "",
    val targetSsid: String = "",
    val interfaces: List<WifiInterface> = emptyList(),
    val selectedInterface: WifiInterface? = null,
    val attackStatus: AttackStatus = AttackStatus.PENDING,
    val consoleLines: List<String> = emptyList(),
    val isRunning: Boolean = false,
    val hasPreselectedTarget: Boolean = false,
)

@HiltViewModel
class AttackViewModel @Inject constructor(
    private val orchestrator: AttackOrchestrator,
    private val interfaceManager: InterfaceManager,
    private val selectedNetworkRepository: SelectedNetworkRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AttackUiState())
    val uiState: StateFlow<AttackUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val ifaces = interfaceManager.listInterfaces().filter { it.supportsMonitor }
            _uiState.value = _uiState.value.copy(interfaces = ifaces, selectedInterface = ifaces.firstOrNull())
        }
        viewModelScope.launch {
            orchestrator.attackState.collect { attack ->
                _uiState.value = _uiState.value.copy(attackStatus = attack.status, isRunning = attack.status == AttackStatus.RUNNING)
            }
        }
        viewModelScope.launch {
            orchestrator.consoleOutput.collect { lines ->
                _uiState.value = _uiState.value.copy(consoleLines = lines)
            }
        }
        viewModelScope.launch {
            selectedNetworkRepository.selectedNetwork
                .filterNotNull()
                .distinctUntilChanged()
                .collect { network ->
                    _uiState.value = _uiState.value.copy(
                        targetBssid = network.bssid,
                        targetSsid = network.ssid,
                        hasPreselectedTarget = true,
                    )
                }
        }
    }

    fun selectAttackType(type: AttackType) { _uiState.value = _uiState.value.copy(selectedAttackType = type) }
    fun setTarget(bssid: String, ssid: String) { _uiState.value = _uiState.value.copy(targetBssid = bssid, targetSsid = ssid) }
    fun selectInterface(iface: WifiInterface) { _uiState.value = _uiState.value.copy(selectedInterface = iface) }

    fun launchAttack() {
        val state = _uiState.value
        val iface = state.selectedInterface ?: return
        val attack = Attack(type = state.selectedAttackType, targetBssid = state.targetBssid, targetSsid = state.targetSsid, interfaceName = iface.name)
        viewModelScope.launch(Dispatchers.IO) { orchestrator.launchAttack(attack) }
    }

    fun stopAttack() { viewModelScope.launch(Dispatchers.IO) { orchestrator.stopAttack() } }
}
