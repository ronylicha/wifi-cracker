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
import com.wificracker.scan.model.EncryptionType
import com.wificracker.scan.model.RiskLevel

@Composable
fun EncryptionBadge(encryption: EncryptionType, modifier: Modifier = Modifier) {
    val bgColor = when (encryption.riskLevel) {
        RiskLevel.CRITICAL -> Color(0xFFFF4444)
        RiskLevel.HIGH -> Color(0xFFFF8C00)
        RiskLevel.MEDIUM -> Color(0xFFFFD700)
        RiskLevel.LOW -> Color(0xFF00C853)
    }
    val textColor = if (encryption.riskLevel == RiskLevel.MEDIUM) Color.Black else Color.White

    Text(
        text = encryption.label,
        color = textColor,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .background(bgColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
