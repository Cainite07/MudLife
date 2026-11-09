package cn.mudlife.app.ui

import android.app.Activity
import android.content.Intent
import android.view.WindowManager
import android.webkit.CookieManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    val activity = LocalContext.current as ComponentActivity
    val timetableFeature = activity.application as TimetableFeature
    val timetableOwner = remember(campus.hasSession, campus.selected, campus.currentCard?.number, campus.updatedAt) {
        campus.session.timetableOwner
    }
    val timetablePage = timetableFeature.rememberTab(activity, timetableOwner)
    val tap = { HapticHelper.tick(activity) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var foreground by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var loggedIn by rememberSaveable { mutableStateOf(PrefsHelper.isLoggedIn) }
    var phone by rememberSaveable { mutableStateOf(PrefsHelper.telephone) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var page by rememberSaveable { mutableStateOf(when {
        !PrefsHelper.accountGuideSeen && !loggedIn && !life.isQzhqLoggedIn && !campus.hasSession -> "setup"
        campus.startupCode -> "card"
        else -> ""
    }) }
    LaunchedEffect(Unit) {
        if (page != "setup" && (loggedIn || life.isQzhqLoggedIn || campus.hasSession)) PrefsHelper.accountGuideSeen = true
    }
    var loginBack by rememberSaveable { mutableStateOf("") }
    var featureBack by rememberSaveable { mutableIntStateOf(2) }
    var rechargeBack by rememberSaveable { mutableStateOf("") }
    val loginLife = { loginBack = page; page = "login" }
    val pageState = rememberSaveableStateHolder()
    var pending by remember { mutableStateOf(false) }
    var logout by remember { mutableStateOf("") }
    var account by remember { mutableStateOf("") }
    var showSchoolTestTools by rememberSaveable { mutableStateOf(false) }
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
    val resetSchool = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) {
            campus.authenticated()
            loginSchool()
        } else if (campus.hasSession != campus.session.hasSession) {
            campus.authenticated()
        }
    }
    val openRecharge = { featureBack = tab; rechargeBack = page; if (campus.session.pendingCheckout.isNotBlank()) pending = true else page = "recharge" }
    val openPayment: () -> Unit = {
        page = "card"
        if (!campus.hasSession || campus.needsLogin) loginSchool()
    }
    val openElectric = { featureBack = tab; page = "electric" }
    val openCodes = { codesBack = ""; page = "codes" }
    DisposableEffect(lifecycle, campus, life) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> { foreground = true; campus.resume(); life.validateQzhqSession() }
                Lifecycle.Event.ON_PAUSE -> { foreground = false; campus.pause() }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (foreground) { campus.resume(); life.validateQzhqSession() }
        onDispose { lifecycle.removeObserver(observer); campus.pause() }
    }
    LaunchedEffect(loggedIn, phone) { if (loggedIn) life.initManagers(activity.applicationContext) }
    LaunchedEffect(campus.paymentNotice) { campus.paymentNotice?.let { snackbar.showSnackbar(it) } }
    // A restored task may still carry the old balance-detail route.
    val onCardPage = page == "card" || page == "balance"
    val codeVisible = onCardPage && campus.hasSession && !campus.needsLogin
    LaunchedEffect(codeVisible, campus.currentCard?.account, campus.needsLogin) { campus.codeVisibility(codeVisible) }
    LaunchedEffect(tab, page, campus.currentCard?.account, campus.hasSession) {
        if (campus.hasSession) when {
            page == "electric" -> campus.loadElectric()
            page == "recharge" -> campus.loadRechargeRules()
            page.isEmpty() && tab == 2 -> campus.enterBills()
            page.isEmpty() && tab == 3 -> campus.loadIdentity()
            (onCardPage || tab == 0 && page.isEmpty()) &&
                System.currentTimeMillis() - campus.updatedAt > 60000 -> campus.refresh()
        }
    }
    LaunchedEffect(page) { if (page == "accounts" || page == "setup") life.validateQzhqSession() }
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
            "recharge" -> { page = rechargeBack; tab = featureBack }
            "electric" -> { page = ""; tab = featureBack }
            "" -> tab = 0
            else -> page = ""
        }
    }
    BackHandler(page.isNotEmpty() || tab != 0, onBack = back)
    val edgeRoot = page.isEmpty()
    Scaffold(containerColor = AppColors.Background, snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
        if (page.isEmpty() && !life.isShowering) {
            CampusNavigationBar(tab) { tab = it }
        }
    }) { insets ->
        Box(Modifier.fillMaxSize()) {
            val frame = if (edgeRoot) Modifier else Modifier.padding(insets).consumeWindowInsets(insets)
            RouteMotion(tab, page, frame) {
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
                page == "accounts" || page == "setup" -> AccountPage(loggedIn, life.isQzhqLoggedIn, campus.hasSession, campus.needsLogin, life.qzhqSessionExpired,
                    setup = page == "setup", back = back, finish = finishSetup, openTestTools = { showSchoolTestTools = true }, select = {
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
                            page = rechargeBack; tab = featureBack
                            school.launch(Intent(activity, SchoolActivity::class.java).putExtra("mode", "payment")
                                .putExtra("account", card.account).putExtra("target", target).putExtra("fen", fen))
                        }
                    }
                }
                onCardPage -> Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                    PageTitle("校园付款码", back = back, padded = false)
                    if (!campus.hasSession) SchoolLogin(loginSchool)
                    else {
                        SchoolError(campus, loginSchool)
                        UnifiedCardPage(campus)
                    }
                }
                tab == 0 -> {
                    val services: @Composable (() -> Unit) -> Unit = { scan ->
                        HomeCampusServices(campus, onPayment = openPayment, onScan = scan,
                            onElectric = openElectric, onCodes = openCodes)
                    }
                    if (loggedIn) MainScreen(phone, life, onUseCodes = openCodes,
                        campusServices = services, onCampusRefresh = campus::refresh, pageInsets = insets)
                    else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(insets).padding(horizontal = 22.dp)) {
                        HomePageHeader()
                        services(loginLife)
                        SettingsRow("账号管理", Icons.Outlined.ManageAccounts) { page = "accounts" }
                    }
                }
                tab == 1 -> {
                    if (timetablePage != null) timetablePage.Content(insets) { tab = 0 }
                    else Column(Modifier.fillMaxSize().padding(insets).padding(horizontal = 22.dp)) {
                        PageTitle("我的课表", padded = false)
                        when {
                            !campus.hasSession || campus.needsLogin -> {
                                Text("登录学校账号后可查看自己的课表。", color = AppColors.TextSecondary)
                                TextButton(onClick = loginSchool) { Text("登录学校账号") }
                            }
                            campus.busy -> LinearProgressIndicator(Modifier.fillMaxWidth())
                            else -> {
                                Text(campus.error ?: "暂未取得学校账号对应的学号。", color = AppColors.TextSecondary)
                                TextButton(onClick = campus::refresh) { Text("重新读取") }
                            }
                        }
                    }
                }
                tab == 2 -> UnifiedBillsPage(campus, life, loggedIn, loginSchool, openRecharge,
                    pageInsets = if (edgeRoot) insets else PaddingValues(0.dp))
                else -> UnifiedProfile(campus, loginSchool, accounts = { page = "accounts" },
                    electric = openElectric, codes = openCodes,
                    checkingUpdate = life.isCheckingUpdate, checkUpdate = { life.checkUpdate(true) },
                    pageInsets = if (edgeRoot) insets else PaddingValues(0.dp))
            }
            }
            }
            if (edgeRoot) Spacer(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars)
                .background(Brush.verticalGradient(listOf(AppColors.Background.copy(alpha = .45f), Color.Transparent))))
        }
    }
    AppUpdateDialog(life)
    MudLifeToast(life.toastMessage, bottomSpacing = if (page.isEmpty() && !life.isShowering) 82.dp else 16.dp) { life.toastMessage = null }
    if (account.isNotEmpty()) {
        val selected = account
        val connected = when (selected) { "school" -> campus.hasSession; "life" -> loggedIn; else -> life.isQzhqLoggedIn }
        val title = when (selected) { "school" -> "学校账号"; "life" -> "趣智校园"; else -> "趣智后勤热水" }
        AlertDialog(onDismissRequest = { account = "" }, title = { Text(title) }, text = {
            Column {
                Text(if (selected == "logistics" && life.qzhqSessionExpired ||
                    selected == "school" && campus.needsLogin) "登录已失效"
                    else if (connected) "已登录" else "未登录", color = AppColors.TextSecondary)
                if (selected != "school" && connected) TextButton(onClick = { tap()
                    codesBack = page; page = "codes"; account = ""
                }) { Text("使用码") }
                TextButton(onClick = { tap()
                    account = ""
                    when (selected) { "school" -> loginSchool(); "life" -> loginLife(); else -> logisticsLogin = true }
                }) { Text(if (connected || (selected == "logistics" && life.qzhqSessionExpired)) "重新登录" else "登录") }
                if (connected) TextButton(onClick = { tap(); account = ""; logout = selected }) { Text("退出登录", color = AppColors.Danger) }
            }
        }, confirmButton = { TextButton(onClick = { tap(); account = "" }) { Text("关闭") } })
    }
    if (BuildConfig.DEBUG && showSchoolTestTools) AlertDialog(
        onDismissRequest = { showSchoolTestTools = false },
        title = { Text("测试工具") },
        text = { Text("从头测试学校登录时使用。将清除本应用内的校园卡、统一认证和教务登录，再打开校方登录页。已导入的课表、手动修改和其他账号会保留。") },
        confirmButton = { TextButton(onClick = {
            tap()
            showSchoolTestTools = false
            resetSchool.launch(Intent().setClassName(activity, "cn.mudlife.app.timetable.SchoolAuthResetActivity"))
        }) { Text("重置并重新登录", color = AppColors.Danger) } },
        dismissButton = { TextButton(onClick = { showSchoolTestTools = false }) { Text("关闭") } }
    )
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

