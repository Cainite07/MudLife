package cn.mudlife.app.ui

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import cn.mudlife.app.model.CampusBillCategory
import cn.mudlife.app.model.MonthlyBillReport
import cn.mudlife.app.ui.theme.AppColors
import cn.mudlife.campuscard.Money
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.time.YearMonth
import java.util.Collections
import java.util.Locale
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.random.Random

@Composable
internal fun MonthlyBillOverview(
    report: MonthlyBillReport,
    category: CampusBillCategory,
    selectedDay: Int?,
    onDay: (Int?) -> Unit,
    onCategory: (CampusBillCategory) -> Unit
) {
    var calendar by rememberSaveable { mutableStateOf(false) }
    val total = report.totalFen
    val days = remember(report, category) { report.dailyTotals(category) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(total?.let { "¥ " + Money.yuan(it) } ?: "— —", Modifier.weight(1f), color = AppColors.TextPrimary,
                fontSize = 38.sp, lineHeight = 44.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium)
            Text("支出 ${report.expenseCount} 笔", fontSize = 13.sp, color = AppColors.TextSecondary)
        }
        if (total != null && report.until > 0) {
            val peak = days.maxByOrNull { it.value }?.takeIf { it.value > 0 }
            val caption = when {
                selectedDay != null -> "${selectedDay}日 · ¥${Money.yuan(days[selectedDay] ?: 0L)}"
                peak != null -> "峰值 ${peak.key}日 · ¥${Money.yuan(peak.value)}"
                else -> ""
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(if (category == CampusBillCategory.ALL) "每日支出" else "${category.label} · 每日支出",
                        fontSize = 17.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                    if (caption.isNotEmpty()) Text(caption, color = AppColors.TextSecondary, fontSize = 12.sp)
                }
                Row(Modifier.clip(RoundedCornerShape(9.dp)).background(AppColors.Card).padding(3.dp)) {
                    listOf("柱状", "月历").forEachIndexed { index, label ->
                        val active = calendar == (index == 1)
                        Box(Modifier.clip(RoundedCornerShape(7.dp)).background(if (active) AppColors.Accent else Color.Transparent)
                            .clickable(role = Role.Button) { calendar = index == 1 }.semantics { selected = active }
                            .padding(horizontal = 11.dp, vertical = 6.dp)) {
                            Text(label, fontSize = 12.sp, color = if (active) AppColors.OnAccent else AppColors.TextSecondary)
                        }
                    }
                }
            }
            val pick: (Int) -> Unit = { date -> onDay(date.takeUnless { it == selectedDay }) }
            if (calendar) BillMonthCalendar(report.month, report.until, days, selectedDay, pick)
            else BillDayBars(report.until, days, selectedDay, pick, onDay)
            if (total > 0) CategoryShares(report.categoryTotals, total, category, onCategory)
        }
        key(report.month) { OneBillNote(report.notes) }
    }
}

private fun axisLimit(maxFen: Long): Double {
    if (maxFen <= 0) return 100.0
    val unit = 10.0.pow(floor(log10(maxFen.toDouble())))
    return listOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 8.0, 10.0).first { it * unit >= maxFen } * unit
}

