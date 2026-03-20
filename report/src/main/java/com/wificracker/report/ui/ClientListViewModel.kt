package com.wificracker.report.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wificracker.report.data.dao.ClientProfileDao
import com.wificracker.report.data.entity.ClientProfileEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ClientListViewModel @Inject constructor(private val dao: ClientProfileDao) : ViewModel() {
    val clients: Flow<List<ClientProfileEntity>> = dao.getAllClients()
    fun delete(client: ClientProfileEntity) { viewModelScope.launch(Dispatchers.IO) { dao.delete(client) } }
}
