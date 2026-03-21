package com.wificracker.core.service

import com.wificracker.core.model.SelectedNetwork
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository partagé pour le réseau actuellement ciblé.
 * Injecté dans ScanViewModel, AttackViewModel et CrackViewModel.
 */
@Singleton
class SelectedNetworkRepository @Inject constructor() {
    private val _selectedNetwork = MutableStateFlow<SelectedNetwork?>(null)
    val selectedNetwork: StateFlow<SelectedNetwork?> = _selectedNetwork.asStateFlow()

    fun select(network: SelectedNetwork) {
        _selectedNetwork.value = network
    }

    fun clear() {
        _selectedNetwork.value = null
    }
}