@Composable
private fun BillDayBars(until: Int, days: Map<Int, Long>, selection: Int?,
                        pick: (Int) -> Unit, scrub: (Int) -> Unit) {
    val onScrub by rememberUpdatedState(scrub)
    val max = days.values.maxOrNull() ?: 0L
    val scale = axisLimit(max)
    val grid = AppColors.Border
    val pale = AppColors.Accent.copy(alpha = if (AppColors.isDark) .52f else .45f)
    val ink = AppColors.Accent
    val activeBg = AppColors.ActiveBg
    Column {
        Row(Modifier.fillMaxWidth().height(80.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
                val axis = BigDecimal.valueOf(scale).movePointLeft(2).stripTrailingZeros().toPlainString()
                Text("¥$axis", color = AppColors.TextSecondary, fontSize = 10.sp, lineHeight = 12.sp)
                Text("0", color = AppColors.TextSecondary, fontSize = 10.sp, lineHeight = 12.sp)
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                Canvas(Modifier.fillMaxSize()) {
                    val bottom = size.height - .5f
                    listOf(.5f, size.height / 2, bottom).forEach { y -> drawLine(grid, Offset(0f, y), Offset(size.width, y)) }
                }
                Row(Modifier.fillMaxSize().pointerInput(until) {
                    var lastTouched = 0
                    fun selectAt(x: Float) {
                        if (size.width == 0) return
                        val touched = (1 + (x / size.width * until).toInt()).coerceIn(1, until)
                        if (touched != lastTouched) {
                            lastTouched = touched
                            onScrub(touched)
                        }
                    }
                    detectHorizontalDragGestures(
                        onDragStart = { lastTouched = 0; selectAt(it.x) },
                        onHorizontalDrag = { change, _ -> selectAt(change.position.x) }
                    )
                }) {
                    (1..until).forEach { day ->
                        val fen = days[day] ?: 0L
                        val active = selection == day
                        Box(Modifier.weight(1f).fillMaxHeight().background(if (active) activeBg else Color.Transparent)
                            .clickable(role = Role.Button) { pick(day) }.semantics {
                                contentDescription = "${day}日，支出${Money.yuan(fen)}元"
                                selected = active
                            }.padding(horizontal = .6.dp), contentAlignment = Alignment.BottomCenter) {
                            if (fen > 0) {
                                val height = (80.0 * fen / scale).coerceIn(.8, 80.0).toFloat().dp
                                Spacer(Modifier.fillMaxWidth().height(height).background(
                                    if (active || fen == max) ink else pale, RoundedCornerShape(topStart = 1.5.dp, topEnd = 1.5.dp)))
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, top = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(1, (until + 1) / 2, until).distinct().forEach {
                Text("${it}日", fontSize = 10.sp, color = AppColors.TextSecondary, lineHeight = 14.sp)
            }
        }
    }
}

@Composable
private fun BillMonthCalendar(month: YearMonth, until: Int, days: Map<Int, Long>, selection: Int?, pick: (Int) -> Unit) {
    val offset = month.atDay(1).dayOfWeek.value - 1
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth()) {
            "一二三四五六日".forEach {
                Text(it.toString(), Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 11.sp, color = AppColors.TextSecondary)
            }
        }
        repeat((offset + month.lengthOfMonth() + 6) / 7) { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(7) { weekday ->
                    val day = week * 7 + weekday + 1 - offset
                    if (day !in 1..month.lengthOfMonth()) Spacer(Modifier.weight(1f))
                    else {
                        val active = selection == day
                        val enabled = day <= until
                        val fen = days[day] ?: 0L
                        val bg = when {
                            active -> AppColors.Accent
                            enabled && fen > 0 -> AppColors.ActiveBg
                            else -> Color.Transparent
                        }
                        val fg = when {
                            active -> AppColors.OnAccent
                            !enabled -> AppColors.TextSecondary.copy(alpha = .45f)
                            else -> AppColors.TextPrimary
                        }
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(bg)
                            .clickable(enabled = enabled, role = Role.Button) { pick(day) }
                            .semantics { selected = active; contentDescription = if (enabled) "${day}日，支出${Money.yuan(fen)}元" else "${day}日" }
                            .padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(day.toString(), fontSize = 12.sp, lineHeight = 15.sp, color = fg)
                            Text(if (fen > 0) Money.yuan(fen) else " ", fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1, color = fg)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun categoryColor(category: CampusBillCategory): Color = when (category) {
    CampusBillCategory.BATH -> AppColors.Accent
    CampusBillCategory.FOOD -> AppColors.Warning
    CampusBillCategory.WATER -> AppColors.Cold
    CampusBillCategory.DRYER -> if (AppColors.isDark) Color(0xFFC3ACCE) else Color(0xFF886B94)
    else -> if (AppColors.isDark) Color(0xFFB9B9A1) else Color(0xFF747965)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryShares(totals: Map<CampusBillCategory, Long>, total: Long, selection: CampusBillCategory,
                           onCategory: (CampusBillCategory) -> Unit) {
    Column {
        Text("分类占比", fontSize = 17.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
        Row(Modifier.fillMaxWidth().padding(top = 7.dp, bottom = 3.dp).height(7.dp).clip(RoundedCornerShape(4.dp))) {
            totals.forEach { (category, amount) ->
                if (amount > 0) Spacer(Modifier.fillMaxHeight().weight((amount.toDouble() / total).toFloat()).background(categoryColor(category)))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            totals.forEach { (category, amount) ->
                val active = category == selection
                val percentage = 100.0 * amount / total
                val label = if (amount > 0 && percentage < .1) "<0.1%" else
                    String.format(Locale.ROOT, "%.1f", percentage).removeSuffix(".0") + "%"
                Row(Modifier.clip(RoundedCornerShape(6.dp)).background(if (active) AppColors.ActiveBg else Color.Transparent)
                    .clickable(role = Role.Button) { onCategory(if (active) CampusBillCategory.ALL else category) }
                    .semantics { selected = active; contentDescription = "${category.label} $label，${Money.yuan(amount)}元" }
                    .padding(horizontal = 3.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.size(7.dp).background(categoryColor(category), RoundedCornerShape(2.dp)))
                    Text("${category.label} $label", Modifier.padding(start = 4.dp), fontSize = 12.sp,
                        lineHeight = 17.sp, color = if (active) AppColors.Accent else AppColors.TextPrimary)
                }
            }
        }
    }
}

@Composable
private fun OneBillNote(notes: List<String>) {
    val choices = remember(notes) { notes.distinct().filter(String::isNotBlank) }
    if (choices.isEmpty()) return
    val deck = remember(choices) { ArrayDeque(choices.shuffled()) }
    var current by remember(choices) { mutableStateOf(deck.removeFirst()) }
    val opacity = remember(choices) { Animatable(1f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(choices, lifecycle) {
        if (choices.size == 1) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            opacity.snapTo(1f)
            var deadline = SystemClock.uptimeMillis() + 10_000
            while (true) {
                delay((deadline - SystemClock.uptimeMillis()).coerceAtLeast(0L))
                deadline = SystemClock.uptimeMillis() + 10_000
                if (deck.isEmpty()) {
                    val refill = choices.shuffled().toMutableList()
                    if (refill.first() == current) Collections.swap(refill, 0, Random.nextInt(1, refill.size))
                    deck.addAll(refill)
                }
                opacity.animateTo(0f, tween(130))
                current = deck.removeFirst()
                opacity.animateTo(1f, tween(210))
            }
        }
    }
    val style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif, fontSize = 14.sp, lineHeight = 20.sp)
    val measurer = rememberTextMeasurer()
    Row(Modifier.fillMaxWidth().background(AppColors.ActiveBg, RoundedCornerShape(9.dp))
        .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("生活小记", fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, color = AppColors.Accent)
        BoxWithConstraints(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            val widthPx = constraints.maxWidth
            val heightPx = remember(choices, widthPx, style, measurer) {
                choices.maxOf { measurer.measure(it, style, constraints = Constraints(maxWidth = widthPx)).size.height }
            }
            val height = with(LocalDensity.current) { heightPx.toDp() }
            Text(current, Modifier.fillMaxWidth().heightIn(min = height).wrapContentHeight(Alignment.CenterVertically)
                .graphicsLayer { alpha = opacity.value }, style = style, color = AppColors.TextPrimary)
        }
    }
}
