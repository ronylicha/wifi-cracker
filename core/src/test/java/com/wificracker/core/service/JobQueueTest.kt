package com.wificracker.core.service

import org.junit.Assert.*
import org.junit.Test

class JobQueueTest {
    private val queue = JobQueue()
    @Test fun `enqueue adds job`() { queue.enqueue(Job.ScanJob(interfaceName = "wlan0")); assertEquals(1, queue.size()) }
    @Test fun `dequeue returns first`() { val j1 = Job.ScanJob(interfaceName = "w0"); val j2 = Job.ScanJob(interfaceName = "w1"); queue.enqueue(j1); queue.enqueue(j2); assertEquals(j1.id, queue.dequeue()?.id); assertEquals(1, queue.size()) }
    @Test fun `dequeue empty returns null`() { assertNull(queue.dequeue()) }
    @Test fun `cancel removes job`() { val j = Job.ScanJob(interfaceName = "w0"); queue.enqueue(j); queue.cancel(j.id); assertTrue(queue.isEmpty()) }
    @Test fun `clear removes all`() { queue.enqueue(Job.ScanJob(interfaceName = "w0")); queue.enqueue(Job.ScanJob(interfaceName = "w1")); queue.clear(); assertTrue(queue.isEmpty()) }
}
