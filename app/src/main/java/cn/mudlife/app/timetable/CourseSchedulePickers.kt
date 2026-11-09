package cn.mudlife.app.timetable

import android.os.Build
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.NumberPicker
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import cn.mudlife.app.ui.theme.AppColors
import java.text.Normalizer
import kotlin.math.roundToInt

@Composable
internal fun TimePickerSheet(
    day: Int, start: Int, end: Int, limit: Int,
    dismiss: () -> Unit, accept: (Int, Int, Int) -> Unit
) {
    var weekday by rememberSaveable { mutableIntStateOf(day) }
    var first by rememberSaveable { mutableIntStateOf(start.coerceIn(1, limit)) }
    var last by rememberSaveable { mutableIntStateOf(end.coerceIn(first, limit)) }
    val starts = remember(limit) { (1..limit).toList() }
    val ends = remember(limit, first) { (first..limit).toList() }
    PickerSheet("上课时间", dismiss, done = { accept(weekday, first, last) }) {
        if (end > limit) Text("原安排为第 ${start}–${end} 节，确定后使用所选节次。", style = TimetableType.detail, color = AppColors.TextSecondary)
        WheelHeadings(listOf("星期", "开始", "结束"))
        WheelRow {
            NumberWheel("星期", (1..7).toList(), weekday, { "周${"一二三四五六日"[it - 1]}" }, Modifier.weight(1f)) {
                weekday = it
            }
            NumberWheel("开始节次", starts, first, { "第 ${it} 节" }, Modifier.weight(1f)) {
                first = it
                if (last < it) last = it
            }
            NumberWheel("结束节次", ends, last, { "第 ${it} 节" }, Modifier.weight(1f)) { last = it }
        }
    }
}

private enum class Rhythm(val title: String) { Every("每周"), Odd("单周"), Even("双周") }

private data class WeekBand(val first: Int, val last: Int, val rhythm: Rhythm = Rhythm.Every) {
    val usable: Boolean get() = first != last || when (rhythm) {
        Rhythm.Every -> true
        Rhythm.Odd -> first % 2 == 1
        Rhythm.Even -> first % 2 == 0
    }

    fun expression(): String {
        val span = if (first == last) first.toString() else "$first-$last"
        val suffix = when (rhythm) {
            Rhythm.Every -> ""
            Rhythm.Odd -> "(单)"
            Rhythm.Even -> "(双)"
        }
        return "${span}周$suffix"
    }
}

private val digits = Regex("""\d+""")
private fun readBand(token: String, limit: Int): WeekBand {
    val clean = Normalizer.normalize(token, Normalizer.Form.NFKC)
    val bounds = digits.findAll(clean).map { it.value.toInt() }.toList()
    val first = bounds.firstOrNull() ?: 1
    val last = bounds.lastOrNull() ?: limit
    val rhythm = when {
        '单' in clean -> Rhythm.Odd
        '双' in clean -> Rhythm.Even
        else -> Rhythm.Every
    }
    return WeekBand(first, last, rhythm)
}

private fun readBands(text: String, limit: Int) =
    Normalizer.normalize(text, Normalizer.Form.NFKC).split(',', '、', ';').map { readBand(it, limit) }

@Composable
internal fun WeekPickerSheet(
    weeks: String, limit: Int, dismiss: () -> Unit, accept: (String) -> Unit
) {
    val recognised = remember(weeks) { TeachingWeeks.parse(weeks) != null }
    val original = remember(weeks, limit) {
        if (recognised) readBands(weeks, limit) else listOf(WeekBand(1, limit))
    }
    var expression by rememberSaveable { mutableStateOf(original.joinToString(",") { it.expression() }) }
    var changed by rememberSaveable { mutableStateOf(false) }
    var position by rememberSaveable { mutableIntStateOf(0) }
    val bands = remember(expression) { readBands(expression, limit) }
    val index = position.coerceIn(bands.indices)
    val storedBand = bands[index]
    val band = storedBand.copy(first = storedBand.first.coerceIn(1, limit), last = storedBand.last.coerceIn(1, limit))
    val available = remember(limit) { (1..limit).toList() }

    fun update(next: List<WeekBand>, select: Int = index) {
        expression = next.joinToString(",") { it.expression() }
        position = select.coerceIn(next.indices)
        changed = true
    }
    fun replace(next: WeekBand) { update(bands.toMutableList().also { it[index] = next }) }

    PickerSheet("上课周次", dismiss, enabled = bands.all { it.usable }, done = {
        val result = when {
            !changed && recognised -> weeks
            bands.size == 1 && storedBand.first == 1 && storedBand.last == limit -> storedBand.rhythm.title
            else -> expression
        }
        accept(result)
    }) {
        if (!recognised) Text("原周次：$weeks", color = AppColors.TextSecondary,
            modifier = Modifier.padding(bottom = 8.dp))
        if (storedBand != band) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("原安排 ${storedBand.expression()}", Modifier.weight(1f), style = TimetableType.detail, color = AppColors.TextSecondary)
                TextButton(onClick = { replace(band) }) { Text("使用所选范围") }
            }
        }
        if (bands.size > 1) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                bands.forEachIndexed { i, item ->
                    FilterChip(selected = i == index, onClick = { position = i },
                        label = { Text(item.expression()) })
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Rhythm.values().forEach { rhythm ->
                FilterChip(modifier = Modifier.weight(1f), selected = rhythm == band.rhythm,
                    onClick = { if (rhythm != band.rhythm) replace(band.copy(rhythm = rhythm)) },
                    label = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Text(rhythm.title) } })
            }
        }
        WheelHeadings(listOf("起始周", "结束周"))
        WheelRow {
            NumberWheel("起始周", available, band.first, { "第 ${it} 周" }, Modifier.weight(1f)) {
                replace(band.copy(first = it, last = maxOf(it, band.last)))
            }
            NumberWheel("结束周", available.filter { it >= band.first }, band.last, { "第 ${it} 周" }, Modifier.weight(1f)) {
                replace(band.copy(last = it))
            }
        }
        if (!band.usable) Text("这一段没有${band.rhythm.title}", color = AppColors.Danger)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = {
                val used = TeachingWeeks.parse(expression).orEmpty()
                val firstFree = (1..limit).firstOrNull { it > band.last && it !in used }
                    ?: (1..limit).firstOrNull { it !in used } ?: limit
                update(bands + WeekBand(firstFree, firstFree), bands.size)
            }) {
                Icon(Icons.Outlined.Add, null, Modifier.size(19.dp))
                Spacer(Modifier.width(4.dp))
                Text("添加另一段")
            }
            if (bands.size > 1) TextButton(onClick = {
                update(bands.filterIndexed { i, _ -> i != index })
            }) {
                Icon(Icons.Outlined.DeleteOutline, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("移除这一段")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerSheet(
    title: String, dismiss: () -> Unit, enabled: Boolean = true, done: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppColors.SolidSurface, contentColor = AppColors.TextPrimary) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = TimetableType.title)
                TextButton(enabled = enabled, onClick = done) {
                    Text("确定", style = TimetableType.body)
                }
            }
            content()
        }
    }
}

