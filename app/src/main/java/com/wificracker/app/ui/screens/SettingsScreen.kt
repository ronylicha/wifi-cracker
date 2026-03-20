package com.wificracker.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wificracker.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Appearance section
            Text(
                stringResource(R.string.settings_appearance),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_dark_theme)) },
                supportingContent = { Text(stringResource(R.string.settings_dark_theme_desc)) },
                leadingContent = { Icon(Icons.Default.DarkMode, null) },
                trailingContent = {
                    Switch(
                        checked = state.darkTheme,
                        onCheckedChange = { viewModel.setDarkTheme(it) },
                    )
                },
            )

            HorizontalDivider()

            // Language section
            Text(
                stringResource(R.string.settings_language),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            viewModel.getSupportedLocales().forEach { locale ->
                val label = when (locale.language) {
                    "en" -> "English"
                    "fr" -> "Francais"
                    else -> locale.displayLanguage
                }
                ListItem(
                    headlineContent = { Text(label) },
                    leadingContent = { Icon(Icons.Default.Language, null) },
                    trailingContent = {
                        if (state.currentLocale.language == locale.language) {
                            Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    modifier = Modifier.clickable { viewModel.setLocale(locale) },
                )
            }

            HorizontalDivider()

            // Info section
            Text(
                stringResource(R.string.settings_info),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_version)) },
                supportingContent = { Text("1.0.0") },
                leadingContent = { Icon(Icons.Default.Info, null) },
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_root_status)) },
                supportingContent = { Text(stringResource(R.string.settings_root_detected)) },
                leadingContent = { Icon(Icons.Default.Security, null) },
            )
        }
    }
}
