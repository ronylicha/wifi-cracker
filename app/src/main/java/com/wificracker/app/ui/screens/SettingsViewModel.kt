package com.wificracker.app.ui.screens

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import com.wificracker.core.i18n.LocaleManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject

data class SettingsUiState(
    val darkTheme: Boolean = true,
    val currentLocale: Locale = Locale.getDefault(),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: SharedPreferences,
    private val localeManager: LocaleManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        SettingsUiState(
            darkTheme = prefs.getBoolean("dark_theme", true),
            currentLocale = localeManager.getCurrentLocale(),
        ),
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun setDarkTheme(enabled: Boolean) {
        prefs.edit().putBoolean("dark_theme", enabled).apply()
        _uiState.value = _uiState.value.copy(darkTheme = enabled)
    }

    fun setLocale(locale: Locale) {
        localeManager.setLocale(locale)
        _uiState.value = _uiState.value.copy(currentLocale = locale)
    }

    fun getSupportedLocales(): List<Locale> = LocaleManager.SUPPORTED_LOCALES
}
