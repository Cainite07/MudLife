package cn.mudlife.campuscard

import cn.mudlife.app.BuildConfig

import android.graphics.Bitmap
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.time.YearMonth

@OptIn(ExperimentalMaterialApi::class)
@Composable private fun Refreshable(refreshing: Boolean, refresh: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberPullRefreshState(refreshing, refresh)
    Box(Modifier.fillMaxSize().pullRefresh(state)) {
        content()
        PullRefreshIndicator(refreshing, state, Modifier.align(Alignment.TopCenter), backgroundColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.primary)
    }
}

@Composable internal fun WalletPage(vm: CampusViewModel, recharge: () -> Unit) {
    Refreshable(vm.busy, vm::refresh) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            if (vm.cards.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                vm.cards.forEach { c -> FilterChip(selected = vm.currentCard?.account == c.account, onClick = { vm.selectCard(c.account) }, label = { Text(c.name) }) }
            }
            val card = vm.currentCard
            val colors = MaterialTheme.colorScheme
            Surface(Modifier.fillMaxWidth(), color = colors.surface, shape = RoundedCornerShape(18.dp), border = BorderStroke(.7.dp, colors.outline)) {
                Column(Modifier.padding(20.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(card?.name ?: "校园卡", fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Text(if (card?.blocked == true) "卡片受限" else "江苏大学", fontSize = 11.sp, color = colors.onSurfaceVariant)
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 23.dp, bottom = 20.dp), verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            Text("账户余额", fontSize = 11.sp, color = colors.onSurfaceVariant)
                            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 6.dp)) {
                                Text("¥", fontSize = 16.sp, modifier = Modifier.padding(end = 5.dp, bottom = 5.dp), color = colors.onSurfaceVariant)
                                Text(card?.balance(vm.config?.accountType.orEmpty())?.let(Money::yuan) ?: "— —", fontSize = 35.sp, fontWeight = FontWeight.Normal, letterSpacing = (-.8).sp)
                            }
                        }
                        Button(onClick = recharge, enabled = card != null && !vm.needsLogin && !card.blocked,
                            shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 18.dp), modifier = Modifier.height(40.dp).padding(bottom = 2.dp)) {
                            Text("充值", fontSize = 14.sp)
                        }
                    }
                    HorizontalDivider(color = colors.outline.copy(alpha = .65f))
                    Row(Modifier.fillMaxWidth().padding(top = 13.dp)) {
                        Text(card?.owner.orEmpty(), fontSize = 12.sp, modifier = Modifier.weight(1f), color = colors.onSurfaceVariant)
                        Text(card?.number?.takeIf { it.isNotBlank() }?.let { "尾号 ${it.takeLast(4)}" }.orEmpty(), fontSize = 11.sp, color = colors.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable internal fun CodePage(vm: CampusViewModel) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Paper {
            Text(vm.currentCard?.name ?: "校园卡", fontSize = 16.sp)
            Spacer(Modifier.height(20.dp))
            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                val code = vm.code
                if (code != null && vm.codeSeconds > 0) {
                    val bitmap = remember(code.value) {
                        val matrix = QRCodeWriter().encode(code.value, BarcodeFormat.QR_CODE, 800, 800, mapOf(EncodeHintType.MARGIN to 2))
                        Bitmap.createBitmap(IntArray(800 * 800) { i -> if (matrix[i % 800, i / 800]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }, 800, 800, Bitmap.Config.ARGB_8888).asImageBitmap()
                    }
                    Image(bitmap, "校园付款二维码", Modifier.fillMaxSize())
                } else if (vm.codeBusy) CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp)
                else Text(vm.codeError ?: "付款码已隐藏", color = Color(0xFF343A31), fontSize = 14.sp, modifier = Modifier.padding(20.dp))
            }
            Text(if (vm.code != null) "${vm.codeSeconds} 秒后更新" else "", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 16.dp))
            TextButton(onClick = vm::generateCode, enabled = !vm.codeBusy, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("刷新付款码") }
        }
        if (vm.methods.size > 1) vm.methods.forEach { method ->
            Row(Modifier.fillMaxWidth().clickable { vm.selectMethod(method.id) }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = vm.selectedMethod == method.id, onClick = { vm.selectMethod(method.id) }); Text(method.name, fontSize = 14.sp)
            }
        }
        Paper {
            StartupSetting(vm)
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("出示时提亮并保持常亮", fontSize = 14.sp, modifier = Modifier.weight(1f).padding(end = 12.dp))
                Switch(checked = vm.brightCode, onCheckedChange = vm::updateBrightCode)
            }
        }
    }
}

