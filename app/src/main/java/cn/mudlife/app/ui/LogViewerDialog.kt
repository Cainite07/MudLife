package cn.mudlife.app.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cn.mudlife.app.ui.theme.AppColors
import cn.mudlife.app.utils.AppLogger
import cn.mudlife.app.utils.LogEntry
import cn.mudlife.app.utils.LogLevel
import kotlinx.coroutines.launch

enum class LogFilter(val label: String) {
    ALL("核心"),
    ERR("错误"),
    NET("网络"),
    SYS("系统")
}

@Composable
fun LogViewerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedFilter by remember { mutableStateOf(LogFilter.ALL) }
    var expandedLogId by remember { mutableStateOf<Long?>(null) }
    val listState = rememberLazyListState()

    // 观察 AppLogger 的响应式版本，有新日志自动刷新
    val version = AppLogger.versionState
    val allLogs = remember(version) { AppLogger.getLogs() }

    val filteredLogs = remember(allLogs, selectedFilter) {
        when (selectedFilter) {
            LogFilter.ALL -> allLogs.filter { it.level != LogLevel.NET } // 核心业务日志，不被网络报文淹没
            LogFilter.ERR -> allLogs.filter { it.level == LogLevel.ERROR }
            LogFilter.NET -> allLogs.filter { it.level == LogLevel.NET }
            LogFilter.SYS -> allLogs.filter { it.level == LogLevel.SYS || it.level == LogLevel.INFO }
        }.reversed()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = AppColors.SolidSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 1. 标题栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "内测运行诊断日志",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppColors.TextPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = AppColors.Accent.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    "共 ${allLogs.size} 条",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AppColors.Accent,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "敏感 Token 已自动脱敏，可放心发给开发者",
                            fontSize = 10.sp,
                            color = AppColors.TextSecondary
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = AppColors.TextSecondary
                        )
                    }
                }

                Divider(color = AppColors.TextSecondary.copy(alpha = 0.2f), thickness = 0.5.dp)

                // 2. 标签分类栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val errorCount = remember(allLogs) { allLogs.count { it.level == LogLevel.ERROR } }
                    val netCount = remember(allLogs) { allLogs.count { it.level == LogLevel.NET } }
                    val sysCount = remember(allLogs) { allLogs.count { it.level == LogLevel.SYS || it.level == LogLevel.INFO } }

                    FilterChip(
                        selected = selectedFilter == LogFilter.ALL,
                        onClick = { selectedFilter = LogFilter.ALL },
                        label = { Text("全部 (${allLogs.size})", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = selectedFilter == LogFilter.ERR,
                        onClick = { selectedFilter = LogFilter.ERR },
                        label = {
                            Text(
                                "错误 ($errorCount)",
                                fontSize = 11.sp,
                                color = if (errorCount > 0 && selectedFilter != LogFilter.ERR) AppColors.Danger else Color.Unspecified
                            )
                        }
                    )
                    FilterChip(
                        selected = selectedFilter == LogFilter.NET,
                        onClick = { selectedFilter = LogFilter.NET },
                        label = { Text("网络 ($netCount)", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = selectedFilter == LogFilter.SYS,
                        onClick = { selectedFilter = LogFilter.SYS },
                        label = { Text("系统 ($sysCount)", fontSize = 11.sp) }
                    )
                }

                Divider(color = AppColors.TextSecondary.copy(alpha = 0.2f), thickness = 0.5.dp)

                // 3. 日志列表展示区
                if (filteredLogs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "暂无对应分类日志",
                            fontSize = 13.sp,
                            color = AppColors.TextSecondary
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(AppColors.Background.copy(alpha = 0.4f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredLogs, key = { it.id }) { entry ->
                            LogItemCard(
                                entry = entry,
                                isExpanded = expandedLogId == entry.id,
                                onToggleExpand = {
                                    expandedLogId = if (expandedLogId == entry.id) null else entry.id
                                }
                            )
                        }
                    }
                }

                Divider(color = AppColors.TextSecondary.copy(alpha = 0.2f), thickness = 0.5.dp)

                // 4. 底部快捷操作栏
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 系统分享（核心功能）
                        Button(
                            onClick = {
                                try {
                                    val shareIntent = AppLogger.createShareIntent(context)
                                    val chooser = Intent.createChooser(shareIntent, "分享泥浆生活诊断日志")
                                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(chooser)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "调起系统分享失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1.2f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent)
                        ) {
                            Text("📤 分享", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        // 一键复制最近报错
                        OutlinedButton(
                            onClick = {
                                val text = AppLogger.copyRecentErrorsToClipboard(context)
                                Toast.makeText(context, "已复制最近报错至剪贴板，可直接粘贴！", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("📋 复制报错", fontSize = 12.sp, color = AppColors.TextPrimary)
                        }

                        // 导出到手机下载目录
                        OutlinedButton(
                            onClick = {
                                val res = AppLogger.exportToDownload(context)
                                if (res) {
                                    Toast.makeText(context, "已导出至下载目录", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "导出失败", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("💾 存到手机", fontSize = 12.sp, color = AppColors.TextPrimary)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "提示：遇到异常可先点「分享」直接发送",
                            fontSize = 10.sp,
                            color = AppColors.TextSecondary
                        )
                        TextButton(
                            onClick = {
                                AppLogger.clear()
                                Toast.makeText(context, "日志已清空", Toast.LENGTH_SHORT).show()
                            },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Text("清空日志", fontSize = 11.sp, color = AppColors.Danger)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogItemCard(
    entry: LogEntry,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    val levelColor = when (entry.level) {
        LogLevel.ERROR -> AppColors.Danger
        LogLevel.WARN -> Color(0xFFFB8C00)
        LogLevel.NET -> Color(0xFF00897B)
        LogLevel.SYS -> Color(0xFF1E88E5)
        LogLevel.INFO -> AppColors.TextSecondary
        LogLevel.DEBUG -> Color(0xFF7CB342)
    }

    val cardBg = if (entry.level == LogLevel.ERROR) {
        AppColors.Danger.copy(alpha = 0.08f)
    } else {
        AppColors.Card
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = cardBg,
        border = if (entry.level == LogLevel.ERROR) {
            androidx.compose.foundation.BorderStroke(1.dp, AppColors.Danger.copy(alpha = 0.3f))
        } else null,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleExpand() }
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = levelColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            entry.level.label,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = levelColor,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        entry.tag,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.TextPrimary
                    )
                    if (entry.repeatCount > 1) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFFB8C00).copy(alpha = 0.2f)
                        ) {
                            Text(
                                "x${entry.repeatCount}",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFB8C00),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Text(
                    entry.timeFormatted,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = AppColors.TextSecondary
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = entry.message,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 15.sp,
                color = if (entry.level == LogLevel.ERROR) AppColors.Danger else AppColors.TextPrimary,
                maxLines = if (isExpanded) Int.MAX_VALUE else 3
            )

            if (!entry.stackTrace.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                if (isExpanded) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = AppColors.Background,
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(top = 4.dp)
                    ) {
                        Text(
                            text = entry.stackTrace,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 13.sp,
                            color = AppColors.Danger,
                            modifier = Modifier.padding(6.dp)
                        )
                    }
                } else {
                    Text(
                        "▶ 点击展开完整异常堆栈...",
                        fontSize = 9.sp,
                        color = AppColors.Accent,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}
