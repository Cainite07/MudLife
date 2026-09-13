package com.hualala.linyu.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.QrCodeScanner
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.hualala.linyu.QrScanActivity
import com.hualala.linyu.ui.theme.AppColors
import com.hualala.linyu.utils.HapticHelper
import com.hualala.linyu.utils.PrefsHelper

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun MainScreen(phone: String, viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val appContext = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var visible by remember { mutableStateOf(false) }

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
        viewModel.loadBills()
        viewModel.initManagers(appContext)
        visible = true
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

    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Scaffold(
            containerColor = Color.Transparent
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
                        modifier = Modifier.fillMaxSize().pullRefresh(pullState).padding(horizontal = 20.dp),
                        contentPadding = PaddingValues(bottom = 110.dp),
                        verticalArrangement = Arrangement.spacedBy(22.dp)
                    ) {
                        item { Spacer(Modifier.height(10.dp)) }

                        item {
                            Row(Modifier.fillMaxWidth().padding(top = 6.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text("泥浆生活", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                            }
                        }

                        // 居中大号扫码出水卡片（唯一扫码直达入口）
                        item {
                            Card(
                                onClick = {
                                    HapticHelper.click(context)
                                    scanLauncher.launch(Intent(context, QrScanActivity::class.java))
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(26.dp),
                                colors = CardDefaults.cardColors(containerColor = AppColors.Accent.copy(alpha = 0.08f)),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(26.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier.size(58.dp).clip(CircleShape).background(AppColors.Accent),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Outlined.QrCodeScanner, null, tint = Color.White, modifier = Modifier.size(30.dp))
                                    }
                                    Spacer(Modifier.width(18.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("扫码出水", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                                        Spacer(Modifier.height(4.dp))
                                        Text("支持直饮水机冷水与热水", fontSize = 13.sp, color = AppColors.TextSecondary)
                                    }
                                    Icon(Icons.Default.KeyboardArrowRight, null, tint = AppColors.TextSecondary)
                                }
                            }
                        }

                        // 使用中的设备（多个）
                        if (viewModel.activeOrders.isNotEmpty()) {
                            item {
                                Text("使用中的设备", fontWeight = FontWeight.Bold, fontSize = 18.sp,
                                    color = AppColors.TextPrimary)
                            }
                            viewModel.activeOrders.forEach { order ->
                                item { ActiveOrderCard(order, viewModel, phone) }
                            }
                        }

                        // 常用饮水机（支持一冷一热双直达，一键出水）
                        if (viewModel.recentDevices.isNotEmpty()) {
                            item {
                                Text("常用饮水机", fontWeight = FontWeight.Bold, fontSize = 18.sp,
                                    color = AppColors.TextPrimary)
                            }
                            viewModel.recentDevices.forEach { dev ->
                                item { RecentDrinkingDeviceCard(dev, viewModel, phone) }
                            }
                        }

                        item { Spacer(Modifier.height(32.dp)) }
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
                        Text("💧 ", fontSize = 16.sp)
                        Text(viewModel.autoCloseDeviceName, fontWeight = FontWeight.Medium, color = AppColors.TextPrimary)
                    }
                    Spacer(Modifier.height(6.dp))
                    val sec = viewModel.autoCloseElapsed
                    val t = if (sec / 60 > 0) "${sec / 60}分${sec % 60}秒" else "${sec}秒"
                    Text("⏱ 已用 $t", color = AppColors.TextSecondary)
                    Spacer(Modifier.height(12.dp))
                    if (viewModel.autoCloseConsumed > 0.0) {
                        Text("本次消费：¥%.2f".format(viewModel.autoCloseConsumed),
                            fontWeight = FontWeight.Bold, color = AppColors.Accent)
                    } else {
                        Text("账单已在后台记入钱包", fontSize = 12.sp, color = AppColors.TextSecondary)
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
private fun ActiveOrderCard(order: com.hualala.linyu.model.ActiveOrder, viewModel: MainViewModel, phone: String) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.ActiveBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
                .background(if (order.deviceEmoji == "🪥") Color(0xFFFFCC80) else if (order.deviceEmoji == "🚰") Color(0xFF0284C7) else AppColors.Accent),
                contentAlignment = Alignment.Center) { Text(order.deviceEmoji, fontSize = 22.sp) }
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
                    Text("点击进入出水页面", color = AppColors.TextSecondary, fontSize = 12.sp)
                }
            }
            Button(onClick = {
                HapticHelper.click(context)
                viewModel.lastDeviceSnCode = order.snCode
                viewModel.lastDeviceMac = order.deviceMac
                viewModel.startLastDevice(phone)
            }, shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Warning)) { Text("查看", color = Color.White) }
        }
    }
}

