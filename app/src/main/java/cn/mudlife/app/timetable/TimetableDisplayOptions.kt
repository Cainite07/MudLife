package cn.mudlife.app.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cn.mudlife.app.ui.theme.AppColors
import kotlin.math.roundToInt

internal data class TimetableDisplayOptions(
    val periodCount: Int? = null,
    val cardOpacity: Int = 100,
    val showInactive: Boolean = false,
    val showSaturday: Boolean = true,
    val showSunday: Boolean = true,
    val showBellTimes: Boolean = true,
    val rowHeightDp: Int = 56,
    val termWeeks: Int? = null,
    val cornerRadiusDp: Int = 5,
    val inactiveCardOpacity: Int = 60,
    val showPlace: Boolean = true,
    val showTeacher: Boolean = false,
    val showGridLines: Boolean = false,
    val centerTextHorizontally: Boolean = true,
    val centerTextVertically: Boolean = true
) {
    init {
        require(periodCount == null || periodCount in 1..TimetableLimits.MAX_PERIODS)
        require(cardOpacity in 0..100)
        require(inactiveCardOpacity in 0..100)
        require(cornerRadiusDp in 0..24)
        require(rowHeightDp in 40..96)
        require(termWeeks == null || termWeeks in 1..TimetableLimits.MAX_WEEKS)
    }
}

internal enum class TimetableDisplayGroup(val title: String) {
    Layout("时间与布局"), Content("课程内容"), Style("课程外观")
}

@Composable
internal fun DisplayOptionsPanel(dismiss: () -> Unit, open: (TimetableDisplayGroup) -> Unit) {
    TimetablePage("课表设置", dismiss) {
        MenuSection {
            MenuRow(Icons.Outlined.GridView, TimetableDisplayGroup.Layout.title, titleStyle = TimetableType.navigation) { open(TimetableDisplayGroup.Layout) }
            MenuRow(Icons.Outlined.MenuBook, TimetableDisplayGroup.Content.title, titleStyle = TimetableType.navigation) { open(TimetableDisplayGroup.Content) }
            MenuRow(Icons.Outlined.Palette, TimetableDisplayGroup.Style.title, titleStyle = TimetableType.navigation) { open(TimetableDisplayGroup.Style) }
        }
    }
}

