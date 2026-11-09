package cn.mudlife.app.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mudlife.app.ui.theme.AppColors
import java.time.LocalDate

internal sealed interface TimetablePanel {
    data object Tools : TimetablePanel
    data object Display : TimetablePanel
    data class DisplayGroup(val group: TimetableDisplayGroup) : TimetablePanel
    data class BellDetail(val number: Int) : TimetablePanel
    data object StartDate : TimetablePanel
    data object Weeks : TimetablePanel
    data object AllCourses : TimetablePanel
    data object Seasons : TimetablePanel
    data object Bells : TimetablePanel
    data class BellEditor(val template: String? = null) : TimetablePanel
    data object NewTerm : TimetablePanel
    data object Wallpaper : TimetablePanel
    data class Overlaps(val rows: List<SavedCourse>) : TimetablePanel
    data class Colors(val row: SavedCourse) : TimetablePanel
    data class Course(val row: SavedCourse) : TimetablePanel
    data class EditCourse(val term: String, val existing: SavedCourse?, val day: Int, val period: Int, val week: Int?) : TimetablePanel
}

internal data class WeekHeading(
    val week: Int = 1, val limit: Int = 20, val dateRange: String = "", val note: String = "",
    val current: Boolean = false
)

@Composable
internal fun TimetableHeader(
    term: String?, hasCourses: Boolean, heading: WeekHeading, back: (() -> Unit)?, selectTerm: () -> Unit,
    add: () -> Unit, tools: () -> Unit, selectWeek: () -> Unit, move: (Int) -> Unit, reset: () -> Unit,
    openNote: (() -> Unit)?, wallpaper: Boolean
) {
    Column(Modifier.fillMaxWidth().background(if (wallpaper) Color.Transparent else AppColors.Background)) {
        Row(Modifier.fillMaxWidth().padding(start = if (back == null) 16.dp else 2.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (back != null) HeaderIcon(Icons.Outlined.ArrowBack, "返回", back)
            Row(Modifier.weight(1f).heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClickLabel = "切换学期", onClick = selectTerm)
                .semantics { term?.let { stateDescription = it } }, verticalAlignment = Alignment.CenterVertically) {
                Text("我的课表", style = TimetableType.title, color = AppColors.TextPrimary)
                Spacer(Modifier.width(8.dp))
                Row(Modifier.background(AppColors.ActiveBg, RoundedCornerShape(20.dp))
                    .padding(start = 9.dp, end = 5.dp, top = 3.dp, bottom = 3.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("切换学期", style = TimetableType.detail, color = AppColors.Accent)
                    Icon(Icons.Outlined.ExpandMore, null, Modifier.size(15.dp), tint = AppColors.Accent)
                }
            }
            HeaderIcon(Icons.Outlined.Add, "添加课程", add)
            HeaderIcon(Icons.Outlined.MoreVert, "更多功能", tools)
        }
        if (hasCourses && term != null) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClick = selectWeek)
                    .semantics { stateDescription = "${heading.dateRange}，${heading.note}" }, verticalAlignment = Alignment.CenterVertically) {
                    Text("第 ${heading.week} 周", style = TimetableType.week, color = AppColors.TextPrimary)
                    Icon(Icons.Outlined.ExpandMore, "选择教学周", Modifier.size(20.dp), tint = AppColors.TextSecondary)
                }
                WeekArrow(Icons.Outlined.ChevronLeft, "上一周", heading.week > 1) { move(-1) }
                Box(Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).clickable(role = Role.Button, onClick = reset),
                    contentAlignment = Alignment.Center) {
                    Text("本周", color = AppColors.Accent, style = TimetableType.detail, fontWeight = FontWeight.Medium)
                }
                WeekArrow(Icons.Outlined.ChevronRight, "下一周", heading.week < heading.limit) { move(1) }
            }
            val prompt = when {
                heading.dateRange == "设置开学日期" -> heading.dateRange
                openNote != null || heading.note == "本周没有课" -> heading.note
                else -> null
            }
            if (prompt != null) {
                val action = if (openNote != null) Modifier.clickable(role = Role.Button, onClick = openNote) else Modifier
                Text(prompt, Modifier.fillMaxWidth().then(action).padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                    color = if (openNote != null) AppColors.Accent else AppColors.TextSecondary,
                    style = TimetableType.detail, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (!wallpaper) HorizontalDivider(color = AppColors.Border)
    }
}

@Composable
private fun HeaderIcon(icon: ImageVector, label: String, action: () -> Unit) {
    IconButton(action, Modifier.size(48.dp)) { Icon(icon, label, Modifier.size(21.dp), tint = AppColors.TextPrimary) }
}

