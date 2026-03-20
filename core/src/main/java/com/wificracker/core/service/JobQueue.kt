package com.wificracker.core.service

import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JobQueue @Inject constructor() {
    private val queue = ConcurrentLinkedQueue<Job>()
    fun enqueue(job: Job) { queue.add(job) }
    fun dequeue(): Job? = queue.poll()
    fun cancel(jobId: String) { queue.removeAll { it.id == jobId } }
    fun clear() { queue.clear() }
    fun size(): Int = queue.size
    fun isEmpty(): Boolean = queue.isEmpty()
    fun peek(): Job? = queue.peek()
}
