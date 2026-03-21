package com.wificracker.report.domain

import com.wificracker.report.model.*
import com.wificracker.report.ui.SessionStats
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReportGenerator @Inject constructor(
    private val cvssCalculator: CvssCalculator,
    private val riskRating: RiskRating,
    private val autoRecommender: AutoRecommender,
) {
    fun generateReport(
        missionInfo: MissionInfo,
        companyProfile: CompanyProfile,
        findings: List<Finding>,
        manualRecommendations: List<Recommendation> = emptyList(),
        sessionStats: SessionStats = SessionStats(),
    ): Report {
        val autoRecs = autoRecommender.generateRecommendations(findings)
        val allRecs = (manualRecommendations + autoRecs).distinctBy { it.title }.sortedBy { it.priority }
        val summary = riskRating.computeSummary(findings)

        val executiveSummary = buildExecutiveSummary(missionInfo, findings, summary, sessionStats)

        return Report(
            missionInfo = missionInfo,
            companyProfile = companyProfile,
            findings = findings.sortedByDescending { it.cvssScore },
            recommendations = allRecs,
            overallScore = summary.overallGrade,
            executiveSummary = executiveSummary,
            status = ReportStatus.COMPLETED,
        )
    }

    private fun buildExecutiveSummary(missionInfo: MissionInfo, findings: List<Finding>, summary: RiskSummary, sessionStats: SessionStats): String {
        return buildString {
            appendLine("WiFi Security Assessment - Executive Summary")
            appendLine()
            appendLine("Assessment Date: ${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(missionInfo.date))}")
            appendLine("Scope: ${missionInfo.scope}")
            appendLine()

            // Session activity overview
            if (sessionStats.networksScanned > 0 || sessionStats.attacksPerformed > 0) {
                appendLine("Assessment Activity:")
                if (sessionStats.networksScanned > 0) appendLine("  Networks Scanned: ${sessionStats.networksScanned}")
                if (sessionStats.attacksPerformed > 0) appendLine("  Attacks Performed: ${sessionStats.attacksPerformed} (${sessionStats.attacksSuccessful} successful)")
                if (sessionStats.cracksAttempted > 0) appendLine("  Crack Attempts: ${sessionStats.cracksAttempted} (${sessionStats.cracksSuccessful} successful)")
                if (sessionStats.passwordsFound.isNotEmpty()) appendLine("  Passwords Recovered: ${sessionStats.passwordsFound.size}")
                appendLine()
            }

            appendLine("Overall Security Grade: ${summary.overallGrade}")
            appendLine("Average CVSS Score: ${"%.1f".format(summary.averageCvss)}/10.0")
            appendLine()
            appendLine("Findings Summary:")
            appendLine("  Critical: ${summary.criticalCount}")
            appendLine("  High: ${summary.highCount}")
            appendLine("  Medium: ${summary.mediumCount}")
            appendLine("  Low: ${summary.lowCount}")
            appendLine("  Info: ${summary.infoCount}")
            appendLine("  Total: ${findings.size}")
            appendLine()
            if (summary.criticalCount > 0) appendLine("IMMEDIATE ACTION REQUIRED: ${summary.criticalCount} critical vulnerabilities were identified that require urgent remediation.")
            if (sessionStats.passwordsFound.isNotEmpty()) appendLine("WARNING: ${sessionStats.passwordsFound.size} WiFi password(s) were successfully recovered during this assessment, demonstrating insufficient password complexity.")
        }
    }
}
