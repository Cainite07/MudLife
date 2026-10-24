package cn.mudlife.campuscard

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.cancelChildren
import java.io.IOException
import java.time.YearMonth
import org.json.JSONArray

class CampusViewModel(app: Application) : AndroidViewModel(app) {
    val session = SessionStore(app)
    private val api = SchoolApi(session)
    var startupCode by mutableStateOf(session.startupCode); private set
    var brightCode by mutableStateOf(session.brightCode); private set
    var paymentNotice by mutableStateOf<String?>(null); private set
    var paymentChecking by mutableStateOf(false); private set
    private var paymentJob: Job? = null
    private var lastPaymentCheck = 0L
    var identity by mutableStateOf<StudentIdentity?>(null); private set
    var identityBusy by mutableStateOf(false); private set
    var identityError by mutableStateOf<String?>(null); private set
    var billRecords by mutableStateOf(emptyList<Bill>()); private set
    val billTypes = BillPolicy.filters
    var billBusy by mutableStateOf(false); private set
    var billError by mutableStateOf<String?>(null); private set
    var billMonth by mutableStateOf(BillPeriodPolicy.restore(session.billPeriod)); private set
    var billType by mutableStateOf(""); private set
    var billIncome by mutableStateOf<Long?>(null); private set
    var billExpenses by mutableStateOf<Long?>(null); private set
    var billHasMore by mutableStateOf(false); private set
    var billSummaryBusy by mutableStateOf(false); private set
    var billSummaryError by mutableStateOf<String?>(null); private set
    var billTotal by mutableStateOf<Long?>(null); private set
    var billSyncBusy by mutableStateOf(false); private set
    var billMonths by mutableStateOf(listOf(YearMonth.now())); private set
    private val billArchive = BillArchiveStore(app)
    var allBills by mutableStateOf(emptyList<Bill>()); private set
    var billsComplete by mutableStateOf(false); private set
    private var billsOwner = ""
    private var billsLoadedAt = 0L
    private var billLimit = 30
    private var billJob: Job? = null
    private var billSummaryJob: Job? = null
    private var billGeneration = 0
    var electricBusy by mutableStateOf(false); private set
    var electricError by mutableStateOf<String?>(null); private set
    var electricRules by mutableStateOf<ElectricRules?>(null); private set
    var electricOptions by mutableStateOf(List(3) { emptyList<ElectricOption>() }); private set
    var electricSelected by mutableStateOf(List<ElectricOption?>(3) { null }); private set
    var electricResult by mutableStateOf<ElectricResult?>(null); private set
    var favorites by mutableStateOf(readFavorites()); private set
    private var electricLoaded = false
    val dorm: Dorm? get() = electricSelected.let { s -> if (s.any { it == null }) null else Dorm(ElectricOption("校本部", "校本部"), s[0]!!, s[1]!!, s[2]!!) }
    var hasSession by mutableStateOf(session.hasSession); private set
    var needsLogin by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var cards by mutableStateOf(emptyList<CampusCard>()); private set
    var config by mutableStateOf<SchoolConfig?>(null); private set
    var selected by mutableStateOf(session.selectedAccount); private set
    var updatedAt by mutableStateOf(0L); private set
    var methods by mutableStateOf(emptyList<CodeMethod>()); private set
    var selectedMethod by mutableStateOf(""); private set
    var code by mutableStateOf<PaymentCode?>(null); private set
    var codeSeconds by mutableStateOf(0); private set
    var codeBusy by mutableStateOf(false); private set
    var codeError by mutableStateOf<String?>(null); private set
    var rules by mutableStateOf<RechargeRules?>(null); private set
    var rulesBusy by mutableStateOf(false); private set
    var rulesError by mutableStateOf<String?>(null); private set
    private var codeJob: Job? = null
    private var codeGeneration = 0L
    private var codeVisible = false
    private var foreground = true
    private var authenticatedToken = session.token
    val currentCard get() = cards.find { it.account == selected } ?: cards.firstOrNull()

    init { if (hasSession) refresh() }

