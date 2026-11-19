package cn.mudlife.app.timetable

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.mudlife.app.R
import cn.mudlife.app.ui.theme.AppColors
import java.time.Instant
import java.time.format.DateTimeFormatter

private val syncTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(campusZone)

@Composable
internal fun ExamPanel(
    terms: List<ExamTerm>, initial: ExamTerm, store: ExamStore, revision: Int,
    dismiss: () -> Unit, sync: (ExamTerm) -> Unit
) {
    var selected by rememberSaveable(initial.label) { mutableStateOf(initial.label) }
    var choosing by remember { mutableStateOf(false) }
    val term = terms.firstOrNull { it.label == selected } ?: initial
    val cached = remember(term, revision) { store.read(term) }
    val resources = LocalContext.current.resources
    val headingCharacters = remember(resources) {
        resources.openRawResource(R.raw.timetable_heading_characters).bufferedReader().use { it.readText() }
    }
    val termPrefix = remember(term) {
        val dash = "[-－–—]"
        val semester = "[${term.semester}${"一二三"[term.semester - 1]}]"
        Regex("""^\s*${term.year}\s*$dash\s*${term.year + 1}\s*(?:$dash\s*$semester(?!\d)(?:\s*学期)?|学年\s*第?\s*$semester\s*学期)\s*[-－–—:：·]?\s*""")
    }
    TimetablePage("考试信息查询", dismiss, footer = {
        Button(onClick = { sync(term) }, shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
            Text(if (cached == null) "添加考试信息" else "更新考试信息")
        }
    }) {
        if (terms.size <= 1) {
            Text(term.label, Modifier.padding(vertical = 12.dp), style = TimetableType.body, color = AppColors.TextPrimary)
        } else {
            Box {
                TextButton(onClick = { choosing = true }, contentPadding = PaddingValues(end = 8.dp)) {
                    Text(term.label)
                    Icon(Icons.Outlined.ExpandMore, "切换学期", Modifier.size(18.dp))
                }
                DropdownMenu(expanded = choosing, onDismissRequest = { choosing = false }) {
                    terms.forEach { choice ->
                        DropdownMenuItem(text = { Text(choice.label) }, onClick = {
                            selected = choice.label
                            choosing = false
                        })
                    }
                }
            }
        }
        val summary = when {
            cached == null -> "还没有导入考试信息"
            cached.rows.isEmpty() -> "教务系统暂无该学期的考试记录"
            else -> "${cached.rows.size} 场考试"
        }
        Text(summary, style = TimetableType.body, color = AppColors.TextPrimary)
        cached?.let {
            Text("同步于 ${syncTime.format(Instant.ofEpochMilli(it.syncedAt))}",
                Modifier.padding(top = 4.dp, bottom = 8.dp), style = TimetableType.detail, color = AppColors.TextSecondary)
            it.rows.forEach { exam ->
                MenuSection {
                    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val courseStyle = if (exam.course.all { it in headingCharacters }) TimetableType.section
                            else TimetableType.body.copy(fontWeight = FontWeight.Medium)
                        Text(exam.course, style = courseStyle, color = AppColors.TextPrimary)
                        Text(exam.time.ifBlank { "时间待公布" }, style = TimetableType.body, color = AppColors.TextPrimary)
                        val place = listOf(exam.campus, exam.room).filter(String::isNotBlank).joinToString(" · ")
                        val location = listOf(place.ifBlank { "考场待公布" }, exam.seat.takeIf(String::isNotBlank)?.let { "座位 $it" })
                            .filterNotNull().joinToString(" · ")
                        Text(location, style = TimetableType.body, color = AppColors.TextPrimary)
                        val session = exam.session.replaceFirst(termPrefix, "")
                        val details = listOf(session, exam.method, exam.note).filter(String::isNotBlank).joinToString(" · ")
                        if (details.isNotEmpty()) Text(details, style = TimetableType.detail, color = AppColors.TextSecondary)
                    }
                }
            }
        }
    }
}
