package cn.mudlife.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mudlife.app.ui.theme.AppColors
import cn.mudlife.app.utils.HapticHelper
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection

private val sections = listOf("首页" to Icons.Outlined.Home, "课表" to Icons.Outlined.DateRange,
    "账单" to Icons.Outlined.ReceiptLong, "我的" to Icons.Outlined.PersonOutline)

@Composable
internal fun CampusNavigationBar(selected: Int, select: (Int) -> Unit) {
    val context = LocalContext.current
    val paper = AppColors.Background
    val indicator = AppColors.ActiveBg.copy(alpha = .91f)
    val position = animateFloatAsState(selected.toFloat(), tween(215, easing = FastOutSlowInEasing),
        label = "selectedTabPosition")
    val dark = AppColors.isDark
    val scrim = remember(paper, dark) {
        if (dark) {
            // Keep light icons readable over a bright timetable wallpaper.
            Brush.verticalGradient(0f to paper.copy(alpha = 0f), .16f to paper.copy(alpha = .78f),
                .40f to paper.copy(alpha = .88f), 1f to paper.copy(alpha = .91f))
        } else {
            Brush.verticalGradient(0f to paper.copy(alpha = 0f), .34f to paper.copy(alpha = .68f),
                .72f to paper.copy(alpha = .82f), 1f to paper.copy(alpha = .88f))
        }
    }
    Box(Modifier.fillMaxWidth().background(scrim)
        .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
        .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 2.dp)) {
        Row(Modifier.fillMaxWidth().selectableGroup().drawBehind {
            val slot = size.width / sections.size
            val index = if (layoutDirection == LayoutDirection.Ltr) position.value else sections.lastIndex - position.value
            val width = minOf(52.dp.toPx(), slot)
            drawRoundRect(indicator, Offset(slot * (index + .5f) - width / 2, 3.dp.toPx()),
                Size(width, 29.dp.toPx()), CornerRadius(15.dp.toPx()))
        }) {
            sections.forEachIndexed { index, (label, icon) ->
                val interaction = remember { MutableInteractionSource() }
                val focused by interaction.collectIsFocusedAsState()
                val ink by animateColorAsState(if (index == selected) AppColors.Accent else AppColors.TextSecondary,
                    tween(145), label = "tabInk")
                Column(Modifier.weight(1f).heightIn(min = 54.dp)
                    .then(if (focused) Modifier.border(1.dp, AppColors.Accent, RoundedCornerShape(14.dp)) else Modifier)
                    .selectable(selected = index == selected, role = Role.Tab,
                        interactionSource = interaction, indication = null) {
                        if (index != selected) { HapticHelper.tick(context); select(index) }
                    }.padding(top = 3.dp, bottom = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.height(29.dp), contentAlignment = Alignment.Center) {
                        Icon(icon, null, Modifier.size(22.dp), tint = ink)
                    }
                    Text(label, fontSize = 11.sp, lineHeight = 16.sp, maxLines = 1, color = ink)
                }
            }
        }
    }
}

@Composable
internal fun RouteMotion(tab: Int, page: String, modifier: Modifier, content: @Composable () -> Unit) {
    val route = tab to page
    var last by remember { mutableStateOf(route) }
    val previous = remember(route) { last }
    SideEffect { last = route }
    val horizontal = previous.second.isEmpty() && page.isEmpty()
    if (tab == 1 && page.isEmpty()) {
        // The timetable's full-bleed wallpaper and its wash share the native image view.
        // Translating that view uncovers a strip of the scaffold along the leading or trailing edge.
        Box(modifier.fillMaxSize().clipToBounds()) { content() }
        return
    }
    val progress = remember(route) { Animatable(if (previous == route) 1f else 0f) }
    LaunchedEffect(progress) {
        if (progress.value < 1f) progress.animateTo(1f, tween(200, easing = LinearOutSlowInEasing))
    }
    val direction = if (horizontal) tab.compareTo(previous.first) else if (page.isEmpty()) -1 else 1
    val readingOrder = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    Box(modifier.fillMaxSize().clipToBounds()) {
        // Keep a single live destination, especially for payment codes and the timetable editor.
        Box(Modifier.fillMaxSize().graphicsLayer {
            val remaining = 1f - progress.value
            val travel = 12.dp.toPx() * remaining * direction
            // A horizontal tab change moves an opaque page. Fading a whole native grid
            // would allocate an extra screen-sized buffer and pulse the wallpaper's brightness.
            alpha = if (horizontal) 1f else 1f - .10f * remaining
            translationX = if (horizontal) travel * readingOrder else 0f
            translationY = if (horizontal) 0f else travel
        }) { content() }
    }
}
