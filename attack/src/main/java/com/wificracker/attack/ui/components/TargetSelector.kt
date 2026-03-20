package com.wificracker.attack.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wificracker.attack.R

@Composable
fun TargetSelector(bssid: String, ssid: String, onBssidChange: (String) -> Unit, onSsidChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = bssid, onValueChange = onBssidChange, label = { Text(stringResource(R.string.attack_target_bssid)) }, placeholder = { Text("AA:BB:CC:DD:EE:FF") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = ssid, onValueChange = onSsidChange, label = { Text(stringResource(R.string.attack_target_ssid)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
    }
}
