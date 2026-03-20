package com.wificracker.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wificracker.core.logging.AuditEntry
import com.wificracker.core.logging.AuditLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuditLogViewModel @Inject constructor(private val auditLogger: AuditLogger) : ViewModel() {
    private val _entries = MutableStateFlow<List<AuditEntry>>(emptyList())
    val entries: StateFlow<List<AuditEntry>> = _entries.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _entries.value = auditLogger.getEntries()
        }
    }

    fun purge() {
        viewModelScope.launch(Dispatchers.IO) {
            auditLogger.purge()
            _entries.value = emptyList()
        }
    }

    fun export() {
        viewModelScope.launch(Dispatchers.IO) {
            auditLogger.exportJson()
        }
    }
}
