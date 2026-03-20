package com.wificracker.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.wificracker.app.ui.screens.DisclaimerScreen
import com.wificracker.app.ui.screens.MainDashboard
import com.wificracker.app.ui.screens.RootErrorScreen

@Composable
fun AppNavGraph(viewModel: MainViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    when {
        state.isLoading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        !state.isRooted -> RootErrorScreen()
        !state.disclaimerAccepted -> DisclaimerScreen(onAccept = viewModel::acceptDisclaimer)
        else -> MainDashboard()
    }
}
