package com.wificracker.report.model

enum class ExportFormat(val extension: String, val mimeType: String) {
    PDF("pdf", "application/pdf"),
    HTML("html", "text/html"),
    JSON("json", "application/json"),
}
