package cn.mudlife.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mudlife.app.model.CampusBillCategory
import cn.mudlife.app.model.CampusBillLedger
import cn.mudlife.app.model.DeviceExpenses
import cn.mudlife.app.ui.theme.AppColors
import cn.mudlife.app.utils.HapticHelper
import cn.mudlife.app.utils.PrefsHelper
import cn.mudlife.campuscard.*

@OptIn(ExperimentalMaterialApi::class)
@Composable internal fun UnifiedBillsPage(campus: CampusViewModel, life: MainViewModel, lifeLoggedIn: Boolean, schoolLogin: () -> Unit,
    recharge: () -> Unit, pageInsets: PaddingValues = PaddingValues(0.dp)) {
    val context = LocalContext.current
    val tap = { HapticHelper.tick(context) }
    // Preserve the month stored by the previous combined statement.
    val owner = campus.currentCard?.account.orEmpty() + "|"
    val legacyOwner = owner + if (PrefsHelper.isLoggedIn) PrefsHelper.telephone else ""
    var period by remember(owner) { mutableStateOf(campus.session.unifiedPeriod(owner)
        .ifBlank { campus.session.unifiedPeriod(legacyOwner) }.ifBlank { BillPeriodPolicy.save(campus.billMonth) }) }
    val month = remember(period) { BillPeriodPolicy.restore(period) }
    var category by rememberSaveable { mutableStateOf(CampusBillCategory.ALL) }
    var monthMenu by remember { mutableStateOf(false) }
    var detail by remember(campus.currentCard?.account) { mutableStateOf<Bill?>(null) }
    var limit by remember(period, category, campus.currentCard?.account) { mutableIntStateOf(50) }
    val devices = life.deviceBills
    val deviceMonth = month ?: java.time.YearMonth.now()
    LaunchedEffect(deviceMonth, lifeLoggedIn) {
        devices.attach(context)
        devices.load(deviceMonth)
    }
    val earliestSchoolMonth = remember(campus.allBills) { BillLedger.months(campus.allBills).minOrNull() }
    LaunchedEffect(lifeLoggedIn, earliestSchoolMonth) {
        if (lifeLoggedIn) {
            devices.attach(context)
            devices.syncHistory(earliestSchoolMonth)
        }
    }
    val deviceRecords = if (lifeLoggedIn) devices.records.values.flatten() else emptyList()
    val combined = remember(campus.allBills, deviceRecords) { DeviceExpenses.merge(campus.allBills, deviceRecords) }
    val monthRows = remember(combined, month) { CampusBillLedger.select(combined, month) }
    val rows = remember(monthRows, category) { monthRows.filter { category == CampusBillCategory.ALL || CampusBillLedger.category(it) == category } }
    val displayRows = remember(rows) { DeviceExpenses.rows(rows) }
    val months = remember(combined, month) { (BillLedger.months(combined) + listOfNotNull(month)).distinct().sortedDescending() }
    val devicesComplete = !lifeLoggedIn || if (month == null) devices.historyComplete &&
        months.all { it >= devices.historyStart!! && devices.monthComplete(it) } else devices.monthComplete(month)
    val complete = (!campus.hasSession || campus.billsComplete) && devicesComplete
    // Across months, show the known records explicitly as a synced subtotal.
    val expense = when {
        month == null && monthRows.isNotEmpty() -> CampusBillLedger.expense(monthRows)
        complete -> CampusBillLedger.expense(monthRows)
        else -> null
    }
    var expandedWater by rememberSaveable(owner, period, PrefsHelper.userId) { mutableStateOf(emptyList<String>()) }
    val refreshing = campus.billSyncBusy || campus.busy || deviceMonth in devices.foregroundLoading
    val pull = rememberPullRefreshState(refreshing, {
        if (campus.needsLogin && !lifeLoggedIn) schoolLogin() else {
            if (campus.hasSession && !campus.needsLogin) { campus.refresh(); campus.refreshBills() }
            devices.load(deviceMonth, force = true)
        }
    })
    val direction = LocalLayoutDirection.current
    Box(Modifier.fillMaxSize().pullRefresh(pull)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(
            start = 22.dp + pageInsets.calculateStartPadding(direction), end = 22.dp + pageInsets.calculateEndPadding(direction),
            top = pageInsets.calculateTopPadding(), bottom = 24.dp + pageInsets.calculateBottomPadding())) {
            item(key = "title") { PageTitle("账单", padded = false) }
            item(key = "balance") { CampusBalance(campus, schoolLogin, recharge) }
            if (campus.hasSession || lifeLoggedIn) {
                item(key = "summary") {
                    Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            TextButton(onClick = { tap(); monthMenu = true }, contentPadding = PaddingValues(0.dp)) {
                                Text(month?.let { "${it.year}年 ${it.monthValue}月" } ?: "全部月份", fontSize = 14.sp)
                                Icon(Icons.Outlined.ExpandMore, "选择月份", Modifier.padding(start = 4.dp).size(18.dp))
                            }
                            DropdownMenu(monthMenu, onDismissRequest = { monthMenu = false }, modifier = Modifier.heightIn(max = 360.dp)) {
                                DropdownMenuItem(text = { Text("全部月份") }, onClick = { tap(); period = "ALL"; campus.session.saveUnifiedPeriod(owner, period); monthMenu = false })
                                months.forEach { m -> DropdownMenuItem(text = { Text("${if (m == month) "✓ " else ""}${m.year}年 ${m.monthValue}月") }, onClick = { tap(); period = m.toString(); campus.session.saveUnifiedPeriod(owner, period); monthMenu = false }) }
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(if (month == null) "支出合计" else "本月支出", fontSize = 11.sp, color = AppColors.TextSecondary)
                            Text(expense?.let { "¥ ${Money.yuan(it)}" } ?: "— —", fontSize = 22.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        CampusBillCategory.entries.forEach { item ->
                            FilterChip(category == item, onClick = { tap(); category = item }, label = { Text(item.label, fontSize = 12.sp) },
                                shape = RoundedCornerShape(12.dp), colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AppColors.ActiveBg, selectedLabelColor = AppColors.Accent, labelColor = AppColors.TextSecondary))
                        }
                    }
                    if (campus.billError != null) TextButton(onClick = { tap(); if (campus.needsLogin) schoolLogin() else campus.refreshBills() }) {
                        Text(if (campus.needsLogin) "学校登录已过期，重新登录" else "同步失败，点击重试", fontSize = 12.sp)
                    }
                    if (lifeLoggedIn && deviceMonth in devices.errors) TextButton(onClick = { tap(); devices.load(deviceMonth, force = true) }) {
                        Text(devices.errors.getValue(deviceMonth), fontSize = 12.sp)
                    }

                }
                displayRows.take(limit).groupBy { it.day }.forEach { (day, entries) ->
                    item(key = "day:$day") { Text(day, fontSize = 12.sp, color = AppColors.TextSecondary,
                        modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 6.dp)) }
                    items(entries, key = { it.key }) { entry ->
                        if (!entry.drinking) StatementBillRow(entry.bills.single()) { tap(); detail = it }
                        else {
                            val expanded = day in expandedWater
                            val total = CampusBillLedger.expense(entry.bills)
                            Row(Modifier.fillMaxWidth().clickable { tap(); expandedWater = if (expanded) expandedWater - day else expandedWater + day }
                                .heightIn(min = 68.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(38.dp).background(AppColors.Card, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Outlined.WaterDrop, null, Modifier.size(19.dp), tint = AppColors.Accent)
                                }
                                Text("饮水 · ${entry.bills.size} 笔", Modifier.weight(1f).padding(horizontal = 12.dp), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text(total?.let { "−${Money.yuan(it)}" } ?: "—", fontSize = 15.sp)
                                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, if (expanded) "收起饮水明细" else "展开饮水明细",
                                    Modifier.padding(start = 6.dp).size(18.dp), tint = AppColors.TextSecondary)
                            }
                            HorizontalDivider(color = AppColors.Border.copy(alpha = .45f), modifier = Modifier.padding(start = 50.dp))
                            if (expanded) Column(Modifier.padding(start = 16.dp)) {
                                entry.bills.forEach { bill -> StatementBillRow(bill) { tap(); detail = it } }
                            }
                        }
                    }
                }
                if (displayRows.size > limit) item { TextButton(onClick = { tap(); limit += 50 }, modifier = Modifier.fillMaxWidth()) { Text("加载更多") } }
                if (rows.isEmpty() && !refreshing) item { Text(if (campus.billError != null) "账单暂时无法加载" else "这个月份暂无${if (category == CampusBillCategory.ALL) "" else category.label}记录",
                    color = AppColors.TextSecondary, modifier = Modifier.padding(vertical = 28.dp)) }
            }
        }
        PullRefreshIndicator(refreshing, pull, Modifier.align(Alignment.TopCenter).padding(top = pageInsets.calculateTopPadding()),
            backgroundColor = AppColors.Card)
    }
    detail?.let { bill -> AlertDialog(onDismissRequest = { detail = null }, title = { Text(bill.title) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(bill.fen?.takeUnless { it == Long.MIN_VALUE }?.let { "¥ ${Money.yuan(kotlin.math.abs(it))}" } ?: "金额未知", fontSize = 26.sp)
            Text(bill.time); Text(CampusBillLedger.category(bill).label)
            if (bill.description.isNotBlank()) Text(bill.description)
            if (bill.id.isNotBlank()) Text("订单 ${bill.id}", fontSize = 12.sp)
        }
    }, confirmButton = { TextButton(onClick = { tap(); detail = null }) { Text("关闭") } }) }
}

