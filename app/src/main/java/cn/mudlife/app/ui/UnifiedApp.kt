package cn.mudlife.app.ui

import android.app.Activity
import android.content.Intent
import android.view.WindowManager
import android.webkit.CookieManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import cn.mudlife.app.BuildConfig
import cn.mudlife.app.ui.theme.*
import cn.mudlife.app.utils.PrefsHelper
import cn.mudlife.app.utils.HapticHelper
import cn.mudlife.app.utils.UpdateManager
import cn.mudlife.campuscard.*
import kotlinx.coroutines.flow.collect

@Composable
fun UnifiedApp(life: MainViewModel, campus: CampusViewModel) {
    val activity = LocalContext.current as Activity
    val tap = { HapticHelper.tick(activity) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var foreground by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var loggedIn by rememberSaveable { mutableStateOf(PrefsHelper.isLoggedIn) }
    var phone by rememberSaveable { mutableStateOf(PrefsHelper.telephone) }
    var tab by rememberSaveable { mutableIntStateOf(if (campus.startupCode) 1 else 0) }
    var page by rememberSaveable { mutableStateOf(if (!PrefsHelper.accountGuideSeen && !loggedIn && !life.isQzhqLoggedIn && !campus.hasSession) "setup" else "") }
    LaunchedEffect(Unit) {
        if (page != "setup" && (loggedIn || life.isQzhqLoggedIn || campus.hasSession)) PrefsHelper.accountGuideSeen = true
    }
    var loginBack by rememberSaveable { mutableStateOf("") }
    var featureBack by rememberSaveable { mutableIntStateOf(2) }
    val loginLife = { loginBack = page; page = "login" }
    val pageState = rememberSaveableStateHolder()
    var pending by remember { mutableStateOf(false) }
    var logout by remember { mutableStateOf("") }
    var account by remember { mutableStateOf("") }
    var logisticsLogin by remember { mutableStateOf(false) }
    var codesBack by rememberSaveable { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }
    val school = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) {
            campus.authenticated()
            if (it.data?.getBooleanExtra("payment_confirmed", false) == true)
                campus.confirmedPayment(it.data?.getStringExtra("payment_kind").orEmpty())
        } else if (campus.hasSession) campus.refresh()
    }
    val loginSchool = { school.launch(Intent(activity, SchoolActivity::class.java)); Unit }
    val openRecharge = { featureBack = tab; if (campus.session.pendingCheckout.isNotBlank()) pending = true else page = "recharge" }
    DisposableEffect(lifecycle, campus) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> { foreground = true; campus.resume() }
                Lifecycle.Event.ON_PAUSE -> { foreground = false; campus.pause() }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (foreground) campus.resume()
        onDispose { lifecycle.removeObserver(observer); campus.pause() }
    }
    LaunchedEffect(loggedIn, phone) { if (loggedIn) life.initManagers(activity.applicationContext) }
    LaunchedEffect(campus.paymentNotice) { campus.paymentNotice?.let { snackbar.showSnackbar(it) } }
    val codeVisible = tab == 1 && page.isEmpty() && campus.hasSession
    LaunchedEffect(codeVisible, campus.currentCard?.account, campus.needsLogin) { campus.codeVisibility(codeVisible) }
    LaunchedEffect(tab, page, campus.currentCard?.account, campus.hasSession) {
        if (campus.hasSession) when {
            page == "electric" -> campus.loadElectric()
            page == "recharge" -> campus.loadRechargeRules()
            tab == 2 -> campus.enterBills()
            tab == 3 -> campus.loadIdentity()
            tab in setOf(1, 2) && System.currentTimeMillis() - campus.updatedAt > 60000 -> campus.refresh()
        }
    }
    LaunchedEffect(page, campus.config?.feeItem) { if (page == "recharge") campus.loadRechargeRules() }
    DisposableEffect(codeVisible, foreground, campus.brightCode) {
        val enabled = codeVisible && foreground && campus.brightCode
        val previous = activity.window.attributes.screenBrightness
        if (enabled) {
            activity.window.attributes = activity.window.attributes.apply { screenBrightness = 1f }
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose { if (enabled) {
            activity.window.attributes = activity.window.attributes.apply { screenBrightness = previous }
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } }
    }
    val finishSetup = { PrefsHelper.accountGuideSeen = true; page = "" }
    val back = {
        when (page) {
            "login" -> page = loginBack
            "setup" -> finishSetup()
            "codes", "bath", "dryer" -> page = codesBack
            "recharge", "electric" -> { page = ""; tab = featureBack }
            "" -> tab = 0
            else -> page = ""
        }
    }
    BackHandler(page.isNotEmpty() || tab != 0, onBack = back)
    Scaffold(containerColor = AppColors.Background, snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
        if (page.isEmpty() && !life.isShowering) UnifiedNavigation(tab) { tab = it }
    }) { insets ->
        Box(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets).background(AppColors.Background)) {
            pageState.SaveableStateProvider(page.ifEmpty { "tab:$tab" }) {
            when {
                page == "login" -> LoginScreen(onCampusCard = loginSchool, onBack = back, onLoginSuccess = {
                    life.lifeAccountAuthenticated()
                    phone = it; loggedIn = true; page = loginBack
                })
                page in setOf("codes", "bath", "dryer") -> Column {
                    PageTitle(when (page) { "bath" -> "洗浴使用码"; "dryer" -> "吹风机使用码"; else -> "使用码" }, back = { page = codesBack })
                    UserScreen(phone, onLogout = {}, viewModel = life, codesOnly = true, codeKind = if (page == "codes") "all" else page, onLifeLogin = loginLife)
                }
                page == "accounts" || page == "setup" -> AccountPage(loggedIn, life.isQzhqLoggedIn, campus.hasSession, campus.needsLogin,
                    setup = page == "setup", back = back, finish = finishSetup, select = {
                        when {
                            it == "school" && (!campus.hasSession || campus.needsLogin) -> loginSchool()
                            it == "life" && !loggedIn -> loginLife()
                            it == "logistics" && !life.isQzhqLoggedIn -> logisticsLogin = true
                            else -> account = it
                        }
                    })
                page == "electric" || page == "recharge" -> Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                    PageTitle(if (page == "electric") "电费" else "校园卡充值", back = back, padded = false)
                    if (!campus.hasSession || campus.needsLogin) SchoolLogin(loginSchool)
                    else if (page == "electric") ElectricPage(campus) { dorm, quantity, fen ->
                        if (campus.session.pendingCheckout.isNotBlank()) pending = true
                        else school.launch(Intent(activity, SchoolActivity::class.java).putExtra("mode", "electric")
                            .putExtra("dorm", dorm.toJson().toString()).putExtra("quantity", quantity).putExtra("fen", fen))
                    } else RechargePage(campus) { fen, target ->
                        val card = campus.currentCard
                        if (campus.session.pendingCheckout.isNotBlank()) pending = true
                        else if (card != null) {
                            page = ""; tab = featureBack
                            school.launch(Intent(activity, SchoolActivity::class.java).putExtra("mode", "payment")
                                .putExtra("account", card.account).putExtra("target", target).putExtra("fen", fen))
                        }
                    }
                }
                tab == 0 -> if (loggedIn) MainScreen(phone, life, onUseCodes = { codesBack = ""; page = "codes" })
                    else HomeWelcome(campus.hasSession, login = loginLife,
                        codes = { codesBack = ""; page = "codes" }, accounts = { page = "accounts" }, card = { tab = 1 })
                tab == 1 -> Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                    PageTitle("校园卡", padded = false)
                    if (!campus.hasSession) SchoolLogin(loginSchool)
                    else {
                        SchoolError(campus, loginSchool)
                        UnifiedCardPage(campus)
                    }
                }
                tab == 2 -> UnifiedBillsPage(campus, loginSchool, openRecharge)
                else -> UnifiedProfile(campus, loginSchool, accounts = { page = "accounts" },
                    electric = { featureBack = tab; page = "electric" }, codes = { codesBack = ""; page = "codes" },
                    checkingUpdate = life.isCheckingUpdate, checkUpdate = { life.checkUpdate(true) })
            }
            }
        }
    }
    AppUpdateDialog(life)
    MudLifeToast(life.toastMessage, bottomSpacing = if (page.isEmpty() && !life.isShowering) 82.dp else 16.dp) { life.toastMessage = null }
    if (account.isNotEmpty()) {
        val selected = account
        val connected = when (selected) { "school" -> campus.hasSession; "life" -> loggedIn; else -> life.isQzhqLoggedIn }
        val title = when (selected) { "school" -> "学校账号"; "life" -> "趣智校园"; else -> "趣智热水后勤" }
        AlertDialog(onDismissRequest = { account = "" }, title = { Text(title) }, text = {
            Column {
                Text(if (connected) "已登录" else "未登录", color = AppColors.TextSecondary)
                if (selected != "school" && connected) TextButton(onClick = { tap()
                    codesBack = page; page = "codes"; account = ""
                }) { Text("使用码") }
                TextButton(onClick = { tap()
                    account = ""
                    when (selected) { "school" -> loginSchool(); "life" -> loginLife(); else -> logisticsLogin = true }
                }) { Text(if (connected) "重新登录" else "登录") }
                if (connected) TextButton(onClick = { tap(); account = ""; logout = selected }) { Text("退出登录", color = AppColors.Danger) }
            }
        }, confirmButton = { TextButton(onClick = { tap(); account = "" }) { Text("关闭") } })
    }
    if (logisticsLogin) QzhqLoginDialog(
        initialPhone = PrefsHelper.qzhqPhone.ifEmpty { PrefsHelper.telephone },
        onDismiss = { logisticsLogin = false },
        onPasswordSubmit = { number, password -> life.loginQzhq(number, password) { success, _ -> if (success) logisticsLogin = false } },
        onSmsSubmit = { number, code -> life.smsLoginQzhq(number, code) { success, _ -> if (success) logisticsLogin = false } },
        onSendSms = { number, sent -> life.sendQzhqSmsCode(number) { success, _ -> sent(success) } }
    )
    if (pending) AlertDialog(onDismissRequest = { pending = false }, title = { Text("有待核对的充值") }, text = {
        Column {
            Text("继续原订单，避免重复充值。")
            TextButton(onClick = { tap(); campus.checkPendingPayment(); pending = false }) { Text("查询支付结果") }
            TextButton(onClick = { tap(); campus.session.clearPending(); pending = false }) { Text("我已核对，清除本机记录") }
        }
    }, confirmButton = { TextButton(onClick = { tap(); pending = false; school.launch(Intent(activity, SchoolActivity::class.java)
        .putExtra("mode", if (campus.session.pendingKind == "electric") "electric" else "payment")) }) { Text("继续原订单") } },
        dismissButton = { TextButton(onClick = { tap(); pending = false }) { Text("返回") } })
    if (logout.isNotEmpty()) AlertDialog(onDismissRequest = { logout = "" }, title = { Text("退出${when (logout) { "school" -> "学校"; "logistics" -> "后勤"; else -> "趣智校园" }}账号？") },
        text = { Text(if (life.isShowering && logout == "life") "退出不会停止当前出水。" else "下次使用该账号需要重新登录。") },
        confirmButton = { TextButton(onClick = { tap()
            if (logout == "school") { campus.logout(); CookieManager.getInstance().removeAllCookies(null); CookieManager.getInstance().flush() }
            else if (logout == "logistics") life.logoutQzhq()
            else { life.logout(); PrefsHelper.clearLifeAccount(); loggedIn = false; phone = "" }
            logout = ""
        }) { Text("退出登录") } }, dismissButton = { TextButton(onClick = { tap(); logout = "" }) { Text("取消") } })
    if (life.kickedOut && loggedIn) AlertDialog(onDismissRequest = {}, title = { Text("趣智账号已下线") }, text = { Text("请重新登录趣智账号，校园卡仍可独立使用。") },
        confirmButton = { TextButton(onClick = { tap(); life.logout(); PrefsHelper.loginCode = ""; loggedIn = false; phone = ""; life.kickedOut = false; page = "login" }) { Text("重新登录") } },
        dismissButton = { TextButton(onClick = { tap(); life.logout(); PrefsHelper.loginCode = ""; loggedIn = false; phone = ""; life.kickedOut = false }) { Text("稍后") } })
}

