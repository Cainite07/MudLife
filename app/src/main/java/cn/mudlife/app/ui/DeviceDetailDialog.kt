package cn.mudlife.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mudlife.app.model.DeviceInfo
import cn.mudlife.app.ui.theme.AppColors

@Composable
fun DeviceDetailDialog(
    device: DeviceInfo?,
    isActive: Boolean = false,
    isOwner: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    if (device == null) return

    val emoji = device.typeEmoji
    val type = device.typeName
    val location = device.displayName

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        containerColor = AppColors.SolidSurface,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("$emoji $type", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(location, fontSize = 15.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
            }
        },
        text = {
            Column {
                DetailRow("SN 码", device.snCode)
                DetailRow("MAC 地址", device.macAddress)
                DetailRow("预扣金额", "¥ ${device.withholdMoney}")
                DetailRow("状态", if (isActive) "使用中" else "空闲")
            }
        },
        confirmButton = {
            val canUse = !isActive || isOwner
            val txt = if (isActive && !isOwner) "他人使用中" else if (isActive) "恢复使用" else device.actionText
            Button(onClick = { if (canUse) { onConfirm(); onDismiss() } },
                enabled = canUse,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (canUse) AppColors.Accent else AppColors.TextSecondary
                )) {
                Text(txt)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
