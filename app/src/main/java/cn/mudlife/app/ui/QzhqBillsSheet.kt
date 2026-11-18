package cn.mudlife.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mudlife.app.api.QzhqNetwork
import cn.mudlife.app.model.QzhqWalletBillItem
import cn.mudlife.app.ui.theme.AppColors
import cn.mudlife.app.utils.HapticHelper
import cn.mudlife.app.utils.PrefsHelper
import kotlinx.coroutines.CancellationException
import java.util.Locale

@Composable
internal fun BathProjectPicker(
    life: MainViewModel, projectId: String, enabled: Boolean = true, onSelect: (String) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val currentProject = life.qzhqProjects.firstOrNull { it.id.toString() == projectId }
    Box {
        TextButton(
            enabled = enabled,
            onClick = {
                HapticHelper.tick(context)
                open = true
                life.loadQzhqProjects()
            },
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            Text(currentProject?.label ?: "洗浴分区", Modifier.widthIn(max = 159.dp),
                color = if (enabled) AppColors.Accent else AppColors.TextSecondary,
                fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Outlined.ExpandMore, "选择洗浴分区", Modifier.size(16.dp))
        }
        MaterialTheme(
            shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(14.dp)),
            colorScheme = MaterialTheme.colorScheme.copy(
                surface = AppColors.SolidSurface, surfaceContainer = AppColors.SolidSurface
            )
        ) {
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                life.qzhqProjects.forEach { project ->
                    val id = project.id.toString()
                    val current = id == projectId
                    DropdownMenuItem(
                        modifier = Modifier.padding(horizontal = 5.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (current) AppColors.ActiveBg else Color.Transparent)
                            .semantics { selected = current },
                        text = {
                            Text(project.label, fontSize = 13.sp,
                                color = if (current) AppColors.Accent else AppColors.TextPrimary,
                                fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal)
                        },
                        trailingIcon = {
                            Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                                if (current) Icon(Icons.Outlined.Check, null, tint = AppColors.Accent)
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        onClick = {
                            HapticHelper.tick(context)
                            open = false
                            onSelect(id)
                        }
                    )
                }
                if (life.qzhqProjects.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text(if (life.qzhqProjectsError) "重试加载分区" else "正在加载分区…", fontSize = 13.sp) },
                        enabled = life.qzhqProjectsError,
                        onClick = life::loadQzhqProjects
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QzhqBillsSheet(life: MainViewModel, onLogin: () -> Unit, onDismiss: () -> Unit) {
    val phone = PrefsHelper.qzhqPhone.ifEmpty { PrefsHelper.telephone }
    val token = PrefsHelper.qzhqLoginCode
    var projectId by remember(token) { mutableStateOf(life.qzhqProjectId) }
    var bills by remember(token, projectId) { mutableStateOf(emptyList<QzhqWalletBillItem>()) }
    var page by remember(token, projectId) { mutableIntStateOf(1) }
    var retry by remember(token, projectId) { mutableIntStateOf(0) }
    var busy by remember(token, projectId) { mutableStateOf(true) }
    var error by remember(token, projectId) { mutableStateOf<String?>(null) }
    var canLoadMore by remember(token, projectId) { mutableStateOf(false) }

    LaunchedEffect(life.isQzhqLoggedIn) {
        if (life.isQzhqLoggedIn) life.loadQzhqProjects()
    }
    LaunchedEffect(token, projectId, page, retry, life.isQzhqLoggedIn) {
        if (!life.isQzhqLoggedIn) return@LaunchedEffect
        busy = true
        error = null
        try {
            val response = QzhqNetwork.getWalletBillList(phone, token, projectId, page)
            if (token != PrefsHelper.qzhqLoginCode) return@LaunchedEffect
            if (response.isSuccess) {
                val received = response.data.orEmpty()
                val added = received.filterNot(bills::contains)
                bills = bills + added
                canLoadMore = added.isNotEmpty()
            } else {
                error = response.message.ifBlank { "后勤账单暂时无法加载" }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            error = "后勤账单暂时无法加载"
        } finally {
            busy = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppColors.Card,
        contentColor = AppColors.TextPrimary
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("后勤账单", Modifier.weight(1f), fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                if (life.isQzhqLoggedIn) BathProjectPicker(life, projectId, onSelect = { projectId = it })
            }
            if (!life.isQzhqLoggedIn) {
                TextButton(onClick = onLogin) {
                    Text(if (life.qzhqSessionExpired) "后勤登录已失效，重新登录" else "登录趣智后勤热水")
                }
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 440.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                    items(bills) { bill ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).padding(end = 10.dp)) {
                                Text(bill.description?.takeIf { it.isNotBlank() }
                                    ?: bill.areaName?.takeIf { it.isNotBlank() }
                                    ?: bill.consumeType?.takeIf { it.isNotBlank() } ?: "后勤流水", fontSize = 14.sp)
                                bill.dealDate?.let { Text(it, color = AppColors.TextSecondary, fontSize = 12.sp) }
                            }
                            bill.xfMoney?.takeIf { it > 0 && it.isFinite() }?.let {
                                Text(String.format(Locale.CHINA, "¥%.2f", it), fontSize = 14.sp)
                            }
                        }
                        HorizontalDivider(color = AppColors.Border)
                    }
                    item {
                        when {
                            busy -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            }
                            error != null -> TextButton(onClick = { busy = true; retry++ }) { Text("加载失败，重试") }
                            canLoadMore -> TextButton(onClick = { busy = true; page++ }, modifier = Modifier.fillMaxWidth()) {
                                Text("加载更多")
                            }
                            bills.isEmpty() -> Text("该分区暂无记录", Modifier.padding(vertical = 16.dp),
                                color = AppColors.TextSecondary)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