@Composable internal fun PageTitle(title: String, back: (() -> Unit)? = null, padded: Boolean = true) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(horizontal = if (padded) 22.dp else 0.dp).padding(top = 20.dp, bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        if (back != null) IconButton(onClick = { HapticHelper.tick(context); back() }, modifier = Modifier.size(38.dp).padding(end = 6.dp)) { Icon(Icons.Outlined.ArrowBack, "返回") }
        Text(title, fontFamily = FontFamily.Serif, fontSize = 27.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable private fun UnifiedNavigation(tab: Int, select: (Int) -> Unit) {
    val context = LocalContext.current
    Column(Modifier.background(AppColors.Background).navigationBarsPadding().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 7.dp, bottom = 4.dp)) {
            listOf("首页" to Icons.Outlined.Home, "校园卡" to Icons.Outlined.QrCodeScanner,
                "账单" to Icons.Outlined.ReceiptLong, "我的" to Icons.Outlined.PersonOutline).forEachIndexed { index, (name, icon) ->
                key(AppColors.isDark) {
                    val color by animateColorAsState(if (index == tab) AppColors.Accent else AppColors.TextSecondary, tween(160), label = "navigationInk")
                    val indicator by animateColorAsState(if (index == tab) AppColors.ActiveBg else androidx.compose.ui.graphics.Color.Transparent, tween(160), label = "navigationPill")
                    Column(Modifier.weight(1f).selectable(selected = index == tab, role = Role.Tab, onClick = { if (index != tab) { HapticHelper.tick(context); select(index) } }).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(width = 54.dp, height = 30.dp).background(indicator, RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
                            Icon(icon, name, Modifier.size(22.dp), tint = color)
                        }
                        Text(name, fontSize = 11.sp, color = color, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable internal fun SchoolLogin(login: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("江苏大学一卡通", fontFamily = FontFamily.Serif, fontSize = 22.sp)
        Text("首次使用，请完成学校认证。", color = AppColors.TextSecondary)
        Button(onClick = login, shape = RoundedCornerShape(12.dp)) { Text("登录学校账号") }
    }
}

@Composable private fun SchoolError(vm: CampusViewModel, login: () -> Unit) {
    vm.error?.let {
        Column { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            TextButton(onClick = if (vm.needsLogin) login else vm::refresh) { Text(if (vm.needsLogin) "更新学校登录" else "重试") } }
    }
}

@Composable internal fun SettingsRow(title: String, icon: ImageVector, trailing: String = "", action: () -> Unit) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().clickable { HapticHelper.tick(context); action() }.heightIn(min = 55.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(21.dp), tint = AppColors.TextPrimary)
        Text(title, Modifier.weight(1f).padding(start = 14.dp), fontSize = 15.sp)
        if (trailing.isNotEmpty()) Text(trailing, fontSize = 12.sp, color = AppColors.TextSecondary)
        Icon(Icons.Outlined.ChevronRight, null, Modifier.padding(start = 7.dp).size(18.dp), tint = AppColors.TextSecondary)
    }
}

@Composable private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = AppColors.Card, shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 5.dp), content = content)
    }
}