@Composable internal fun StartupSetting(vm: CampusViewModel) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("启动时打开付款码", fontSize = 15.sp, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = vm.startupCode, onCheckedChange = vm::updateStartupCode)
    }
}
@Composable internal fun ProfilePage(vm: CampusViewModel, login: () -> Unit, logout: () -> Unit, electric: () -> Unit) {
    Refreshable(vm.identityBusy, { vm.loadIdentity(true) }) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            val identity = vm.identity
            if (identity != null) {
                StudentCard(identity)
                if (identity.portrait == null) TextButton(onClick = { vm.loadIdentity(true) }, enabled = !vm.identityBusy) { Text("照片未加载，重试") }
            }
            else Paper {
                Text("电子学生卡", fontFamily = FontFamily.Serif, fontSize = 19.sp)
                if (vm.identityBusy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 22.dp))
                vm.identityError?.let { Text(it, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp)); TextButton(onClick = { vm.loadIdentity(true) }) { Text("重试") } }
            }
            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Text("其他功能", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
                Paper { ProfileRow("电费", Icons.Outlined.Bolt, electric) }
            }
            Paper {
                ProfileRow("更新学校认证", Icons.Outlined.Lock, login)
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline)
                ProfileRow("退出登录", Icons.Outlined.Logout, logout)
            }
            Text("泥浆校园卡  ${BuildConfig.VERSION_NAME}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 20.dp))
        }
    }
}
@Composable private fun ProfileRow(title: String, icon: ImageVector, action: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = action).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary); Text(title, fontSize = 15.sp, modifier = Modifier.weight(1f).padding(start = 14.dp)); Icon(Icons.Outlined.ChevronRight, null, Modifier.size(19.dp))
    }
}
@Composable internal fun StudentCard(card: StudentIdentity) {
    if (card.background != null) {
        BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(card.background.width.toFloat() / card.background.height).clip(RoundedCornerShape(16.dp))) {
            Image(card.background.asImageBitmap(), "学校电子学生卡卡面", Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            val w = maxWidth; val h = maxHeight
            card.portrait?.let { Image(it.asImageBitmap(), "本人照片", Modifier.offset(w * card.portraitX, h * card.portraitY).width(w * card.portraitWidth).aspectRatio(it.width.toFloat() / it.height).clip(RoundedCornerShape(3.dp)), contentScale = ContentScale.Crop) }
            val fontScale = LocalDensity.current.fontScale
            card.texts.forEach { t ->
                Text(t.text, color = Color(t.color), fontSize = (t.size * w.value / 375f / fontScale).sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    modifier = Modifier.offset(w * t.x, h * t.y).width(w * (1f - t.x).coerceAtLeast(.01f)), overflow = TextOverflow.Clip)
            }
        }
        if (card.texts.isEmpty()) Paper { Text(card.name, fontSize = 19.sp); Text(card.number, fontSize = 14.sp); Text(card.department, fontSize = 13.sp) }
    } else Paper {
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            card.portrait?.let { Image(it.asImageBitmap(), "本人照片", Modifier.width(74.dp).aspectRatio(.75f).clip(RoundedCornerShape(5.dp)), contentScale = ContentScale.Crop) }
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) { Text(card.name, fontSize = 20.sp); Text(card.number, fontSize = 14.sp); Text(card.department, fontSize = 13.sp); Text(card.identity, fontSize = 13.sp) }
        }
    }
}

