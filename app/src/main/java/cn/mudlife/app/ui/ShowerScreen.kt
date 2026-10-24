package cn.mudlife.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Timer
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
import cn.mudlife.app.ui.theme.paperBackground
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
    val minutes = elapsedSec / 60
    val seconds = elapsedSec % 60
    val timeFormatted = "%02d:%02d".format(minutes, seconds)

    Box(
        modifier = Modifier
            .fillMaxSize()
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

            // A static vector keeps the running page calm and avoids continuous decoration redraws.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(96.dp)
                    .padding(bottom = 12.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(AppColors.Accent.copy(alpha = 0.10f))
                ) {
                    Icon(deviceSymbol(emoji), null, tint = AppColors.Accent, modifier = Modifier.size(34.dp))
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
                        .background(AppColors.Success)
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

            Spacer(Modifier.height(24.dp))

            // 3. 实时出水计时卡片（白玉亚克力/沉浸黑曜石微透晶体，彻底去除旧版多余白块与模糊阴影）
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = AppColors.Card),
                border = BorderStroke(1.dp, AppColors.Border),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp, horizontal = 18.dp),
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
                            fontSize = 38.sp,
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
                        Icon(Icons.Outlined.Timer, null, tint = AppColors.Warning, modifier = Modifier.size(18.dp))
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
                    containerColor = AppColors.Danger,
                    disabledContainerColor = AppColors.Danger.copy(alpha = 0.45f)
                ),
                shape = RoundedCornerShape(18.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                if (isStopping) {
                    CircularProgressIndicator(
                        Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = AppColors.OnAccent
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "正在关闭...",
                        color = AppColors.OnAccent,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                } else {
                    Text(
                        "结 束 用 水",
                        color = AppColors.OnAccent,
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
