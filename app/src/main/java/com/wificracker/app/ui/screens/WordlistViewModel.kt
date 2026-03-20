package com.wificracker.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wificracker.crack.domain.WordlistManager
import com.wificracker.crack.model.Wordlist
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WordlistUiState(
    val installed: List<Wordlist> = emptyList(),
    val available: List<WordlistManager.DownloadableWordlist> = WordlistManager.DOWNLOADABLE_WORDLISTS,
    val downloading: String = "",
    val downloadLog: String = "",
    val isLoading: Boolean = true,
)

@HiltViewModel
class WordlistViewModel @Inject constructor(private val wordlistManager: WordlistManager) : ViewModel() {
    private val _uiState = MutableStateFlow(WordlistUiState())
    val uiState: StateFlow<WordlistUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch(Dispatchers.IO) {
            val installed = wordlistManager.getInstalledWordlists()
            _uiState.value = _uiState.value.copy(installed = installed, isLoading = false)
        }
    }

    fun download(wl: WordlistManager.DownloadableWordlist) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(downloading = wl.id, downloadLog = "")
            val success = wordlistManager.downloadWordlist(wl) { msg ->
                _uiState.value = _uiState.value.copy(downloadLog = msg)
            }
            _uiState.value = _uiState.value.copy(downloading = "")
            if (success) refresh()
        }
    }

    fun delete(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            wordlistManager.deleteWordlist(path)
            refresh()
        }
    }
}