@Composable internal fun BillsPage(vm: CampusViewModel) {
    var detail by remember { mutableStateOf<Bill?>(null) }
    var monthMenu by remember { mutableStateOf(false) }
    val allMonths = vm.billMonth == null
    val periodTitle = vm.billMonth?.let { "${it.year} 年 ${it.monthValue} 月" } ?: "全部月份"
    Refreshable(vm.billSyncBusy, vm::refreshBills) {
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(0.dp), contentPadding = PaddingValues(bottom = 22.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .4f)), modifier = Modifier.clickable { monthMenu = true }) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(periodTitle, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                Icon(Icons.Outlined.ExpandMore, "选择月份", Modifier.padding(start = 5.dp).size(17.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        DropdownMenu(expanded = monthMenu, onDismissRequest = { monthMenu = false }, modifier = Modifier.heightIn(max = 380.dp).background(MaterialTheme.colorScheme.surface)) {
                            DropdownMenuItem(text = { Text((if (allMonths) "✓ " else "") + "全部月份", color = MaterialTheme.colorScheme.primary) }, onClick = { vm.changeBillMonth(null); monthMenu = false })
                            vm.billMonths.forEach { month ->
                                val selected = vm.billMonth == month
                                DropdownMenuItem(text = { Text((if (selected) "✓ " else "") + "${month.year} 年 ${month.monthValue} 月" + if (month == YearMonth.now()) "（当月）" else "", fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) }, onClick = { vm.changeBillMonth(month); monthMenu = false })
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    vm.billTotal?.let { Text("$it 笔", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            item { Paper(Modifier.padding(bottom = 10.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) { Text(if (allMonths) "全部支出" else "本月支出", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(vm.billExpenses?.let { "¥ ${Money.yuan(it)}" } ?: "—", fontSize = 21.sp, modifier = Modifier.padding(top = 6.dp)) }
                    Column(Modifier.weight(1f)) { Text(if (allMonths) "全部收入" else "本月收入", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(vm.billIncome?.let { "¥ ${Money.yuan(it)}" } ?: "—", fontSize = 21.sp, modifier = Modifier.padding(top = 6.dp)) }
                }
                if (vm.billSummaryBusy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 14.dp))
                vm.billSummaryError?.let { message -> Text(message, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp)); TextButton(onClick = vm::refreshBills) { Text("重试统计") } }
            } }
            item { Row(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                vm.billTypes.forEach { t -> FilterChip(selected = vm.billType == t.id, onClick = { vm.changeBillType(t.id) }, label = { Text(t.name) }) }
            } }
            itemsIndexed(vm.billRecords) { index, bill ->
                val month = bill.time.take(7)
                if (allMonths && (index == 0 || vm.billRecords[index - 1].time.take(7) != month)) Text(month.replace("-", " 年 ") + " 月", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp, bottom = 12.dp))
                Column(Modifier.fillMaxWidth().clickable { detail = bill }) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(bill.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp); Text(bill.time, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Text(bill.displayAmount, fontSize = 15.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace)
                    }
                    HorizontalDivider(thickness = .5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = .65f))
                }
            }
            item {
                when {
                    vm.billError != null -> { Text(vm.billError!!, fontSize = 13.sp); TextButton(onClick = { vm.loadBills(vm.billRecords.isEmpty(), force = true) }) { Text("重试") } }
                    vm.billBusy -> Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }
                    vm.billSyncBusy -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 12.dp))
                    vm.billRecords.isEmpty() -> Text("所选范围暂无账单", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                    vm.billHasMore -> TextButton(onClick = { vm.loadBills() }, modifier = Modifier.fillMaxWidth()) { Text("加载更多") }
                }
            }
        }
    }
    detail?.let { b -> AlertDialog(onDismissRequest = { detail = null }, title = { Text("账单详情") }, text = {
        Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(b.displayAmount, fontSize = 29.sp)
            listOf("交易" to b.title, "时间" to b.time, "商户" to b.merchant, "学校摘要" to b.description, "支付方式" to b.payment, "卡余额" to b.balance?.let { "¥ ${Money.yuan(it)}" }.orEmpty()).filter { it.second.isNotBlank() }.forEach { (k, v) -> Text("$k  $v", fontSize = 14.sp) }
        }
    }, confirmButton = { TextButton(onClick = { detail = null }) { Text("关闭") } }) }
}
@Composable internal fun ElectricPage(vm: CampusViewModel, pay: (Dorm, String, Long) -> Unit) {
    var picker by remember { mutableIntStateOf(-1) }
    var quantity by rememberSaveable { mutableStateOf("") }
    var confirming by remember { mutableStateOf(false) }
    var accepted by remember { mutableStateOf(false) }
    val result = vm.electricResult; val rules = vm.electricRules; val dorm = vm.dorm
    val price = result?.price ?: rules?.price.orEmpty()
    val fen = ElectricPolicy.cost(quantity, price)
    val minimum = maxOf(rules?.minFen ?: 1, result?.minFen ?: 1)
    val valid = !vm.electricBusy && vm.electricError == null && dorm != null && result != null && !result.blocked && rules != null && fen != null && fen in minimum..rules.maxFen && (result.fixedFen == null || result.fixedFen == fen)
    Refreshable(vm.electricBusy, vm::refreshElectric) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if (vm.favorites.isNotEmpty()) {
                Text("常用宿舍", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                vm.favorites.forEach { favorite ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { vm.useDorm(favorite) }, enabled = !vm.electricBusy, modifier = Modifier.weight(1f)) { Text(favorite.title, maxLines = 2) }
                        IconButton(onClick = { vm.removeDorm(favorite) }) { Icon(Icons.Outlined.Close, "移除常用宿舍", Modifier.size(18.dp)) }
                    }
                }
            }
            Paper {
                listOf("区域", "楼栋", "房间").forEachIndexed { i, title ->
                    Row(Modifier.fillMaxWidth().clickable(enabled = !vm.electricBusy && vm.electricOptions[i].isNotEmpty()) { picker = i }.padding(vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(title, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(52.dp))
                        Text(vm.electricSelected[i]?.name ?: "请选择", fontSize = 16.sp, modifier = Modifier.weight(1f).padding(end = 8.dp)); Icon(Icons.Outlined.ExpandMore, null, Modifier.size(19.dp))
                    }
                    if (i < 2) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
            }
            vm.electricError?.let { Text(it, fontSize = 14.sp); TextButton(onClick = vm::refreshElectric) { Text("重试") } }
            if (result != null && dorm != null) {
                Paper {
                    result.info.forEach { (key, value) -> Row(Modifier.padding(vertical = 7.dp)) { Text(key, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f)); Text(value, fontSize = 14.sp, modifier = Modifier.weight(1.5f)) } }
                    TextButton(onClick = vm::rememberDorm, enabled = vm.favorites.none { it.fields() == dorm.fields() }) { Icon(Icons.Outlined.BookmarkBorder, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(if (vm.favorites.any { it.fields() == dorm.fields() }) "已保存为常用" else "保存为常用宿舍") }
                }
                if (result.blocked) Text(result.message, fontSize = 14.sp)
                else Paper {
                    Text("购买电量", fontSize = 17.sp)
                    OutlinedTextField(value = quantity, onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() || c == '.' }) quantity = it }, label = { Text("电量（度）") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
                    Row(Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) { Text("¥ $price / 度", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f)); Text(fen?.let { "¥ ${Money.yuan(it)}" } ?: "—", fontSize = 25.sp) }
                    if (fen != null && !valid && rules != null) Text("缴费范围 ¥${Money.yuan(minimum)}–${Money.yuan(rules.maxFen)}" + (result.fixedFen?.let { "，学校指定 ¥${Money.yuan(it)}" } ?: ""), fontSize = 12.sp)
                    Button(onClick = { accepted = rules?.notice.isNullOrBlank(); confirming = true }, enabled = valid, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp)) { Text("下一步") }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (picker >= 0) OptionPicker(listOf("选择区域", "选择楼栋", "选择房间")[picker], vm.electricOptions[picker], { picker = -1 }) { vm.chooseElectric(picker, it); picker = -1; quantity = "" }
    if (confirming && dorm != null && fen != null && rules != null) AlertDialog(onDismissRequest = { confirming = false }, title = { Text("确认电费充值") }, text = {
        Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(15.dp)) {
            Text(dorm.title, fontSize = 15.sp); Text("$quantity 度 · ¥ ${Money.yuan(fen)}", fontSize = 23.sp)
            if (rules.notice.isNotBlank()) { Text(rules.notice, fontSize = 13.sp, lineHeight = 21.sp); Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = accepted, onCheckedChange = { accepted = it }); Text("已阅读缴费须知", fontSize = 14.sp) } }
        }
    }, confirmButton = { TextButton(enabled = valid && accepted, onClick = { confirming = false; pay(dorm, quantity, fen) }) { Text("创建订单并前往微信") } }, dismissButton = { TextButton(onClick = { confirming = false }) { Text("返回修改") } })
}
@Composable private fun OptionPicker(title: String, options: List<ElectricOption>, dismiss: () -> Unit, select: (ElectricOption) -> Unit) {
    var search by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = {
        Column {
            if (options.size > 10) OutlinedTextField(value = search, onValueChange = { search = it }, label = { Text("搜索") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp))
            LazyColumn(Modifier.heightIn(max = 330.dp)) { itemsIndexed(options.filter { it.name.contains(search, true) }) { _, item -> Text(item.name, fontSize = 16.sp, modifier = Modifier.fillMaxWidth().clickable { select(item) }.padding(vertical = 17.dp)) } }
        }
    }, confirmButton = { TextButton(onClick = dismiss) { Text("取消") } })
}
