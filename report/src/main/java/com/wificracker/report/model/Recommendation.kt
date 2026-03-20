package com.wificracker.report.model

data class Recommendation(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val description: String,
    val priority: Int = 0,
    val relatedFindings: List<String> = emptyList(),
)
