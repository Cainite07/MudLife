package com.hualala.linyu.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hualala.linyu.ui.theme.AppColors
import kotlinx.coroutines.delay

@Composable
fun LinYuToast(
    message: String?,
    onDismiss: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        if (!message.isNullOrEmpty()) {
            visible = true
            delay(2400)
            visible = false
            delay(300)
            onDismiss()
        }
    }

    val isDark = AppColors.isDark

    // 智能语义微图标与色彩匹配
    val (iconText, iconColor, iconBg) = remember(message, isDark) {
        val msg = message.orEmpty()
        when {
            msg.contains("成功") || msg.contains("已开启") || msg.contains("已更换") || msg.contains("已激活") ->
                Triple("✓", if (isDark) Color(0xFF34D399) else Color(0xFF16A34A), if (isDark) Color(0x3034D399) else Color(0x1816A34A))
            msg.contains("复制") ->
                Triple("📋", if (isDark) Color(0xFFA8C7FA) else Color(0xFF1E40AF), if (isDark) Color(0x30A8C7FA) else Color(0x181E40AF))
            msg.contains("关闭") || msg.contains("取消") || msg.contains("已停止") ->
                Triple("ℹ", if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B), if (isDark) Color(0x3094A3B8) else Color(0x1864748B))
            msg.contains("失败") || msg.contains("错误") || msg.contains("异常") || msg.contains("超时") || msg.contains("耗尽") ->
                Triple("✕", if (isDark) Color(0xFFF87171) else Color(0xFFDC2626), if (isDark) Color(0x30F87171) else Color(0x18DC2626))
            msg.contains("请") || msg.contains("注意") || msg.contains("不足") ->
                Triple("⚠", if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706), if (isDark) Color(0x30FBBF24) else Color(0x18D97706))
            else ->
                Triple("💧", if (isDark) Color(0xFFA8C7FA) else Color(0xFF1E40AF), if (isDark) Color(0x30A8C7FA) else Color(0x181E40AF))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(
                initialOffsetY = { -it },
                animationSpec = spring(dampingRatio = 0.72f, stiffness = 380f)
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { -it / 2 },
                animationSpec = spring(stiffness = 500f)
            ) + fadeOut()
        ) {
            Surface(
                modifier = Modifier
                    .padding(top = 16.dp, start = 20.dp, end = 20.dp)
                    .shadow(
                        elevation = if (isDark) 16.dp else 10.dp,
                        shape = RoundedCornerShape(30.dp),
                        ambientColor = if (isDark) Color.Black else Color(0x20000000),
                        spotColor = if (isDark) Color.Black else Color(0x30000000)
                    ),
                shape = RoundedCornerShape(30.dp),
                color = if (isDark) Color(0xF01E2430) else Color(0xF8FFFFFF),
                border = BorderStroke(
                    1.dp,
                    if (isDark) Color(0x35FFFFFF) else Color(0x18000000)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(iconBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = iconText,
                            color = iconColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Text(
                        text = message.orEmpty(),
                        color = AppColors.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
