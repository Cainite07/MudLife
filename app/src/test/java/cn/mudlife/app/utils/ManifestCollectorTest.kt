package cn.mudlife.app.utils

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ManifestCollectorTest {
    private fun version(code: Int) = UpdateInfo(versionCode = code, versionName = "$code")
    @Test(timeout = 5000) fun slowMirrorIsCancelledAfterUsefulReply() = runBlocking {
        var cancelled = false
        val result = collectManifests(listOf("fast", "slow"), 35, 40, 2000) {
            if (it == "fast") version(36) else try { awaitCancellation() } finally { cancelled = true }
        }
        assertEquals(36, result?.versionCode)
        assertTrue(cancelled)
    }
    @Test(timeout = 5000) fun newerReplyWithinWindowBeatsOldCache() = runBlocking {
        val result = collectManifests(listOf("cache", "fresh"), 35, 200, 2000) {
            if (it == "cache") version(35) else { delay(30); version(36) }
        }
        assertEquals(36, result?.versionCode)
    }
    @Test(timeout = 5000) fun olderThanInstalledReplyDoesNotStartShortWindow() = runBlocking {
        val result = collectManifests(listOf("old", "fresh"), 35, 10, 2000) {
            if (it == "old") version(34) else { delay(70); version(36) }
        }
        assertEquals(36, result?.versionCode)
    }
    @Test(timeout = 5000) fun allFailedSourcesReturnNoManifest() = runBlocking {
        assertNull(collectManifests(listOf("empty", "broken"), 35, 40, 2000) {
            if (it == "broken") throw java.io.IOException("offline") else null
        })
    }
    @Test(timeout = 5000) fun deadlineRetainsAnOlderValidReply() = runBlocking {
        val result = collectManifests(listOf("old", "slow"), 35, 20, 70) {
            if (it == "old") version(34) else awaitCancellation()
        }
        assertEquals(34, result?.versionCode)
    }
    @Test(timeout = 5000) fun callerCancellationStopsAllSources() = runBlocking {
        val started = CompletableDeferred<Unit>(); var stopped = false
        val job = launch {
            collectManifests(listOf("slow"), 35) {
                started.complete(Unit)
                try { awaitCancellation() } finally { stopped = true }
            }
        }
        started.await(); job.cancelAndJoin()
        assertTrue(stopped)
    }
}
