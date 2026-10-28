package cn.mudlife.app.utils

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class AccountRequestsTest {
    @Test fun lateReplyCannotWriteIntoAnotherAccount() = runBlocking {
        var owner = listOf("token-a", "a")
        val requests = AccountRequests(this) { owner }
        val reply = CompletableDeferred<String>()
        var saved: String? = null
        val job = requests.launch { saved = request { reply.await() } }
        yield()
        owner = listOf("token-b", "b")
        reply.complete("old-code")
        job.join()
        assertNull(saved)
        assertTrue(job.isCancelled)
    }
    @Test fun logoutCancelsWorkAndAllowsNewSession() = runBlocking {
        val requests = AccountRequests(this) { listOf("token", "account") }
        var stopped = false
        val old = requests.launch { request { try { awaitCancellation() } finally { stopped = true } } }
        yield()
        requests.invalidate()
        old.join()
        var saved = false
        requests.launch { request { Unit }; saved = true }.join()
        assertTrue(stopped)
        assertTrue(saved)
    }
    @Test fun newLoginCancelsOldWorkBeforeStartingNewRequests() = runBlocking {
        var owner = listOf("a")
        val requests = AccountRequests(this) { owner }
        val old = requests.launch { request { awaitCancellation() } }
        yield()
        owner = listOf("b")
        requests.launch { request { Unit } }.join()
        old.join()
        assertTrue(old.isCancelled)
    }
    @Test fun lateFailureCannotShowAnErrorForTheNewAccount() = runBlocking {
        var owner = listOf("a")
        val requests = AccountRequests(this) { owner }
        val reply = CompletableDeferred<Unit>()
        var errorShown = false
        val job = requests.launch {
            try { request { reply.await() } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { errorShown = true }
        }
        yield(); owner = listOf("b"); reply.completeExceptionally(java.io.IOException("offline"))
        job.join()
        assertFalse(errorShown)
    }
    @Test fun signedOutAccountDoesNotStartNetworkWork() = runBlocking {
        val requests = AccountRequests(this) { listOf("") }
        var called = false
        requests.launch { request { called = true } }.join()
        assertFalse(called)
    }
    @Test fun joinedRefreshWaitsForEveryActualRequest() = runBlocking {
        val requests = AccountRequests(this) { listOf("a") }
        val fast = CompletableDeferred<Unit>(); val slow = CompletableDeferred<Unit>()
        var refreshing = true
        val refresh = launch { try {
            listOf(requests.launch { request { fast.await() } }, requests.launch { request { slow.await() } }).joinAll()
        } finally { refreshing = false } }
        yield(); fast.complete(Unit); yield()
        assertTrue(refreshing)
        slow.complete(Unit); refresh.join()
        assertFalse(refreshing)
    }
}
