package cn.mudlife.app.timetable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import cn.mudlife.app.ui.theme.AppColors
import java.time.LocalTime

@Composable
internal fun NewTermPanel(dismiss: () -> Unit, create: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    val submit: () -> Unit = { create(name.trim()) }
    TimetablePage("添加学期", dismiss, footer = {
        HorizontalDivider(color = AppColors.Border)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.End) {
            Button(submit, Modifier.heightIn(min = 46.dp), enabled = name.isNotBlank(),
                shape = RoundedCornerShape(8.dp)) { Text("创建学期") }
        }
    }) {
        TimetableTextField("学期名称", name, { name = it }, Modifier.fillMaxWidth(), ime = ImeAction.Done,
            keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) submit() }))
    }
}

@Composable
internal fun BellSettingsPanel(custom: List<BellPeriod>, dismiss: () -> Unit, automatic: () -> Unit, edit: (String?) -> Unit) {
    TimetablePage("上下课时间", dismiss) {
        MenuSection {
            MenuRow(Icons.Outlined.Place, "按教室自动匹配",
                if (custom.isEmpty()) "使用中" else "", action = automatic)
            MenuRow(Icons.Outlined.Schedule, "自定义节次时间",
                if (custom.isNotEmpty()) "使用中" else "") { edit(null) }
        }
        MenuSection("学校作息模板") {
            for (template in timetableBellTemplates) {
                MenuRow(Icons.Outlined.LocationOn, template) { edit(template) }
            }
        }
    }
}

// Edits can temporarily leave a period or its neighbours out of order.
private data class BellDraft(val number: Int, val start: LocalTime, val end: LocalTime)

private val bellDraftSaver = listSaver<List<BellDraft>, Long>(
    save = { rows -> rows.flatMap { listOf(it.number.toLong(), it.start.toNanoOfDay(), it.end.toNanoOfDay()) } },
    restore = { data -> data.chunked(3).map { BellDraft(it[0].toInt(), LocalTime.ofNanoOfDay(it[1]), LocalTime.ofNanoOfDay(it[2])) } }
)

private fun minutes(time: LocalTime) = time.hour * 60 + time.minute
private fun localTime(value: Int): LocalTime = LocalTime.of(value / 60, value % 60)

private fun validateBells(rows: List<BellDraft>): String? {
    if (rows.isEmpty()) return "请先添加一个节次"
    val invalid = rows.firstOrNull { it.start >= it.end }
    if (invalid != null) return "第 ${invalid.number} 节的下课时间须晚于上课时间"
    val overlap = rows.zipWithNext().firstOrNull { (a, b) -> a.end > b.start }
    return overlap?.let { (a, b) -> "第 ${b.number} 节开始得太早，请与第 ${a.number} 节错开" }
}

private fun newBell(rows: List<BellDraft>, number: Int): BellDraft {
    val before = rows.lastOrNull { it.number < number }
    val after = rows.firstOrNull { it.number > number }
    val upper = after?.let { minutes(it.start) } ?: (24 * 60 - 1)
    val lower = before?.let { minutes(it.end) } ?: 0
    val suggestedStart = if (before != null) lower + 10 else if (after != null) upper - 55 else 8 * 60
    val start = suggestedStart.coerceAtMost(upper - 45).coerceAtLeast(lower).coerceIn(0, 1438)
    val end = (start + 45).coerceAtMost(upper).coerceAtLeast(start + 1)
    return BellDraft(number, localTime(start), localTime(end))
}

