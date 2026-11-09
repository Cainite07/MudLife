package cn.mudlife.app.timetable

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import cn.mudlife.app.ui.TimetableFeature
import cn.mudlife.app.ui.TimetableTabPage

internal class MudLifeApplication : Application(), TimetableFeature {
    @Composable
    override fun rememberTab(activity: ComponentActivity, owner: String?): TimetableTabPage? =
        rememberTimetableTab(activity, owner)
}
