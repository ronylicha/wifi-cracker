package com.wificracker.report.domain

import com.wificracker.report.model.Finding
import com.wificracker.report.model.Severity
import javax.inject.Inject
import javax.inject.Singleton

data class RiskSummary(
    val overallGrade: String,
    val criticalCount: Int,
    val highCount: Int,
    val mediumCount: Int,
    val lowCount: Int,
    val infoCount: Int,
    val averageCvss: Float,
)

@Singleton
class RiskRating @Inject constructor(private val cvssCalculator: CvssCalculator) {

    fun computeSummary(findings: List<Finding>): RiskSummary {
        val scores = findings.map { it.cvssScore }
        val avg = if (scores.isEmpty()) 0f else scores.average().toFloat()
        return RiskSummary(
            overallGrade = cvssCalculator.scoreToGrade(avg),
            criticalCount = findings.count { it.severity == Severity.CRITICAL },
            highCount = findings.count { it.severity == Severity.HIGH },
            mediumCount = findings.count { it.severity == Severity.MEDIUM },
            lowCount = findings.count { it.severity == Severity.LOW },
            infoCount = findings.count { it.severity == Severity.INFO },
            averageCvss = avg,
        )
    }
}
