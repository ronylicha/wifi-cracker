package com.wificracker.report.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wificracker.report.R
import com.wificracker.report.model.ExportFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportDashboard(viewModel: ReportViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("${stringResource(R.string.report_title)} - ${state.step.name.replace("_", " ")}") }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)) },
        bottomBar = {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                if (state.step.ordinal > 0) { Button(onClick = { viewModel.prevStep() }) { Icon(Icons.Default.ArrowBack, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.report_back)) } } else { Spacer(Modifier) }
                when (state.step) {
                    ReportStep.MISSION_INFO -> Button(onClick = { viewModel.nextStep() }) { Text(stringResource(R.string.report_next)); Spacer(Modifier.width(4.dp)); Icon(Icons.Default.ArrowForward, null) }
                    ReportStep.FINDINGS -> Button(onClick = { viewModel.generateReport() }) { Text(stringResource(R.string.report_generate)) }
                    ReportStep.PREVIEW -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.exportReport(ExportFormat.PDF) }) { Text(stringResource(R.string.report_export_pdf)) }
                        OutlinedButton(onClick = { viewModel.exportReport(ExportFormat.HTML) }) { Text("HTML") }
                        OutlinedButton(onClick = { viewModel.exportReport(ExportFormat.JSON) }) { Text("JSON") }
                    }
                    ReportStep.EXPORT -> {}
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            when (state.step) {
                ReportStep.MISSION_INFO -> MissionInfoStep(state, viewModel)
                ReportStep.FINDINGS -> FindingsStep(state, viewModel)
                ReportStep.PREVIEW -> PreviewStep(state)
                ReportStep.EXPORT -> ExportStep(state)
            }
        }
    }
}

@Composable
private fun MissionInfoStep(state: ReportUiState, viewModel: ReportViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.report_mission_info), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(value = state.missionTitle, onValueChange = { viewModel.updateMission(it, state.missionScope) }, label = { Text(stringResource(R.string.report_mission_title)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = state.missionScope, onValueChange = { viewModel.updateMission(state.missionTitle, it) }, label = { Text(stringResource(R.string.report_scope)) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.report_company_profile), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(value = state.companyProfile.name, onValueChange = { viewModel.updateCompanyProfile(state.companyProfile.copy(name = it)) }, label = { Text(stringResource(R.string.report_company_name)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = state.companyProfile.contactEmail, onValueChange = { viewModel.updateCompanyProfile(state.companyProfile.copy(contactEmail = it)) }, label = { Text(stringResource(R.string.report_contact_email)) }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.report_client), style = MaterialTheme.typography.titleMedium)

        if (state.savedClients.isNotEmpty()) {
            var clientExpanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(
                    onClick = { clientExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (state.selectedClientId > 0)
                            state.savedClients.find { it.id == state.selectedClientId }?.companyName ?: stringResource(R.string.report_select_client)
                        else stringResource(R.string.report_select_client)
                    )
                }
                DropdownMenu(expanded = clientExpanded, onDismissRequest = { clientExpanded = false }) {
                    state.savedClients.forEach { client ->
                        DropdownMenuItem(
                            text = { Text(client.companyName) },
                            onClick = { viewModel.selectClient(client); clientExpanded = false },
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(stringResource(R.string.report_or_manual), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        OutlinedTextField(value = state.clientProfile.companyName, onValueChange = { viewModel.updateClientProfile(state.clientProfile.copy(companyName = it)) }, label = { Text(stringResource(R.string.report_client_company)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = state.clientProfile.contactName, onValueChange = { viewModel.updateClientProfile(state.clientProfile.copy(contactName = it)) }, label = { Text(stringResource(R.string.report_client_contact)) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun FindingsStep(state: ReportUiState, viewModel: ReportViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.report_findings, state.findings.size), style = MaterialTheme.typography.titleMedium)
        if (state.findings.isEmpty()) {
            Text(stringResource(R.string.report_no_findings), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.findings.forEach { finding ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("[${finding.severity.label}] ${finding.title}", style = MaterialTheme.typography.bodyMedium)
                        Text("CVSS ${finding.cvssScore}", style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = { viewModel.removeFinding(finding.id) }) { Text(stringResource(R.string.report_remove), color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

@Composable
private fun PreviewStep(state: ReportUiState) {
    val report = state.generatedReport ?: return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(16.dp)) {
                Text("${stringResource(R.string.report_security_grade)} ${report.overallScore}", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                Text("${report.findings.size} findings | ${report.recommendations.size} recommendations", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text(stringResource(R.string.report_exec_summary), style = MaterialTheme.typography.titleMedium)
        Text(report.executiveSummary, style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.report_top_findings), style = MaterialTheme.typography.titleMedium)
        report.findings.take(5).forEach { f ->
            Text("[${f.severity.label}] ${f.title} (CVSS ${f.cvssScore})", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ExportStep(state: ReportUiState) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.report_exported), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(state.exportedPath, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
