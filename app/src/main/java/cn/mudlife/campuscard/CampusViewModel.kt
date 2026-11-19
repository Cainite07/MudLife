package cn.mudlife.campuscard

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
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
    var electricOptions by mutableStateOf(List(ElectricPolicy.keys.size) { emptyList<ElectricOption>() }); private set
    var electricSelected by mutableStateOf(List<ElectricOption?>(ElectricPolicy.keys.size) { null }); private set
    var electricResult by mutableStateOf<ElectricResult?>(null); private set
    var favorites by mutableStateOf(readFavorites()); private set
    private var electricLoaded = false
    private var electricJob: Job? = null
    val dorm: Dorm? get() {
        val (campus, region, building, room) = electricSelected
        return Dorm(campus ?: return null, region ?: return null, building ?: return null, room ?: return null)
    }
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
        val requestToken = authenticatedToken
        busy = true
        viewModelScope.launch {
            try {
                val freshConfig = api.config()
                val freshCards = api.cards()
                val previousAccount = selected
                val card = freshCards.find { it.account == selected } ?: freshCards.firstOrNull()
                val account = card?.account.orEmpty()
                if (!session.selectAccount(requestToken, account, card)) return@launch
                config = freshConfig; cards = freshCards; selected = account
                if (previousAccount != selected) { resetBills(); billMonth = BillPeriodPolicy.restore(session.billPeriod); resetElectric() }
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
            resetBills(); hideCode()
            cards = emptyList(); config = null; methods = emptyList(); rules = null; rulesBusy = false
            resetElectric()
            busy = false; authenticatedToken = session.token
        }
        hasSession = session.hasSession; needsLogin = false; refresh()
    }
    fun selectCard(account: String) {
        if (!session.selectAccount(authenticatedToken, account, cards.find { it.account == account })) return
        selected = account; hideCode(); methods = emptyList()
        resetBills(); billMonth = BillPeriodPolicy.restore(session.billPeriod); favorites = readFavorites(); resetElectric()
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
        if (electricLoaded) return
        queryElectric("电费服务暂时无法加载") {
            electricRules = api.electricRules()
            val campuses = api.electricOptions(0, emptyMap()).options
            if (campuses.isEmpty()) throw SchoolFailure("学校暂未返回可选校区")
            electricOptions = listOf(campuses, emptyList(), emptyList(), emptyList())
            electricSelected = List(ElectricPolicy.keys.size) { null }
            electricResult = null
            electricLoaded = true
            val favorite = favorites.firstOrNull()
            if (favorite != null) {
                selectSavedDorm(favorite)
            } else {
                campuses.firstOrNull { it.name == "校本部" }?.let { selectElectric(0, it) }
            }
        }
    }
    private suspend fun selectSavedDorm(d: Dorm) {
        require(ElectricPolicy.valid(d))
        val savedFields = d.fields()
        val names = listOf("校区", "区域", "楼栋", "房间")
        val options = mutableListOf(electricOptions[0])
        val selected = mutableListOf<ElectricOption>()
        val fields = linkedMapOf<String, String>()
        // Revalidate saved choices against the same campus and hierarchy that supplied them.
        for ((index, key) in ElectricPolicy.keys.withIndex()) {
            if (index > 0) options.add(api.electricOptions(index, fields).options)
            val option = options[index].firstOrNull { it.id == savedFields[key] }
                ?: throw SchoolFailure("常用宿舍的${names[index]}已变更，请重新选择")
            selected.add(option)
            fields[key] = option.id
        }
        val result = api.electricOptions(ElectricPolicy.keys.size, fields)
        electricOptions = options
        electricSelected = selected
        electricResult = result
    }
    fun useDorm(d: Dorm) {
        if (electricOptions[0].isEmpty()) return
        queryElectric("宿舍暂时无法查询") {
            electricResult = null
            selectSavedDorm(d)
        }
    }
    fun chooseElectric(index: Int, option: ElectricOption) {
        if (index !in electricOptions.indices || option !in electricOptions[index]) return
        queryElectric("宿舍暂时无法查询") { selectElectric(index, option) }
    }
    private fun queryElectric(fallback: String, query: suspend () -> Unit) {
        if (electricBusy) return
        electricBusy = true
        electricError = null
        electricJob = viewModelScope.launch {
            try { query() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { electricError = featureFailure(e, fallback) }
            finally { if (isActive) electricBusy = false }
        }
    }
    private suspend fun selectElectric(index: Int, option: ElectricOption) {
        electricSelected = electricSelected.take(index) + option + List(ElectricPolicy.keys.lastIndex - index) { null }
        electricOptions = electricOptions.mapIndexed { i, choices -> if (i > index) emptyList() else choices }
        electricResult = null
        val level = index + 1
        val fields = ElectricPolicy.keys.take(level).zip(electricSelected.take(level).map { requireNotNull(it).id }).toMap()
        val result = api.electricOptions(level, fields)
        if (level == ElectricPolicy.keys.size) electricResult = result
        else electricOptions = electricOptions.mapIndexed { i, choices -> if (i == level) result.options else choices }
    }
    fun refreshElectric() {
        if (electricBusy) return
        val index = electricSelected.indexOfLast { it != null }
        if (index >= 0) chooseElectric(index, requireNotNull(electricSelected[index]))
        else { electricLoaded = false; loadElectric() }
    }
    private fun resetElectric() {
        electricJob?.cancel()
        electricLoaded = false
        electricBusy = false
        electricError = null
        electricRules = null
        electricOptions = List(ElectricPolicy.keys.size) { emptyList() }
        electricSelected = List(ElectricPolicy.keys.size) { null }
        electricResult = null
    }
    private fun featureFailure(e: Exception, fallback: String): String {
        if (e is LoginExpired) { needsLogin = true; return "请更新学校登录" }
        return if (e is SchoolFailure) e.message ?: fallback else fallback
    }
    fun logout() {
        viewModelScope.coroutineContext.cancelChildren()
        busy = false; rulesBusy = false; authenticatedToken = ""
        paymentJob?.cancel(); paymentNotice = null; paymentChecking = false
        resetBills()
        viewModelScope.launch { billArchive.clear() }
        favorites = emptyList(); resetElectric()
        hideCode(); session.clear(); hasSession = false; needsLogin = false; cards = emptyList(); methods = emptyList()
        config = null; rules = null; updatedAt = 0; error = null
    }
}
