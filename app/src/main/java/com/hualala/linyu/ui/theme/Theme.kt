package com.hualala.linyu.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

enum class ThemeMode { LIGHT, DARK }

// ── Light palette (现代 MD3 浅色清新充盈灵透白玉调色) ──
val LightColors = AppColorSet(
    Background = Color(0xFFF1F5F9), // 清新珍珠浅底
    Card = Color(0xB8FFFFFF),       // 72% 高透亚克力灵透纯白
    SolidSurface = Color(0xFFFFFFFF),// 100% 纯白实体，彻底杜绝叠加透视
    Primary = Color(0xFF0F172A),
    Accent = Color(0xFF1E40AF),
    TextPrimary = Color(0xFF0F172A),
    TextSecondary = Color(0xFF64748B),
    Success = Color(0xFF16A34A), Warning = Color(0xFFD97706),
    Danger = Color(0xFFDC2626), ActiveBg = Color(0x201E40AF),
    SurfaceVariant = Color(0xEEF1F5F9), Border = Color(0xD0FFFFFF), // 晶体微光高光描边
    isDark = false
)

// ── Dark palette (现代 MD3 深度星空微透磨砂调色) ──
val DarkColors = AppColorSet(
    Background = Color(0xFF0E131D), // 极具纵深的沉浸墨底色
    Card = Color(0xBF1D232E),       // 75% 磨砂亚克力微透卡片色
    SolidSurface = Color(0xFF1E2430),// 100% 实体暗色，彻底杜绝叠加透视
    Primary = Color(0xFFFFFFFF),
    Accent = Color(0xFFA8C7FA),
    TextPrimary = Color(0xFFFFFFFF),
    TextSecondary = Color(0xFF94A3B8),
    Success = Color(0xFF34D399), Warning = Color(0xFFFBBF24),
    Danger = Color(0xFFF87171), ActiveBg = Color(0x301E293B),
    SurfaceVariant = Color(0xCC242C3A), Border = Color(0x22FFFFFF),
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
val LocalThemeMode = compositionLocalOf { mutableStateOf(ThemeMode.DARK) }

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
    val isDark: Boolean @Composable get() = LocalAppColors.current.isDark
}

@Composable
fun LinYuThemeSpecific(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = (mode == ThemeMode.DARK)
    val set = if (dark) DarkColors else LightColors
    val scheme = if (dark) darkColorScheme(
        primary = set.Accent,
        background = set.Background,
        surface = set.SolidSurface,
        surfaceContainer = set.SolidSurface,
        surfaceContainerHigh = set.SolidSurface,
        surfaceContainerHighest = set.SolidSurface,
        onPrimary = Color(0xFF062E6F),
        secondary = set.TextSecondary,
        surfaceVariant = set.SurfaceVariant
    ) else lightColorScheme(
        primary = set.Accent,
        background = set.Background,
        surface = set.SolidSurface,
        surfaceContainer = set.SolidSurface,
        surfaceContainerHigh = set.SolidSurface,
        surfaceContainerHighest = set.SolidSurface,
        onPrimary = Color.White,
        secondary = set.TextSecondary,
        surfaceVariant = set.SurfaceVariant
    )
    CompositionLocalProvider(LocalAppColors provides set) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

@Composable
fun LinYuTheme(content: @Composable () -> Unit) {
    val themeModeState = LocalThemeMode.current
    val currentMode by themeModeState
    LinYuThemeSpecific(currentMode, content)
}
