package com.wificracker.scan.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun VulnBadge(severity: String, modifier: Modifier = Modifier) {
    val (bgColor, textColor) = when (severity.uppercase()) {
        "CRITICAL" -> Color(0xFFFF4444) to Color.White
        "HIGH" -> Color(0xFFFF8C00) to Color.White
        "MEDIUM" -> Color(0xFFFFD700) to Color.Black
        "LOW" -> Color(0xFF00C853) to Color.White
        else -> Color(0xFF8B949E) to Color.White
    }

    Text(
        text = severity.uppercase(),
        color = textColor,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .background(bgColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
