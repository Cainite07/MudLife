package cn.mudlife.app.ui

import android.graphics.Bitmap
import androidx.compose.ui.platform.LocalContext
import cn.mudlife.app.utils.HapticHelper
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mudlife.app.ui.theme.AppColors
import cn.mudlife.campuscard.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable internal fun UnifiedCardPage(vm: CampusViewModel) {
    val context = LocalContext.current
    val tap = { HapticHelper.tick(context) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if (vm.cards.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            vm.cards.forEach { c -> FilterChip(selected = vm.currentCard?.account == c.account, onClick = { tap(); vm.selectCard(c.account) }, label = { Text(c.name) }) }
        }
        if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Text("付款码", color = AppColors.Accent, fontSize = 14.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp, bottom = 18.dp))
        val codeValue = vm.code?.value
        val encoded by produceState<Pair<String, Bitmap>?>(null, codeValue) {
            this.value = null
            if (codeValue != null) this.value = withContext(Dispatchers.Default) {
                runCatching {
                    val matrix = QRCodeWriter().encode(codeValue, BarcodeFormat.QR_CODE, 800, 800, mapOf(EncodeHintType.MARGIN to 3))
                    codeValue to Bitmap.createBitmap(IntArray(800 * 800) { i -> if (matrix[i % 800, i / 800]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }, 800, 800, Bitmap.Config.ARGB_8888)
                }.getOrNull()
            }
        }
        Box(Modifier.align(Alignment.CenterHorizontally).widthIn(max = 285.dp).fillMaxWidth().aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp)).background(Color.White), contentAlignment = Alignment.Center) {
            val current = encoded?.takeIf { it.first == codeValue }?.second
            if (codeValue != null && vm.codeSeconds > 0 && current != null) Image(current.asImageBitmap(), "校园付款二维码", Modifier.fillMaxSize())
            else if (vm.codeBusy || (codeValue != null && current == null)) CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp)
            else Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(vm.codeError ?: "付款码暂不可用", color = Color(0xFF292F2B), fontSize = 13.sp)
                TextButton(onClick = { tap(); vm.generateCode() }, enabled = !vm.needsLogin) { Text("重新加载") }
            }
        }
        Text(if (vm.code != null) "${vm.codeSeconds} 秒后自动更新" else "在线付款码", fontSize = 12.sp,
            color = AppColors.TextSecondary, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp, bottom = 20.dp))
        if (vm.methods.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            vm.methods.forEach { method -> FilterChip(vm.selectedMethod == method.id, onClick = { tap(); vm.selectMethod(method.id) }, label = { Text(method.name) }) }
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 53.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("启动时打开付款码", Modifier.weight(1f), fontSize = 13.sp)
            Switch(vm.startupCode, onCheckedChange = { tap(); vm.updateStartupCode(it) })
        }
        HorizontalDivider(color = AppColors.Border)
        Row(Modifier.fillMaxWidth().heightIn(min = 53.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("出示时提亮并保持亮屏", Modifier.weight(1f), fontSize = 13.sp)
            Switch(vm.brightCode, onCheckedChange = { tap(); vm.updateBrightCode(it) })
        }
        Spacer(Modifier.height(16.dp))
    }
}