@Composable
private fun WeekArrow(icon: ImageVector, label: String, enabled: Boolean, action: () -> Unit) {
    IconButton(action, Modifier.size(48.dp), enabled = enabled) {
        Icon(icon, label, Modifier.size(20.dp), tint = AppColors.TextSecondary.copy(alpha = if (enabled) 1f else .35f))
    }
}

private val LocalMenuBack = compositionLocalOf<(() -> Unit)?> { null }

@Composable
internal fun TimetableMenuHost(back: (() -> Unit)?, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalMenuBack provides back, content = content)
}

@Composable
internal fun TimetablePage(
    title: String, dismiss: () -> Unit, footer: (@Composable () -> Unit)? = null, scrollBody: Boolean = true,
    titleStyle: TextStyle = TimetableType.title, wrapContentHeight: Boolean = false,
    body: @Composable ColumnScope.() -> Unit
) {
    val back = LocalMenuBack.current
    val pageSize = if (wrapContentHeight) Modifier.fillMaxWidth() else Modifier.fillMaxSize()
    Column(pageSize.background(timetableSheetSurface)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 4.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            HeaderIcon(Icons.Outlined.ArrowBack, if (back == null) "返回课表" else "返回上一级", back ?: dismiss)
            Text(title, Modifier.weight(1f).padding(horizontal = 4.dp).semantics { heading() }, style = titleStyle,
                color = AppColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (back != null) HeaderIcon(Icons.Outlined.Close, "关闭", dismiss)
        }
        HorizontalDivider(color = AppColors.Border)
        val scroll = if (scrollBody) Modifier.verticalScroll(rememberScrollState()) else Modifier
        val bottomPadding = when {
            !scrollBody -> 0.dp
            wrapContentHeight -> 8.dp
            else -> 20.dp
        }
        // Reserve the fixed footer first. A short form uses only the scroll space it needs.
        Column(Modifier.fillMaxWidth().weight(1f, fill = !wrapContentHeight).then(scroll)
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = bottomPadding), content = body)
        footer?.invoke()
    }
}

@Composable
internal fun MenuSection(title: String? = null, titleStyle: TextStyle = TimetableType.section, content: @Composable ColumnScope.() -> Unit) {
    if (title != null) {
        Text(title, Modifier.semantics { heading() }.padding(top = 20.dp, bottom = 4.dp),
            color = AppColors.TextSecondary, style = titleStyle)
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(20.dp))
        .background(AppColors.Card).padding(horizontal = 14.dp), content = content)
}

@Composable
internal fun MenuRow(icon: ImageVector, title: String, value: String = "", titleStyle: TextStyle = TimetableType.body,
    action: (() -> Unit)? = null) {
    val click = if (action == null) Modifier else Modifier.clickable(role = Role.Button, onClick = action)
    Row(Modifier.fillMaxWidth().then(click).heightIn(min = 54.dp).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, Modifier.size(20.dp), tint = AppColors.TextSecondary)
        Text(title, Modifier.weight(1f), color = AppColors.TextPrimary, style = titleStyle)
        if (value.isNotBlank()) {
            Text(value, Modifier.weight(1f), color = AppColors.TextSecondary, style = TimetableType.detail,
                textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
        if (action != null) Icon(Icons.Outlined.ChevronRight, null, Modifier.size(16.dp), tint = AppColors.TextSecondary)
    }
    HorizontalDivider(color = AppColors.Border)
}

@Composable
internal fun TimetableActions(term: String?, count: Int, selectTerm: () -> Unit, create: () -> Unit,
    sync: () -> Unit, courses: () -> Unit, settings: () -> Unit, bells: () -> Unit, widget: () -> Unit) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, bottom = 12.dp)) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(AppColors.ActiveBg)
            .clickable(role = Role.Button, onClickLabel = "切换学期", onClick = selectTerm)
            .heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(term ?: "选择学期", style = TimetableType.dataTitle.copy(fontSize = 17.sp, lineHeight = 22.sp), color = AppColors.TextPrimary)
                if (term != null) Text("$count 条安排", style = TimetableType.detail, color = AppColors.TextSecondary)
            }
            Icon(Icons.Outlined.UnfoldMore, "切换学期", Modifier.size(21.dp), tint = AppColors.Accent)
        }
        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(AppColors.Card).padding(vertical = 4.dp)) {
            Row(Modifier.fillMaxWidth()) {
                ActionTile(Icons.Outlined.Add, "添加课程", create)
                ActionTile(Icons.Outlined.Sync, "教务同步", sync)
                ActionTile(Icons.Outlined.MenuBook, "全部课程", courses)
            }
            Row(Modifier.fillMaxWidth()) {
                ActionTile(Icons.Outlined.Tune, "课表设置", settings, enabled = term != null)
                ActionTile(Icons.Outlined.Schedule, "上下课时间", bells)
                ActionTile(Icons.Outlined.Widgets, "桌面小组件", widget)
            }
        }
    }
}

