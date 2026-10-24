package cn.mudlife.campuscard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun RechargePage(vm: CampusViewModel, proceed: (Long, String) -> Unit) {
    val card = vm.currentCard ?: return
    val config = vm.config
    val targets = buildList {
        if (config != null && config.accountType != "1") add(card.account to "实体卡账户")
        if (config != null && config.accountType != "2") card.accounts.forEach { add(it.type to it.name) }
    }.distinctBy { it.first }
    var target by rememberSaveable(card.account) { mutableStateOf(targets.firstOrNull()?.first.orEmpty()) }
    var preset by rememberSaveable(card.account) { mutableStateOf<Long?>(null) }
    var custom by rememberSaveable(card.account) { mutableStateOf("") }
    var initialized by rememberSaveable(card.account) { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    val rules = vm.rules
    val focus = LocalFocusManager.current
    LaunchedEffect(rules) {
        if (rules != null && !initialized) {
            preset = RechargePolicy.presets.firstOrNull { RechargePolicy.allowed(it, rules) }
            initialized = true
        }
    }
    LaunchedEffect(targets) { if (targets.none { it.first == target }) target = targets.firstOrNull()?.first.orEmpty() }
    val fen = preset ?: Money.parseYuan(custom)
    val valid = RechargePolicy.allowed(fen, rules) && targets.any { it.first == target }
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().imePadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("充值至", fontSize = 12.sp, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text(card.name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                if (targets.size == 1 && targets[0].second != card.name) Text(targets[0].second, fontSize = 12.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 7.dp))
                if (targets.size > 1) Column(Modifier.padding(top = 10.dp)) {
                    targets.forEach { (id, name) -> FilterChip(selected = target == id, onClick = { target = id }, label = { Text(name) }) }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("选择金额", fontSize = 12.sp, color = colors.onSurfaceVariant)
                RechargePolicy.presets.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { amount ->
                            val selected = preset == amount
                            Surface(onClick = { focus.clearFocus(); preset = amount; custom = "" }, enabled = RechargePolicy.allowed(amount, rules),
                                modifier = Modifier.weight(1f).height(60.dp), shape = RoundedCornerShape(13.dp),
                                color = if (selected) colors.secondaryContainer else colors.surface,
                                border = BorderStroke(if (selected) 1.4.dp else .7.dp, if (selected) colors.primary else colors.outline)) {
                                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                    Text((amount / 100).toString(), fontSize = 22.sp, fontWeight = FontWeight.Medium,
                                        color = if (RechargePolicy.allowed(amount, rules)) colors.onSurface else colors.onSurfaceVariant.copy(alpha = .4f))
                                    Text(" 元", fontSize = 11.sp, color = colors.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            OutlinedTextField(value = custom, onValueChange = { value ->
                if (value.length <= 8 && value.all { it.isDigit() || it == '.' }) { custom = value; preset = null }
            }, enabled = rules?.custom == true, label = { Text("自定义金额") }, prefix = { Text("¥", fontSize = 15.sp) },
                singleLine = true, textStyle = TextStyle(fontSize = 20.sp), shape = RoundedCornerShape(13.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                isError = custom.isNotEmpty() && !RechargePolicy.allowed(fen, rules))
            if (custom.isNotEmpty() && !RechargePolicy.allowed(fen, rules)) Text("请输入学校支持的充值金额", fontSize = 13.sp, color = colors.error)
            if (vm.rulesBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            vm.rulesError?.let { error ->
                Column { Text(error, fontSize = 13.sp, color = colors.error); TextButton(onClick = vm::loadRechargeRules) { Text("重新加载") } }
            }
            Spacer(Modifier.height(8.dp))
        }
        Button(onClick = { focus.clearFocus(); confirming = true }, enabled = valid && !vm.rulesBusy,
            shape = RoundedCornerShape(13.dp), modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 22.dp).height(48.dp)) {
            Text(if (valid) "充值 ¥${Money.yuan(fen!!)}" else "选择充值金额", fontSize = 15.sp)
        }
    }
    if (confirming) AlertDialog(onDismissRequest = { confirming = false }, title = { Text("确认充值") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(targets.firstOrNull { it.first == target }?.second.orEmpty())
            Text("¥${fen?.let(Money::yuan).orEmpty()}", fontSize = 30.sp, fontWeight = FontWeight.Medium)
        } }, confirmButton = { TextButton(enabled = valid, onClick = {
            if (valid && fen != null) { confirming = false; proceed(fen, target) }
        }) { Text("前往付款") } }, dismissButton = { TextButton(onClick = { confirming = false }) { Text("取消") } })
}
