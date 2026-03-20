package com.wificracker.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wificracker.app.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModulesScreen(
    viewModel: ModulesViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val installedCount = state.modules.count { it.isInstalled }
    val totalCount = state.modules.size
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.modules_title) + " ($installedCount/$totalCount)") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = { viewModel.checkModules() }) {
                        Icon(Icons.Default.Refresh, stringResource(R.string.modules_refresh))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // Termux command card (show when modules are missing)
            val missingModules = state.modules.filter { !it.isInstalled }
            if (missingModules.isNotEmpty() && !state.isChecking) {
                TermuxCommandCard(
                    missingModules = missingModules,
                    onCopy = { cmd ->
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Termux command", cmd))
                        scope.launch {
                            snackbarHostState.showSnackbar(context.getString(R.string.modules_copied))
                        }
                    },
                )
            }

            // Install all button
            if (missingModules.isNotEmpty() && !state.isChecking) {
                Button(
                    onClick = { viewModel.installAllMissing() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Icon(Icons.Default.Download, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.modules_install_all))
                }
            }

            if (state.isChecking) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(state.modules, key = { it.name }) { module ->
                        ModuleCard(
                            module = module,
                            onInstall = { viewModel.installFromTermux(module.name) },
                        )
                    }
                }
            }

            // Install log
            if (state.installLog.isNotBlank()) {
                Text(
                    stringResource(R.string.modules_log),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .background(Color(0xFF0D1117), RoundedCornerShape(8.dp))
                        .padding(8.dp),
                ) {
                    Text(
                        text = state.installLog,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color(0xFF00FF41),
                    )
                }
            }
        }
    }
}

@Composable
private fun TermuxCommandCard(
    missingModules: List<ModuleInfo>,
    onCopy: (String) -> Unit,
) {
    // Build multi-step Termux install command
    val termuxCmd = buildString {
        val hasAircrack = missingModules.any { it.name in listOf("aircrack-ng", "airodump-ng", "aireplay-ng") }
        val hasHcx = missingModules.any { it.name in listOf("hcxdumptool", "hcxpcapngtool") }
        val hasIw = missingModules.any { it.name == "iw" }
        val hasDnsmasq = missingModules.any { it.name == "dnsmasq" }
        val hasHostapd = missingModules.any { it.name == "hostapd" }
        val hasHashcat = missingModules.any { it.name == "hashcat" }

        // Build deps (needed for compiling from source)
        append("pkg install -y git make clang")

        // Standard repos
        val stdPkgs = mutableListOf<String>()
        if (hasIw) stdPkgs.add("iw")
        if (hasDnsmasq) stdPkgs.add("dnsmasq")
        if (hasHostapd) stdPkgs.add("hostapd")
        if (stdPkgs.isNotEmpty()) append(" ${stdPkgs.joinToString(" ")}")

        // Aircrack-ng via .deb from GitHub
        if (hasAircrack) {
            append(" && curl -sLO https://github.com/pitube08642/aircrack-ng-for-termux/releases/download/Aircrack-ng_termux/aircrack-ng_3_1.7_aarch64.deb && dpkg -i aircrack-ng_3_1.7_aarch64.deb && rm -f *.deb")
        }

        // hcxtools from source
        if (hasHcx) {
            append(" && pkg install -y libcurl openssl && git clone https://github.com/ZerBea/hcxdumptool.git && cd hcxdumptool && make && cp hcxdumptool \$PREFIX/bin/ && cd .. && rm -rf hcxdumptool")
            append(" && git clone https://github.com/ZerBea/hcxtools.git && cd hcxtools && make && cp hcxpcapngtool \$PREFIX/bin/ && cd .. && rm -rf hcxtools")
        }

        // hashcat from source
        if (hasHashcat) {
            append(" && git clone https://github.com/hashcat/hashcat.git && cd hashcat && make && cp hashcat \$PREFIX/bin/ && cd .. && rm -rf hashcat")
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Terminal, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.modules_termux_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.modules_termux_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))

            // Command box with copy button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0D1117), RoundedCornerShape(8.dp))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = termuxCmd,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Color(0xFF00FF41),
                    modifier = Modifier.weight(1f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(
                    onClick = { onCopy(termuxCmd) },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(Icons.Default.ContentCopy, stringResource(R.string.modules_copy), tint = Color(0xFF58A6FF))
                }
            }
        }
    }
}

@Composable
private fun ModuleCard(module: ModuleInfo, onInstall: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (module.isInstalled) Icons.Default.CheckCircle else Icons.Default.Cancel,
                contentDescription = null,
                tint = if (module.isInstalled) Color(0xFF00C853) else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(module.name, style = MaterialTheme.typography.titleMedium)
                Text(module.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!module.isInstalled) {
                if (module.isInstalling) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = onInstall) {
                        Icon(Icons.Default.Download, stringResource(R.string.modules_install), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