@Composable
internal fun DisplayGroupPanel(
    group: TimetableDisplayGroup,
    options: TimetableDisplayOptions,
    dismiss: () -> Unit,
    onStartDate: () -> Unit,
    onWallpaper: () -> Unit,
    change: (TimetableDisplayOptions, TimetableFeedback?) -> Unit
) {
    var edit by rememberSaveable { mutableStateOf<CountSetting?>(null) }
    TimetablePage(group.title, dismiss) {
        when (group) {
            TimetableDisplayGroup.Layout -> {
                MenuSection("教学安排") {
                    MenuRow(Icons.Outlined.CalendarToday, "开学日期", action = onStartDate)
                    MenuRow(Icons.Outlined.FormatListNumbered, "每天节数", options.periodCount?.let { "$it 节" } ?: "自动") {
                        edit = CountSetting.Periods
                    }
                    MenuRow(Icons.Outlined.DateRange, "学期周数", options.termWeeks?.let { "$it 周" } ?: "自动") {
                        edit = CountSetting.Weeks
                    }
                }
                MenuSection("星期") {
                    DisplaySwitch("显示周六", options.showSaturday) { change(options.copy(showSaturday = it), TimetableFeedback.Toggle) }
                    DisplaySwitch("显示周日", options.showSunday) { change(options.copy(showSunday = it), TimetableFeedback.Toggle) }
                }
                MenuSection("课表网格") {
                    DisplaySwitch("显示网格线", options.showGridLines) { change(options.copy(showGridLines = it), TimetableFeedback.Toggle) }
                    DisplaySwitch("显示上下课时间", options.showBellTimes) { change(options.copy(showBellTimes = it), TimetableFeedback.Toggle) }
                    DisplaySlider("课程格子高度", options.rowHeightDp, 40..96, { "$it dp" }, preview = { height ->
                        Box(Modifier.padding(end = 8.dp).size(34.dp, 48.dp).clearAndSetSemantics {},
                            contentAlignment = Alignment.Center) {
                            Box(Modifier.size(30.dp, (height / 2f).dp)
                                .background(AppColors.ActiveBg, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                                Text("课", style = TimetableType.detail, color = AppColors.Accent)
                            }
                        }
                    }) {
                        change(options.copy(rowHeightDp = it), null)
                    }
                }
            }
            TimetableDisplayGroup.Content -> {
                MenuSection("课程信息") {
                    DisplaySwitch("显示上课地点", options.showPlace) { change(options.copy(showPlace = it), TimetableFeedback.Toggle) }
                    DisplaySwitch("显示任课老师", options.showTeacher) { change(options.copy(showTeacher = it), TimetableFeedback.Toggle) }
                    DisplaySwitch("文字水平居中", options.centerTextHorizontally) { change(options.copy(centerTextHorizontally = it), TimetableFeedback.Toggle) }
                    DisplaySwitch("文字垂直居中", options.centerTextVertically) { change(options.copy(centerTextVertically = it), TimetableFeedback.Toggle) }
                }
                MenuSection("教学周") {
                    DisplaySwitch("显示非本周课程", options.showInactive) { change(options.copy(showInactive = it), TimetableFeedback.Toggle) }
                }
            }
            TimetableDisplayGroup.Style -> {
                MenuSection {
                    MenuRow(Icons.Outlined.Image, "课表背景", action = onWallpaper)
                }
                MenuSection("形状") {
                    DisplaySlider("课程块圆角", options.cornerRadiusDp, 0..24, { "$it dp" },
                        preview = { radius -> Swatch(radius, 100) }) { change(options.copy(cornerRadiusDp = it), null) }
                }
                MenuSection("透明度") {
                    OpacitySlider("本周课程透明度", options.cardOpacity, options.cornerRadiusDp) {
                        change(options.copy(cardOpacity = it), null)
                    }
                    OpacitySlider("非本周课程透明度", options.inactiveCardOpacity, options.cornerRadiusDp, inactive = true) {
                        change(options.copy(inactiveCardOpacity = it), null)
                    }
                }
            }
        }
    }
    edit?.let { setting ->
        val current = when (setting) {
            CountSetting.Periods -> options.periodCount
            CountSetting.Weeks -> options.termWeeks
        }
        CountDialog(setting, current, dismiss = { edit = null }) { count ->
            edit = null
            if (count != current) {
                change(when (setting) {
                    CountSetting.Periods -> options.copy(periodCount = count)
                    CountSetting.Weeks -> options.copy(termWeeks = count)
                }, TimetableFeedback.Saved)
            }
        }
    }
}

private enum class CountSetting(val title: String, val unit: String, val initial: Int, val limit: Int) {
    Periods("每天节数", "节", 11, TimetableLimits.MAX_PERIODS),
    Weeks("学期周数", "周", TimetableLimits.MAX_WEEKS, TimetableLimits.MAX_WEEKS)
}

@Composable
private fun Swatch(radius: Int, opacity: Int) {
    Box(Modifier.padding(end = 8.dp).size(width = 32.dp, height = 24.dp).clip(RoundedCornerShape(radius.dp))
        .background(AppColors.Accent.copy(alpha = opacity / 100f)))
}

@Composable
private fun OpacitySlider(label: String, opacity: Int, radius: Int, inactive: Boolean = false, change: (Int) -> Unit) {
    DisplaySlider(label, 100 - opacity, 0..100, format = { value ->
        when (value) {
            0 -> "0% · 不透明"
            100 -> "100% · 全透明"
            else -> "$value% 透明"
        }
    }, preview = { transparent ->
        val alpha = (100 - transparent) / 100f
        val fill = if (inactive) MaterialTheme.colorScheme.surfaceVariant else AppColors.ActiveBg.copy(alpha = alpha)
        Box(Modifier.padding(end = 8.dp).size(width = 32.dp, height = 24.dp)
            .clearAndSetSemantics { }.alpha(if (inactive) alpha else 1f)
            .background(fill, RoundedCornerShape(radius.dp)), contentAlignment = Alignment.Center) {
            Text("课", style = TimetableType.detail, color = AppColors.TextPrimary)
        }
    }) { change(100 - it) }
}

@Composable
private fun CountDialog(setting: CountSetting, current: Int?, dismiss: () -> Unit, choose: (Int?) -> Unit) {
    var input by rememberSaveable(setting, current) { mutableStateOf((current ?: setting.initial).toString()) }
    val number = input.toIntOrNull()?.takeIf { it in 1..setting.limit }
    AlertDialog(
        onDismissRequest = dismiss,
        containerColor = AppColors.SolidSurface,
        title = { Text(setting.title, color = AppColors.TextPrimary) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(enabled = number != null && number > 1, onClick = { number?.let {
                    input = (it - 1).toString()
                } }) {
                    Icon(Icons.Outlined.Remove, "减少一${setting.unit}")
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { if (it.length <= 2 && it.all(Char::isDigit)) input = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("1–${setting.limit} ${setting.unit}") },
                    textStyle = TimetableType.number.copy(textAlign = TextAlign.Center),
                    singleLine = true,
                    isError = input.isNotEmpty() && number == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { number?.let(choose) })
                )
                IconButton(enabled = number != null && number < setting.limit,
                    onClick = { number?.let {
                        input = (it + 1).toString()
                    } }) {
                    Icon(Icons.Outlined.Add, "增加一${setting.unit}")
                }
            }
        },
        dismissButton = { TextButton(onClick = { choose(null) }) { Text("自动") } },
        confirmButton = { TextButton(enabled = number != null, onClick = { number?.let(choose) }) { Text("确定") } }
    )
}

@Composable
private fun DisplaySlider(
    label: String,
    value: Int,
    range: IntRange,
    format: (Int) -> String,
    preview: (@Composable (Int) -> Unit)? = null,
    change: (Int) -> Unit
) {
    var draft by remember(value) { mutableIntStateOf(value) }
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = TimetableType.body, color = AppColors.TextPrimary)
        preview?.invoke(draft)
        Text(format(draft), color = AppColors.TextSecondary, style = TimetableType.detail)
    }
    Slider(value = draft.toFloat(), onValueChange = { draft = it.roundToInt() },
        modifier = Modifier.fillMaxWidth().semantics {
            contentDescription = label
            stateDescription = format(draft)
        },
        valueRange = range.first.toFloat()..range.last.toFloat(),
        onValueChangeFinished = { if (draft != value) change(draft) })
    HorizontalDivider(color = AppColors.Border)
}

@Composable
private fun DisplaySwitch(label: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(checked, role = Role.Switch, onValueChange = change),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = TimetableType.body, color = AppColors.TextPrimary)
        Switch(checked = checked, onCheckedChange = null)
    }
    HorizontalDivider(color = AppColors.Border)
}
