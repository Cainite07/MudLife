package cn.mudlife.app.utils

import kotlinx.coroutines.*

/** Used on the main thread, like the account preferences and ViewModel state it protects. */
internal class AccountRequests(private val scope: CoroutineScope, private val identity: () -> List<String>) {
    private var generation = 0L
    private var owner: List<String>? = null
    private val jobs = mutableSetOf<Job>()

    fun invalidate() {
        generation++
        jobs.toList().forEach { it.cancel() }
        jobs.clear()
    }

    fun launch(block: suspend Request.() -> Unit): Job {
        val captured = identity()
        if (owner != captured) { invalidate(); owner = captured }
        val request = Request(generation, captured)
        val job = scope.launch(start = CoroutineStart.LAZY) {
            request.ensureCurrent()
            request.block()
        }
        jobs += job
        job.invokeOnCompletion { jobs.remove(job) }
        job.start()
        return job
    }

    inner class Request internal constructor(private val epoch: Long, private val captured: List<String>) {
        val isCurrent: Boolean get() = generation == epoch && captured == identity() && captured.firstOrNull()?.isNotBlank() == true
        suspend fun ensureCurrent() {
            currentCoroutineContext().ensureActive()
            if (!isCurrent) throw CancellationException("Account changed")
        }
        suspend fun <T> request(call: suspend () -> T): T {
            ensureCurrent()
            return try { call().also { ensureCurrent() } }
            catch (e: Exception) { ensureCurrent(); throw e }
        }
    }
}
