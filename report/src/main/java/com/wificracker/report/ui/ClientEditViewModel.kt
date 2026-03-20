package com.wificracker.report.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wificracker.report.data.dao.ClientProfileDao
import com.wificracker.report.data.entity.ClientProfileEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ClientEditUiState(
    val companyName: String = "", val address: String = "", val contactName: String = "",
    val contactTitle: String = "", val contactEmail: String = "", val contractRef: String = "",
    val logoPath: String = "", val saved: Boolean = false, val existingId: Long = 0,
)

@HiltViewModel
class ClientEditViewModel @Inject constructor(private val dao: ClientProfileDao) : ViewModel() {
    private val _uiState = MutableStateFlow(ClientEditUiState())
    val uiState: StateFlow<ClientEditUiState> = _uiState.asStateFlow()

    fun loadClient(id: Long) { viewModelScope.launch(Dispatchers.IO) { dao.getById(id)?.let { e -> _uiState.value = ClientEditUiState(companyName = e.companyName, address = e.address, contactName = e.contactName, contactTitle = e.contactTitle, contactEmail = e.contactEmail, contractRef = e.contractReference, logoPath = e.logoPath, existingId = e.id) } } }

    fun setCompanyName(v: String) { _uiState.value = _uiState.value.copy(companyName = v) }
    fun setAddress(v: String) { _uiState.value = _uiState.value.copy(address = v) }
    fun setContactName(v: String) { _uiState.value = _uiState.value.copy(contactName = v) }
    fun setContactTitle(v: String) { _uiState.value = _uiState.value.copy(contactTitle = v) }
    fun setContactEmail(v: String) { _uiState.value = _uiState.value.copy(contactEmail = v) }
    fun setContractRef(v: String) { _uiState.value = _uiState.value.copy(contractRef = v) }
    fun setLogoUri(v: String) { _uiState.value = _uiState.value.copy(logoPath = v) }

    fun save() {
        val s = _uiState.value
        val entity = ClientProfileEntity(id = s.existingId, companyName = s.companyName, address = s.address, contactName = s.contactName, contactTitle = s.contactTitle, contactEmail = s.contactEmail, contractReference = s.contractRef, logoPath = s.logoPath)
        viewModelScope.launch(Dispatchers.IO) { dao.save(entity); _uiState.value = _uiState.value.copy(saved = true) }
    }
}
