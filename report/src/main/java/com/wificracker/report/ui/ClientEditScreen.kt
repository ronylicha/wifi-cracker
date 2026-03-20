package com.wificracker.report.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import com.wificracker.report.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientEditScreen(
    clientId: Long = 0,
    viewModel: ClientEditViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val savedMsg = stringResource(R.string.profile_saved)

    LaunchedEffect(clientId) { if (clientId > 0) viewModel.loadClient(clientId) }
    LaunchedEffect(state.saved) { if (state.saved) { snackbarHostState.showSnackbar(savedMsg); onBack() } }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.setLogoUri(it.toString()) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(if (clientId > 0) stringResource(R.string.profile_edit_client) else stringResource(R.string.profile_add_client)) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(100.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable { imagePicker.launch("image/*") }, contentAlignment = Alignment.Center) {
                if (state.logoPath.isNotBlank()) {
                    Image(painter = rememberAsyncImagePainter(state.logoPath), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Default.AddAPhoto, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            OutlinedTextField(value = state.companyName, onValueChange = viewModel::setCompanyName, label = { Text(stringResource(R.string.profile_client_company)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = state.address, onValueChange = viewModel::setAddress, label = { Text(stringResource(R.string.profile_address)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = state.contactName, onValueChange = viewModel::setContactName, label = { Text(stringResource(R.string.profile_client_contact_name)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = state.contactTitle, onValueChange = viewModel::setContactTitle, label = { Text(stringResource(R.string.profile_client_contact_title)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = state.contactEmail, onValueChange = viewModel::setContactEmail, label = { Text(stringResource(R.string.profile_contact_email)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = state.contractRef, onValueChange = viewModel::setContractRef, label = { Text(stringResource(R.string.profile_client_contract_ref)) }, modifier = Modifier.fillMaxWidth())
            Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.profile_save)) }
        }
    }
}
