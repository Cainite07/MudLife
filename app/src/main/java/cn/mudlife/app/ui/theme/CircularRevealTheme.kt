package cn.mudlife.app.ui.theme

import android.app.Activity
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.Window
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.hypot
import kotlin.math.max

/** Exactly one live page tree; a short-lived bitmap freezes the complete old palette. */
data class ThemeRevealController(
    val toggle: (Offset) -> Unit = {},
    val select: (ThemeMode, Offset) -> Unit = { _, _ -> },
    val transitioning: Boolean = false
)
val LocalThemeReveal = staticCompositionLocalOf { ThemeRevealController() }
val LocalThemeWindowDark = compositionLocalOf { false }

internal object ThemeRevealGeometry {
    fun radius(width: Float, height: Float, x: Float, y: Float): Float =
        hypot(max(x, width - x), max(y, height - y)) + 1f
}

@OptIn(ExperimentalCoroutinesApi::class)
private suspend fun capture(window: Window): Bitmap? {
    val view = window.decorView
    if (view.width <= 0 || view.height <= 0) return null
    return withTimeoutOrNull(350) {
        suspendCancellableCoroutine { continuation ->
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            try {
                PixelCopy.request(window, bitmap, { result ->
                    if (result == PixelCopy.SUCCESS && continuation.isActive)
                        continuation.resume(bitmap) { bitmap.recycle() }
                    else {
                        bitmap.recycle()
                        if (continuation.isActive) continuation.resume(null) {}
                    }
                }, Handler(Looper.getMainLooper()))
            } catch (_: IllegalArgumentException) {
                bitmap.recycle()
                if (continuation.isActive) continuation.resume(null) {}
            }
        }
    }
}

@Composable
fun CircularRevealThemeHost(
    themeModeState: MutableState<ThemeMode>,
    onThemeChanged: (ThemeMode) -> Unit,
    content: @Composable () -> Unit
) {
    val activity = LocalContext.current as Activity
    val systemDark = isSystemInDarkTheme()
    val requested = themeModeState.value.resolve(systemDark)
    var rendered by remember { mutableStateOf(requested) }
    var previous by remember { mutableStateOf(rendered) }
    var snapshot by remember { mutableStateOf<Bitmap?>(null) }
    var pendingOrigin by remember { mutableStateOf(Offset.Unspecified) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var rootOffset by remember { mutableStateOf(Offset.Zero) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    var transitioning by remember { mutableStateOf(false) }
    val fraction = remember { Animatable(0f) }
    val onChanged by rememberUpdatedState(onThemeChanged)
    val select: (ThemeMode, Offset) -> Unit = { next, point ->
        if (!transitioning) {
            pendingOrigin = if (next.resolve(systemDark) != rendered) point else Offset.Unspecified
            themeModeState.value = next
            onChanged(next)
        }
    }
    val controller = ThemeRevealController(
        toggle = { select(if (rendered == ThemeMode.DARK) ThemeMode.LIGHT else ThemeMode.DARK, it) },
        select = select, transitioning = transitioning)

    LaunchedEffect(requested) {
        if (requested == rendered) return@LaunchedEffect
        if (!ValueAnimator.areAnimatorsEnabled() || rootSize == IntSize.Zero) {
            rendered = requested
            return@LaunchedEffect
        }
        transitioning = true
        try {
            // Let the appearance picker disappear while retaining the complete old palette.
            withFrameNanos { }; withFrameNanos { }
            val old = capture(activity.window)
            if (old == null) { rendered = requested; return@LaunchedEffect }
            val windowScreen = IntArray(2).also { activity.window.decorView.getLocationOnScreen(it) }
            val point = if (pendingOrigin.isSpecified)
                pendingOrigin - Offset(windowScreen[0].toFloat(), windowScreen[1].toFloat()) - rootOffset
            else Offset(rootSize.width / 2f, rootSize.height / 2f)
            origin = Offset(point.x.coerceIn(0f, rootSize.width.toFloat()), point.y.coerceIn(0f, rootSize.height.toFloat()))
            pendingOrigin = Offset.Unspecified
            previous = rendered
            fraction.snapTo(if (requested == ThemeMode.DARK) 0f else 1f)
            snapshot = old
            rendered = requested
            withFrameNanos { }
            fraction.animateTo(if (requested == ThemeMode.DARK) 1f else 0f, tween(420, easing = FastOutSlowInEasing))
        } finally {
            // Drop the in-memory snapshot; never save it or manually recycle a GPU-referenced bitmap.
            snapshot = null
            transitioning = false
        }
    }
    CompositionLocalProvider(LocalThemeMode provides themeModeState, LocalThemeReveal provides controller,
        LocalThemeWindowDark provides ((if (snapshot != null) previous else rendered) == ThemeMode.DARK)) {
        MudLifeThemeSpecific(rendered) {
            Box(Modifier.fillMaxSize().background(AppColors.Background).onGloballyPositioned {
                rootOffset = it.positionInWindow(); rootSize = it.size
            }) {
                content()
                snapshot?.let { bitmap ->
                    val image = remember(bitmap) { bitmap.asImageBitmap() }
                    Canvas(Modifier.fillMaxSize().pointerInput(bitmap) {
                        awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
                    }) {
                        val radius = ThemeRevealGeometry.radius(size.width, size.height, origin.x, origin.y) * fraction.value
                        val circle = Path().apply { addOval(androidx.compose.ui.geometry.Rect(
                            origin.x - radius, origin.y - radius, origin.x + radius, origin.y + radius)) }
                        clipPath(circle, clipOp = if (rendered == ThemeMode.DARK) ClipOp.Difference else ClipOp.Intersect) {
                            drawImage(image, topLeft = -rootOffset)
                        }
                    }
                }
            }
        }
    }
}
