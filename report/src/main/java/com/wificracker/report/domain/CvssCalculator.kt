package com.wificracker.report.domain

import com.wificracker.report.model.Severity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CvssCalculator @Inject constructor() {

    fun scoreToCvss(score: Float): Severity = when {
        score >= 9.0f -> Severity.CRITICAL
        score >= 7.0f -> Severity.HIGH
        score >= 4.0f -> Severity.MEDIUM
        score >= 0.1f -> Severity.LOW
        else -> Severity.INFO
    }

    fun calculateOverallScore(scores: List<Float>): Float {
        if (scores.isEmpty()) return 0f
        return scores.max()
    }

    fun scoreToGrade(averageScore: Float): String = when {
        averageScore <= 1.0f -> "A"
        averageScore <= 3.0f -> "B"
        averageScore <= 5.0f -> "C"
        averageScore <= 7.0f -> "D"
        else -> "F"
    }
}
