package com.hualala.linyu.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.hypot
import kotlin.math.max

data class ThemeRevealController(
    val toggle: (Offset) -> Unit = {}
)

val LocalThemeReveal = staticCompositionLocalOf { ThemeRevealController() }

/**
 * 顶层双向光影圆滑收缩/扩散容器
 * - 暗切亮：从右上角按钮向外圆环展开铺满全屏 (0 -> maxRadius)
 * - 亮切暗：从屏幕四周圆滑收缩聚拢回右上角按钮 (maxRadius -> 0)
 */
@Composable
fun CircularRevealThemeHost(
    themeModeState: MutableState<ThemeMode>,
    onThemeChanged: (ThemeMode) -> Unit,
    content: @Composable () -> Unit
) {
    val currentMode by themeModeState
    var isAnimating by remember { mutableStateOf(false) }
    var animOrigin by remember { mutableStateOf(Offset.Zero) }
    var targetMode by remember { mutableStateOf(currentMode) }
    val animProgress = remember { Animatable(0f) }

    val config = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { config.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { config.screenHeightDp.dp.toPx() }

    val controller = remember(currentMode, isAnimating) {
        ThemeRevealController(
            toggle = { origin ->
                if (!isAnimating) {
                    val nextMode = if (currentMode == ThemeMode.LIGHT) ThemeMode.DARK else ThemeMode.LIGHT
                    targetMode = nextMode
                    animOrigin = origin
                    isAnimating = true
                }
            }
        )
    }

    LaunchedEffect(isAnimating) {
        if (isAnimating) {
            animProgress.snapTo(0f)
            animProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 420,
                    easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)
                )
            )
            themeModeState.value = targetMode
            onThemeChanged(targetMode)
            isAnimating = false
        }
    }

    CompositionLocalProvider(
        LocalThemeMode provides themeModeState,
        LocalThemeReveal provides controller
    ) {
        if (!isAnimating) {
            // 常态：仅渲染当前主题，零性能开销
            LinYuThemeSpecific(currentMode) {
                Box(modifier = Modifier.fillMaxSize().background(LocalAppColors.current.Background)) {
                    content()
                }
            }
        } else {
            // 双向光影转场中：
            val originX = if (animOrigin == Offset.Zero) screenWidthPx - with(density) { 36.dp.toPx() } else animOrigin.x
            val originY = if (animOrigin == Offset.Zero) with(density) { 48.dp.toPx() } else animOrigin.y
            val center = Offset(originX, originY)

            val maxRadius = max(
                max(hypot(center.x, center.y), hypot(screenWidthPx - center.x, center.y)),
                max(hypot(center.x, screenHeightPx - center.y), hypot(screenWidthPx - center.x, screenHeightPx - center.y))
            )

            // 双向物理半径模型：
            // 目标为 LIGHT（暗切亮）：从圆心向外扩展铺满全屏 (0 -> maxRadius)
            // 目标为 DARK（亮切暗）：从屏幕四周圆滑收缩聚拢回圆心 (maxRadius -> 0)
            val currentRadius = if (targetMode == ThemeMode.LIGHT) {
                animProgress.value * maxRadius
            } else {
                (1f - animProgress.value) * maxRadius
            }

            Box(modifier = Modifier.fillMaxSize().background(DarkColors.Background)) {
                // 底层固定渲染暗色
                LinYuThemeSpecific(ThemeMode.DARK) {
                    Box(modifier = Modifier.fillMaxSize().background(DarkColors.Background)) {
                        content()
                    }
                }

                // 顶层渲染浅色，并应用以动画圆心为中心的圆形剪裁蒙版
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            val clipPath = Path().apply {
                                addOval(Rect(center = center, radius = currentRadius))
                            }
                            clipPath(clipPath) {
                                this@drawWithContent.drawContent()
                            }
                        }
                ) {
                    LinYuThemeSpecific(ThemeMode.LIGHT) {
                        Box(modifier = Modifier.fillMaxSize().background(LightColors.Background)) {
                            content()
                        }
                    }
                }
            }
        }
    }
}
