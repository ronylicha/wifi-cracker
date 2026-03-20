package com.wificracker.report.domain

import com.wificracker.report.model.Finding
import com.wificracker.report.model.Severity
import org.junit.Assert.*
import org.junit.Test

class AutoRecommenderTest {
    private val recommender = AutoRecommender()

    @Test fun `generates recommendation for open network finding`() {
        val findings = listOf(Finding(title = "Unencrypted Network", description = "Open network detected", severity = Severity.CRITICAL, cvssScore = 10f))
        val recs = recommender.generateRecommendations(findings)
        assertTrue(recs.any { it.title.contains("Encryption") })
    }

    @Test fun `generates WPS recommendation`() {
        val findings = listOf(Finding(title = "WPS Enabled", description = "WPS brute-force vulnerable", severity = Severity.HIGH, cvssScore = 7f))
        val recs = recommender.generateRecommendations(findings)
        assertTrue(recs.any { it.title.contains("WPS") })
    }

    @Test fun `deduplicates recommendations`() {
        val findings = listOf(
            Finding(title = "Open Net 1", description = "unencrypted open", severity = Severity.CRITICAL, cvssScore = 10f),
            Finding(title = "Open Net 2", description = "another open network", severity = Severity.CRITICAL, cvssScore = 10f),
        )
        val recs = recommender.generateRecommendations(findings)
        assertEquals(1, recs.count { it.title.contains("Encryption") })
    }

    @Test fun `results sorted by priority`() {
        val findings = listOf(
            Finding(title = "WPS enabled", description = "wps vulnerable", severity = Severity.HIGH, cvssScore = 7f),
            Finding(title = "Open", description = "unencrypted open", severity = Severity.CRITICAL, cvssScore = 10f),
        )
        val recs = recommender.generateRecommendations(findings)
        assertTrue(recs.first().priority <= recs.last().priority)
    }
}
