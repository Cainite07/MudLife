package cn.mudlife.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"), LIGHT("浅色"), DARK("深色");
    fun resolve(systemDark: Boolean): ThemeMode = if (this == SYSTEM) { if (systemDark) DARK else LIGHT } else this
    companion object { fun restore(saved: String): ThemeMode = entries.firstOrNull { it.name == saved } ?: SYSTEM }
}

// Paper, warm ink and muted olive. Geometry belongs to the existing screens.
val LightColors = AppColorSet(
    Background = Color(0xFFF6F3EB),
    Card = Color(0xFFFFFDF8),
    SolidSurface = Color(0xFFFFFDF8),
    Primary = Color(0xFF292F2B),
    Accent = Color(0xFF344E43), OnAccent = Color(0xFFFFFCF4),
    TextPrimary = Color(0xFF292F2B),
    TextSecondary = Color(0xFF777B71),
    Success = Color(0xFF537051), Warning = Color(0xFF98632D),
    Danger = Color(0xFFA34636), ActiveBg = Color(0xFFE8ECE3),
    SurfaceVariant = Color(0xFFE8ECE3), Border = Color(0xFFE4E3D8),
    isDark = false
)

val DarkColors = AppColorSet(
    Background = Color(0xFF181B19),
    Card = Color(0xFF272D29),
    SolidSurface = Color(0xFF303832),
    Primary = Color(0xFFE5E7DE),
    Accent = Color(0xFFAAC8AF), OnAccent = Color(0xFF192C21),
    TextPrimary = Color(0xFFE5E7DE),
    TextSecondary = Color(0xFFAAB1A8),
    Success = Color(0xFFA5C6AA), Warning = Color(0xFFD7B687),
    Danger = Color(0xFFE3A99B), ActiveBg = Color(0xFF304438),
    SurfaceVariant = Color(0xFF39423B), Border = Color(0xFF424A44),
    isDark = true
)

data class AppColorSet(
    val Background: Color, val Card: Color,
    val SolidSurface: Color = Color.White,
    val Primary: Color, val Accent: Color, val OnAccent: Color,
    val TextPrimary: Color, val TextSecondary: Color,
    val Success: Color, val Warning: Color,
    val Danger: Color, val ActiveBg: Color,
    val SurfaceVariant: Color,
    val Border: Color = Color(0x18FFFFFF),
    val isDark: Boolean = false
)

val LocalAppColors = staticCompositionLocalOf { LightColors }
val LocalThemeMode = compositionLocalOf { mutableStateOf(ThemeMode.SYSTEM) }

/** Convenience: use AppColors.xxx in composable functions */
object AppColors {
    val Background: Color @Composable get() = LocalAppColors.current.Background
    val Card: Color @Composable get() = LocalAppColors.current.Card
    val SolidSurface: Color @Composable get() = LocalAppColors.current.SolidSurface
    val Primary: Color @Composable get() = LocalAppColors.current.Primary
    val Accent: Color @Composable get() = LocalAppColors.current.Accent
    val TextPrimary: Color @Composable get() = LocalAppColors.current.TextPrimary
    val TextSecondary: Color @Composable get() = LocalAppColors.current.TextSecondary
    val Success: Color @Composable get() = LocalAppColors.current.Success
    val Warning: Color @Composable get() = LocalAppColors.current.Warning
    val Danger: Color @Composable get() = LocalAppColors.current.Danger
    val ActiveBg: Color @Composable get() = LocalAppColors.current.ActiveBg
    val Border: Color @Composable get() = LocalAppColors.current.Border
    val OnAccent: Color @Composable get() = LocalAppColors.current.OnAccent
    val Cold: Color @Composable get() = if (isDark) Color(0xFF9FBAC5) else Color(0xFF527783)
    val Hot: Color @Composable get() = if (isDark) Color(0xFFD0A88A) else Color(0xFFAC7748)
    val isDark: Boolean @Composable get() = LocalAppColors.current.isDark
}

@Composable
fun MudLifeThemeSpecific(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = mode == ThemeMode.DARK || (mode == ThemeMode.SYSTEM && isSystemInDarkTheme())
    val set = if (dark) DarkColors else LightColors
    val scheme = if (dark) darkColorScheme(
        primary = set.Accent,
        primaryContainer = set.ActiveBg,
        onPrimaryContainer = set.Accent,
        secondaryContainer = set.ActiveBg,
        onSecondaryContainer = set.Accent,
        background = set.Background,
        surface = set.Card,
        surfaceDim = set.Background,
        surfaceBright = Color(0xFF414A43),
        surfaceContainerLowest = Color(0xFF131613),
        surfaceContainerLow = Color(0xFF1E231F),
        surfaceContainer = set.Card,
        surfaceContainerHigh = set.SolidSurface,
        surfaceContainerHighest = set.SurfaceVariant,
        onPrimary = set.OnAccent,
        onSurface = set.TextPrimary,
        onBackground = set.TextPrimary,
        onSurfaceVariant = set.TextSecondary,
        outline = Color(0xFF738176),
        outlineVariant = set.Border,
        error = set.Danger,
        onError = Color(0xFF34201C),
        errorContainer = Color(0xFF4B332F),
        onErrorContainer = set.Danger,
        secondary = set.TextSecondary,
        onSecondary = set.OnAccent,
        tertiary = set.Warning,
        onTertiary = Color(0xFF33281A),
        tertiaryContainer = Color(0xFF463B2C),
        onTertiaryContainer = set.Warning,
        inverseSurface = set.TextPrimary,
        inverseOnSurface = set.Background,
        inversePrimary = LightColors.Accent,
        surfaceVariant = set.SurfaceVariant
    ) else lightColorScheme(
        primary = set.Accent,
        primaryContainer = set.ActiveBg,
        onPrimaryContainer = set.Accent,
        secondaryContainer = set.ActiveBg,
        onSecondaryContainer = set.Accent,
        background = set.Background,
        surface = set.SolidSurface,
        surfaceContainer = set.SolidSurface,
        surfaceContainerHigh = set.SolidSurface,
        surfaceContainerHighest = set.SolidSurface,
        onPrimary = set.OnAccent,
        onSurface = set.TextPrimary,
        onBackground = set.TextPrimary,
        onSurfaceVariant = set.TextSecondary,
        outline = set.Border,
        outlineVariant = set.Border,
        error = set.Danger,
        secondary = set.TextSecondary,
        surfaceVariant = set.SurfaceVariant
    )
    CompositionLocalProvider(LocalAppColors provides set) {
        val base = remember { Typography() }
        MaterialTheme(colorScheme = scheme, typography = base.copy(
            bodyLarge = base.bodyLarge.copy(fontSize = 14.sp, lineHeight = 20.sp),
            bodyMedium = base.bodyMedium.copy(fontSize = 13.sp, lineHeight = 19.sp),
            bodySmall = base.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
            headlineLarge = base.headlineLarge.copy(fontFamily = FontFamily.Serif),
            headlineMedium = base.headlineMedium.copy(fontFamily = FontFamily.Serif),
            headlineSmall = base.headlineSmall.copy(fontFamily = FontFamily.Serif),
            titleLarge = base.titleLarge.copy(fontFamily = FontFamily.Serif)
        )) {
            CompositionLocalProvider(LocalIndication provides rememberPaperPressIndication(set.Accent), content = content)
        }
    }
}

@Composable
fun MudLifeTheme(content: @Composable () -> Unit) {
    val themeModeState = LocalThemeMode.current
    val currentMode by themeModeState
    MudLifeThemeSpecific(currentMode, content)
}
