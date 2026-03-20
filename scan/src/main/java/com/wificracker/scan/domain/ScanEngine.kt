package com.wificracker.scan.domain

import com.wificracker.core.logging.AuditEntry
import com.wificracker.core.logging.AuditLogger
import com.wificracker.core.util.MacVendorLookup
import com.wificracker.core.wifi.MonitorModeManager
import com.wificracker.scan.data.WifiCommandRunner
import com.wificracker.scan.model.Client
import com.wificracker.scan.model.Network
import com.wificracker.scan.model.ScanResult
import com.wificracker.scan.model.ScanStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScanEngine @Inject constructor(
    private val commandRunner: WifiCommandRunner,
    private val monitorModeManager: MonitorModeManager,
    private val macVendorLookup: MacVendorLookup,
    private val auditLogger: AuditLogger,
) {

    private val _scanState = MutableStateFlow(ScanResult())
    val scanState: StateFlow<ScanResult> = _scanState.asStateFlow()

    private var scanJob: Job? = null
    private var currentInterface: String? = null

    suspend fun startScan(interfaceName: String) {
        if (_scanState.value.status == ScanStatus.SCANNING) return

        // Enable monitor mode
        val monitorResult = monitorModeManager.enableMonitorMode(interfaceName)
        if (!monitorResult.isSuccess) {
            _scanState.value = ScanResult(
                interfaceName = interfaceName,
                status = ScanStatus.FAILED,
            )
            return
        }

        currentInterface = interfaceName
        _scanState.value = ScanResult(
            interfaceName = interfaceName,
            status = ScanStatus.SCANNING,
            timestamp = System.currentTimeMillis(),
        )

        auditLogger.log(AuditEntry(
            action = "SCAN_START",
            module = "scan",
            target = interfaceName,
        ))

        coroutineScope {
            scanJob = launch {
                commandRunner.startScan(interfaceName)
                    .onEach { update ->
                        val enrichedNetworks = update.networks.map { network ->
                            network.copy(
                                clients = update.clients
                                    .filter { it.bssid == network.bssid }
                                    .map { enrichClient(it) },
                            )
                        }

                        _scanState.value = _scanState.value.copy(
                            networks = enrichedNetworks,
                            clients = update.clients.map { enrichClient(it) },
                            duration = System.currentTimeMillis() - _scanState.value.timestamp,
                        )
                    }
                    .catch { e ->
                        _scanState.value = _scanState.value.copy(status = ScanStatus.FAILED)
                        auditLogger.log(AuditEntry(
                            action = "SCAN_ERROR",
                            module = "scan",
                            target = interfaceName,
                            details = e.message ?: "Unknown error",
                        ))
                    }
                    .collect {}
            }
        }
    }

    suspend fun stopScan() {
        val iface = currentInterface ?: return
        scanJob?.cancel()
        scanJob = null

        commandRunner.stopScan(iface)
        monitorModeManager.disableMonitorMode(iface)

        _scanState.value = _scanState.value.copy(
            status = ScanStatus.COMPLETED,
            duration = System.currentTimeMillis() - _scanState.value.timestamp,
        )

        auditLogger.log(AuditEntry(
            action = "SCAN_STOP",
            module = "scan",
            target = iface,
            result = "${_scanState.value.networks.size} networks found",
        ))

        currentInterface = null
    }

    private fun enrichClient(client: Client): Client {
        return client.copy(vendor = macVendorLookup.resolve(client.macAddress))
    }
}