    fun refresh() {
        if (busy || !session.hasSession) return
        busy = true
        viewModelScope.launch {
            try {
                val freshConfig = api.config()
                val freshCards = api.cards()
                config = freshConfig; cards = freshCards
                val previousAccount = selected
                selected = freshCards.find { it.account == selected }?.account ?: freshCards.firstOrNull()?.account.orEmpty()
                session.selectedAccount = selected
                if (previousAccount != selected) { resetBills(); billMonth = BillPeriodPolicy.restore(session.billPeriod) }
                favorites = readFavorites()
                hasSession = true; needsLogin = false; error = null; updatedAt = System.currentTimeMillis()
            } catch (_: LoginExpired) {
                needsLogin = true; error = "学校会话需要更新，本机登录记录已保留"; hideCode()
            } catch (e: IOException) {
                error = e.takeIf { it is SchoolFailure }?.message ?: "暂时连接不上学校服务，请检查网络后重试。登录已保留。"
            } catch (_: Exception) { error = "学校数据暂时无法识别，登录已保留，请稍后重试" }
            finally { busy = false }
        }
    }
    fun authenticated() {
        if (authenticatedToken != session.token) {
            viewModelScope.coroutineContext.cancelChildren()
            resetBills(); hideCode(); identity = null; identityError = null; identityBusy = false
            cards = emptyList(); config = null; methods = emptyList(); rules = null; rulesBusy = false
            electricLoaded = false; electricBusy = false; electricSelected = List(3) { null }; electricResult = null
            busy = false; authenticatedToken = session.token
        }
        hasSession = session.hasSession; needsLogin = false; refresh()
    }
    fun selectCard(account: String) {
        selected = account; session.selectedAccount = account; hideCode(); methods = emptyList(); identity = null
        resetBills(); billMonth = BillPeriodPolicy.restore(session.billPeriod); favorites = readFavorites(); electricLoaded = false; electricSelected = List(3) { null }; electricResult = null
    }
    fun selectMethod(id: String) { selectedMethod = id; generateCode() }
    fun codeVisibility(visible: Boolean) {
        codeVisible = visible
        if (visible && foreground && hasSession && !needsLogin) generateCode() else hideCode()
    }
    fun pause() { foreground = false; hideCode(); paymentJob?.cancel(); paymentChecking = false }
    fun resume() {
        foreground = true
        if (codeVisible && hasSession && !needsLogin) generateCode()
        if (hasSession && System.currentTimeMillis() - updatedAt > 60000) refresh()
        checkPendingPayment()
    }
    fun checkPendingPayment() {
        if (!foreground || !hasSession || paymentJob?.isActive == true || SystemClock.elapsedRealtime() - lastPaymentCheck < 5000) return
        val payment = PendingPayment.capture(session) ?: return
        lastPaymentCheck = SystemClock.elapsedRealtime(); paymentChecking = true
        paymentJob = viewModelScope.launch {
            try {
                var state = PaymentState.UNKNOWN
                for (wait in listOf(0L, 1500L, 3000L, 5000L)) {
                    delay(wait)
                    if (!foreground || !payment.matches(session)) return@launch
                    state = api.pendingPaymentStatus(payment)
                    if (state != PaymentState.AWAITING) break
                }
                if (!payment.matches(session) || !foreground) return@launch
                paymentNotice = PaymentPolicy.message(state)
                if (state == PaymentState.PAID) { session.clearPending(); confirmedPayment(payment.kind) }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { paymentNotice = featureFailure(e, "暂时无法核对支付，原订单已保留") }
            finally { paymentChecking = false }
        }
    }
    fun confirmedPayment(kind: String) {
        paymentNotice = PaymentPolicy.message(PaymentState.PAID)
        refresh(); refreshBills()
        if (kind == "electric" && dorm != null) refreshElectric()
    }
    private fun hideCode() { codeGeneration++; codeJob?.cancel(); codeJob = null; code = null; codeSeconds = 0; codeBusy = false }

    fun generateCode() {
        hideCode()
        if (!codeVisible || !foreground || !hasSession || needsLogin) return
        val generation = codeGeneration
        codeJob = viewModelScope.launch {
            try {
                while (codeVisible && foreground) {
                    codeBusy = true; codeError = null
                    methods = api.methods().filter { it.account == currentCard?.account && it.available }
                    val method = methods.find { it.id == selectedMethod } ?: methods.firstOrNull()
                        ?: throw SchoolFailure("学校暂未提供此卡的可用付款方式")
                    selectedMethod = method.id
                    val started = SystemClock.elapsedRealtime()
                    val fresh = api.code(method, config?.codeRefreshSeconds ?: 60)
                    val deadline = started + fresh.validSeconds * 1000L - 1500L
                    if (deadline <= SystemClock.elapsedRealtime()) throw SchoolFailure("付款码已过期，请重新刷新")
                    code = fresh; codeBusy = false
                    while (foreground && codeVisible) {
                        codeSeconds = ((deadline - SystemClock.elapsedRealtime()) / 1000L).toInt().coerceAtLeast(0)
                        if (codeSeconds <= 0) break
                        delay(1000)
                    }
                    code = null
                }
            } catch (_: LoginExpired) { needsLogin = true; codeError = "学校会话需要更新" }
            catch (e: IOException) { codeError = e.takeIf { it is SchoolFailure }?.message ?: "付款码加载失败，请检查网络后重试" }
            finally { if (generation == codeGeneration) { codeBusy = false; code = null; codeSeconds = 0 } }
        }
    }
    fun loadRechargeRules() {
        if (rulesBusy) return
        val fee = config?.feeItem?.takeIf { it.isNotBlank() } ?: return
        rulesBusy = true; rulesError = null; rules = null
        viewModelScope.launch {
            try { rules = api.rechargeRules(fee) }
            catch (_: LoginExpired) { needsLogin = true; rulesError = "请先更新学校登录" }
            catch (e: IOException) { rulesError = e.takeIf { it is SchoolFailure }?.message ?: "充值规则暂时无法获取，请稍后重试" }
            finally { rulesBusy = false }
        }
    }
    fun updateStartupCode(value: Boolean) { startupCode = value; session.startupCode = value }
    fun updateBrightCode(value: Boolean) { brightCode = value; session.brightCode = value }
    fun loadIdentity(force: Boolean = false) {
        if (identityBusy || (!force && identity != null)) return
        val account = currentCard?.account ?: return
        identityBusy = true; identityError = null
        viewModelScope.launch {
            try { val fresh = api.identity(account); if (currentCard?.account == account) identity = fresh }
            catch (e: Exception) { identityError = featureFailure(e, "电子学生卡暂时无法加载") }
            finally { identityBusy = false }
        }
    }
    fun changeBillMonth(month: YearMonth?) {
        session.billPeriod = BillPeriodPolicy.save(month)
        if (month == billMonth) return
        billMonth = month; billLimit = 30; rebuildBills(); if (!billsComplete) loadBillSummary()
    }
    fun changeBillType(type: String) {
        if (type == billType || type !in billTypes.map { it.id }) return
        billType = type; billLimit = 30; rebuildBills()
    }
    fun enterBills() = syncBills(false)
    fun refreshBills() = syncBills(true)
    fun loadBills(reset: Boolean = false, force: Boolean = false) {
        if (reset || force || billError != null) syncBills(force = true)
        else { billLimit += 30; rebuildBills() }
    }
    private fun rebuildBills() {
        val rows = BillLedger.select(allBills, billMonth, billType)
        billRecords = rows.take(billLimit); billHasMore = rows.size > billLimit
        billMonths = BillLedger.months(allBills)
        billTotal = if (billsComplete) rows.size.toLong() else null
        if (billsComplete) {
            billSummaryJob?.cancel(); billSummaryBusy = false; billSummaryError = null
            val summary = BillLedger.summary(allBills, billMonth)
            billIncome = summary.income; billExpenses = summary.expenses
        }
    }
    private fun loadBillSummary() {
        billSummaryJob?.cancel()
        val month = billMonth; val generation = billGeneration
        billIncome = null; billExpenses = null; billSummaryBusy = true; billSummaryError = null
        billSummaryJob = viewModelScope.launch {
            try {
                val result = api.billSummary(month)
                if (generation == billGeneration && month == billMonth && !billsComplete) { billIncome = result.income; billExpenses = result.expenses }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { if (generation == billGeneration && month == billMonth) billSummaryError = featureFailure(e, "统计暂时无法加载") }
            finally { if (generation == billGeneration && month == billMonth) billSummaryBusy = false }
        }
    }
    private fun syncBills(force: Boolean) {
        val owner = currentCard?.account ?: return
        if (!hasSession || owner.isBlank()) return
        if (billJob?.isActive == true) { if (!force) return else billJob?.cancel() }
        if (owner != billsOwner) { resetBills(); billsOwner = owner }
        if (!force && billsComplete && System.currentTimeMillis() - billsLoadedAt < 60000) { rebuildBills(); return }
        val generation = ++billGeneration
        billSyncBusy = true; billBusy = allBills.isEmpty(); billError = null
        billJob = viewModelScope.launch {
            try {
                if (allBills.isEmpty()) {
                    val stored = billArchive.read(owner)
                    if (generation != billGeneration || currentCard?.account != owner) return@launch
                    if (stored != null) { allBills = stored.records; billsComplete = true; billsLoadedAt = stored.savedAt; billBusy = false; rebuildBills() }
                    if (!force && stored != null && System.currentTimeMillis() - stored.savedAt < 60000) return@launch
                }
                val hadComplete = billsComplete
                if (!hadComplete) loadBillSummary()
                val first = api.bills(1, null, "")
                if (generation != billGeneration || currentCard?.account != owner) return@launch
                val incoming = first.records.toMutableList()
                if (!hadComplete) { allBills = incoming.toList(); billBusy = false; rebuildBills() }
                for (batch in (2..first.pages).toList().chunked(3)) {
                    val pages = coroutineScope { batch.map { page -> async { api.bills(page, null, "") } }.awaitAll() }
                    if (generation != billGeneration || currentCard?.account != owner) return@launch
                    pages.forEach { incoming.addAll(it.records) }
                    if (!hadComplete) { allBills = incoming.distinctBy(BillLedger::key).sortedByDescending { it.time }; rebuildBills() }
                }
                val fresh = incoming.distinctBy(BillLedger::key).sortedByDescending { it.time }
                if (first.total != null && fresh.size.toLong() != first.total) throw SchoolFailure("账单记录有更新，请下拉重新同步")
                allBills = fresh; billsComplete = true; billsLoadedAt = System.currentTimeMillis(); rebuildBills()
                billArchive.write(owner, BillArchive(fresh, billsLoadedAt))
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { if (generation == billGeneration) billError = featureFailure(e, "账单同步失败，已有记录已保留") }
            finally { if (generation == billGeneration) { billBusy = false; billSyncBusy = false } }
        }
    }
    private fun resetBills() {
        billJob?.cancel(); billSummaryJob?.cancel(); billGeneration++
        allBills = emptyList(); billsComplete = false; billsOwner = ""; billsLoadedAt = 0; billLimit = 30
        billRecords = emptyList(); billTotal = null; billIncome = null; billExpenses = null
        billBusy = false; billSyncBusy = false; billSummaryBusy = false; billError = null; billSummaryError = null; billMonths = listOf(YearMonth.now())
    }
    private fun readFavorites(): List<Dorm> = runCatching { JSONArray(session.electricFavorites).objects().mapNotNull(Dorm::parse) }.getOrDefault(emptyList())
    private fun saveFavorites() { session.electricFavorites = JSONArray().apply { favorites.forEach { put(it.toJson()) } }.toString() }
    fun rememberDorm() { val d = dorm ?: return; favorites = (listOf(d) + favorites.filter { it.fields() != d.fields() }).take(8); saveFavorites() }
    fun removeDorm(d: Dorm) { favorites = favorites.filter { it.fields() != d.fields() }; saveFavorites() }
    fun loadElectric() {
        if (electricBusy || electricLoaded) return
        electricBusy = true; electricError = null
        viewModelScope.launch {
            try {
                electricRules = api.electricRules()
                electricOptions = listOf(api.electricOptions(1, mapOf("campus" to "校本部")).options, emptyList(), emptyList())
                electricLoaded = true
                favorites.firstOrNull()?.let { selectSavedDorm(it) }
            } catch (e: Exception) { electricError = featureFailure(e, "电费服务暂时无法加载") }
            finally { electricBusy = false }
        }
    }
    private suspend fun selectSavedDorm(d: Dorm) {
        require(ElectricPolicy.valid(d))
        // Revalidate every saved choice against the current school hierarchy.
        val regions = electricOptions[0]
        if (regions.none { it.id == d.region.id }) throw SchoolFailure("常用宿舍区域已变更，请重新选择")
        val buildings = api.electricOptions(2, mapOf("campus" to "校本部", "building" to d.region.id)).options
        if (buildings.none { it.id == d.building.id }) throw SchoolFailure("常用宿舍楼栋已变更，请重新选择")
        val rooms = api.electricOptions(3, mapOf("campus" to "校本部", "building" to d.region.id, "floor" to d.building.id)).options
        if (rooms.none { it.id == d.room.id }) throw SchoolFailure("常用宿舍房间已变更，请重新选择")
        electricOptions = listOf(regions, buildings, rooms)
        electricSelected = listOf(regions.first { it.id == d.region.id }, buildings.first { it.id == d.building.id }, rooms.first { it.id == d.room.id })
        electricResult = api.electricOptions(4, d.fields(), true)
    }
    fun useDorm(d: Dorm) {
        if (electricBusy) return
        electricBusy = true; electricError = null; electricResult = null; electricSelected = List(3) { null }
        viewModelScope.launch { try { selectSavedDorm(d) } catch (e: Exception) { electricError = featureFailure(e, "宿舍暂时无法查询") } finally { electricBusy = false } }
    }
    fun chooseElectric(index: Int, option: ElectricOption) {
        if (electricBusy || index !in 0..2 || electricOptions[index].none { it.id == option.id }) return
        electricSelected = electricSelected.mapIndexed { i, old -> if (i < index) old else if (i == index) option else null }
        electricOptions = electricOptions.mapIndexed { i, list -> if (i > index) emptyList() else list }
        electricResult = null; electricBusy = true; electricError = null
        viewModelScope.launch {
            try {
                val fields = mutableMapOf("campus" to "校本部")
                electricSelected.forEachIndexed { i, o -> if (o != null) fields[ElectricPolicy.keys[i + 1]] = o.id }
                val result = api.electricOptions(index + 2, fields, index == 2)
                if (index == 2) electricResult = result else electricOptions = electricOptions.mapIndexed { i, old -> if (i == index + 1) result.options else old }
            } catch (e: Exception) { electricError = featureFailure(e, "宿舍暂时无法查询") }
            finally { electricBusy = false }
        }
    }
    fun refreshElectric() { dorm?.let(::useDorm) ?: run { electricLoaded = false; loadElectric() } }
    private fun featureFailure(e: Exception, fallback: String): String {
        if (e is LoginExpired) { needsLogin = true; return "请更新学校登录" }
        return if (e is SchoolFailure) e.message ?: fallback else fallback
    }
    fun logout() {
        viewModelScope.coroutineContext.cancelChildren()
        busy = false; identityBusy = false; rulesBusy = false; electricBusy = false; authenticatedToken = ""
        paymentJob?.cancel(); paymentNotice = null; paymentChecking = false
        resetBills()
        viewModelScope.launch { billArchive.clear() }
        identity = null; favorites = emptyList(); electricSelected = List(3) { null }; electricOptions = List(3) { emptyList() }; electricResult = null; electricLoaded = false; electricRules = null
        hideCode(); session.clear(); hasSession = false; needsLogin = false; cards = emptyList(); methods = emptyList()
        config = null; rules = null; updatedAt = 0; error = null
    }
}
