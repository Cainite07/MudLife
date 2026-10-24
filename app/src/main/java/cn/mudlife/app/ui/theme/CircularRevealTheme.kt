package cn.mudlife.app.ui.theme

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset

data class ThemeRevealController(val toggle: (Offset) -> Unit = {})
val LocalThemeReveal = staticCompositionLocalOf { ThemeRevealController() }

/** Keep one composition alive: changing appearance must not recreate pages or their requests. */
@Composable
fun CircularRevealThemeHost(
    themeModeState: MutableState<ThemeMode>,
    onThemeChanged: (ThemeMode) -> Unit,
    content: @Composable () -> Unit
) {
    val onChanged by rememberUpdatedState(onThemeChanged)
    val controller = remember(themeModeState) {
        ThemeRevealController {
            val next = if (themeModeState.value == ThemeMode.LIGHT) ThemeMode.DARK else ThemeMode.LIGHT
            themeModeState.value = next
            onChanged(next)
        }
    }
    CompositionLocalProvider(LocalThemeMode provides themeModeState, LocalThemeReveal provides controller) {
        MudLifeThemeSpecific(themeModeState.value, content)
    }
}
