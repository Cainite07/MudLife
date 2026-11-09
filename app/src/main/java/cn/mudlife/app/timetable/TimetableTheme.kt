package cn.mudlife.app.timetable

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import cn.mudlife.app.R
import cn.mudlife.app.ui.theme.MudLifeThemeSpecific
import cn.mudlife.app.ui.theme.ThemeMode
import cn.mudlife.app.utils.PrefsHelper

internal object TimetableType {
    private val displayFamily = FontFamily(Font(R.font.timetable_heading, weight = FontWeight.Medium))
    val body = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp)
    val detail = body.copy(fontSize = 13.sp, lineHeight = 18.sp)
    val title = TextStyle(fontFamily = displayFamily, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium)
    val dataTitle = title.copy(fontFamily = FontFamily.SansSerif)
    val courseName = body.copy(fontWeight = FontWeight.Normal)
    val courseTitle = dataTitle.copy(fontWeight = FontWeight.Normal)
    val week = title.copy(fontSize = 25.sp, lineHeight = 31.sp)
    val navigation = title.copy(fontSize = 15.sp, lineHeight = 20.sp)
    val section = title.copy(fontSize = 14.sp, lineHeight = 19.sp)
    val number = body.copy(fontSize = 17.sp, lineHeight = 22.sp,
        fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum")
}

internal fun timetableThemeMode(context: Context): ThemeMode {
    val saved = try {
        PrefsHelper.themeMode
    } catch (_: UninitializedPropertyAccessException) {
        // A desktop widget can open the timetable before MainActivity initializes preferences.
        PrefsHelper.init(context.applicationContext)
        PrefsHelper.themeMode
    }
    return ThemeMode.restore(saved)
}

internal fun timetableThemeIsDark(context: Context): Boolean {
    val systemDark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    return timetableThemeMode(context).resolve(systemDark) == ThemeMode.DARK
}

@Composable
internal fun TimetableTheme(mode: ThemeMode? = null, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val resolved = mode ?: remember(context) { timetableThemeMode(context) }
    MudLifeThemeSpecific(resolved) {
        val hostPress = LocalIndication.current
        val hostType = MaterialTheme.typography
        val type = remember(hostType) {
            hostType.copy(
                headlineSmall = TimetableType.title,
                titleLarge = TimetableType.title,
                titleMedium = TimetableType.body.copy(fontWeight = FontWeight.Medium),
                bodyLarge = TimetableType.body, bodyMedium = TimetableType.body,
                bodySmall = TimetableType.detail, labelMedium = TimetableType.detail,
                labelLarge = TimetableType.body.copy(fontWeight = FontWeight.Medium)
            )
        }
        MaterialTheme(colorScheme = MaterialTheme.colorScheme, typography = type, shapes = MaterialTheme.shapes) {
            CompositionLocalProvider(LocalIndication provides hostPress, content = content)
        }
    }
}
