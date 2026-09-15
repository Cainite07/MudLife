package cn.mudlife.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import cn.mudlife.app.model.BillDTO
import cn.mudlife.app.model.BillItem
import cn.mudlife.app.model.DeviceInfo
import cn.mudlife.app.ui.theme.AppColors
import cn.mudlife.app.utils.HapticHelper
import kotlinx.coroutines.launch

enum class BillCategory(val title: String, val emoji: String) {
    ALL("全部", "📋"),
    WATER("直饮水", "🚰"),
    SHOWER("洗浴", "🚿"),
    DRYER("吹风机", "💨")
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun WalletScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        viewModel.loadBills()
    }

    var selectedCategory by remember { mutableStateOf(BillCategory.ALL) }
    var selectedMonth by remember { mutableStateOf("ALL") }
    var monthMenuExpanded by remember { mutableStateOf(false) }
    var detailBill by remember { mutableStateOf<BillDTO?>(null) }
    var pullRefreshing by remember { mutableStateOf(false) }

    val pullState = rememberPullRefreshState(
        refreshing = pullRefreshing,
        onRefresh = {
            pullRefreshing = true
            viewModel.pullRefresh()
            scope.launch {
                kotlinx.coroutines.delay(1000)
                pullRefreshing = false
            }
        }
    )

    val currentMonth = remember {
        java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.getDefault()).format(java.util.Calendar.getInstance().time)
    }

    // 账单过滤分类：严格互斥过滤，彻底杜绝吹风机与洗浴相互混淆
    val filteredBills = remember(viewModel.billList, selectedCategory) {
        when (selectedCategory) {
            BillCategory.ALL -> viewModel.billList
            BillCategory.WATER -> viewModel.billList.filter {
                it.safeConsumeBillDTO.deviceTypeLabel.contains("直饮")
            }
            BillCategory.SHOWER -> viewModel.billList.filter {
                it.safeConsumeBillDTO.deviceTypeLabel.contains("浴室") || it.safeConsumeBillDTO.deviceTypeLabel.contains("洗手台")
            }
            BillCategory.DRYER -> viewModel.billList.filter {
                it.safeConsumeBillDTO.deviceTypeLabel.contains("吹风")
            }
        }
    }

    // 可选月份列表（近 6 个月内有记录的月份 + 当月）
    val availableMonths = remember(filteredBills, currentMonth) {
        val set = linkedSetOf<String>()
        set.add(currentMonth)
        filteredBills.forEach {
            val d = it.safeConsumeBillDTO.safeConsumeDate
            if (d.length >= 7) {
                set.add(d.take(7))
            }
        }
        set.sortedDescending()
    }

    // 方案二：月份下拉过滤后的账单
    val displayedBills = remember(filteredBills, selectedMonth) {
        if (selectedMonth == "ALL") {
            filteredBills
        } else {
            filteredBills.filter { it.safeConsumeBillDTO.safeConsumeDate.startsWith(selectedMonth) }
        }
    }

    // 方案一：按月份分组
    val monthGroups = remember(displayedBills) {
        displayedBills.groupBy { it.safeConsumeBillDTO.safeConsumeDate.take(7) }
    }

    val totalSpent = remember(displayedBills) {
        displayedBills.sumOf { it.safeConsumeBillDTO.safeConsumeMoney.toDoubleOrNull() ?: 0.0 }
    }

    val spentTitle = remember(selectedCategory, selectedMonth) {
        val catName = if (selectedCategory == BillCategory.ALL) "支出" else selectedCategory.title
        if (selectedMonth == "ALL") {
            "全部$catName"
        } else {
            "${formatMonthDisplay(selectedMonth)}$catName"
        }
    }

    Scaffold(
        containerColor = Color.Transparent
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .pullRefresh(pullState)
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(Modifier.height(16.dp))

                // 顶部标题与月份选择器 (方案二) + 统计卡片
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("账单明细", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                            Spacer(Modifier.width(8.dp))
                            // 月份下拉选择器
                            Box {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = AppColors.Card,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Accent.copy(alpha = 0.4f)),
                                    modifier = Modifier.clickable {
                                        HapticHelper.tick(context)
                                        monthMenuExpanded = true
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val displayTxt = if (selectedMonth == "ALL") "全部月份 ▾" else "${formatMonthDisplay(selectedMonth)} ▾"
                                        Text(
                                            displayTxt,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = AppColors.Accent
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = monthMenuExpanded,
                                    onDismissRequest = { monthMenuExpanded = false },
                                    modifier = Modifier.background(AppColors.SolidSurface)
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (selectedMonth == "ALL") "✓ 全部月份 (近半年)" else "全部月份 (近半年)",
                                                fontWeight = if (selectedMonth == "ALL") FontWeight.Bold else FontWeight.Normal,
                                                color = if (selectedMonth == "ALL") AppColors.Accent else AppColors.TextPrimary
                                            )
                                        },
                                        onClick = {
                                            HapticHelper.tick(context)
                                            selectedMonth = "ALL"
                                            monthMenuExpanded = false
                                        }
                                    )
                                    availableMonths.forEach { m ->
                                        val isThisMonth = (m == currentMonth)
                                        val isSel = (m == selectedMonth)
                                        DropdownMenuItem(
                                            text = {
                                                val suffix = if (isThisMonth) " (当月)" else ""
                                                val check = if (isSel) "✓ " else ""
                                                Text(
                                                    "$check${formatMonthDisplay(m)}$suffix",
                                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSel) AppColors.Accent else AppColors.TextPrimary
                                                )
                                            },
                                            onClick = {
                                                HapticHelper.tick(context)
                                                selectedMonth = m
                                                monthMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            if (selectedMonth == "ALL") "近 6 个月记录 · 按月分组流水" else "展示 ${formatMonthDisplay(selectedMonth)} 的账单记录",
                            fontSize = 11.sp,
                            color = AppColors.TextSecondary
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = AppColors.Card,
                        border = BorderStroke(1.dp, AppColors.Border),
                        shadowElevation = 0.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(spentTitle, fontSize = 11.sp, color = AppColors.TextSecondary)
                            Text("¥ %.2f".format(totalSpent), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                            Text("${displayedBills.size} 笔", fontSize = 10.sp, color = AppColors.TextSecondary)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // 分类筛选 Chip 选项卡
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BillCategory.values().forEach { category ->
                        val isSelected = selectedCategory == category
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    HapticHelper.tick(context)
                                    selectedCategory = category
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) AppColors.Accent else AppColors.Card,
                            border = BorderStroke(1.dp, if (isSelected) Color.Transparent else AppColors.Border),
                            shadowElevation = 0.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(category.emoji, fontSize = 13.sp)
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    category.title,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else AppColors.TextPrimary
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // 账单列表：方案一（按月分组带有头部小计）
                if (viewModel.isLoadingBills) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp), color = AppColors.Accent, strokeWidth = 2.5.dp)
                    }
                } else if (displayedBills.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(selectedCategory.emoji, fontSize = 38.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("暂无此类消费账单", color = AppColors.TextSecondary, fontSize = 14.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 110.dp)
                    ) {
                        monthGroups.forEach { (monthKey, billsInMonth) ->
                            // 方案一：月度自然吸顶/分组头部
                            item(key = "header_$monthKey") {
                                val groupSpent = billsInMonth.sumOf { it.safeConsumeBillDTO.safeConsumeMoney.toDoubleOrNull() ?: 0.0 }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = AppColors.Background,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "📅 ${formatMonthDisplay(monthKey)}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AppColors.TextPrimary
                                        )
                                        Text(
                                            "支出 ¥%.2f · %d笔".format(groupSpent, billsInMonth.size),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = AppColors.TextSecondary
                                        )
                                    }
                                }
                            }
                            items(billsInMonth) { bill ->
                                BillCard(bill) {
                                    HapticHelper.click(context)
                                    detailBill = bill.safeConsumeBillDTO
                                }
                            }
                        }
                    }
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

    // 账单详情弹窗
    if (detailBill != null) {
        val dto = detailBill!!
        AlertDialog(
            onDismissRequest = { detailBill = null },
            shape = RoundedCornerShape(20.dp),
            containerColor = AppColors.SolidSurface,
            title = { Text("消费详情", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    WDetailRow("设备名称", dto.displayDesc)
                    WDetailRow("设备类型", dto.deviceTypeLabel)
                    WDetailRow("消费金额", "-¥ ${dto.safeConsumeMoney}")
                    if (!dto.beforeMoney.isNullOrEmpty()) {
                        WDetailRow("扣费前卡结存", "¥ ${dto.beforeMoney}")
                    }
                    if (!dto.afterMoney.isNullOrEmpty()) {
                        WDetailRow("扣费后卡结存", "¥ ${dto.afterMoney}")
                    }
                    WDetailRow("消费时间", dto.safeConsumeDate.take(19))
                    WDetailRow("商户订单号", dto.safeOrderId)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        HapticHelper.click(context)
                        detailBill = null
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent)
                ) { Text("我知道了") }
            }
        )
    }
}

