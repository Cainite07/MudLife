package cn.mudlife.app.timetable

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.mudlife.app.ui.theme.AppColors
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val monthDay = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)
private val yearMonthDay = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)
private val accessibleDay = DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.CHINA)
private val weekdayLabels = listOf("一", "二", "三", "四", "五", "六", "日")

private fun LocalDate.weekStart() = minusDays((dayOfWeek.value - 1).toLong())

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun StartDatePanel(
    term: String, initialMonday: LocalDate?, today: LocalDate, dismiss: () -> Unit, save: (LocalDate) -> Unit
) {
    val initial = initialMonday ?: today
    var selectedEpoch by rememberSaveable(term) { mutableLongStateOf(initial.weekStart().toEpochDay()) }
    val monday = LocalDate.ofEpochDay(selectedEpoch)
    val sunday = monday.plusDays(6)
    val firstMonth = YearMonth.of(minOf(1900, initial.year), 1)
    val lastYear = maxOf(2100, initial.year)
    val monthCount = (lastYear - firstMonth.year + 1) * 12
    val pager = rememberPagerState(initialPage = firstMonth.until(YearMonth.from(initial), ChronoUnit.MONTHS).toInt()) { monthCount }
    val shown = firstMonth.plusMonths(pager.currentPage.toLong())
    var pickingMonth by rememberSaveable { mutableStateOf(false) }
    var jumpYear by rememberSaveable { mutableIntStateOf(shown.year) }
    val scope = rememberCoroutineScope()

    fun turn(direction: Int) {
        if (pickingMonth) {
            jumpYear = (jumpYear + direction).coerceIn(firstMonth.year, lastYear)
        } else {
            val target = (pager.currentPage + direction).coerceIn(0, monthCount - 1)
            scope.launch { pager.animateScrollToPage(target, animationSpec = tween(210)) }
        }
    }

    TimetablePage("开学日期", dismiss, footer = {
        HorizontalDivider(color = AppColors.Border)
        Button(onClick = { save(monday) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
            .heightIn(min = 48.dp), shape = RoundedCornerShape(10.dp)) {
            Text("保存开学日期", style = TimetableType.body, fontWeight = FontWeight.Medium)
        }
    }) {
        Column(Modifier.align(Alignment.CenterHorizontally).widthIn(max = 480.dp).fillMaxWidth()
            .semantics { paneTitle = "$term 开学日期" }) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClickLabel = if (pickingMonth) "返回月历" else "选择年月") {
                        if (!pickingMonth) jumpYear = shown.year
                        pickingMonth = !pickingMonth
                    }, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (pickingMonth) "${jumpYear}年" else "${shown.year}年${shown.monthValue}月",
                        style = TimetableType.title, color = AppColors.TextPrimary)
                    Icon(if (pickingMonth) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        if (pickingMonth) "返回月历" else "选择年月", Modifier.size(22.dp), tint = AppColors.TextSecondary)
                }
                val arrowColors = IconButtonDefaults.iconButtonColors(contentColor = AppColors.TextSecondary)
                IconButton({ turn(-1) }, enabled = if (pickingMonth) jumpYear > firstMonth.year else pager.currentPage > 0,
                    colors = arrowColors) {
                    Icon(Icons.Outlined.ChevronLeft, if (pickingMonth) "上一年" else "上个月")
                }
                IconButton({ turn(1) }, enabled = if (pickingMonth) jumpYear < lastYear else pager.currentPage < monthCount - 1,
                    colors = arrowColors) {
                    Icon(Icons.Outlined.ChevronRight, if (pickingMonth) "下一年" else "下个月")
                }
            }
            AnimatedContent(pickingMonth, transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(100)) },
                label = "选择年月") { choosing ->
                if (choosing) {
                    Column {
                        for (quarter in 0..3) {
                            Row(Modifier.fillMaxWidth()) {
                                for (i in 1..3) {
                                    val number = quarter * 3 + i
                                    val active = shown.year == jumpYear && shown.monthValue == number
                                    val shape = RoundedCornerShape(8.dp)
                                    Box(Modifier.weight(1f).padding(2.dp).clip(shape)
                                        .then(if (active) Modifier.background(AppColors.ActiveBg) else Modifier)
                                        .selectable(active, role = Role.RadioButton) {
                                            val target = (jumpYear - firstMonth.year) * 12 + number - 1
                                            pickingMonth = false
                                            scope.launch { pager.scrollToPage(target) }
                                        }.heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
                                        Text("${number}月", color = if (active) AppColors.Accent else AppColors.TextPrimary)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Column {
                        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)) {
                            weekdayLabels.forEach { label ->
                                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    Text(label, color = AppColors.TextSecondary, style = TimetableType.detail)
                                }
                            }
                        }
                        HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth().animateContentSize(tween(180))) { page ->
                            val month = firstMonth.plusMonths(page.toLong())
                            MonthGrid(month, monday, today, firstMonth.year..lastYear) { date ->
                                selectedEpoch = date.weekStart().toEpochDay()
                            }
                        }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(top = 14.dp), color = AppColors.Border)
            val displayedYear = if (pickingMonth) jumpYear else shown.year
            val from = if (monday.year == displayedYear && monday.year == sunday.year) monthDay else yearMonthDay
            val through = if (monday.year == sunday.year) monthDay else yearMonthDay
            Text("第一周　${monday.format(from)}（周一）– ${sunday.format(through)}", Modifier.padding(top = 18.dp),
                style = TimetableType.body, fontWeight = FontWeight.Medium, color = AppColors.TextPrimary)
        }
    }
}

@Composable
private fun MonthGrid(month: YearMonth, monday: LocalDate, today: LocalDate, years: IntRange, select: (LocalDate) -> Unit) {
    val first = remember(month) { month.atDay(1).weekStart() }
    val last = remember(month) { month.atEndOfMonth() }
    Column(Modifier.fillMaxWidth()) {
        for (week in 0L..5L) {
            val start = first.plusWeeks(week)
            if (start.isAfter(last)) break
            val highlighted = start == monday
            Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)
                .then(if (highlighted) Modifier.background(AppColors.ActiveBg, RoundedCornerShape(10.dp)) else Modifier)) {
                for (offset in 0L..6L) {
                    val date = start.plusDays(offset)
                    if (date.month != month.month && !highlighted || date.year !in years) {
                        Spacer(Modifier.weight(1f))
                        continue
                    }
                    val anchor = date == monday
                    Box(Modifier.weight(1f).heightIn(min = 46.dp)
                        .selectable(selected = highlighted, role = Role.RadioButton) { select(date) }
                        .semantics { contentDescription = date.format(accessibleDay) }, contentAlignment = Alignment.Center) {
                        Box(Modifier.defaultMinSize(minWidth = 34.dp, minHeight = 34.dp)
                            .then(if (anchor) Modifier.background(AppColors.Accent, CircleShape) else Modifier),
                            contentAlignment = Alignment.Center) {
                            Text(date.dayOfMonth.toString(), style = TimetableType.body, fontWeight = if (highlighted || date == today) FontWeight.SemiBold else FontWeight.Normal,
                                color = when {
                                    anchor -> MaterialTheme.colorScheme.onPrimary
                                    highlighted || date == today -> AppColors.Accent
                                    else -> AppColors.TextPrimary
                                })
                        }
                    }
                }
            }
        }
    }
}
