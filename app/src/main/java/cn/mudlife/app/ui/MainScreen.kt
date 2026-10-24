package cn.mudlife.app.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.outlined.Shower
import androidx.compose.material.icons.outlined.Key
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import cn.mudlife.app.QrScanActivity
import cn.mudlife.app.ui.theme.paperBackground
import cn.mudlife.app.ui.theme.AppColors
import cn.mudlife.app.utils.HapticHelper
import cn.mudlife.app.utils.PrefsHelper

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun MainScreen(phone: String, viewModel: MainViewModel = viewModel(), onUseCodes: () -> Unit = {}) {
    val context = LocalContext.current
    val appContext = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()

    // 扫码绑定设备
    val scanLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val sn = result.data?.getStringExtra(QrScanActivity.EXTRA_SN_CODE)
            if (!sn.isNullOrEmpty()) {
                viewModel.scanBind(sn)
            } else {
                viewModel.toastMessage = "未识别到有效二维码"
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshWallet()
        viewModel.initManagers(appContext)
    }

    var pullRefreshing by remember { mutableStateOf(false) }
    val pullState = rememberPullRefreshState(
        refreshing = pullRefreshing,
        onRefresh = {
            pullRefreshing = true
            viewModel.pullRefresh()
            // 1秒后隐藏顶部指示器
            scope.launch {
                kotlinx.coroutines.delay(1000)
                pullRefreshing = false
            }
        }
    )

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = AppColors.Background
        ) { padding ->
            if (viewModel.isShowering) {
                ShowerScreen(
                    emoji = viewModel.selectedDevice?.typeEmoji ?: "🚿",
                    statusText = viewModel.selectedDevice?.statusText ?: "正在沐浴中",
                    location = viewModel.selectedDevice?.locationOnly ?: "",
                    remaining = viewModel.showerRemaining,
                    elapsedSec = viewModel.showerElapsedSec,
                    autoDisConSec = viewModel.autoDisConSec,
                    isStopping = viewModel.isStopping,
                    onStopClick = { viewModel.stopShower() }
                )
            } else {
                Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().pullRefresh(pullState).padding(horizontal = 22.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("泥浆生活", fontFamily = FontFamily.Serif, fontSize = 27.sp, fontWeight = FontWeight.Medium, color = AppColors.TextPrimary, modifier = Modifier.weight(1f))
                                Text(java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("M月d日 · E", java.util.Locale.CHINA)), fontSize = 11.sp, color = AppColors.TextSecondary)
                            }
                        }
                        item {
                            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                HomeQuickAction("扫码出水", Icons.Outlined.QrCodeScanner, true, Modifier.weight(1f)) {
                                    scanLauncher.launch(Intent(context, QrScanActivity::class.java))
                                }
                                HomeQuickAction("使用码", Icons.Outlined.Key, false, Modifier.weight(1f), onUseCodes)
                            }
                        }

                        if (viewModel.unverifiedOrders.isNotEmpty()) {
                            item {
                                OutlinedCard(Modifier.fillMaxWidth()) {
                                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(if (viewModel.isVerifyingOrders) "正在核实上次设备状态" else "上次设备状态尚未确认",
                                                color = AppColors.TextPrimary)
                                            Text("${viewModel.unverifiedOrders.size} 台设备 · 以服务器结果为准",
                                                fontSize = 13.sp, color = AppColors.TextSecondary)
                                        }
                                        TextButton(onClick = { viewModel.cleanInactiveOrders() },
                                            enabled = !viewModel.isVerifyingOrders) { Text("重试") }
                                    }
                                }
                            }
                        }
                        // Only server-confirmed orders are presented as currently in use.
                        if (viewModel.activeOrders.isNotEmpty()) {
                            item {
                                Text("使用中的设备", fontWeight = FontWeight.Medium, fontSize = 16.sp,
                                    color = AppColors.TextPrimary)
                            }
                            viewModel.activeOrders.forEach { order ->
                                item { ActiveOrderCard(order, viewModel, phone) }
                            }
                        }

                        // 常用饮水机（支持一冷一热双直达，一键出水）
                        if (viewModel.recentDevices.isNotEmpty()) {
                            item {
                                Text("常用饮水机", fontWeight = FontWeight.Medium, fontSize = 16.sp,
                                    color = AppColors.TextPrimary)
                            }
                            item {
                                Surface(color = AppColors.Card, shape = RoundedCornerShape(22.dp)) {
                                    Column(Modifier.padding(horizontal = 16.dp, vertical = 3.dp)) {
                                        viewModel.recentDevices.forEachIndexed { index, dev ->
                                            key(dev.snCode) { RecentDrinkingDeviceCard(dev, viewModel, phone) }
                                            if (index < viewModel.recentDevices.lastIndex) HorizontalDivider(color = AppColors.Border.copy(alpha = .5f))
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            if (viewModel.recentDevices.isEmpty()) Text("扫码添加常用饮水机", fontSize = 13.sp, color = AppColors.TextSecondary, modifier = Modifier.padding(vertical = 20.dp))

                        }
                    }

                    PullRefreshIndicator(
                        refreshing = pullRefreshing,
                        state = pullState,
                        modifier = Modifier.align(Alignment.TopCenter),
                        backgroundColor = AppColors.Card,
                        contentColor = AppColors.Accent
                    )
                }
            }
        }
    }

    if (viewModel.showDeviceDetail) {
        val isActive = viewModel.selectedDevice?.snCode?.let { viewModel.isDeviceActive(it) } ?: false
        DeviceDetailDialog(viewModel.selectedDevice, isActive, viewModel.isOwner,
            onDismiss = { viewModel.showDeviceDetail = false },
            onConfirm = { viewModel.startShower(phone) })
    }

    // 开始使用中的加载提示（开阀确认需要几秒）
    if (viewModel.isStartingShower) {
        val promptText = viewModel.selectedDevice?.startingText ?: "正在开启设备..."
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(15_000)
            if (viewModel.isStartingShower) {
                viewModel.isStartingShower = false
                viewModel.toastMessage = "开阀超时，请重试"
            }
        }
        AlertDialog(
            onDismissRequest = {
                viewModel.isStartingShower = false
                viewModel.toastMessage = "已取消"
            },
            shape = RoundedCornerShape(22.dp),
            containerColor = AppColors.SolidSurface,
            title = { Text(promptText, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()) },
            text = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp, color = AppColors.Accent)
                    Spacer(Modifier.width(12.dp))
                    Text("正在确认设备是否开启，请稍候", color = AppColors.TextSecondary)
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    viewModel.isStartingShower = false
                    viewModel.toastMessage = "已取消"
                }) { Text("取消") }
            }
        )
    }

    // 自动关停确认弹窗
    if (viewModel.showAutoCloseDialog) {
        AlertDialog(
            onDismissRequest = {},
            shape = RoundedCornerShape(22.dp),
            containerColor = AppColors.SolidSurface,
            title = {
                Text("用水已完成", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth())
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.WaterDrop, null, tint = AppColors.Cold, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(viewModel.autoCloseDeviceName, fontWeight = FontWeight.Medium, color = AppColors.TextPrimary)
                    }
                    Spacer(Modifier.height(6.dp))
                    val sec = viewModel.autoCloseElapsed
                    val t = if (sec / 60 > 0) "${sec / 60}分${sec % 60}秒" else "${sec}秒"
                    Text("已用 $t", color = AppColors.TextSecondary)
                    Spacer(Modifier.height(12.dp))
                    if (viewModel.autoCloseConsumed > 0.0) {
                        Text("本次消费：¥%.2f".format(viewModel.autoCloseConsumed),
                            fontWeight = FontWeight.Bold, color = AppColors.Accent)
                    } else {
                        Text("账单已在后台记入钱包", fontSize = 13.sp, color = AppColors.TextSecondary)
                    }
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.confirmAutoClose() },
                    modifier = Modifier.fillMaxWidth()) {
                    Text("确 认")
                }
            },
            dismissButton = {}
        )
    }

}

