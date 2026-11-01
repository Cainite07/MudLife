package cn.mudlife.app.ui

import androidx.compose.runtime.*
import cn.mudlife.app.api.*
import cn.mudlife.app.model.DeviceExpense
import cn.mudlife.app.model.DeviceExpenses
import cn.mudlife.app.utils.AccountRequests
import cn.mudlife.app.utils.PrefsHelper
import kotlinx.coroutines.*
import java.time.YearMonth

/** Demand-loaded monthly records. No background six-month sweep or second bath ledger. */
internal class DeviceBillStore(scope: CoroutineScope) {
    private val requests = AccountRequests(scope) { listOf(PrefsHelper.loginCode, PrefsHelper.userId) }
    private var owner = emptyList<String>()
    private val jobs = mutableMapOf<YearMonth, Job>()
    private val loadedAt = mutableMapOf<YearMonth, Long>()
    var records by mutableStateOf<Map<YearMonth, List<DeviceExpense>>>(emptyMap()); private set
    var errors by mutableStateOf<Map<YearMonth, String>>(emptyMap()); private set
    var loading by mutableStateOf<Set<YearMonth>>(emptySet()); private set

    fun clear() {
        requests.invalidate(); jobs.clear(); loadedAt.clear()
        records = emptyMap(); errors = emptyMap(); loading = emptySet(); owner = emptyList()
    }

    fun load(month: YearMonth, force: Boolean = false) {
        val identity = listOf(PrefsHelper.loginCode, PrefsHelper.userId)
        if (owner != identity) { clear(); owner = identity }
        if (!PrefsHelper.isLoggedIn || jobs[month]?.isActive == true) return
        if (!force && System.currentTimeMillis() - (loadedAt[month] ?: 0) < 60_000) return
        jobs[month] = requests.launch {
            loading = loading + month; errors = errors - month
            try {
                val projects = request { NetworkModule.apiService.getUserProjectsSafe() }
                check(projects.success && !projects.data.isNullOrEmpty()) { "设备账号暂时无法同步" }
                val targets = projects.data!!.filter { it.projectId > 0 && it.accountId > 0 }.distinctBy { it.projectId to it.accountId }
                check(targets.isNotEmpty()) { "未找到设备账号" }
                val incoming = mutableListOf<DeviceExpense>()
                for (batch in targets.chunked(3)) {
                    val results = request { coroutineScope { batch.map { target -> async {
                        NetworkModule.apiService.getBillListSafe(month.toString(), projectId = target.projectId.toString(), accountId = target.accountId.toString())
                    } }.awaitAll() } }
                    results.forEach { result ->
                        check(result.success) { "设备账单暂时无法同步" }
                        val raw = result.data.orEmpty()
                        val accepted = raw.mapNotNull(DeviceExpenses::parse).filter { it.bill.time.startsWith(month.toString()) }
                        cn.mudlife.app.utils.AppLogger.i("DeviceBills", "$month: records=${raw.size}, independent=${accepted.size}")
                        incoming += accepted
                    }
                }
                records = records + (month to incoming.distinctBy { it.bill.id to it.bill.time })
                loadedAt[month] = System.currentTimeMillis()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { errors = errors + (month to "饮水与吹风机记录同步失败，点击重试") }
            finally { if (isCurrent) loading = loading - month }
        }
    }
}
