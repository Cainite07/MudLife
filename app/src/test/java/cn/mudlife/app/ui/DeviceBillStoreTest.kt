package cn.mudlife.app.ui

import cn.mudlife.app.api.UserProjectItem
import cn.mudlife.app.data.*
import cn.mudlife.app.model.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.time.YearMonth

class DeviceBillStoreTest {
    private val end = YearMonth.of(2026, 11)
    private val start = end.minusMonths(2)
    private val clock = 1_800_000_000_000L
    private val projects = listOf(UserProjectItem(1, 1, 10), UserProjectItem(1, 2, 20), UserProjectItem(1, 3, 30))
    private class Disk : DeviceBillPersistence {
        val owners = mutableMapOf<String, DeviceBillSnapshot>()
        override suspend fun read(owner: String) = owners[owner] ?: DeviceBillSnapshot()
        override suspend fun saveMonth(owner: String, month: YearMonth, value: CachedDeviceMonth) {
            val old = read(owner); owners[owner] = old.copy(months = old.months + (month to value))
        }
        override suspend fun saveStart(owner: String, start: YearMonth) { owners[owner] = read(owner).copy(start = start) }
    }
    private fun bill(month: YearMonth, id: String = "expense") = BillItem(consumeBillDTO = BillDTO(
        orderId = id, consumeDate = "$month-02 12:00:00", consumeMoney = "0.02", deviceTypeName = "直饮水", deductTypeName = "支付宝"))
    private fun store(scope: CoroutineScope, disk: Disk, identity: () -> List<String> = { listOf("token", "a") },
        targets: suspend () -> List<UserProjectItem> = { projects }, initial: YearMonth = start,
        fetch: suspend (YearMonth, UserProjectItem) -> List<BillItem>) = DeviceBillStore(scope,
        identity = identity, ownerKey = { identity()[1] }, fetchProjects = targets, fetchBills = fetch,
        archive = disk, now = { clock }, today = { end }, initialStart = { initial }, betweenMonths = 0, report = {})

    @Test fun emptyMonthsDoNotStopHistoryAndEachSuccessfulMonthIsSaved() = runBlocking {
        val disk = Disk(); val queried = mutableSetOf<YearMonth>()
        val vm = store(this, disk) { month, _ -> queried += month; if (month == end.minusMonths(1)) emptyList() else listOf(bill(month)) }
        vm.syncHistory(start)!!.join()
        assertEquals(setOf(start, start.plusMonths(1), end), queried)
        assertEquals(3, vm.historyDone); assertTrue(vm.historyComplete)
        assertEquals(0, disk.read("a").months.getValue(end.minusMonths(1)).entries.size)
        assertEquals(start, disk.read("a").start)
    }

    @Test fun failedProjectDoesNotSaveAPartialMonthAndRestartResumesMissingMonths() = runBlocking {
        val disk = Disk(); var failing = true
        val calls = mutableListOf<YearMonth>()
        val fetch: suspend (YearMonth, UserProjectItem) -> List<BillItem> = { month, target ->
            calls += month
            if (failing && month == end.minusMonths(1) && target.projectId == 2L) error("offline")
            listOf(bill(month))
        }
        val first = store(this, disk, fetch = fetch); first.syncHistory(start)!!.join()
        assertFalse(first.historyComplete); assertNotNull(first.historyError)
        assertEquals(setOf(end), disk.read("a").months.keys)
        calls.clear(); failing = false
        val reopened = store(this, disk, fetch = fetch); reopened.syncHistory()!!.join()
        assertFalse(calls.contains(end)); assertTrue(calls.contains(start))
        assertTrue(reopened.historyComplete)
    }

    @Test fun oldParserCacheIsSilentlyRequeriedEvenWhenFresh() = runBlocking {
        val disk = Disk()
        disk.saveMonth("a", end, CachedDeviceMonth(emptyList(), clock, "1:10|2:20|3:30"))
        var calls = 0
        val vm = store(this, disk) { month, _ -> calls++; listOf(bill(month)) }
        vm.load(end)!!.join()
        assertEquals(3, calls)
        assertEquals(1, vm.records.getValue(end).size)
        assertTrue(disk.read("a").months.getValue(end).projects.startsWith("expense-v2:"))
    }

    @Test fun reopeningRestoresRecordsWithoutRefetchingFreshMonth() = runBlocking {
        val disk = Disk(); var calls = 0
        val fetch: suspend (YearMonth, UserProjectItem) -> List<BillItem> = { month, _ -> calls++; listOf(bill(month)) }
        store(this, disk, fetch = fetch).load(end)!!.join()
        val before = calls
        val reopened = store(this, disk, fetch = fetch); reopened.load(end)!!.join()
        assertEquals(before, calls); assertEquals(1, reopened.records[end]!!.size)
    }

