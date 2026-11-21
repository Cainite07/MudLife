package cn.mudlife.app.ui

import androidx.compose.ui.res.stringResource
import cn.mudlife.app.R
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Shower
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import cn.mudlife.app.BuildConfig
import cn.mudlife.app.ui.theme.*
import cn.mudlife.app.utils.HapticHelper
import cn.mudlife.app.utils.PrefsHelper
import cn.mudlife.app.utils.UpdateManager

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun UserScreen(
    phone: String,
    onLogout: () -> Unit,
    viewModel: MainViewModel? = null,
    codesOnly: Boolean = false,
    codeKind: String = "all",
    onLifeLogin: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val themeReveal = LocalThemeReveal.current
    val view = LocalView.current
    val isDark = AppColors.isDark

    var buttonCenter by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(codeKind) {
        if (codeKind != "dryer") {
            viewModel?.loadQzhqUseCode()
            viewModel?.loadQzhqProjects()
        }
        if (codeKind != "bath" && PrefsHelper.isLoggedIn) viewModel?.loadUseCode()
    }

    val qzhqUseCode = viewModel?.qzhqUseCodeData

    var showChangeCodeDialog by remember { mutableStateOf(false) }
    var showRememberPrefixDialog by remember { mutableStateOf(false) }
    var showQzhqLoginDialog by remember { mutableStateOf(false) }
    var showLogViewerDialog by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel?.isQzhqLoggedIn, viewModel?.qzhqProjectId) {
        showChangeCodeDialog = false
        showRememberPrefixDialog = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (!codesOnly) {
        // 1. 顶部标题栏 + 矢量细线双向光影切换按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "我的账号",
                fontFamily = FontFamily.Serif,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary
            )

            // 精致矢量圆环月亮/太阳切换按钮（动态捕获圆心坐标，驱动双向光影）
            Surface(
                onClick = {
                    HapticHelper.click(context)
                    themeReveal.toggle(buttonCenter)
                },
                shape = CircleShape,
                color = AppColors.Background,
                border = BorderStroke(1.dp, AppColors.Border.copy(alpha = 0.65f)),
                modifier = Modifier
                    .size(44.dp)
                    .onGloballyPositioned { coords ->
                        val screen = IntArray(2).also(view::getLocationOnScreen)
                        val window = IntArray(2).also(view::getLocationInWindow)
                        buttonCenter = coords.boundsInWindow().center + Offset((screen[0] - window[0]).toFloat(), (screen[1] - window[1]).toFloat())
                    }
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = if (isDark) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                        contentDescription = "切换主题",
                        tint = AppColors.TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // 2. 账号基础信息卡片 (紧凑收敛 18dp 圆角)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = AppColors.Card),
            border = BorderStroke(1.dp, AppColors.Border.copy(alpha = 0.65f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                InfoRow("姓名", PrefsHelper.userName.ifEmpty { "江大学子" })
                Spacer(Modifier.height(8.dp))
                val maskedPhone = if (phone.length == 11) "${phone.take(3)}****${phone.takeLast(4)}" else phone
                InfoRow("手机号", maskedPhone, isMono = true)
                if (PrefsHelper.studentNo.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    InfoRow("学号", PrefsHelper.studentNo, isMono = true)
                }
            }
        }

        }
        if (codeKind != "dryer" && viewModel != null) {
            val hasCode = qzhqUseCode?.isSet == true
            val loading = viewModel.qzhqLoading
            val error = viewModel.qzhqCodeError
            val loggedIn = viewModel.isQzhqLoggedIn
            val fullCode = if (loggedIn && !loading && qzhqUseCode?.useCodeStatus == 1) {
                cn.mudlife.app.model.BathCode.complete(qzhqUseCode.randomCode, viewModel.qzhqPrefix)
            } else null
            val needsPrefix = hasCode && viewModel.qzhqPrefix.isBlank() &&
                qzhqUseCode?.randomCode?.matches(Regex("[0-9]{3}")) == true

            Surface(color = AppColors.Card, shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, AppColors.Border.copy(alpha = 0.65f))) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Shower, null, tint = AppColors.Accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("洗浴使用码", Modifier.weight(1f), fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                        if (loggedIn) {
                            BathProjectPicker(viewModel, viewModel.qzhqProjectId, enabled = !loading,
                                onSelect = viewModel::selectQzhqProject)
                        }
                    }

                    if (fullCode != null) {
                        Text(fullCode, Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .combinedClickable(onClick = {
                                clipboardManager.setText(AnnotatedString(fullCode))
                                HapticHelper.tick(context)
                                viewModel.toastMessage = "已复制洗浴使用码"
                            }, onLongClick = { showRememberPrefixDialog = true },
                                onClickLabel = "复制使用码", onLongClickLabel = "记住前五位"),
                            fontSize = 34.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                            letterSpacing = 2.sp, color = AppColors.TextPrimary)
                    } else {
                        Text(
                            when {
                                !loggedIn -> if (viewModel.qzhqSessionExpired) "后勤登录已失效" else "请先登录趣智后勤热水"
                                loading -> "正在同步使用码…"
                                error != null -> error
                                qzhqUseCode == null -> "等待同步使用码"
                                !hasCode -> "该分区未设置使用码"
                                qzhqUseCode.useCodeStatus != 1 -> "使用码已关闭"
                                needsPrefix -> "已设置，需填写前五位以显示完整码"
                                else -> "暂时无法显示完整使用码"
                            }, color = AppColors.TextSecondary, fontSize = 14.sp
                        )
                    }

                    if (!loading || hasCode) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            when {
                                !loggedIn -> TextButton(onClick = { showQzhqLoginDialog = true }) {
                                    Text(if (viewModel.qzhqSessionExpired) "重新登录" else "登录后勤")
                                }
                                error != null || qzhqUseCode == null ->
                                    TextButton(enabled = !loading, onClick = { viewModel.loadQzhqUseCode() }) { Text("重试") }
                                !hasCode -> TextButton(onClick = { showChangeCodeDialog = true }) { Text("设置使用码") }
                                else -> {
                                    TextButton(enabled = !loading, onClick = { showChangeCodeDialog = true }) { Text("修改") }
                                    if (needsPrefix) TextButton(enabled = !loading, onClick = {
                                        showRememberPrefixDialog = true
                                    }) { Text("记住前五位") }
                                    Spacer(Modifier.weight(1f))
                                    Switch(
                                        checked = qzhqUseCode?.useCodeStatus == 1,
                                        enabled = !loading,
                                        onCheckedChange = { viewModel.toggleQzhqStatus(if (it) 1 else 0) },
                                        modifier = Modifier.semantics { contentDescription = "启用洗浴使用码" },
                                        colors = SwitchDefaults.colors(checkedTrackColor = AppColors.Accent)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (codeKind != "bath") {
        // 5. 💨 吹风机使用码（居下方，与洗浴码严格镜像对称） 💨
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = AppColors.Card),
            border = BorderStroke(1.dp, AppColors.Border.copy(alpha = 0.65f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                // 标题栏：极简无括号
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Air, null, tint = AppColors.Accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("吹风机使用码", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextSecondary)
                    }

                    TextButton(
                        onClick = {
                            HapticHelper.click(context)
                            if (PrefsHelper.isLoggedIn) viewModel?.openDryerCodeModal() else onLifeLogin()
                        },
                        contentPadding = PaddingValues(horizontal = 6.dp)
                    ) {
                        Text(if (PrefsHelper.isLoggedIn) "更换新码" else "登录趣智", fontSize = 13.sp, color = AppColors.Accent, fontWeight = FontWeight.Medium)
                    }
                }

                Spacer(Modifier.height(4.dp))

                val dryerCode = if (PrefsHelper.isLoggedIn) viewModel?.useCodeData?.useCode?.ifEmpty { null }
                    ?: PrefsHelper.useCode.ifEmpty { null } else null

                // 8 位大字使用码（整行可点击复制 + 磁吸双击微震）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            if (!dryerCode.isNullOrEmpty()) {
                                HapticHelper.doubleClick(context)
                                clipboardManager.setText(AnnotatedString(dryerCode))
                                viewModel?.toastMessage = "已复制吹风机使用码"
                            }
                        }
                        .padding(vertical = 4.dp)
                ) {
                    if (!dryerCode.isNullOrEmpty()) {
                        Text(
                            text = dryerCode,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 2.sp,
                            color = AppColors.TextPrimary
                        )
                    } else {
                        Text(
                            if (PrefsHelper.isLoggedIn) "暂未获取到使用码" else "登录后查看使用码",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.TextSecondary
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = AppColors.Border, thickness = 1.dp)
                Spacer(Modifier.height(8.dp))

                // 极简单行：左侧提示 + 右侧 M3 Switch 开关
                val isDryerCodeOn = (viewModel?.useCodeData?.useCodeStatus ?: if (PrefsHelper.useCodeStatus) 1 else 0) == 1
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "吹风机键盘输入此码启动",
                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                        fontSize = 12.sp,
                        color = AppColors.TextSecondary
                    )
                    Switch(
                        checked = isDryerCodeOn,
                        enabled = PrefsHelper.isLoggedIn,
                        onCheckedChange = { checked ->
                            HapticHelper.click(context)
                            viewModel?.toggleUseCodeStatus(checked)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AppColors.OnAccent,
                            checkedTrackColor = AppColors.Accent,
                            uncheckedThumbColor = AppColors.TextSecondary,
                            uncheckedTrackColor = AppColors.Border
                        )
                    )
                }
            }
        }

        }
        if (!codesOnly) {
        // 6. 运行与诊断日志入口卡片 (极简素雅线框)
        Card(
            onClick = {
                HapticHelper.tick(context)
                showLogViewerDialog = true
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = AppColors.Card),
            border = BorderStroke(1.dp, AppColors.Border.copy(alpha = 0.65f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ReceiptLong,
                        contentDescription = "运行与诊断日志",
                        tint = AppColors.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "运行与诊断日志",
                        color = AppColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(6.dp))
                    LogErrorBadge()
                }
                Icon(Icons.Outlined.ChevronRight, null, tint = AppColors.TextSecondary, modifier = Modifier.size(20.dp))
            }
        }

        // 7. 检查更新卡片 (极简素雅线框)
        Card(
            onClick = {
                HapticHelper.tick(context)
                viewModel?.checkUpdate(isManual = true)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = AppColors.Card),
            border = BorderStroke(1.dp, AppColors.Border.copy(alpha = 0.65f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SystemUpdate,
                        contentDescription = "检查更新",
                        tint = AppColors.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "检查更新",
                        color = AppColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = AppColors.Background,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            "v${cn.mudlife.app.BuildConfig.VERSION_NAME}",
                            color = AppColors.TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Outlined.ChevronRight, null, tint = AppColors.TextSecondary, modifier = Modifier.size(20.dp))
                }
            }
        }

        // 8. 查看源代码卡片 (低调纯粹)
        Card(
            onClick = {
                HapticHelper.tick(context)
                UpdateManager.openBrowser(context, UpdateManager.GITHUB_REPO_URL)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = AppColors.Card),
            border = BorderStroke(1.dp, AppColors.Border.copy(alpha = 0.65f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        painter = painterResource(cn.mudlife.app.R.drawable.ic_github),
                        contentDescription = "GitHub",
                        tint = AppColors.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "查看源代码",
                            color = AppColors.TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "在Github上查看源代码",
                            color = AppColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
                Icon(Icons.Outlined.ChevronRight, null, tint = AppColors.TextSecondary, modifier = Modifier.size(20.dp))
            }
        }

        Spacer(Modifier.height(4.dp))

        // 7. 退出登录按钮
        OutlinedButton(
            onClick = {
                HapticHelper.heavyClick(context)
                onLogout()
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, AppColors.Danger.copy(alpha = 0.35f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Danger)
        ) {
            Text("退出登录", fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }

        Spacer(Modifier.height(2.dp))
        Text(
            stringResource(R.string.app_version_footer, stringResource(R.string.app_name), cn.mudlife.app.BuildConfig.VERSION_NAME),
            color = AppColors.TextSecondary.copy(alpha = 0.6f),
            fontSize = 12.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        }
        Spacer(Modifier.height(if (codesOnly) 16.dp else 100.dp))
    }

    if (viewModel?.showDryerCodeModal == true) {
        val candidateCode = viewModel.candidateUseCode.ifEmpty {
            viewModel.useCodeData?.useCode?.ifEmpty { null } ?: PrefsHelper.useCode
        }
        val remainTimes = viewModel.remainUseCodeTimes
        val isRolling = viewModel.isRollingUseCode

        AlertDialog(
            onDismissRequest = { viewModel.closeDryerCodeModal() },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("更换吹风机使用码", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (remainTimes <= 3) AppColors.Warning.copy(alpha = 0.15f) else AppColors.Accent.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, if (remainTimes <= 3) AppColors.Warning.copy(alpha = 0.3f) else AppColors.Accent.copy(alpha = 0.25f))
                    ) {
                        Text(
                            text = "剩余 $remainTimes/20 次",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (remainTimes <= 3) AppColors.Warning else AppColors.Accent,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = AppColors.Card,
                        border = BorderStroke(1.dp, AppColors.Border.copy(alpha = 0.65f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp, horizontal = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "候选使用码",
                                fontSize = 13.sp,
                                color = AppColors.TextSecondary,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(6.dp))
                            if (isRolling) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(28.dp),
                                    strokeWidth = 2.5.dp,
                                    color = AppColors.Accent
                                )
                            } else {
                                Text(
                                    text = candidateCode.ifEmpty { "--------" },
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 2.sp,
                                    color = AppColors.Accent
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        HapticHelper.click(context)
                        viewModel.confirmUseCandidateCode()
                    },
                    enabled = candidateCode.isNotEmpty() && !isRolling,
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        "确认使用",
                        color = AppColors.OnAccent,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { viewModel.closeDryerCodeModal() }) {
                        Text("取消", color = AppColors.TextSecondary)
                    }
                    Spacer(Modifier.width(4.dp))
                    OutlinedButton(
                        onClick = {
                            HapticHelper.click(context)
                            viewModel.rollNewUseCode()
                        },
                        enabled = remainTimes > 0 && !isRolling,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, AppColors.Accent.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = if (remainTimes > 0) "换一个" else "已用尽",
                            color = AppColors.Accent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            shape = RoundedCornerShape(22.dp),
            containerColor = AppColors.SolidSurface
        )
    }

    if (showLogViewerDialog) {
        LogViewerDialog(onDismiss = { showLogViewerDialog = false })
    }

    if (showRememberPrefixDialog && viewModel?.isQzhqLoggedIn == true) {
        var prefix by remember { mutableStateOf(viewModel?.qzhqPrefix.orEmpty()) }
        AlertDialog(
            onDismissRequest = { showRememberPrefixDialog = false },
            title = { Text("记住已有前五位") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("填写你当前洗浴使用码的前五位，仅保存到本机，不修改服务器上的使用码。")
                    OutlinedTextField(
                        value = prefix,
                        onValueChange = { if (it.length <= 5 && it.all { c -> c in '0'..'9' }) prefix = it },
                        label = { Text("前五位") }, singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword)
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = cn.mudlife.app.model.BathCode.isPrefix(prefix), onClick = {
                    viewModel?.rememberQzhqPrefix(prefix)
                    showRememberPrefixDialog = false
                }) { Text("保存到本机") }
            },
            dismissButton = { TextButton(onClick = { showRememberPrefixDialog = false }) { Text("取消") } }
        )
    }
    if (showChangeCodeDialog && viewModel != null && viewModel.isQzhqLoggedIn) {
        val project = viewModel.qzhqProjects.firstOrNull { it.id.toString() == viewModel.qzhqProjectId }
        ChangeUseCodeDialog(
            firstSetup = qzhqUseCode?.isSet == false,
            projectLabel = project?.label.orEmpty(),
            busy = viewModel.qzhqLoading,
            onDismiss = { showChangeCodeDialog = false },
            onSubmit = viewModel::setQzhqUseCode
        )
    }

    if (showQzhqLoginDialog && viewModel != null) {
        ServiceLoginDialog(
            accountName = "趣智后勤热水",
            initialPhone = PrefsHelper.qzhqPhone.ifEmpty { PrefsHelper.telephone },
            onDismiss = { showQzhqLoginDialog = false },
            onPasswordSubmit = viewModel::loginQzhq,
            onSmsSubmit = viewModel::smsLoginQzhq,
            onSendSms = viewModel::sendQzhqSmsCode
        )
    }

}

@Composable
internal fun AppUpdateDialog(viewModel: MainViewModel) {
    if (!viewModel.showUpdateDialog) return
    val info = viewModel.updateInfo ?: return
    val context = LocalContext.current
    val downloading = viewModel.isDownloadingUpdate
    val download = viewModel.updateDownload
    val progress = download.fraction?.takeIf { download.phase == cn.mudlife.app.utils.UpdateDownloadPhase.DOWNLOADING }
    val error = viewModel.updateError
    val notes = remember(info.changelog) { info.changelog.lines().map(String::trim).filter(String::isNotBlank) }

    Dialog(onDismissRequest = viewModel::closeUpdateDialog) {
        Surface(
            modifier = Modifier.widthIn(max = 370.dp).fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = AppColors.SolidSurface,
            contentColor = AppColors.TextPrimary,
            border = BorderStroke(1.dp, AppColors.Border)
        ) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.SystemUpdate, null, Modifier.size(18.dp), tint = AppColors.Accent)
                    Text("应用更新",
                        color = AppColors.Accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(info.versionName, Modifier.padding(top = 6.dp, bottom = 14.dp),
                    fontFamily = FontFamily.Serif, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                if (notes.isNotEmpty()) {
                    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = AppColors.Background) {
                        Column(Modifier.heightIn(max = 235.dp).verticalScroll(rememberScrollState()).padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("更新内容", color = AppColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            notes.forEach { line ->
                                val bullet = line.first() in listOf('·', '•', '-', '*')
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (bullet) Box(Modifier.padding(top = 9.dp).size(4.dp)
                                        .background(AppColors.Accent, CircleShape))
                                    Text(if (bullet) line.drop(1).trimStart() else line, fontSize = 14.sp,
                                        lineHeight = 21.sp, color = AppColors.TextPrimary)
                                }
                            }
                        }
                    }
                }
                error?.let { Text(it, Modifier.padding(top = 12.dp), color = AppColors.Danger, fontSize = 13.sp) }
                if (downloading) {
                    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(download.description, color = AppColors.TextSecondary, fontSize = 13.sp)
                        progress?.let { Text("${(it * 100).toInt()}%", color = AppColors.Accent, fontSize = 13.sp) }
                    }
                    val track = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp))
                    if (progress != null) LinearProgressIndicator(progress = { progress }, modifier = track,
                        color = AppColors.Accent, trackColor = AppColors.Border)
                    else LinearProgressIndicator(modifier = track, color = AppColors.Accent, trackColor = AppColors.Border)
                } else {
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = viewModel::closeUpdateDialog, modifier = Modifier.weight(1f)) {
                            Text(if (BuildConfig.DEBUG) "关闭" else "稍后", color = AppColors.TextSecondary)
                        }
                        if (!BuildConfig.DEBUG) Button(
                            onClick = {
                                HapticHelper.click(context)
                                viewModel.startDownloadUpdate(context)
                            },
                            modifier = Modifier.weight(1.5f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent, contentColor = AppColors.OnAccent)
                        ) { Text(if (error == null) "下载更新" else "重试下载", fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, isMono: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = AppColors.TextSecondary, fontSize = 15.sp)
        Text(
            value,
            fontWeight = FontWeight.Medium,
            color = AppColors.TextPrimary,
            fontSize = 15.sp,
            fontFamily = if (isMono) FontFamily.Monospace else FontFamily.Default
        )
    }
}


@Composable
private fun LogErrorBadge() {
    val count = cn.mudlife.app.utils.AppLogger.errorCountState
    if (count > 0) {
        Surface(color = AppColors.Danger.copy(alpha = 0.10f), shape = RoundedCornerShape(8.dp)) {
            Text("${count}条异常", color = AppColors.Danger, fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
        }
    }
}