@Composable
private fun BillCard(bill: BillItem, onClick: () -> Unit) {
    val dto = bill.safeConsumeBillDTO
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.Card),
        border = BorderStroke(1.dp, AppColors.Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(dto.displayDesc, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val tagColor = when {
                        dto.deviceTypeLabel.contains("直饮") -> Color(0xFF00897B)
                        dto.deviceTypeLabel.contains("吹风") -> Color(0xFF8E24AA)
                        dto.deviceTypeLabel.contains("洗手台") -> Color(0xFFFB8C00)
                        else -> AppColors.Accent
                    }
                    Text(
                        dto.deviceTypeLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = tagColor
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        dto.safeConsumeDate.take(16),
                        fontSize = 12.sp,
                        color = AppColors.TextSecondary
                    )
                }
            }
            Text(
                "-¥ ${dto.safeConsumeMoney}",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.Danger
            )
        }
    }
}

@Composable
private fun WDetailRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = AppColors.TextSecondary, fontSize = 13.sp)
        Text(value, fontWeight = FontWeight.Medium, color = AppColors.TextPrimary, fontSize = 13.sp)
    }
}

private fun formatMonthDisplay(yyyyMm: String): String {
    val parts = yyyyMm.split("-")
    return if (parts.size == 2) "${parts[0]}年${parts[1].toIntOrNull() ?: parts[1]}月" else yyyyMm
}
