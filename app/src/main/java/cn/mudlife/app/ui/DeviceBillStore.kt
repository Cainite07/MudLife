package cn.mudlife.app.ui

import android.content.Context
import androidx.compose.runtime.*
import cn.mudlife.app.api.*
import cn.mudlife.app.data.*
import cn.mudlife.app.model.*
import cn.mudlife.app.utils.AccountRequests
import cn.mudlife.app.utils.PrefsHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.YearMonth

/** Cached, resumable monthly history. Network concurrency stays bounded across foreground and backfill. */
internal class DeviceBillStore(
    scope: CoroutineScope,
    private val identity: () -> List<String> = { listOf(PrefsHelper.loginCode, PrefsHelper.userId, PrefsHelper.telephone) },
    private val ownerKey: () -> String = { PrefsHelper.userId.ifBlank { PrefsHelper.telephone } },
    private val fetchProjects: suspend () -> List<UserProjectItem> = {
        val result = NetworkModule.apiService.getUserProjectsSafe()
        check(result.success) { "设备账号暂时无法同步" }; result.data.orEmpty()
    },
    private val fetchBills: suspend (YearMonth, UserProjectItem) -> List<BillItem> = { month, target ->
        val result = NetworkModule.apiService.getBillListSafe(month.toString(), projectId = target.projectId.toString(), accountId = target.accountId.toString())
        check(result.success) { "设备账单暂时无法同步" }; result.data.orEmpty()
    },
    private var archive: DeviceBillPersistence? = null,
    private val now: () -> Long = System::currentTimeMillis,
    private val today: () -> YearMonth = YearMonth::now,
    private val initialStart: () -> YearMonth = { today().minusMonths(26) },
    private val betweenMonths: Long = 120L,
    private val report: (String) -> Unit = { cn.mudlife.app.utils.AppLogger.i("DeviceBills", it) }
) {
    private val requests = AccountRequests(scope, identity)
    private var owner = emptyList<String>()
    private var diskOwner = ""
    private var restored = false
    private val lock = Mutex()
    private val jobs = mutableMapOf<YearMonth, Job>()
    private var targets = emptyList<UserProjectItem>()
    private var targetsLoadedAt = 0L
    private var historyJob: Job? = null
    private var historyGeneration = 0L
    private var cached by mutableStateOf<Map<YearMonth, CachedDeviceMonth>>(emptyMap())
    private var projectKey by mutableStateOf("")
    var records by mutableStateOf<Map<YearMonth, List<DeviceExpense>>>(emptyMap()); private set
    var errors by mutableStateOf<Map<YearMonth, String>>(emptyMap()); private set
    var loading by mutableStateOf<Set<YearMonth>>(emptySet()); private set
    var foregroundLoading by mutableStateOf<Set<YearMonth>>(emptySet()); private set
    var historyStart by mutableStateOf<YearMonth?>(null); private set
    var historyRunning by mutableStateOf(false); private set
    var historyError by mutableStateOf<String?>(null); private set

    val historyMonths: List<YearMonth> get() = historyStart?.let { DeviceHistory.months(it, today()) }.orEmpty()
    val historyDone: Int get() = historyMonths.count { monthComplete(it) }
    val historyComplete: Boolean get() = historyStart != null && historyMonths.all { monthComplete(it) }
    fun monthComplete(month: YearMonth) = month in records && cached[month]?.projects == projectKey &&
        projectKey.isNotBlank() && month !in errors && month !in loading

    fun attach(context: Context) { if (archive == null) archive = DeviceBillArchive(context) }

    fun clear() {
        requests.invalidate(); historyGeneration++; jobs.clear(); targets = emptyList(); targetsLoadedAt = 0
        cached = emptyMap(); records = emptyMap(); errors = emptyMap(); loading = emptySet(); foregroundLoading = emptySet()
        restored = false; projectKey = ""; owner = emptyList(); diskOwner = ""
        historyStart = null; historyRunning = false; historyError = null; historyJob = null
    }

    private fun ready(): Boolean {
        val captured = identity()
        if (owner != captured) { clear(); owner = captured; diskOwner = ownerKey() }
        return captured.firstOrNull()?.isNotBlank() == true && diskOwner.isNotBlank()
    }

    private suspend fun AccountRequests.Request.restore() {
        if (restored) return
        val saved = request { archive?.read(diskOwner) ?: DeviceBillSnapshot() }
        cached = saved.months; records = saved.months.mapValues { it.value.entries }; historyStart = saved.start
        projectKey = saved.months.values.maxByOrNull { it.savedAt }?.projects.orEmpty()
        restored = true
    }

    private suspend fun AccountRequests.Request.query(month: YearMonth, force: Boolean) = lock.withLock {
        ensureCurrent()
        loading = loading + month
        try {
            restore()
            if (targets.isEmpty() || now() - targetsLoadedAt >= 60_000) {
                val found = request { fetchProjects() }.filter { it.projectId > 0 && it.accountId > 0 }.distinctBy { it.projectId to it.accountId }
                check(found.isNotEmpty()) { "未找到设备账号" }
                targets = found; targetsLoadedAt = now()
                projectKey = "expense-v2:" + found.map { "${it.projectId}:${it.accountId}" }.sorted().joinToString("|")
            }
            val previous = cached[month]
            if (!force && month !in errors && previous?.projects == projectKey && DeviceHistory.fresh(month, today(), previous.savedAt, now())) {
                errors = errors - month
                return@withLock
            }
            val incoming = mutableListOf<DeviceExpense>()
            for (batch in targets.chunked(3)) {
                val results = request { coroutineScope { batch.map { target -> async { fetchBills(month, target) } }.awaitAll() } }
                results.forEach { raw -> incoming += raw.mapNotNull(DeviceExpenses::parse).filter { it.bill.time.startsWith(month.toString()) } }
            }
            val value = CachedDeviceMonth(incoming.distinctBy { it.bill.id to it.bill.time }, now(), projectKey)
            request { archive?.saveMonth(diskOwner, month, value) }
            cached = cached + (month to value); records = records + (month to value.entries); errors = errors - month
            report("$month: independent=${value.entries.size}, saved=${archive != null}")
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            errors = errors + (month to "设备账单同步失败，已有记录已保留")
            throw e
        } finally { if (isCurrent) loading = loading - month }
    }

    fun load(month: YearMonth, force: Boolean = false): Job? {
        if (!ready()) return null
        if (jobs[month]?.isActive == true) return jobs[month]
        val job = requests.launch {
            foregroundLoading = foregroundLoading + month
            try { query(month, force) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Error state belongs to the requested month. */ }
            finally { if (isCurrent) foregroundLoading = foregroundLoading - month }
        }
        jobs[month] = job
        return job
    }

    /** Resume silently; cover recent months first and extend to any earlier known record. */
    fun syncHistory(start: YearMonth? = null): Job? {
        if (!ready()) return null
        require(start == null || !start.isAfter(today()))
        if (historyJob?.isActive == true && (start == null || historyStart?.let { start >= it } == true)) return historyJob
        historyJob?.cancel()
        val generation = ++historyGeneration
        val job = requests.launch {
            try {
                val first = lock.withLock {
                    restore()
                    val selected = listOfNotNull(start, historyStart, initialStart(), records.keys.minOrNull()).min()
                    if (selected != historyStart) request { archive?.saveStart(diskOwner, selected) }
                    historyStart = selected
                    selected
                } ?: return@launch
                if (generation != historyGeneration) return@launch
                historyRunning = true; historyError = null
                for (month in DeviceHistory.months(first, today())) {
                    ensureCurrent()
                    query(month, false)
                    delay(betweenMonths)
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (generation == historyGeneration) historyError = "同步中断，已保存进度，可继续" }
            finally { if (isCurrent && generation == historyGeneration) historyRunning = false }
        }
        historyJob = job
        return job
    }
}