@Composable
private fun WheelHeadings(labels: List<String>) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        for (label in labels) Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Text(label, style = TimetableType.detail, color = AppColors.TextSecondary)
        }
    }
}

@Composable
private fun WheelRow(content: @Composable RowScope.() -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Spacer(Modifier.fillMaxWidth().height(wheelSlot).background(AppColors.ActiveBg, RoundedCornerShape(14.dp)))
        Row(Modifier.fillMaxWidth().height(144.dp), content = content)
    }
}

private val wheelSlot = 48.dp

@Composable
private fun NumberWheel(
    label: String, choices: List<Int>, value: Int, format: (Int) -> String,
    modifier: Modifier, change: (Int) -> Unit
) {
    val currentChange by rememberUpdatedState(change)
    val ink = AppColors.TextPrimary.toArgb()
    val dark = AppColors.isDark
    val slotPx = with(LocalDensity.current) { wheelSlot.roundToPx() }
    val index = choices.indexOf(value).coerceAtLeast(0)
    Box(modifier.fillMaxHeight().semantics {
        contentDescription = label
        stateDescription = format(value)
        if (choices.size > 1) {
            progressBarRangeInfo = ProgressBarRangeInfo(index.toFloat(), 0f..choices.lastIndex.toFloat(), choices.size - 2)
            setProgress {
                val i = it.roundToInt().coerceIn(choices.indices)
                if (i != index) currentChange(choices[i])
                i != index
            }
        }
    }, contentAlignment = Alignment.Center) {
        if (choices.size == 1) {
            Text(format(choices.single()), color = AppColors.TextPrimary, style = TimetableType.number)
        } else key(choices, dark, slotPx) {
            AndroidView(factory = { host ->
                val theme = if (dark) android.R.style.Theme_Material else android.R.style.Theme_Material_Light
                // Keep the native fling, snapping and full touch surface. Paint labels only in the selected slot.
                object : NumberPicker(ContextThemeWrapper(host, theme)) {
                    override fun draw(canvas: Canvas) {
                        val top = (height - slotPx) / 2
                        val saved = canvas.save()
                        canvas.clipRect(0, top, width, top + slotPx)
                        super.draw(canvas)
                        canvas.restoreToCount(saved)
                    }
                }.apply {
                    minValue = 0
                    maxValue = choices.lastIndex
                    displayedValues = choices.map(format).toTypedArray()
                    wrapSelectorWheel = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                    isHapticFeedbackEnabled = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, TimetableType.number.fontSize.value, resources.displayMetrics)
                        textColor = ink
                        selectionDividerHeight = 0
                    }
                    this.value = index
                    setOnValueChangedListener { _, _, selected ->
                        currentChange(choices[selected])
                    }
                }
            }, modifier = Modifier.fillMaxSize(), update = { if (it.value != index) it.value = index })
            if (index > 0) Icon(Icons.Outlined.KeyboardArrowUp, null, Modifier.align(Alignment.TopCenter)
                .padding(top = 12.dp).size(17.dp), tint = AppColors.TextSecondary)
            if (index < choices.lastIndex) Icon(Icons.Outlined.KeyboardArrowDown, null, Modifier.align(Alignment.BottomCenter)
                .padding(bottom = 12.dp).size(17.dp), tint = AppColors.TextSecondary)
        }
    }
}
