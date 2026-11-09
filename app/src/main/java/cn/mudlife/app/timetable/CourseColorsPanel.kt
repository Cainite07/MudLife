package cn.mudlife.app.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cn.mudlife.app.ui.theme.AppColors

@Composable
internal fun CourseColorsPanel(name: String, appearance: TimetableAppearance, dismiss: () -> Unit, back: () -> Unit) {
    val current = appearance.courseColor(name)
    fun choose(value: Int?) {
        if (value != current) appearance.setCourseColor(name, value)
        back()
    }
    TimetablePage("课程颜色", dismiss) {
        MenuSection(name, titleStyle = TimetableType.courseName) {
            MenuRow(Icons.Outlined.AutoAwesome,
                "自动配色", if (current == null) "已选用" else "") { choose(null) }
        }
        Spacer(Modifier.height(16.dp))
        for (group in appearance.colorOptions.chunked(4)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for ((label, value) in group) {
                    val chosen = current == value
                    val color = Color(if (AppColors.isDark) darkCourseFill(value) else value)
                    Box(Modifier.weight(1f).heightIn(min = 56.dp).clip(RoundedCornerShape(8.dp))
                        .selectable(selected = chosen, role = Role.RadioButton, onClick = { choose(value) })
                        .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
                        Box(Modifier.size(40.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
                            if (chosen) Icon(Icons.Outlined.Check, null, Modifier.size(17.dp),
                                tint = if (color.luminance() > .18f) Color.Black else Color.White)
                        }
                    }
                }
                repeat(4 - group.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
