package cn.mudlife.app.timetable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.mudlife.app.ui.theme.AppColors

@Composable
internal fun PeriodBellPanel(period: Int, slot: WeekBellSlot?, dismiss: () -> Unit) {
    TimetablePage("第 ${period} 节作息", dismiss) {
        if (slot == null) {
            Text("尚未设置这节的时间", Modifier.padding(vertical = 16.dp), color = AppColors.TextSecondary)
        } else {
            MenuSection(slot.source) {
                slot.choices.forEachIndexed { index, choice ->
                    val bell = choice.bell
                    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                        Text("${bell.start} – ${bell.end}", color = AppColors.TextPrimary,
                            style = TimetableType.number)
                        if (choice.locations.isNotEmpty()) Text(choice.locations.joinToString("\n"),
                            Modifier.padding(top = 4.dp), color = AppColors.TextSecondary, style = TimetableType.detail)
                    }
                    if (index != slot.choices.lastIndex || slot.unresolved.isNotEmpty()) HorizontalDivider(color = AppColors.Border)
                }
                if (slot.unresolved.isNotEmpty()) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                        Text("时间待确认", color = AppColors.TextPrimary, style = TimetableType.body)
                        Text(slot.unresolved.joinToString("\n"), Modifier.padding(top = 4.dp),
                            color = AppColors.TextSecondary, style = TimetableType.detail)
                    }
                }
            }
        }
    }
}