@Composable private fun StatementBillRow(bill: Bill, open: (Bill) -> Unit) {
    val kind = CampusBillLedger.category(bill)
    Row(Modifier.fillMaxWidth().clickable { open(bill) }.heightIn(min = 68.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).background(AppColors.Card, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(when (kind) {
                CampusBillCategory.BATH -> Icons.Outlined.Shower
                CampusBillCategory.FOOD -> Icons.Outlined.Restaurant
                CampusBillCategory.WATER -> Icons.Outlined.WaterDrop
                CampusBillCategory.DRYER -> Icons.Outlined.Air
                CampusBillCategory.RECHARGE -> Icons.Outlined.AccountBalanceWallet
                else -> Icons.Outlined.ReceiptLong
            }, null, Modifier.size(19.dp), tint = AppColors.Accent)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(bill.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text("${kind.label} · ${bill.time.drop(11).take(5)}${if (DeviceExpenses.pending(bill)) " · 待核实" else ""}", fontSize = 11.sp, color = AppColors.TextSecondary)
        }
        Text(bill.displayAmount, fontSize = 15.sp, color = if (BillLedger.direction(bill) == "1") AppColors.TextSecondary else AppColors.TextPrimary)
    }
    HorizontalDivider(color = AppColors.Border.copy(alpha = .45f), modifier = Modifier.padding(start = 50.dp))
}

@Composable private fun CampusBalance(vm: CampusViewModel, login: () -> Unit, recharge: () -> Unit) {
    val context = LocalContext.current
    Surface(color = AppColors.Card, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("校园卡余额", fontSize = 12.sp, color = AppColors.TextSecondary)
                    Text(vm.currentCard?.balance(vm.config?.accountType.orEmpty())?.let { "¥ ${Money.yuan(it)}" } ?: "— —",
                        fontSize = 30.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 7.dp))
                }
                if (!vm.hasSession || vm.needsLogin) Button(onClick = { HapticHelper.tick(context); login() }, shape = RoundedCornerShape(12.dp)) { Text("登录学校账号") }
                else FilledTonalButton(onClick = { HapticHelper.tick(context); recharge() }, enabled = vm.currentCard?.blocked == false,
                    shape = RoundedCornerShape(12.dp)) { Text("充值") }
            }
            if (vm.cards.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                vm.cards.forEach { card -> FilterChip(vm.currentCard?.account == card.account, onClick = { HapticHelper.tick(context); vm.selectCard(card.account) }, label = { Text(card.name) }) }
            }
            vm.error?.let {
                TextButton(onClick = { HapticHelper.tick(context); if (vm.needsLogin) login() else vm.refresh() }, contentPadding = PaddingValues(0.dp)) {
                    Text(if (vm.needsLogin) "学校登录已过期" else "余额刷新失败，点击重试", fontSize = 12.sp)
                }
            }
            if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 10.dp))
        }
    }
}
