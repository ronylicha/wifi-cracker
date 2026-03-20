package com.wificracker.report.domain

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.Typeface
import com.wificracker.report.model.ExportFormat
import com.wificracker.report.model.Report
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExportManager @Inject constructor() {

    companion object {
        private const val EXPORT_DIR = "/data/local/tmp/wificracker/reports"
        private const val PAGE_WIDTH = 595 // A4
        private const val PAGE_HEIGHT = 842
        private const val MARGIN = 40
    }

    fun export(context: Context, report: Report, format: ExportFormat): String {
        val dir = File(EXPORT_DIR).also { it.mkdirs() }
        val filename = "report_${report.id.take(8)}_${System.currentTimeMillis()}.${format.extension}"
        val file = File(dir, filename)

        when (format) {
            ExportFormat.PDF -> exportPdf(report, file)
            ExportFormat.HTML -> exportHtml(report, file)
            ExportFormat.JSON -> exportJson(report, file)
        }

        return file.absolutePath
    }

    private fun exportPdf(report: Report, file: File) {
        val pdfDoc = PdfDocument()
        var pageNumber = 1
        var yPos = MARGIN
        var currentPage: PdfDocument.Page? = null

        fun newPage(): Canvas {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber++).create()
            currentPage = pdfDoc.startPage(pageInfo)
            yPos = MARGIN
            return currentPage!!.canvas
        }

        fun finishCurrentPage() {
            currentPage?.let { pdfDoc.finishPage(it) }
            currentPage = null
        }

        fun checkPageBreak(canvas: Canvas, needed: Int): Canvas {
            return if (yPos + needed > PAGE_HEIGHT - MARGIN) {
                finishCurrentPage()
                newPage()
            } else canvas
        }

        val titlePaint = Paint().apply { color = Color.parseColor("#00FF41"); textSize = 24f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val headerPaint = Paint().apply { color = Color.parseColor("#E6EDF3"); textSize = 16f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val bodyPaint = Paint().apply { color = Color.parseColor("#8B949E"); textSize = 11f }
        val accentPaint = Paint().apply { color = Color.parseColor("#00FF41"); textSize = 12f }

        // Page 1 - Title
        var canvas = newPage()
        canvas.drawColor(Color.parseColor("#0D1117"))
        yPos = PAGE_HEIGHT / 3
        canvas.drawText("WiFi Security Assessment", MARGIN.toFloat(), yPos.toFloat(), titlePaint)
        yPos += 40
        canvas.drawText(report.missionInfo.clientProfile.companyName, MARGIN.toFloat(), yPos.toFloat(), headerPaint)
        yPos += 30
        canvas.drawText("By: ${report.companyProfile.name}", MARGIN.toFloat(), yPos.toFloat(), bodyPaint)
        yPos += 20
        canvas.drawText("Grade: ${report.overallScore}", MARGIN.toFloat(), yPos.toFloat(), accentPaint)
        finishCurrentPage()

        // Page 2 - Executive Summary
        canvas = newPage()
        canvas.drawColor(Color.parseColor("#0D1117"))
        canvas.drawText("Executive Summary", MARGIN.toFloat(), yPos.toFloat(), headerPaint)
        yPos += 25
        report.executiveSummary.lines().forEach { line ->
            canvas = checkPageBreak(canvas, 15)
            canvas.drawText(line, MARGIN.toFloat(), yPos.toFloat(), bodyPaint)
            yPos += 15
        }
        finishCurrentPage()

        // Page 3+ - Findings
        canvas = newPage()
        canvas.drawColor(Color.parseColor("#0D1117"))
        canvas.drawText("Findings (${report.findings.size})", MARGIN.toFloat(), yPos.toFloat(), headerPaint)
        yPos += 25
        report.findings.forEach { finding ->
            canvas = checkPageBreak(canvas, 80)
            canvas.drawText("[${finding.severity.label}] ${finding.title}", MARGIN.toFloat(), yPos.toFloat(), accentPaint)
            yPos += 18
            canvas.drawText("CVSS: ${finding.cvssScore} | Network: ${finding.networkSsid}", MARGIN.toFloat(), yPos.toFloat(), bodyPaint)
            yPos += 15
            canvas.drawText(finding.description.take(80), MARGIN.toFloat(), yPos.toFloat(), bodyPaint)
            yPos += 15
            canvas.drawText("Rec: ${finding.recommendation.take(80)}", MARGIN.toFloat(), yPos.toFloat(), bodyPaint)
            yPos += 25
        }
        finishCurrentPage()

        FileOutputStream(file).use { pdfDoc.writeTo(it) }
        pdfDoc.close()
    }

    private fun exportHtml(report: Report, file: File) {
        val sb = StringBuilder()
        sb.appendLine("<!DOCTYPE html><html><head><meta charset='utf-8'>")
        sb.appendLine("<title>WiFi Security Assessment - ${report.missionInfo.clientProfile.companyName}</title>")
        sb.appendLine("<style>body{background:#0D1117;color:#E6EDF3;font-family:monospace;padding:40px;} h1{color:#00FF41;} h2{color:#58A6FF;} .critical{color:#FF4444;} .high{color:#FF8C00;} .medium{color:#FFD700;} .low{color:#00C853;} .finding{border:1px solid #21262D;padding:16px;margin:8px 0;border-radius:8px;}</style>")
        sb.appendLine("</head><body>")
        sb.appendLine("<h1>WiFi Security Assessment</h1>")
        sb.appendLine("<p>Client: ${report.missionInfo.clientProfile.companyName}</p>")
        sb.appendLine("<p>By: ${report.companyProfile.name}</p>")
        sb.appendLine("<p>Grade: <span style='color:#00FF41;font-size:2em'>${report.overallScore}</span></p>")
        sb.appendLine("<h2>Executive Summary</h2><pre>${report.executiveSummary}</pre>")
        sb.appendLine("<h2>Findings (${report.findings.size})</h2>")
        report.findings.forEach { f ->
            val cls = f.severity.label.lowercase()
            sb.appendLine("<div class='finding'><span class='$cls'>[${f.severity.label}]</span> <strong>${f.title}</strong> (CVSS ${f.cvssScore})<br>${f.description}<br><em>Recommendation: ${f.recommendation}</em></div>")
        }
        sb.appendLine("<h2>Recommendations</h2><ol>")
        report.recommendations.forEach { r -> sb.appendLine("<li><strong>${r.title}</strong>: ${r.description}</li>") }
        sb.appendLine("</ol></body></html>")
        file.writeText(sb.toString())
    }

    private fun exportJson(report: Report, file: File) {
        val sb = StringBuilder()
        sb.appendLine("{")
        sb.appendLine("  \"id\": \"${report.id}\",")
        sb.appendLine("  \"overallScore\": \"${report.overallScore}\",")
        sb.appendLine("  \"client\": \"${report.missionInfo.clientProfile.companyName}\",")
        sb.appendLine("  \"date\": ${report.createdAt},")
        sb.appendLine("  \"findingsCount\": ${report.findings.size},")
        sb.appendLine("  \"findings\": [")
        report.findings.forEachIndexed { i, f ->
            val comma = if (i < report.findings.size - 1) "," else ""
            sb.appendLine("    {\"title\":\"${f.title}\",\"severity\":\"${f.severity.label}\",\"cvss\":${f.cvssScore},\"network\":\"${f.networkSsid}\",\"recommendation\":\"${f.recommendation}\"}$comma")
        }
        sb.appendLine("  ],")
        sb.appendLine("  \"recommendations\": [")
        report.recommendations.forEachIndexed { i, r ->
            val comma = if (i < report.recommendations.size - 1) "," else ""
            sb.appendLine("    {\"title\":\"${r.title}\",\"description\":\"${r.description}\",\"priority\":${r.priority}}$comma")
        }
        sb.appendLine("  ]")
        sb.appendLine("}")
        file.writeText(sb.toString())
    }
}
