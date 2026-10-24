package cn.mudlife.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mudlife.app.ui.theme.AppColors
import kotlinx.coroutines.delay

@Composable
fun MudLifeToast(message: String?, bottomSpacing: Dp = 88.dp, onDismiss: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    var displayed by remember { mutableStateOf("") }
    val dismiss by rememberUpdatedState(onDismiss)
    val accessibility = LocalAccessibilityManager.current
    LaunchedEffect(message) {
        if (message.isNullOrBlank()) {
            visible = false
        } else {
            displayed = message
            visible = true
            val duration = if (message.length > 24) 4200L else 2600L
            delay(accessibility?.calculateRecommendedTimeoutMillis(duration, containsIcons = true, containsText = true, containsControls = false) ?: duration)
            visible = false
            delay(160)
            dismiss()
        }
    }
    val icon = when {
        displayed.contains("失败") || displayed.contains("错误") || displayed.contains("异常") -> Icons.Outlined.ErrorOutline
        displayed.contains("复制") -> Icons.Outlined.ContentCopy
        displayed.contains("成功") || displayed.startsWith("已") -> Icons.Outlined.Check
        else -> Icons.Outlined.Info
    }
    Box(Modifier.fillMaxSize().navigationBarsPadding().imePadding().padding(horizontal = 24.dp, vertical = 8.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(visible, enter = fadeIn(tween(160)) + slideInVertically(tween(180)) { it / 4 },
            exit = fadeOut(tween(120)) + slideOutVertically(tween(140)) { it / 5 }, modifier = Modifier.padding(bottom = bottomSpacing)) {
            Surface(Modifier.widthIn(max = 360.dp).semantics { liveRegion = LiveRegionMode.Polite },
                shape = RoundedCornerShape(16.dp), color = AppColors.SolidSurface,
                border = BorderStroke(.5.dp, AppColors.Border), shadowElevation = 3.dp) {
                Row(Modifier.padding(horizontal = 15.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(icon, null, Modifier.size(18.dp), tint = AppColors.Accent)
                    Text(displayed, fontSize = 13.sp, lineHeight = 19.sp, color = AppColors.TextPrimary, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
