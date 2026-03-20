package com.wificracker.core.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class JobProgress(val jobId: String = "", val status: JobStatus = JobStatus.QUEUED, val progress: Float = 0f, val message: String = "", val output: String = "")

@Singleton
class ProgressBroadcaster @Inject constructor() {
    private val _currentProgress = MutableStateFlow(JobProgress())
    val currentProgress: StateFlow<JobProgress> = _currentProgress.asStateFlow()
    fun update(progress: JobProgress) { _currentProgress.value = progress }
    fun reset() { _currentProgress.value = JobProgress() }
}
