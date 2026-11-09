package cn.mudlife.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mudlife.app.ui.theme.AppColors
import cn.mudlife.app.utils.HapticHelper
import cn.mudlife.campuscard.CampusViewModel
import cn.mudlife.campuscard.Money
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val homeDateFormat = DateTimeFormatter.ofPattern("M月d日 · E", Locale.CHINA)

@Composable
internal fun HomePageHeader() {
    PageTitle("江大生活", padded = false) {
        Text(LocalDate.now().format(homeDateFormat), fontSize = 12.sp, color = AppColors.TextSecondary)
    }
}

@Composable
internal fun HomeCampusServices(
    campus: CampusViewModel,
    onPayment: () -> Unit,
    onScan: () -> Unit,
    onElectric: () -> Unit,
    onCodes: () -> Unit
) {
    val dark = AppColors.isDark
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        HomeCampusBalance(campus, onPayment)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ServiceAction("付款码", Icons.Outlined.QrCode2, AppColors.Accent, Modifier.weight(1f), onPayment)
            ServiceAction("扫码出水", Icons.Outlined.QrCodeScanner, AppColors.Cold, Modifier.weight(1f), onScan)
            ServiceAction("宿舍电费", Icons.Outlined.Bolt, AppColors.Hot, Modifier.weight(1f), onElectric)
            ServiceAction("使用码", Icons.Outlined.Key, if (dark) Color(0xFFB2A4BB) else Color(0xFF73667C),
                Modifier.weight(1f), onCodes)
        }
    }
}

@Composable
private fun ServiceAction(title: String, icon: ImageVector, tint: Color, modifier: Modifier, action: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier.clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button) { HapticHelper.tick(context); action() }
            .heightIn(min = 77.dp).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(44.dp).background(tint.copy(alpha = if (AppColors.isDark) .14f else .13f), RoundedCornerShape(15.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, Modifier.size(23.dp), tint = tint)
        }
        Spacer(Modifier.height(8.dp))
        Text(title, color = AppColors.TextPrimary, fontSize = 14.sp, lineHeight = 18.sp,
            fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
    }
}

@Composable
private fun HomeCampusBalance(campus: CampusViewModel, onClick: () -> Unit) {
    val context = LocalContext.current
    val signedIn = campus.hasSession && !campus.needsLogin
    val balance = if (signedIn) campus.currentCard?.balance(campus.config?.accountType.orEmpty()) else null
    val value = when {
        campus.needsLogin -> "学校登录已过期"
        !campus.hasSession -> "未登录学校账号"
        balance != null -> "¥ ${Money.yuan(balance)}"
        campus.busy -> "正在获取余额"
        else -> "暂未获取余额"
    }
    val action = when {
        campus.needsLogin -> "重新登录"
        !campus.hasSession -> "去登录"
        else -> "出示付款码"
    }
    Surface(
        onClick = { HapticHelper.tick(context); onClick() },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = AppColors.ActiveBg
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CreditCard, null, Modifier.size(16.dp), tint = AppColors.Accent)
                    Spacer(Modifier.width(7.dp))
                    Text(if (balance != null && campus.error != null) "校园卡余额 · 上次同步" else "校园卡余额",
                        color = AppColors.Accent, fontSize = 13.sp)
                }
                Spacer(Modifier.height(6.dp))
                Text(value, color = AppColors.TextPrimary, fontSize = if (balance != null) 30.sp else 17.sp,
                    fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.width(8.dp))
            Text(action, color = AppColors.Accent, fontSize = 12.sp)
            Icon(Icons.Outlined.ChevronRight, null, Modifier.size(16.dp), tint = AppColors.Accent)
        }
    }
}
