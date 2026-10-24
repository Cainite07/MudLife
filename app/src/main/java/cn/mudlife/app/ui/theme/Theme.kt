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
    Accent = Color(0xFF344E43),
    TextPrimary = Color(0xFF292F2B),
    TextSecondary = Color(0xFF777B71),
    Success = Color(0xFF537051), Warning = Color(0xFF98632D),
    Danger = Color(0xFFA34636), ActiveBg = Color(0xFFE8ECE3),
    SurfaceVariant = Color(0xFFE8ECE3), Border = Color(0xFFE4E3D8),
    isDark = false
)

// A warm reading palette for the existing dark-mode switch.
val DarkColors = AppColorSet(
    Background = Color(0xFF24221C),
    Card = Color(0xFF302D25),
    SolidSurface = Color(0xFF353127),
    Primary = Color(0xFFEDE4D1),
    Accent = Color(0xFFC4C79C),
    TextPrimary = Color(0xFFEDE4D1),
    TextSecondary = Color(0xFFB8AC94),
    Success = Color(0xFFA3BE8F), Warning = Color(0xFFD6B47D),
    Danger = Color(0xFFE4A08C), ActiveBg = Color(0xFF424633),
    SurfaceVariant = Color(0xFF3B372D), Border = Color(0xFF514A3B),
    isDark = true
)

data class AppColorSet(
    val Background: Color, val Card: Color,
    val SolidSurface: Color = Color.White,
    val Primary: Color, val Accent: Color,
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
    val OnAccent: Color @Composable get() = if (isDark) Color(0xFF2D3321) else Color(0xFFFFFCF4)
    val Cold: Color @Composable get() = if (isDark) Color(0xFFA9C1C5) else Color(0xFF527783)
    val Hot: Color @Composable get() = if (isDark) Color(0xFFD7AF87) else Color(0xFFAC7748)
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
        surface = set.SolidSurface,
        surfaceContainer = set.SolidSurface,
        surfaceContainerHigh = set.SolidSurface,
        surfaceContainerHighest = set.SolidSurface,
        onPrimary = Color(0xFF2D3321),
        onSurface = set.TextPrimary,
        onBackground = set.TextPrimary,
        onSurfaceVariant = set.TextSecondary,
        outline = set.Border,
        outlineVariant = set.Border,
        error = set.Danger,
        secondary = set.TextSecondary,
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
        onPrimary = Color(0xFFFFFCF4),
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