@Composable private fun UnifiedProfile(vm: CampusViewModel, login: () -> Unit, accounts: () -> Unit, electric: () -> Unit, codes: () -> Unit,
    checkingUpdate: Boolean, checkUpdate: () -> Unit) {
    val theme = LocalThemeReveal.current
    val context = LocalContext.current
    var feedback by remember { mutableStateOf(PrefsHelper.touchFeedback) }
    var appearance by remember { mutableStateOf(false) }
    val mode = LocalThemeMode.current.value
    if (appearance) AppearancePicker(mode, { appearance = false }) { next, point ->
        appearance = false
        theme.select(next, point)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = 22.dp)) {
        PageTitle("我的", padded = false)
        val identity = vm.identity
        if (identity != null) StudentCard(identity)
        else if (!vm.hasSession) OutlinedButton(onClick = { HapticHelper.tick(context); login() }, modifier = Modifier.fillMaxWidth().height(74.dp), shape = RoundedCornerShape(20.dp)) { Text("登录学校账号 · 查看电子学生卡") }
        else SettingsGroup {
            Text("电子学生卡", fontSize = 16.sp, modifier = Modifier.padding(top = 14.dp))
            if (vm.identityBusy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 16.dp))
            else TextButton(onClick = { HapticHelper.tick(context); if (vm.needsLogin) login() else vm.loadIdentity(true) }) { Text("加载电子学生卡") }
            vm.identityError?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
        }
        SectionLabel("校园服务")
        SettingsGroup {
            SettingsRow("账号管理", Icons.Outlined.ManageAccounts, action = accounts)
            SettingsRow("电费", Icons.Outlined.Bolt, action = electric)
            SettingsRow("使用码", Icons.Outlined.Key, action = codes)
        }
        SectionLabel("偏好设置")
        SettingsGroup {
            SettingsRow("外观", Icons.Outlined.Palette, mode.label) { if (!theme.transitioning) appearance = true }
            Row(Modifier.fillMaxWidth().heightIn(min = 55.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Vibration, null, Modifier.size(21.dp), tint = AppColors.TextPrimary)
                Text("触感反馈", Modifier.weight(1f).padding(start = 14.dp), fontSize = 15.sp)
                Switch(feedback, onCheckedChange = { feedback = it; PrefsHelper.touchFeedback = it; if (it) HapticHelper.tick(context) })
            }
        }
        SectionLabel("项目支持")
        SettingsGroup {
            SettingsRow("检查更新", Icons.Outlined.SystemUpdate, if (checkingUpdate) "检查中…" else "", checkUpdate)
            SettingsRow("GitHub", Icons.Outlined.Code) { UpdateManager.openBrowser(context, "https://github.com/Cainite07/MudLife") }
            SettingsRow("Gitee", Icons.Outlined.Download) { UpdateManager.openBrowser(context, "https://gitee.com/cainite/MudLife") }
        }
    }
}