    @Test fun historyAndForegroundShareTheSameThreeNetworkSlots() = runBlocking {
        var active = 0; var peak = 0
        val entered = CompletableDeferred<Unit>()
        val vm = store(this, Disk()) { _, _ ->
            active++; peak = maxOf(peak, active); entered.complete(Unit)
            delay(5); active--; emptyList()
        }
        val history = vm.syncHistory(start)!!; entered.await()
        val foreground = vm.load(end.minusMonths(1), force = true)!!
        joinAll(history, foreground)
        assertEquals(3, peak); assertEquals(0, active); assertTrue(vm.historyComplete)
    }

    @Test fun newProjectInvalidatesPreviouslyCompletedCoverage() = runBlocking {
        val disk = Disk()
        store(this, disk, targets = { projects.take(1) }) { month, _ -> listOf(bill(month)) }.syncHistory(start)!!.join()
        val vm = store(this, disk) { month, _ -> listOf(bill(month)) }
        vm.load(end)!!.join()
        assertFalse(vm.historyComplete)
        assertEquals(1, vm.historyDone)
        vm.syncHistory()!!.join(); assertTrue(vm.historyComplete)
    }

    @Test fun interruptedHistoryResumesSilentlyAfterRestart() = runBlocking {
        val disk = Disk(); val blocked = CompletableDeferred<Unit>(); var shouldBlock = true
        val fetch: suspend (YearMonth, UserProjectItem) -> List<BillItem> = { month, _ ->
            if (shouldBlock && month == end.minusMonths(1)) { blocked.complete(Unit); awaitCancellation() }
            emptyList()
        }
        val first = store(this, disk, fetch = fetch); val job = first.syncHistory(start)!!
        blocked.await(); first.clear(); job.join(); yield()
        shouldBlock = false
        val reopened = store(this, disk, fetch = fetch); reopened.syncHistory()!!.join()
        assertTrue(reopened.historyComplete); assertFalse(reopened.historyRunning)
    }

    @Test fun accountChangeCannotWriteLateResultsIntoNewOwner() = runBlocking {
        val disk = Disk(); var account = "a"; val reply = CompletableDeferred<List<BillItem>>()
        val entered = CompletableDeferred<Unit>()
        val vm = store(this, disk, identity = { listOf("token-$account", account) }) { month, _ ->
            if (account == "a") { entered.complete(Unit); withContext(NonCancellable) { reply.await() } }
            else listOf(bill(month, "new-owner"))
        }
        val old = vm.load(end)!!; entered.await(); account = "b"
        val new = vm.load(end)!!; reply.complete(listOf(bill(end, "old-owner")))
        joinAll(old, new)
        assertTrue(old.isCancelled)
        assertFalse(disk.read("a").months.containsKey(end))
        assertEquals("new-owner", vm.records[end]!!.single().order)
    }

    @Test fun failedRefreshKeepsSavedRecordsAndDoesNotClaimComplete() = runBlocking {
        val disk = Disk(); var fail = false; var calls = 0
        val vm = store(this, disk) { month, _ -> calls++; if (fail) error("offline") else listOf(bill(month)) }
        vm.load(end)!!.join(); val saved = vm.records[end]
        fail = true; vm.load(end, force = true)!!.join()
        assertEquals(saved, vm.records[end]); assertTrue(end in vm.errors); assertFalse(vm.monthComplete(end))
        val failedCalls = calls; fail = false; vm.load(end)!!.join()
        assertTrue(calls > failedCalls); assertTrue(vm.monthComplete(end))
    }

    @Test fun automaticHistoryCovers27MonthsAndExtendsWithoutForegroundSpinner() = runBlocking {
        val disk = Disk(); val queried = mutableSetOf<YearMonth>()
        lateinit var vm: DeviceBillStore
        vm = store(this, disk, initial = end.minusMonths(26)) { month, _ ->
            assertTrue(vm.foregroundLoading.isEmpty()); queried += month; emptyList()
        }
        vm.syncHistory()!!.join()
        assertEquals(27, queried.size); assertTrue(vm.historyComplete)
        queried.clear(); vm.syncHistory(end.minusMonths(27))!!.join()
        assertEquals(setOf(end.minusMonths(27)), queried); assertEquals(28, vm.historyDone)
    }

    @Test fun calendarPlanningCrossesYearBoundariesAndNeverStopsAtAnEmptyGap() {
        assertEquals(listOf(YearMonth.of(2025, 2), YearMonth.of(2025, 1), YearMonth.of(2024, 12)),
            DeviceHistory.months(YearMonth.of(2024, 12), YearMonth.of(2025, 2)))
        assertEquals(27, DeviceHistory.months(YearMonth.of(2024, 9), YearMonth.of(2026, 11)).size)
        assertTrue(DeviceHistory.fresh(start, end, clock, clock + 60_001))
        assertFalse(DeviceHistory.fresh(end, end, clock, clock + 60_001))
        assertFalse(DeviceHistory.fresh(start, end, clock, clock + 31L * 24 * 60 * 60 * 1000))
    }
}
