package com.wificracker.core.logging

import kotlinx.serialization.Serializable

@Serializable
data class AuditEntry(val timestamp: Long = System.currentTimeMillis(), val action: String, val module: String, val target: String = "", val result: String = "", val details: String = "")