@Composable private fun AppearancePicker(mode: ThemeMode, dismiss: () -> Unit, select: (ThemeMode, Offset) -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text("外观") }, text = {
        Column {
            ThemeMode.entries.forEach { option ->
                AppearanceOption(option, mode == option) { point ->
                    if (mode == option) dismiss() else select(option, point)
                }
            }
        }
    }, confirmButton = { TextButton(onClick = dismiss) { Text("关闭") } })
}

@Composable private fun AppearanceOption(mode: ThemeMode, selected: Boolean, select: (Offset) -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val interactions = remember { MutableInteractionSource() }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var press by remember { mutableStateOf<Offset?>(null) }
    LaunchedEffect(interactions) {
        interactions.interactions.collect { if (it is PressInteraction.Press) press = it.pressPosition }
    }
    Row(Modifier.fillMaxWidth().onGloballyPositioned { coordinates = it }
        .selectable(selected = selected, interactionSource = interactions, indication = LocalIndication.current,
            role = Role.RadioButton, onClick = {
                val layout = coordinates
                val screen = IntArray(2).also(view::getLocationOnScreen)
                val window = IntArray(2).also(view::getLocationInWindow)
                val point = layout?.localToWindow(press ?: Offset(layout.size.width / 2f, layout.size.height / 2f))
                val origin = point?.plus(Offset((screen[0] - window[0]).toFloat(), (screen[1] - window[1]).toFloat()))
                    ?: Offset.Unspecified
                HapticHelper.tick(context)
                select(origin)
            }).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick = null)
        Text(mode.label, Modifier.padding(start = 14.dp))
    }
}