@Composable
private fun ActiveOrderCard(order: cn.mudlife.app.model.ActiveOrder, viewModel: MainViewModel, phone: String) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.ActiveBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp))
                .background(AppColors.Accent.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center) {
                Icon(deviceSymbol(order.deviceEmoji), null, tint = AppColors.Accent, modifier = Modifier.size(23.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(order.deviceName.ifEmpty { "设备" },
                    fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(AppColors.ActiveBg)
                        .padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text("使用中", color = AppColors.Warning, fontSize = 11.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("点击进入出水页面", color = AppColors.TextSecondary, fontSize = 13.sp)
                }
            }
            Button(onClick = {
                HapticHelper.click(context)
                viewModel.lastDeviceSnCode = order.snCode
                viewModel.lastDeviceMac = order.deviceMac
                viewModel.startLastDevice(phone)
            }, shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Warning)) { Text("查看", color = AppColors.OnAccent) }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun RecentDrinkingDeviceCard(device: cn.mudlife.app.model.RecentDevice, viewModel: MainViewModel, phone: String) {
    var remove by remember { mutableStateOf(false) }
    val color = if (device.isHot) AppColors.Hot else AppColors.Accent
    Row(Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = { remove = true }).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = .09f)), contentAlignment = Alignment.Center) {
            Icon(deviceSymbol(if (device.isHot) "♨️" else "❄️"), null, Modifier.size(20.dp), tint = color)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(device.cleanName, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = AppColors.TextPrimary, maxLines = 2)
            val sn = if (device.snCode.contains(",")) device.snCode.substringAfterLast(",") else device.snCode.takeLast(8)
            Text("编号 $sn", fontSize = 12.sp, color = AppColors.TextSecondary, modifier = Modifier.padding(top = 3.dp))
        }
        OutlinedButton(onClick = { viewModel.startRecentDevice(device, phone) }, shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, AppColors.Border), contentPadding = PaddingValues(horizontal = 15.dp)) { Text("出水", fontSize = 13.sp) }
    }
    if (remove) AlertDialog(onDismissRequest = { remove = false }, title = { Text("移除常用设备？") },
        text = { Text(device.cleanName) }, confirmButton = { TextButton(onClick = { viewModel.deleteRecentDrinkingDevice(device.snCode); remove = false }) { Text("移除") } },
        dismissButton = { TextButton(onClick = { remove = false }) { Text("取消") } })
}

@Composable
private fun HomeQuickAction(title: String, symbol: androidx.compose.ui.graphics.vector.ImageVector, primary: Boolean, modifier: Modifier, action: () -> Unit) {
    val context = LocalContext.current
    val color = if (primary) AppColors.OnAccent else AppColors.TextPrimary
    Surface(onClick = { HapticHelper.tick(context); action() }, modifier = modifier, shape = RoundedCornerShape(16.dp),
        color = if (primary) AppColors.Accent else AppColors.Card,
        border = if (primary) null else BorderStroke(1.dp, AppColors.Border)) {
        Row(Modifier.heightIn(min = 68.dp).padding(horizontal = 16.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(symbol, null, Modifier.size(23.dp), tint = color)
            Spacer(Modifier.width(10.dp))
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = color)
        }
    }
}
