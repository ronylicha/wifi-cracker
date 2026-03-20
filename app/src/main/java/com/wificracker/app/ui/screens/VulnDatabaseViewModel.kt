package com.wificracker.app.ui.screens

import androidx.lifecycle.ViewModel
import com.wificracker.core.database.dao.VulnDao
import com.wificracker.core.database.entity.VulnEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class VulnDatabaseViewModel @Inject constructor(vulnDao: VulnDao) : ViewModel() {
    val vulns: Flow<List<VulnEntity>> = vulnDao.getAll()
}
