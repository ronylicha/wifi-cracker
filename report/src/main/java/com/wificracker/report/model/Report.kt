package com.wificracker.report.model

data class Report(
    val id: String = java.util.UUID.randomUUID().toString(),
    val missionInfo: MissionInfo = MissionInfo(),
    val companyProfile: CompanyProfile = CompanyProfile(),
    val findings: List<Finding> = emptyList(),
    val recommendations: List<Recommendation> = emptyList(),
    val overallScore: String = "N/A",
    val executiveSummary: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val status: ReportStatus = ReportStatus.DRAFT,
)

enum class ReportStatus { DRAFT, IN_PROGRESS, COMPLETED, EXPORTED }
