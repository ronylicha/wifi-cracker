package com.wificracker.report.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wificracker.report.data.dao.CompanyProfileDao
import com.wificracker.report.data.entity.CompanyProfileEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CompanyProfileUiState(
    val name: String = "", val address: String = "", val siret: String = "",
    val contactName: String = "", val contactEmail: String = "", val contactPhone: String = "",
    val certifications: String = "", val legalMention: String = "", val logoPath: String = "",
    val saved: Boolean = false, val existingId: Long = 0,
)

@HiltViewModel
class CompanyProfileViewModel @Inject constructor(private val dao: CompanyProfileDao) : ViewModel() {
    private val _uiState = MutableStateFlow(CompanyProfileUiState())
    val uiState: StateFlow<CompanyProfileUiState> = _uiState.asStateFlow()

    init { viewModelScope.launch { dao.getCompanyProfile().collect { entity -> entity?.let { loadFromEntity(it) } } } }

    private fun loadFromEntity(e: CompanyProfileEntity) {
        _uiState.value = CompanyProfileUiState(name = e.name, address = e.address, siret = e.siret, contactName = e.contactName, contactEmail = e.contactEmail, contactPhone = e.contactPhone, certifications = e.certifications, legalMention = e.legalMention, logoPath = e.logoPath, existingId = e.id)
    }

    fun setName(v: String) { _uiState.value = _uiState.value.copy(name = v) }
    fun setAddress(v: String) { _uiState.value = _uiState.value.copy(address = v) }
    fun setSiret(v: String) { _uiState.value = _uiState.value.copy(siret = v) }
    fun setContactName(v: String) { _uiState.value = _uiState.value.copy(contactName = v) }
    fun setContactEmail(v: String) { _uiState.value = _uiState.value.copy(contactEmail = v) }
    fun setContactPhone(v: String) { _uiState.value = _uiState.value.copy(contactPhone = v) }
    fun setCertifications(v: String) { _uiState.value = _uiState.value.copy(certifications = v) }
    fun setLegalMention(v: String) { _uiState.value = _uiState.value.copy(legalMention = v) }
    fun setLogoUri(v: String) { _uiState.value = _uiState.value.copy(logoPath = v) }
    fun resetSaved() { _uiState.value = _uiState.value.copy(saved = false) }

    fun save() {
        val s = _uiState.value
        val entity = CompanyProfileEntity(id = s.existingId, name = s.name, address = s.address, siret = s.siret, contactName = s.contactName, contactEmail = s.contactEmail, contactPhone = s.contactPhone, certifications = s.certifications, legalMention = s.legalMention, logoPath = s.logoPath)
        viewModelScope.launch(Dispatchers.IO) { dao.save(entity); _uiState.value = _uiState.value.copy(saved = true) }
    }
}