@Composable private fun SectionLabel(text: String) { Text(text, fontSize = 12.sp, color = AppColors.TextSecondary, modifier = Modifier.padding(start = 14.dp, top = 20.dp, bottom = 8.dp)) }

@Composable private fun AccountPage(life: Boolean, logisticsLoggedIn: Boolean, school: Boolean, schoolExpired: Boolean,
    setup: Boolean, back: () -> Unit, finish: () -> Unit, select: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) {
        PageTitle(if (setup) "欢迎使用江大生活" else "账号管理", if (setup) null else back, false)
        if (setup) Text("按需登录，随时可以回来补充。", fontSize = 14.sp, color = AppColors.TextSecondary, modifier = Modifier.padding(bottom = 20.dp))
        SettingsGroup {
            AccountRow("学校账号", "付款码 · 余额 · 账单 · 电费", Icons.Outlined.School, school, schoolExpired) { select("school") }
            HorizontalDivider(color = AppColors.Border.copy(alpha = .5f))
            AccountRow("趣智校园", "饮水机 · 吹风机使用码", Icons.Outlined.WaterDrop, life) { select("life") }
            HorizontalDivider(color = AppColors.Border.copy(alpha = .5f))
            AccountRow("趣智热水后勤", "洗浴使用码", Icons.Outlined.Shower, logisticsLoggedIn) { select("logistics") }
        }
        if (setup) {
            Button(onClick = finish, modifier = Modifier.fillMaxWidth().padding(top = 24.dp), shape = RoundedCornerShape(14.dp)) {
                Text(if (school || life || logisticsLoggedIn) "开始使用" else "稍后登录")
            }
        }
    }
}

