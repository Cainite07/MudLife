package cn.mudlife.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mudlife.app.ui.theme.AppColors
import cn.mudlife.app.utils.HapticHelper

@Composable
fun ShowerScreen(
    emoji: String,
    statusText: String,
    location: String,
    remaining: String = "0.00",
    elapsedSec: Int,
    autoDisConSec: Int = 0,
    isStopping: Boolean = false,
    onStopClick: () -> Unit
) {
    val context = LocalContext.current
    val isDark = AppColors.isDark
    val minutes = elapsedSec / 60
    val seconds = elapsedSec % 60
    val timeFormatted = "%02d:%02d".format(minutes, seconds)

    // 水滴柔光轻微呼吸动画 (0.95 -> 1.12)
    val infiniteTransition = rememberInfiniteTransition(label = "waterPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.height(10.dp))

            // 1. 生动出水图标 + 柔光呼吸动效环
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(120.dp)
                    .padding(bottom = 12.dp)
            ) {
                // 外层呼吸波纹
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(
                            if (isDark) Color(0xFF38BDF8).copy(alpha = pulseAlpha)
                            else Color(0xFF0284C7).copy(alpha = pulseAlpha)
                        )
                )
                // 内层晶体核心图标底板
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(
                            Brush.linearGradient(
                                colors = if (isDark) listOf(Color(0xFF0284C7), Color(0xFF2563EB))
                                else listOf(Color(0xFF38BDF8), Color(0xFF2563EB))
                            )
                        )
                ) {
                    Text(emoji, fontSize = 42.sp)
                }
            }

            // 2. 状态文字与设备具体位置
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981))
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    statusText,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = AppColors.TextPrimary,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                location.ifEmpty { "校园供水设备" },
                fontSize = 13.sp,
                color = AppColors.TextSecondary,
                fontWeight = FontWeight.Medium
            )

            Spacer(Modifier.height(30.dp))

            // 3. 实时出水计时卡片（白玉亚克力/沉浸黑曜石微透晶体，彻底去除旧版多余白块与模糊阴影）
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = AppColors.Card),
                border = BorderStroke(1.dp, AppColors.Border),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp, horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "本次出水已用时",
                        fontSize = 13.sp,
                        color = AppColors.TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            timeFormatted,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Black,
                            color = AppColors.Accent,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 2.sp
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "设备持续供水中 · 关水后按实际流量结算",
                        fontSize = 11.sp,
                        color = AppColors.TextSecondary.copy(alpha = 0.75f)
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // 4. 闲置自动关停倒计时胶囊（如有）
            if (autoDisConSec > 0) {
                val dMin = autoDisConSec / 60
                val dSec = autoDisConSec % 60
                val countdownText = if (dMin > 0) "${dMin}分${dSec}秒" else "${dSec}秒"
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = AppColors.Warning.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, AppColors.Warning.copy(alpha = 0.25f)),
                    shadowElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⏳", fontSize = 14.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "闲置约 $countdownText 后自动关停",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppColors.Warning
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            } else {
                Spacer(Modifier.height(12.dp))
            }

            // 5. 底部结束用水大按钮（纯平大圆角红键，0dp 脏阴影）
            Button(
                onClick = {
                    HapticHelper.heavyClick(context)
                    onStopClick()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                enabled = !isStopping,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFEF4444),
                    disabledContainerColor = Color(0xFFEF4444).copy(alpha = 0.45f)
                ),
                shape = RoundedCornerShape(18.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                if (isStopping) {
                    CircularProgressIndicator(
                        Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "正在关闭...",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                } else {
                    Text(
                        "结 束 用 水",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Text(
                "计费方式: 按实际流量计费 · 编号: ${location.takeLast(6).ifEmpty { "000000" }}",
                fontSize = 11.sp,
                color = AppColors.TextSecondary.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}
