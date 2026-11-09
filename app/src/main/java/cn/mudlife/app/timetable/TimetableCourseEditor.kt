package cn.mudlife.app.timetable

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import cn.mudlife.app.ui.theme.AppColors

private enum class CoursePicker { Time, Weeks }

@Composable
internal fun TimetableCourseEditor(
    draft: TimetablePanel.EditCourse, lastWeek: Int, periodsPerDay: Int, dismiss: () -> Unit,
    save: (TimetableCourse) -> Unit, remove: () -> Unit
) {
    val original = draft.existing?.course
    val dayLimit = periodsPerDay.coerceIn(1, TimetableLimits.MAX_PERIODS)
    val weekLimit = lastWeek.coerceIn(1, TimetableLimits.MAX_WEEKS)
    val defaultPeriod = draft.period.coerceIn(1, dayLimit)
    var name by rememberSaveable { mutableStateOf(original?.name.orEmpty()) }
    var place by rememberSaveable { mutableStateOf(original?.place.orEmpty()) }
    var teacher by rememberSaveable { mutableStateOf(original?.teacher.orEmpty()) }
    var day by rememberSaveable { mutableIntStateOf(original?.weekday ?: draft.day) }
    var start by rememberSaveable { mutableIntStateOf(original?.startPeriod ?: defaultPeriod) }
    var end by rememberSaveable { mutableIntStateOf(original?.endPeriod ?: defaultPeriod) }
    var weeks by rememberSaveable { mutableStateOf(original?.weeks ?: draft.week?.coerceIn(1, weekLimit)?.let { "${it}周" }.orEmpty()) }
    var picker by rememberSaveable { mutableStateOf<CoursePicker?>(null) }
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val nameFocus = remember { FocusRequester() }
    val focus = LocalFocusManager.current

    fun open(which: CoursePicker) {
        focus.clearFocus()
        picker = which
    }
    fun submit() {
        submitted = true
        if (name.isBlank()) {
            nameFocus.requestFocus()
            return
        }

        save(TimetableCourse(name.trim(), day, start, end, weeks.trim(), place.trim(), teacher.trim()))
    }

    TimetablePage(if (original == null) "添加课程" else "编辑课程", dismiss, wrapContentHeight = true, footer = {
        HorizontalDivider(color = AppColors.Border)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (original != null) {
                TextButton({ confirmRemove = true }) {
                    Text("移除课程", color = AppColors.Danger)
                }
            }
            Spacer(Modifier.weight(1f))
            Button(::submit, Modifier.widthIn(min = 108.dp).heightIn(min = 46.dp), shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent, contentColor = AppColors.OnAccent)) {
                Text("保存", style = TimetableType.body, fontWeight = FontWeight.Medium)
            }
        }
    }) {
        Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TimetableTextField("课程名称", name, { name = it }, Modifier.fillMaxWidth().focusRequester(nameFocus), textStyle = TimetableType.courseName,
                error = if (submitted && name.isBlank()) "请填写课程名称" else null)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TimetableTextField("上课地点", place, { place = it }, Modifier.weight(1f))
                TimetableTextField("任课教师", teacher, { teacher = it }, Modifier.weight(1f), ime = ImeAction.Done)
            }
        }
        MenuSection {
            val span = if (start == end) "第 ${start} 节" else "第 ${start}–${end} 节"
            MenuRow(Icons.Outlined.Schedule, "上课时间", "周${"一二三四五六日"[day - 1]} · $span") { open(CoursePicker.Time) }
            MenuRow(Icons.Outlined.DateRange, "上课周次", weeks.ifBlank { "每周" }) { open(CoursePicker.Weeks) }
        }
    }
    when (picker) {
        CoursePicker.Time -> TimePickerSheet(day, start, end, dayLimit, { picker = null }) { d, s, e ->
            day = d
            start = s
            end = e
            picker = null
        }
        CoursePicker.Weeks -> WeekPickerSheet(weeks, weekLimit, { picker = null }) {
            weeks = it
            picker = null
        }
        null -> Unit
    }
    if (confirmRemove && original != null) {
        val span = if (original.startPeriod == original.endPeriod) "${original.startPeriod}" else "${original.startPeriod}–${original.endPeriod}"
        AlertDialog(onDismissRequest = { confirmRemove = false }, title = { Text("移除这条上课安排？") },
            text = { Text("${original.name} · 周${"一二三四五六日"[original.weekday - 1]} · 第 $span 节") },
            dismissButton = { TextButton({ confirmRemove = false }) { Text("取消") } },
            confirmButton = { TextButton({ remove() }) { Text("移除", color = AppColors.Danger) } })
    }
}

@Composable
internal fun TimetableTextField(label: String, value: String, change: (String) -> Unit, modifier: Modifier = Modifier,
                                ime: ImeAction = ImeAction.Next, error: String? = null,
                                textStyle: TextStyle = TimetableType.body,
                                keyboardActions: KeyboardActions = KeyboardActions.Default) {
    TextField(value, change, modifier, singleLine = true, textStyle = textStyle, label = { Text(label, style = TimetableType.detail) },
        isError = error != null, supportingText = if (error != null) ({ Text(error, style = TimetableType.detail) }) else null,
        keyboardOptions = KeyboardOptions(imeAction = ime), keyboardActions = keyboardActions, colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
            errorContainerColor = Color.Transparent, focusedIndicatorColor = AppColors.Accent,
            unfocusedIndicatorColor = AppColors.Border, unfocusedLabelColor = AppColors.TextSecondary))
}
