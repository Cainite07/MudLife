package cn.mudlife.app.timetable

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import cn.mudlife.app.ui.theme.AppColors
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterialApi::class)
@Composable
internal fun TimetablePages(
    page: TimetableMenuEntry?, contentPadding: PaddingValues, back: () -> Unit, close: () -> Unit,
    home: @Composable (interactive: Boolean) -> Unit,
    content: @Composable (TimetableMenuEntry) -> Unit
) {
    val open = page != null
    val latestClose by rememberUpdatedState(close)
    // Keep the menu in the activity window. System back pops one navigation entry,
    // while the sheet's own scrim, dismiss action and downward drag dismiss the whole menu.
    val state = rememberModalBottomSheetState(initialValue = ModalBottomSheetValue.Hidden,
        skipHalfExpanded = true, animationSpec = tween(240))
    LaunchedEffect(open, state) {
        if (open) {
            // An initial or interrupted layout can report Hidden before the sheet ever appeared.
            snapshotFlow { state.currentValue }.dropWhile { it != ModalBottomSheetValue.Expanded }
                .first { it == ModalBottomSheetValue.Hidden }
            latestClose()
        }
    }
    var last by remember { mutableStateOf(page) }
    var measured by remember { mutableStateOf(false) }
    SideEffect { if (page != null) last = page }
    val presented = page ?: last
    LaunchedEffect(open, measured) {
        if (open) {
            if (measured) state.show()
        } else if (last != null) {
            state.hide()
            last = null
            measured = false
        }
    }
    BackHandler(enabled = presented != null) { if (open) back() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxSheetHeight = (maxHeight - contentPadding.calculateTopPadding()).coerceAtLeast(0.dp)
        val direction = LocalLayoutDirection.current
        val sheetPadding = PaddingValues(
            start = contentPadding.calculateStartPadding(direction),
            end = contentPadding.calculateEndPadding(direction),
            bottom = contentPadding.calculateBottomPadding()
        )
        ModalBottomSheetLayout(
            modifier = Modifier.fillMaxSize(),
            sheetState = state,
            sheetGesturesEnabled = open,
            sheetShape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            sheetElevation = 0.dp,
            sheetBackgroundColor = timetableSheetSurface,
            sheetContentColor = AppColors.TextPrimary,
            scrimColor = Color.Black.copy(alpha = if (AppColors.isDark) .48f else .32f),
            sheetContent = {
                if (presented != null) {
                    Column(Modifier.fillMaxWidth().heightIn(max = maxSheetHeight).onSizeChanged { measured = it.height > 0 }
                        .padding(sheetPadding).consumeWindowInsets(sheetPadding).imePadding()) {
                        Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) {
                            Spacer(Modifier.size(32.dp, 4.dp).background(AppColors.TextSecondary.copy(alpha = .55f), RoundedCornerShape(2.dp)))
                        }
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val available = maxHeight
                            AnimatedContent(targetState = presented, contentKey = { it.id }, contentAlignment = Alignment.BottomCenter,
                                transitionSpec = {
                                    val sign = if (targetState.id > initialState.id) 1 else -1
                                    (slideInVertically(tween(180)) { sign * it / 9 } + fadeIn(tween(150)))
                                        .togetherWith(fadeOut(tween(90))).using(SizeTransform { _, _ -> tween(210) })
                                }, label = "切换课表面板") { entry ->
                                val height = when (entry.panel) {
                                    TimetablePanel.Tools, is TimetablePanel.EditCourse -> null
                                    TimetablePanel.Display -> 330.dp
                                    is TimetablePanel.BellDetail, is TimetablePanel.Course, TimetablePanel.Wallpaper,
                                    TimetablePanel.NewTerm, TimetablePanel.Seasons -> 420.dp
                                    else -> available * .92f
                                }
                                Box(Modifier.fillMaxWidth().then(if (height == null) Modifier else Modifier.height(minOf(height, available)))
                                    .then(if (entry.id == presented.id) Modifier else Modifier.clearAndSetSemantics {})) {
                                    content(entry)
                                }
                            }
                        }
                    }
                }
            }
        ) {
            Box(Modifier.fillMaxSize().then(if (presented == null) Modifier else Modifier.clearAndSetSemantics {})) {
                home(presented == null)
                // The scrim appears when the first sheet has been measured. Block taps in that first frame too.
                if (presented != null) Box(Modifier.matchParentSize().pointerInput(Unit) {
                    awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
                })
            }
        }
    }
}