@Composable
private fun RowScope.ActionTile(icon: ImageVector, title: String, action: () -> Unit, enabled: Boolean = true) {
    val tint = if (enabled) AppColors.Accent else AppColors.TextSecondary.copy(alpha = .55f)
    Column(Modifier.weight(1f).heightIn(min = 68.dp)
        .clickable(enabled = enabled, role = Role.Button, onClick = action).padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, null, Modifier.size(22.dp), tint = tint)
        Spacer(Modifier.height(4.dp))
        Text(title, style = TimetableType.section, textAlign = TextAlign.Center, color = if (enabled) AppColors.TextPrimary else tint)
    }
}

@Composable
internal fun WeekChoices(
    selected: Int, current: Int?, limit: Int, firstMonday: LocalDate? = null, choose: (Int) -> Unit
) {
    val groups = remember(limit) { (1..limit).chunked(5) }
    firstMonday?.plusWeeks(selected.toLong() - 1)?.let { start ->
        val end = start.plusDays(6)
        Text("第 ${selected} 周 · ${start.monthValue}月${start.dayOfMonth}日 – ${end.monthValue}月${end.dayOfMonth}日",
            Modifier.padding(vertical = 12.dp), style = TimetableType.body, color = AppColors.TextPrimary)
    }
    for (group in groups) {
        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (week in group) {
                val chosen = week == selected
                val monday = remember(firstMonday, week) { firstMonday?.plusWeeks(week.toLong() - 1) }
                val foreground = if (chosen) AppColors.Accent else AppColors.TextPrimary
                Column(Modifier.weight(1f).heightIn(min = 56.dp).clip(RoundedCornerShape(8.dp))
                    .then(if (chosen) Modifier.background(AppColors.ActiveBg) else Modifier)
                    .selectable(selected = chosen, role = Role.RadioButton, onClick = { choose(week) })
                    .semantics {
                        contentDescription = "第${week}周"
                        monday?.let { stateDescription = "${it.monthValue}月${it.dayOfMonth}日起" }
                    }.padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(week.toString(), style = TimetableType.number, color = foreground,
                        fontWeight = if (chosen) FontWeight.Bold else FontWeight.Medium)
                    if (week == current) Text("本周", style = TimetableType.detail, color = AppColors.Accent)
                }
            }
            repeat(5 - group.size) { Spacer(Modifier.weight(1f)) }
        }
        if (group != groups.last()) HorizontalDivider(color = AppColors.Border)
    }
}

@Composable
internal fun CourseDirectory(rows: List<SavedCourse>, weekdays: List<String>, select: (SavedCourse) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val sameTimeAndPlace = remember(rows) {
        rows.groupBy { it.course.copy(weeks = "", teacher = "") }.values
            .filter { it.size > 1 }.flatten().mapTo(mutableSetOf()) { it.id }
    }
    val grouped = remember(rows, query) {
        val needle = query.trim()
        val matches = if (needle.isEmpty()) rows else rows.filter { row ->
            val c = row.course
            c.name.contains(needle, true) || c.place.contains(needle, true) || c.teacher.contains(needle, true)
        }
        matches.groupBy { it.course.weekday }
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
            placeholder = { Text("搜索课程、地点、教师", style = TimetableType.body) },
            leadingIcon = { Icon(Icons.Outlined.Search, null, Modifier.size(20.dp), tint = AppColors.TextSecondary) },
            trailingIcon = if (query.isBlank()) null else { { IconButton({ query = "" }) {
                Icon(Icons.Outlined.Close, "清除搜索", Modifier.size(18.dp))
            } } },
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = AppColors.Border, focusedBorderColor = AppColors.Accent),
            shape = RoundedCornerShape(8.dp))
        if (grouped.isEmpty()) {
            Text(if (rows.isEmpty()) "本学期暂无课程" else "没有找到匹配的课程",
                Modifier.padding(vertical = 24.dp), color = AppColors.TextSecondary)
        } else {
            LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(bottom = 28.dp)) {
                for ((day, courses) in grouped) {
                    item(key = "day-$day", contentType = "day") {
                        Text(weekdays[day - 1], Modifier.semantics { heading() }.padding(top = 20.dp, bottom = 4.dp),
                            color = AppColors.Accent, style = TimetableType.section)
                    }
                    itemsIndexed(courses, key = { _, row -> "course-${row.id}" }, contentType = { _, _ -> "course" }) { index, row ->
                        val c = row.course
                        val period = if (c.startPeriod == c.endPeriod) "${c.startPeriod} 节" else "${c.startPeriod}–${c.endPeriod} 节"
                        Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = { select(row) })
                            .heightIn(min = 62.dp).padding(vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(period, Modifier.width(60.dp), style = TimetableType.detail,
                                color = AppColors.TextSecondary, fontWeight = FontWeight.Medium)
                            Column(Modifier.weight(1f)) {
                                Text(c.name, style = TimetableType.courseName, color = AppColors.TextPrimary)
                                val detail = if (row.id in sameTimeAndPlace) {
                                    listOf(c.place, c.weeks.ifBlank { "每周" }, c.teacher)
                                        .filter(String::isNotBlank).joinToString(" · ")
                                } else c.place
                                if (detail.isNotBlank()) Text(detail, style = TimetableType.detail,
                                    color = AppColors.TextSecondary)
                            }
                        }
                        if (index < courses.lastIndex) HorizontalDivider(Modifier.padding(start = 72.dp), color = AppColors.Border)
                    }
                }
            }
        }
    }
}