@Composable
internal fun BellEditorPanel(
    initial: List<BellPeriod>, template: String?, dismiss: () -> Unit,
    periodLimit: Int = TimetableLimits.MAX_PERIODS, save: (List<BellPeriod>) -> Unit
) {
    var rows by rememberSaveable(template, stateSaver = bellDraftSaver) {
        mutableStateOf(initial.sortedBy { it.number }.map { BellDraft(it.number, it.start, it.end) })
    }
    var addOpen by rememberSaveable { mutableStateOf(false) }
    var editingNumber by rememberSaveable { mutableIntStateOf(0) }
    var editingStart by rememberSaveable { mutableStateOf(true) }
    val error = remember(rows) { validateBells(rows) }
    val available = remember(rows, periodLimit) {
        val existing = rows.mapTo(mutableSetOf()) { it.number }
        (1..periodLimit.coerceIn(1, TimetableLimits.MAX_PERIODS)).filterNot(existing::contains)
    }

    TimetablePage(template?.let { "$it · 上下课时间" } ?: "自定义节次时间", dismiss, scrollBody = false, footer = {
        HorizontalDivider(color = AppColors.Border)
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            if (error != null) Text(error, Modifier.padding(bottom = 5.dp), style = TimetableType.detail, color = AppColors.Danger)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton({ addOpen = true }, enabled = available.isNotEmpty()) {
                    Text("添加节次")
                }
                Spacer(Modifier.weight(1f))
                Button({ save(rows.map { BellPeriod(it.number, it.start, it.end) }) },
                    Modifier.widthIn(min = 108.dp).heightIn(min = 46.dp), enabled = error == null, shape = RoundedCornerShape(8.dp)) {
                    Text("保存作息", fontWeight = FontWeight.Medium)
                }
            }
        }
    }) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("节次", Modifier.width(46.dp), color = AppColors.TextSecondary, style = TimetableType.detail)
            Text("上课", Modifier.weight(1f), color = AppColors.TextSecondary, style = TimetableType.detail)
            Text("下课", Modifier.weight(1f), color = AppColors.TextSecondary, style = TimetableType.detail)
            Spacer(Modifier.width(44.dp))
        }
        HorizontalDivider(color = AppColors.Border)
        LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(bottom = 12.dp)) {
            items(rows, key = { it.number }) { row ->
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(row.number.toString(), Modifier.width(46.dp), color = AppColors.TextSecondary,
                        style = TimetableType.body, fontWeight = FontWeight.Medium)
                    BellTimeField(row.number, "上课", row.start, Modifier.weight(1f)) {
                        editingStart = true
                        editingNumber = row.number
                    }
                    BellTimeField(row.number, "下课", row.end, Modifier.weight(1f)) {
                        editingStart = false
                        editingNumber = row.number
                    }
                    IconButton({ rows = rows.filterNot { it.number == row.number } }, Modifier.width(44.dp)) {
                        Icon(Icons.Outlined.RemoveCircleOutline, "移除第${row.number}节", Modifier.size(19.dp),
                            tint = AppColors.TextSecondary)
                    }
                }
                if (row != rows.last()) HorizontalDivider(color = AppColors.Border)
            }
        }
    }

    if (addOpen && available.isNotEmpty()) {
        AlertDialog(onDismissRequest = { addOpen = false }, title = { Text("选择要添加的节次") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                for (number in available) {
                    MenuRow(Icons.Outlined.Add, "第${number}节") {
                        rows = (rows + newBell(rows, number)).sortedBy { it.number }
                        addOpen = false
                        editingStart = true
                        editingNumber = number
                    }
                }
            }
        }, confirmButton = { TextButton({ addOpen = false }) { Text("取消") } })
    }
    rows.firstOrNull { it.number == editingNumber }?.let { row ->
        key(row.number, editingStart) {
            EditClock("第 ${row.number} 节 · ${if (editingStart) "上课" else "下课"}",
                minutes(if (editingStart) row.start else row.end), { editingNumber = 0 }) { value ->
                val updated = if (editingStart) row.copy(start = localTime(value)) else row.copy(end = localTime(value))
                rows = rows.map { if (it.number == row.number) updated else it }
                editingNumber = 0
            }
        }
    }
}

@Composable
private fun BellTimeField(number: Int, label: String, value: LocalTime, modifier: Modifier, edit: () -> Unit) {
    val text = value.toString()
    Box(modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(9.dp))
        .clickable(role = Role.Button, onClick = edit).semantics { contentDescription = "第${number}节，${label} $text" },
        contentAlignment = Alignment.CenterStart) {
        Text(text, color = AppColors.TextPrimary, style = TimetableType.body, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditClock(title: String, current: Int, dismiss: () -> Unit, confirm: (Int) -> Unit) {
    val clock = rememberTimePickerState(initialHour = current / 60, initialMinute = current % 60, is24Hour = true)
    var keyboard by rememberSaveable { mutableStateOf(false) }
    val compact = LocalConfiguration.current.screenHeightDp < 520
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            if (keyboard || compact) TimeInput(clock) else TimePicker(clock)
            if (!compact) TextButton({ keyboard = !keyboard }) { Text(if (keyboard) "使用表盘" else "键盘输入") }
        }
    }, dismissButton = { TextButton(dismiss) { Text("取消") } },
        confirmButton = { TextButton({ confirm(clock.hour * 60 + clock.minute) }) { Text("确定") } })
}