@Composable internal fun PageTitle(title: String, back: (() -> Unit)? = null, padded: Boolean = true,
    trailing: (@Composable () -> Unit)? = null) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(horizontal = if (padded) 22.dp else 0.dp)
        .heightIn(min = 64.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (back != null) {
            IconButton(onClick = { HapticHelper.tick(context); back() }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回", Modifier.size(22.dp), tint = AppColors.TextPrimary)
            }
        }
        Text(title, Modifier.weight(1f), fontFamily = FontFamily.Serif, fontSize = 26.sp,
            fontWeight = FontWeight.Medium, color = AppColors.TextPrimary)
        trailing?.invoke()
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
    checkingUpdate: Boolean, checkUpdate: () -> Unit, pageInsets: PaddingValues) {
    val theme = LocalThemeReveal.current
    val context = LocalContext.current
    var feedback by remember { mutableStateOf(PrefsHelper.touchFeedback) }
    var appearance by remember { mutableStateOf(false) }
    val mode = LocalThemeMode.current.value
    if (appearance) AppearancePicker(mode, { appearance = false }) { next, point ->
        appearance = false
        theme.select(next, point)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(pageInsets).padding(horizontal = 22.dp).padding(bottom = 22.dp)) {
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

@Composable private fun AccountPage(life: Boolean, logisticsLoggedIn: Boolean, school: Boolean, schoolExpired: Boolean, logisticsExpired: Boolean,
    setup: Boolean, back: () -> Unit, finish: () -> Unit, openTestTools: () -> Unit, select: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) {
        PageTitle(if (setup) "欢迎使用江大生活" else "账号管理", if (setup) null else back, false)
        if (setup) Text("按需登录，随时可以回来补充。", fontSize = 14.sp, color = AppColors.TextSecondary, modifier = Modifier.padding(bottom = 20.dp))
        SettingsGroup {
            AccountRow("学校账号", "付款码 · 余额 · 账单 · 电费", Icons.Outlined.School, school, schoolExpired) { select("school") }
            HorizontalDivider(color = AppColors.Border.copy(alpha = .5f))
            AccountRow("趣智校园", "饮水机 · 吹风机使用码", Icons.Outlined.WaterDrop, life) { select("life") }
            HorizontalDivider(color = AppColors.Border.copy(alpha = .5f))
            AccountRow("趣智后勤热水", "洗浴使用码", Icons.Outlined.Shower, logisticsLoggedIn, logisticsExpired) { select("logistics") }
        }
        if (!setup && BuildConfig.DEBUG) {
            Spacer(Modifier.height(20.dp))
            SettingsGroup { SettingsRow("测试工具", Icons.Outlined.Build, "开发调试", openTestTools) }
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