@Composable
internal fun CourseDetailContent(course: TimetableCourse, weekday: String, period: String, clock: String?, edit: () -> Unit, color: () -> Unit) {
    Text("$weekday  $period", Modifier.padding(top = 8.dp),
        style = TimetableType.number, color = AppColors.TextPrimary)
    if (!clock.isNullOrBlank()) Text(clock, Modifier.padding(top = 2.dp),
        style = TimetableType.body, color = AppColors.Accent)
    Spacer(Modifier.height(22.dp))
    DetailLine("上课周次", course.weeks.ifBlank { "每周" })
    if (course.place.isNotBlank()) DetailLine("上课地点", course.place)
    if (course.teacher.isNotBlank()) DetailLine("任课教师", course.teacher)
    Row(Modifier.fillMaxWidth().padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(color, Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(8.dp)) {
            Text("课程颜色")
        }
        Button(edit, Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(8.dp)) {
            Text("编辑课程")
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 49.dp).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.width(78.dp), color = AppColors.TextSecondary, style = TimetableType.detail)
        Text(value, Modifier.weight(1f), color = AppColors.TextPrimary, style = TimetableType.body)
    }
    HorizontalDivider(color = AppColors.Border)
}

@Composable
internal fun TermPickerPanel(
    terms: List<String>, selected: String?, dismiss: () -> Unit,
    create: () -> Unit, select: (String) -> Unit, remove: (String) -> Unit
) {
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    TimetablePage("选择学期", dismiss) {
        MenuSection {
            MenuRow(Icons.Outlined.Add, "新建学期", action = create)
            terms.forEach { name ->
                val chosen = name == selected
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).heightIn(min = 54.dp).selectable(
                        selected = chosen, role = Role.RadioButton, onClick = { select(name) }
                    ).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(if (chosen) Icons.Outlined.CheckCircle else Icons.Outlined.CalendarMonth,
                            if (chosen) "已选" else null, Modifier.size(20.dp), tint = AppColors.Accent)
                        Text(name, style = TimetableType.body, color = AppColors.TextPrimary)
                    }
                    TextButton(onClick = { deleting = name }, modifier = Modifier.heightIn(min = 48.dp)
                        .semantics { contentDescription = "删除学期$name" }) {
                        Text("删除", style = TimetableType.detail, color = AppColors.Danger)
                    }
                }
                HorizontalDivider(color = AppColors.Border)
            }
        }
    }
    deleting?.takeIf { it in terms }?.let { target ->
        AlertDialog(onDismissRequest = { deleting = null }, containerColor = AppColors.SolidSurface,
            title = { Text("删除学期？", style = TimetableType.dataTitle) },
            text = { Text("将删除「$target」的本地课程、开学日期和显示设置。", style = TimetableType.body) },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
            confirmButton = { TextButton(onClick = { deleting = null; remove(target) }) {
                Text("确认删除", color = AppColors.Danger)
            } })
    }
}

@Composable
internal fun EmptyTimetable(term: String?, sync: () -> Unit, add: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(36.dp), tint = AppColors.Accent)
        Text(term ?: "还没有本地课表", Modifier.padding(top = 12.dp), color = AppColors.TextPrimary,
            style = if (term == null) TimetableType.title else TimetableType.dataTitle)
        if (term != null) Text("这个学期还没有课程", Modifier.padding(top = 4.dp), style = TimetableType.body,
            color = AppColors.TextSecondary)
        Spacer(Modifier.height(18.dp))
        Button(sync, shape = RoundedCornerShape(8.dp)) { Text("从教务导入") }
        TextButton(add) { Text(if (term == null) "新建学期" else "手动添加课程") }
    }
}
