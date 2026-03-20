package com.wificracker.scan.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SignalWifi0Bar
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun SignalStrengthIndicator(dbm: Int, modifier: Modifier = Modifier) {
    val icon: ImageVector
    val color: Color
    when {
        dbm >= -50 -> { icon = Icons.Default.SignalWifi4Bar; color = Color(0xFF00C853) }
        dbm >= -60 -> { icon = Icons.Default.SignalWifi4Bar; color = Color(0xFF4CAF50) }
        dbm >= -70 -> { icon = Icons.Default.SignalWifi4Bar; color = Color(0xFFFFD700) }
        dbm >= -80 -> { icon = Icons.Default.SignalWifi0Bar; color = Color(0xFFFF8C00) }
        else       -> { icon = Icons.Default.SignalWifi0Bar; color = Color(0xFFFF4444) }
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Icon(icon, contentDescription = "Signal $dbm dBm", tint = color, modifier = Modifier.size(20.dp))
        Text(" ${dbm}dBm", style = MaterialTheme.typography.labelMedium, color = color)
    }
}
