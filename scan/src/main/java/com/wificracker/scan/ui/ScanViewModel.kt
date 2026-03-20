package com.wificracker.scan.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wificracker.core.wifi.InterfaceManager
import com.wificracker.core.wifi.MonitorModeManager
import com.wificracker.core.wifi.WifiInterface
import com.wificracker.scan.domain.ChannelHopper
import com.wificracker.scan.domain.NetworkAnalyzer
import com.wificracker.scan.domain.PcapExporter
import com.wificracker.scan.domain.ScanEngine
import com.wificracker.scan.domain.VulnMatcher
import com.wificracker.scan.model.ScanResult
import com.wificracker.scan.model.ScanStatus
import com.wificracker.scan.model.VulnMatch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScanUiState(
    val scanResult: ScanResult = ScanResult(),
    val interfaces: List<WifiInterface> = emptyList(),
    val selectedInterface: WifiInterface? = null,
    val vulnMatches: Map<String, List<VulnMatch>> = emptyMap(),
    val isScanning: Boolean = false,
    val isStarting: Boolean = false,
    val errorMessage: String? = null,
    val chipsetInfo: String = "",
    val supportsInternalMonitor: Boolean = false,
    val currentChannel: Int = 0,
    val packetCount: Long = 0,
    val channelHopping: Boolean = false,
)

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val scanEngine: ScanEngine,
    private val interfaceManager: InterfaceManager,
    private val vulnMatcher: VulnMatcher,
    private val networkAnalyzer: NetworkAnalyzer,
    private val monitorModeManager: MonitorModeManager,
    private val channelHopper: ChannelHopper,
    private val pcapExporter: PcapExporter,
) : ViewModel() {

    private var hoppingJob: Job? = null
    private var packetCountJob: Job? = null

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    init {
        loadInterfaces()
        observeScanState()
        detectChipset()
    }

    private fun detectChipset() {
        viewModelScope.launch(Dispatchers.IO) {
            val chipInfo = monitorModeManager.getChipsetInfo()
            _uiState.value = _uiState.value.copy(
                chipsetInfo = "${chipInfo.vendor.label}: ${chipInfo.chipName}",
                supportsInternalMonitor = chipInfo.supportsInternalMonitor,
            )
        }
    }

    private fun loadInterfaces() {
        viewModelScope.launch(Dispatchers.IO) {
            val interfaces = interfaceManager.listInterfaces()
            _uiState.value = _uiState.value.copy(
                interfaces = interfaces,
                selectedInterface = interfaces.firstOrNull(),
            )
        }
    }

    private fun observeScanState() {
        viewModelScope.launch {
            scanEngine.scanState.collect { scanResult ->
                _uiState.value = _uiState.value.copy(
                    scanResult = scanResult,
                    isScanning = scanResult.status == ScanStatus.SCANNING,
                    isStarting = if (scanResult.status != ScanStatus.IDLE) false else _uiState.value.isStarting,
                    errorMessage = if (scanResult.status == ScanStatus.FAILED) "Monitor mode or scan binary unavailable" else _uiState.value.errorMessage,
                )
                // Match vulns for discovered networks
                if (scanResult.networks.isNotEmpty()) {
                    launch(Dispatchers.IO) {
                        val matches = vulnMatcher.matchAllNetworks(scanResult.networks)
                        _uiState.value = _uiState.value.copy(vulnMatches = matches)
                    }
                }
            }
        }
    }

    fun selectInterface(iface: WifiInterface) {
        _uiState.value = _uiState.value.copy(selectedInterface = iface)
    }

    fun startScan() {
        val iface = _uiState.value.selectedInterface ?: return
        _uiState.value = _uiState.value.copy(errorMessage = null, isStarting = true)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                scanEngine.startScan(iface.name)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = e.message ?: "Scan failed",
                    isStarting = false,
                )
            }
        }
    }

    fun stopScan() {
        viewModelScope.launch(Dispatchers.IO) {
            hoppingJob?.cancel()
            hoppingJob = null
            packetCountJob?.cancel()
            packetCountJob = null
            scanEngine.stopScan()
            _uiState.value = _uiState.value.copy(channelHopping = false, currentChannel = 0)
        }
    }

    fun toggleChannelHopping() {
        val iface = _uiState.value.selectedInterface ?: return
        if (_uiState.value.channelHopping) {
            hoppingJob?.cancel()
            hoppingJob = null
            _uiState.value = _uiState.value.copy(channelHopping = false)
        } else {
            _uiState.value = _uiState.value.copy(channelHopping = true)
            hoppingJob = viewModelScope.launch(Dispatchers.IO) {
                channelHopper.startHopping(iface.name).collect { channel ->
                    _uiState.value = _uiState.value.copy(currentChannel = channel)
                }
            }
        }
    }

    fun exportPcap() {
        val iface = _uiState.value.selectedInterface ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val path = pcapExporter.startCapture(iface.name)
                _uiState.value = _uiState.value.copy(errorMessage = null)
                // Stop capture after a short moment — caller can invoke stopCapture explicitly
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.message ?: "Export failed")
            }
        }
    }
}
