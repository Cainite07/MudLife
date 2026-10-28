package cn.mudlife.app.utils

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/** Keep version comparison, but stop waiting on dead mirrors after a useful reply. */
internal suspend fun collectManifests(
    sources: List<String>, minimumVersion: Int, settleMillis: Long = 500, timeoutMillis: Long = 10_000,
    fetch: suspend (String) -> UpdateInfo?
): UpdateInfo? = coroutineScope {
    val replies = Channel<UpdateInfo?>(Channel.UNLIMITED)
    val jobs = sources.map { source -> launch {
        val reply = try { fetch(source) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
        replies.send(reply)
    } }
    var best: UpdateInfo? = null
    var received = 0
    suspend fun receive() {
        val reply = replies.receive()
        received++
        if (reply != null && reply.versionCode > (best?.versionCode ?: 0)) best = reply
    }
    try {
        withTimeoutOrNull(timeoutMillis) {
            while (received < sources.size) {
                receive()
                if ((best?.versionCode ?: 0) >= minimumVersion) {
                    withTimeoutOrNull(settleMillis) { while (received < sources.size) receive() }
                    break
                }
            }
        }
        best
    } finally { jobs.forEach { it.cancel() } }
}
