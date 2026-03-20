package com.wificracker.report.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wificracker.report.R
import com.wificracker.report.data.entity.ClientProfileEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientListScreen(
    viewModel: ClientListViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onEditClient: (Long) -> Unit = {},
    onAddClient: () -> Unit = {},
) {
    val clients by viewModel.clients.collectAsState(initial = emptyList())
    var deleteTarget by remember { mutableStateOf<ClientProfileEntity?>(null) }

    deleteTarget?.let { client ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.profile_delete_confirm)) },
            text = { Text(client.companyName) },
            confirmButton = { TextButton(onClick = { viewModel.delete(client); deleteTarget = null }) { Text(stringResource(R.string.profile_delete_client), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.report_back)) } },
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.profile_clients_title)) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }) },
        floatingActionButton = { FloatingActionButton(onClick = onAddClient) { Icon(Icons.Default.Add, stringResource(R.string.profile_add_client)) } },
    ) { padding ->
        if (clients.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.profile_no_clients), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(clients, key = { it.id }) { client ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(client.companyName, style = MaterialTheme.typography.titleMedium)
                                if (client.contactName.isNotBlank()) Text(client.contactName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (client.contractReference.isNotBlank()) Text(client.contractReference, style = MaterialTheme.typography.labelSmall)
                            }
                            IconButton(onClick = { onEditClient(client.id) }) { Icon(Icons.Default.Edit, stringResource(R.string.profile_edit_client)) }
                            IconButton(onClick = { deleteTarget = client }) { Icon(Icons.Default.Delete, stringResource(R.string.profile_delete_client), tint = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }
}
