package com.wificracker.attack.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LiveConsole(lines: List<String>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex) }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth().heightIn(min = 150.dp, max = 300.dp).background(Color(0xFF0D1117), RoundedCornerShape(8.dp)).padding(8.dp),
    ) {
        items(lines) { line ->
            val color = when {
                line.startsWith("[+]") -> Color(0xFF00FF41)
                line.startsWith("[!]") -> Color(0xFFFF4444)
                line.startsWith("[*]") -> Color(0xFF58A6FF)
                else -> Color(0xFFE6EDF3)
            }
            Text(text = line, color = color, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.padding(vertical = 1.dp))
        }
    }
}
