package com.wificracker.app.ui.navigation

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wificracker.core.root.RootChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MainUiState(
    val isLoading: Boolean = true,
    val isRooted: Boolean = false,
    val disclaimerAccepted: Boolean = false,
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val rootChecker: RootChecker,
    private val prefs: SharedPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val rooted = rootChecker.isRooted()
            val accepted = prefs.getBoolean("disclaimer_accepted", false)
            _uiState.value = MainUiState(
                isLoading = false,
                isRooted = rooted,
                disclaimerAccepted = accepted,
            )
        }
    }

    fun acceptDisclaimer() {
        prefs.edit().putBoolean("disclaimer_accepted", true).apply()
        _uiState.value = _uiState.value.copy(disclaimerAccepted = true)
    }
}
