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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import com.wificracker.report.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanyProfileScreen(
    viewModel: CompanyProfileViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val savedMsg = stringResource(R.string.profile_saved)

    LaunchedEffect(state.saved) {
        if (state.saved) { snackbarHostState.showSnackbar(savedMsg); viewModel.resetSaved() }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.setLogoUri(it.toString()) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.profile_company_title)) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.profile_company_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            // Logo
            Box(Modifier.size(120.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable { imagePicker.launch("image/*") }, contentAlignment = Alignment.Center) {
                if (state.logoPath.isNotBlank()) {
                    Image(painter = rememberAsyncImagePainter(state.logoPath), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.AddAPhoto, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.profile_logo_tap), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            OutlinedTextField(value = state.name, onValueChange = viewModel::setName, label = { Text(stringResource(R.string.profile_company_name)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = state.address, onValueChange = viewModel::setAddress, label = { Text(stringResource(R.string.profile_address)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = state.siret, onValueChange = viewModel::setSiret, label = { Text(stringResource(R.string.profile_siret)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = state.contactName, onValueChange = viewModel::setContactName, label = { Text(stringResource(R.string.profile_contact_name)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = state.contactEmail, onValueChange = viewModel::setContactEmail, label = { Text(stringResource(R.string.profile_contact_email)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = state.contactPhone, onValueChange = viewModel::setContactPhone, label = { Text(stringResource(R.string.profile_contact_phone)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = state.certifications, onValueChange = viewModel::setCertifications, label = { Text(stringResource(R.string.profile_certifications)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = state.legalMention, onValueChange = viewModel::setLegalMention, label = { Text(stringResource(R.string.profile_legal_mention)) }, modifier = Modifier.fillMaxWidth(), minLines = 2)

            Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.profile_save)) }
        }
    }
}
