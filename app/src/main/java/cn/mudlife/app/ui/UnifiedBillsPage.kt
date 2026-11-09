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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mudlife.app.model.CampusBillCategory
import cn.mudlife.app.model.CampusBillLedger
import cn.mudlife.app.model.DeviceExpenses
import cn.mudlife.app.model.MonthlyBillReport
import cn.mudlife.app.model.UnifiedLedger
import cn.mudlife.app.ui.theme.AppColors
import cn.mudlife.app.utils.HapticHelper
import cn.mudlife.app.utils.PrefsHelper
import cn.mudlife.campuscard.*
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalMaterialApi::class, ExperimentalMaterial3Api::class)
@Composable internal fun UnifiedBillsPage(campus: CampusViewModel, life: MainViewModel, lifeLoggedIn: Boolean, schoolLogin: () -> Unit,
    recharge: () -> Unit, pageInsets: PaddingValues = PaddingValues(0.dp)) {
    val context = LocalContext.current
    val tap = { HapticHelper.tick(context) }
    // Preserve the month stored by the previous combined statement.
    val owner = campus.currentCard?.account.orEmpty() + "|"
    val legacyOwner = owner + if (PrefsHelper.isLoggedIn) PrefsHelper.telephone else ""
    var period by remember(owner, legacyOwner) { mutableStateOf(campus.session.unifiedPeriod(owner)
        .ifBlank { campus.session.unifiedPeriod(legacyOwner) }.ifBlank { (campus.billMonth ?: YearMonth.now()).toString() }) }
    val month = remember(period) { BillPeriodPolicy.restore(period) }
    val deviceOwner = PrefsHelper.userId
    var category by rememberSaveable(owner, deviceOwner, period) { mutableStateOf(CampusBillCategory.ALL) }
    var selectedDay by rememberSaveable(owner, deviceOwner, period) { mutableStateOf<Int?>(null) }
    var categoryMenu by remember { mutableStateOf(false) }
    var balanceOpen by rememberSaveable { mutableStateOf(false) }
    var monthMenu by remember { mutableStateOf(false) }
    var detail by remember(campus.currentCard?.account) { mutableStateOf<Bill?>(null) }
    var limit by remember(period, category, selectedDay, owner, deviceOwner) { mutableIntStateOf(50) }
    val devices = life.deviceBills
    val deviceMonth = month ?: YearMonth.now()
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
    val selectedDate = month?.let { m -> selectedDay?.takeIf(m::isValidDay)?.let { m.atDay(it).toString() } }
    val rows = remember(monthRows, category, selectedDate) {
        monthRows.filter { bill ->
            (category == CampusBillCategory.ALL || CampusBillLedger.category(bill) == category) &&
                (selectedDate == null || UnifiedLedger.normalizeTime(bill.time).startsWith(selectedDate))
        }
    }
    val displayRows = remember(rows) { DeviceExpenses.rows(rows) }
    val months = remember(combined, month) { (BillLedger.months(combined) + listOfNotNull(month)).distinct().sortedDescending() }
    val devicesComplete = !lifeLoggedIn || if (month == null) devices.historyComplete &&
        months.all { it >= devices.historyStart!! && devices.monthComplete(it) } else devices.monthComplete(month)
    val complete = (!campus.hasSession || campus.billsComplete) && devicesComplete
    val previousComplete = month != null && (!campus.hasSession || campus.billsComplete) &&
        (!lifeLoggedIn || devices.monthComplete(month.minusMonths(1)))
    val today = LocalDate.now()
    val report = remember(month, combined, complete, previousComplete, today) {
        month?.let { MonthlyBillReport.build(it, combined, complete, previousComplete, today) }
    }
    val allExpense = remember(month, monthRows, complete) {
        if (month == null && (complete || monthRows.isNotEmpty())) CampusBillLedger.expense(monthRows) else null
    }
    val hasBills = campus.hasSession || lifeLoggedIn
    val selectPeriod: (String) -> Unit = { value ->
        period = value
        campus.session.saveUnifiedPeriod(owner, value)
        monthMenu = false
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
            item(key = "title") {
                if (!hasBills) {
                    PageTitle("账单", padded = false)
                } else {
                    Row(Modifier.fillMaxWidth().heightIn(min = 58.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            Row(Modifier.clickable { tap(); monthMenu = true }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(month?.let { "${it.monthValue}月账单" } ?: "全部月份", fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.SemiBold, fontSize = 25.sp, color = AppColors.TextPrimary)
                                Icon(Icons.Outlined.ExpandMore, "选择月份", Modifier.padding(start = 4.dp).size(18.dp), tint = AppColors.TextSecondary)
                            }
                            DropdownMenu(monthMenu, onDismissRequest = { monthMenu = false }, modifier = Modifier.heightIn(max = 360.dp)) {
                                DropdownMenuItem(text = { Text("全部月份") }, onClick = { tap(); selectPeriod("ALL") })
                                months.forEach { value ->
                                    DropdownMenuItem(text = { Text("${if (value == month) "✓ " else ""}${value.year}年 ${value.monthValue}月") },
                                        onClick = { tap(); selectPeriod(value.toString()) })
                                }
                            }
                        }
                        if (month == null) TextButton(onClick = { tap(); selectPeriod(YearMonth.now().toString()) }) { Text("本月月报") }
                        TextButton(onClick = { tap(); balanceOpen = true }) {
                            Icon(Icons.Outlined.AccountBalanceWallet, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp)); Text("校园卡")
                        }
                    }
                }
            }
            if (!hasBills) item(key = "balance") { CampusBalance(campus, schoolLogin, recharge) }
            if (hasBills) {
                item(key = "summary") {
                    if (report != null) {
                        key(owner, deviceOwner, period) {
                            MonthlyBillOverview(report, category, selectedDay, { selectedDay = it }, { category = it })
                        }
                    } else {
                        Text("支出合计", color = AppColors.TextSecondary, fontSize = 12.sp)
                        Text(allExpense?.let { "¥ ${Money.yuan(it)}" } ?: "— —", color = AppColors.TextPrimary,
                            fontSize = 28.sp, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    if (campus.billError != null) TextButton(onClick = { tap(); if (campus.needsLogin) schoolLogin() else campus.refreshBills() }) {
                        Text(if (campus.needsLogin) "学校登录已过期，重新登录" else "同步失败，点击重试", fontSize = 12.sp)
                    }
                    if (lifeLoggedIn && deviceMonth in devices.errors) TextButton(onClick = { tap(); devices.load(deviceMonth, force = true) }) {
                        Text(devices.errors.getValue(deviceMonth), fontSize = 12.sp)
                    }
                }
                item(key = "ledgerHeader") {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("账单流水", Modifier.weight(1f), fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = AppColors.TextPrimary)
                        selectedDay?.let { day ->
                            TextButton(onClick = { tap(); selectedDay = null }) { Text("${day}日 ×") }
                        }
                        Box {
                            TextButton(onClick = { tap(); categoryMenu = true }) {
                                Text(if (category == CampusBillCategory.ALL) "全部分类" else category.label)
                                Icon(Icons.Outlined.ExpandMore, "筛选流水分类", Modifier.size(16.dp))
                            }
                            DropdownMenu(categoryMenu, onDismissRequest = { categoryMenu = false }) {
                                CampusBillCategory.entries.forEach { choice ->
                                    DropdownMenuItem(text = { Text(choice.label) }, onClick = {
                                        tap(); category = choice; categoryMenu = false
                                    })
                                }
                            }
                        }
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
    if (balanceOpen && hasBills) {
        val dismiss = { balanceOpen = false }
        ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            dragHandle = null, containerColor = AppColors.Card, contentColor = AppColors.TextPrimary) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = 22.dp, end = 22.dp, top = 10.dp, bottom = 20.dp)) {
                Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("校园卡", Modifier.weight(1f).semantics { heading() }, fontFamily = FontFamily.Serif,
                        fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = dismiss) {
                        Icon(Icons.Outlined.Close, "关闭", Modifier.size(21.dp), tint = AppColors.TextSecondary)
                    }
                }
                CampusBalance(campus, { dismiss(); schoolLogin() }, { dismiss(); recharge() })
            }
        }
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
    val card = vm.currentCard
    val signedIn = vm.hasSession && !vm.needsLogin
    val balance = if (signedIn) card?.balance(vm.config?.accountType.orEmpty()) else null
    val faceInk = if (AppColors.isDark) AppColors.TextPrimary else AppColors.OnAccent
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = RoundedCornerShape(18.dp), color = if (AppColors.isDark) AppColors.ActiveBg else AppColors.Accent) {
            Box {
                Canvas(Modifier.matchParentSize()) {
                    val center = Offset(size.width + 10.dp.toPx(), size.height + 25.dp.toPx())
                    for (radius in listOf(66.dp, 84.dp, 102.dp)) {
                        drawCircle(faceInk.copy(alpha = .11f), radius.toPx(), center, style = Stroke(1.dp.toPx()))
                    }
                }
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CreditCard, null, Modifier.size(18.dp), tint = faceInk)
                        Spacer(Modifier.width(7.dp))
                        Text(if (balance != null && vm.error != null) "卡内余额 · 上次同步" else "卡内余额",
                            Modifier.weight(1f), fontSize = 13.sp, color = faceInk)
                        if (signedIn && card != null && card.name.isNotBlank() && card.name != "校园卡") {
                            Text(card.name, Modifier.widthIn(max = 126.dp).padding(start = 8.dp), color = faceInk,
                                fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (balance != null) Text(buildAnnotatedString {
                        withStyle(SpanStyle(fontSize = 22.sp)) { append("¥ ") }
                        append(Money.yuan(balance))
                    }, color = faceInk, fontSize = 38.sp, lineHeight = 45.sp, fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Medium)
                    else Text(when {
                        vm.needsLogin -> "学校登录已过期"
                        !vm.hasSession -> "未登录学校账号"
                        vm.busy -> "正在获取余额"
                        else -> "暂未获取余额"
                    }, color = faceInk, fontSize = 20.sp, lineHeight = 29.sp)
                }
            }
        }
        if (signedIn && vm.cards.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            vm.cards.forEach { choice -> FilterChip(card?.account == choice.account,
                onClick = { HapticHelper.tick(context); vm.selectCard(choice.account) }, label = { Text(choice.name) }) }
        }
        if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (signedIn && vm.error != null) TextButton(onClick = { HapticHelper.tick(context); vm.refresh() }) {
            Text("余额刷新失败，点击重试")
        }
        Button(onClick = { HapticHelper.tick(context); if (signedIn) recharge() else login() },
            enabled = !signedIn || card?.blocked == false, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(
                containerColor = AppColors.ActiveBg, contentColor = AppColors.Accent)) {
            Icon(if (signedIn) Icons.Outlined.Add else Icons.Outlined.Login, null, Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
            Text(when {
                !signedIn -> "登录学校账号"
                card?.blocked == true -> "此卡暂不可充值"
                else -> "充值"
            })
        }
    }
}