@Composable private fun AccountRow(title: String, purpose: String, icon: ImageVector, connected: Boolean, expired: Boolean = false, action: () -> Unit) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().clickable { HapticHelper.tick(context); action() }.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(23.dp), tint = AppColors.Accent)
        Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(purpose, fontSize = 12.sp, color = AppColors.TextSecondary, modifier = Modifier.padding(top = 5.dp))
        }
        Text(when { expired -> "重新登录"; connected -> "已登录"; else -> "去登录" }, fontSize = 12.sp, color = if (connected && !expired) AppColors.TextSecondary else AppColors.Accent)
        Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp), tint = AppColors.TextSecondary)
    }
}

@Composable private fun HomeWelcome(school: Boolean, login: () -> Unit, codes: () -> Unit, accounts: () -> Unit, card: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) {
        PageTitle("江大生活", padded = false)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(onClick = login, modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Outlined.WaterDrop, null, Modifier.size(19.dp)); Spacer(Modifier.width(8.dp)); Text("登录趣智")
            }
            FilledTonalButton(onClick = codes, modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Outlined.Key, null, Modifier.size(19.dp)); Spacer(Modifier.width(8.dp)); Text("使用码")
            }
        }
        SectionLabel("校园生活")
        SettingsGroup {
            SettingsRow("校园付款码", Icons.Outlined.QrCodeScanner, if (school) "" else "登录学校账号", card)
            SettingsRow("账号管理", Icons.Outlined.ManageAccounts, action = accounts)
        }
    }
}