@Composable
private fun RecentDrinkingDeviceCard(
    device: com.hualala.linyu.model.RecentDevice,
    viewModel: MainViewModel,
    phone: String
) {
    val context = LocalContext.current
    val isHot = device.isHot
    val iconBg = if (isHot) Color(0xFFFF9800) else Color(0xFF0284C7)
    val iconEmoji = if (isHot) "♨️" else "❄️"

    var offsetX by remember { mutableStateOf(0f) }
    val animatedOffset by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "swipeOffset"
    )
    val density = LocalDensity.current
    val maxDragPx = with(density) { 76.dp.toPx() }
    val draggableState = rememberDraggableState { delta ->
        offsetX = (offsetX + delta).coerceIn(-maxDragPx, 0f)
    }

    val swipeProgress = (-animatedOffset / maxDragPx).coerceIn(0f, 1f)
    val endCorner = (20f * (1f - swipeProgress)).dp
    val dynamicCardShape = RoundedCornerShape(
        topStart = 20.dp,
        bottomStart = 20.dp,
        topEnd = endCorner,
        bottomEnd = endCorner
    )

    Box(Modifier.fillMaxWidth()) {
        // 底层操作区：仅单一红色删除按钮！静止时完全透明防透光，左侧直角与主卡片平直接缝
        if (swipeProgress > 0.001f) {
            Row(
                modifier = Modifier
                    .matchParentSize()
                    .alpha(swipeProgress),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(76.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp, topStart = 0.dp, bottomStart = 0.dp))
                        .background(Color(0xFFE53935))
                        .clickable {
                            HapticHelper.heavyClick(context)
                            viewModel.deleteRecentDrinkingDevice(device.snCode)
                            offsetX = 0f
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🗑️", fontSize = 17.sp)
                        Spacer(Modifier.height(2.dp))
                        Text("删除", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        // 顶层主卡片（右侧两角随着滑动距离平滑变方，零悬空凹陷）
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(animatedOffset.roundToInt(), 0) }
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Horizontal,
                    onDragStopped = {
                        offsetX = if (offsetX < -maxDragPx / 2) -maxDragPx else 0f
                    }
                )
                .clickable {
                    if (offsetX < 0f) offsetX = 0f
                },
            shape = dynamicCardShape,
            colors = CardDefaults.cardColors(containerColor = AppColors.Card),
            border = BorderStroke(1.dp, AppColors.Border),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(iconEmoji, fontSize = 20.sp)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    Text(
                        device.cleanName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = AppColors.TextPrimary,
                        maxLines = 2
                    )
                    Spacer(Modifier.height(4.dp))
                    val snSnippet = if (device.snCode.contains(",")) device.snCode.substringAfterLast(",") else device.snCode.takeLast(8)
                    Text("编号: $snSnippet", color = AppColors.TextSecondary, fontSize = 12.sp)
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        HapticHelper.heavyClick(context)
                        if (offsetX < 0f) {
                            offsetX = 0f
                        } else {
                            viewModel.startRecentDevice(device, phone)
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isHot) Color(0xFFF57C00) else AppColors.Accent
                    ),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                ) {
                    Text("出水", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}
