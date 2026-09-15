package cn.mudlife.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.mudlife.app.api.NetworkModule
import cn.mudlife.app.ui.MudLifeToast
import cn.mudlife.app.ui.LoginScreen
import cn.mudlife.app.ui.MainScreen
import cn.mudlife.app.ui.MainViewModel
import cn.mudlife.app.ui.UserScreen
import cn.mudlife.app.ui.WalletScreen
import cn.mudlife.app.ui.theme.*
import cn.mudlife.app.utils.HapticHelper
import cn.mudlife.app.utils.PrefsHelper

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 🌟 1. 物理级全屏 Edge-to-Edge 沉浸：彻底消除系统导航栏白色矩形 🌟
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        cn.mudlife.app.utils.AppLogger.init(this)
        PrefsHelper.init(this)

        val isSysDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val initialThemeMode = if (isSysDark) ThemeMode.DARK else ThemeMode.LIGHT
        updateSystemBars(initialThemeMode)

        val hasToken = PrefsHelper.isLoggedIn
        if (hasToken) NetworkModule.restoreFromPrefs()

        setContent {
            val isSystemDark = isSystemInDarkTheme()
            val themeModeState = remember { mutableStateOf(initialThemeMode) }

            // 🌟 动态监听系统明暗切换：随手机系统深色模式开关毫秒级瞬时自适应 🌟
            LaunchedEffect(isSystemDark) {
                val target = if (isSystemDark) ThemeMode.DARK else ThemeMode.LIGHT
                if (themeModeState.value != target) {
                    themeModeState.value = target
                    updateSystemBars(target)
                }
            }

            // 🌟 2. 状态提升 (State Hoisting)：将核心状态提升至主题动效外层，彻底杜绝切明暗重置回首页 🌟
            val mainViewModel: MainViewModel = viewModel()
            var isLoggedIn by rememberSaveable { mutableStateOf(hasToken) }
            var userPhone by rememberSaveable { mutableStateOf(PrefsHelper.telephone) }
            var currentTab by rememberSaveable { mutableStateOf(0) }
            var showKickedDialog by remember { mutableStateOf(false) }
            var showLogoutConfirm by remember { mutableStateOf(false) }

            CircularRevealThemeHost(
                themeModeState = themeModeState,
                onThemeChanged = { newMode ->
                    updateSystemBars(newMode)
                }
            ) {
                val currentThemeMode by themeModeState
                SideEffect {
                    updateSystemBars(currentThemeMode)
                }

                LaunchedEffect(mainViewModel.kickedOut, isLoggedIn) {
                    if (mainViewModel.kickedOut && !showKickedDialog && isLoggedIn) {
                        showKickedDialog = true
                    }
                }

                // 🌟 3. 根容器全屏着色：全屏统一底色，绝不漏底、绝无局部色块 🌟
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AppColors.Background)
                ) {
                    if (!isLoggedIn) {
                        LoginScreen(onLoginSuccess = { phone ->
                            userPhone = phone
                            isLoggedIn = true
                            currentTab = 0
                        })
                    } else {
                        // 🌟 4. 三大主页面弹性视差左右滑移 (Spring Page Transition) 🌟
                        // 移除 bottom 94dp 硬截断，全屏贯通延伸至屏幕物理底边缘，绝无矩形色块分割
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .statusBarsPadding()
                                .padding(top = 6.dp)
                        ) {
                            AnimatedContent(
                                targetState = currentTab,
                                transitionSpec = {
                                    val forward = targetState > initialState
                                    val springSpec = spring<IntOffset>(
                                        dampingRatio = 0.78f,
                                        stiffness = 420f
                                    )
                                    val fadeSpec = spring<Float>(stiffness = 420f)
                                    (slideInHorizontally(
                                        animationSpec = springSpec,
                                        initialOffsetX = { fullWidth -> if (forward) fullWidth else -fullWidth }
                                    ) + fadeIn(animationSpec = fadeSpec))
                                    .togetherWith(
                                        slideOutHorizontally(
                                            animationSpec = springSpec,
                                            targetOffsetX = { fullWidth -> if (forward) -fullWidth else fullWidth }
                                        ) + fadeOut(animationSpec = fadeSpec)
                                    )
                                },
                                label = "PageSpringTransition"
                            ) { tab ->
                                when (tab) {
                                    0 -> MainScreen(phone = userPhone, viewModel = mainViewModel)
                                    1 -> WalletScreen(viewModel = mainViewModel)
                                    2 -> UserScreen(
                                        phone = userPhone,
                                        viewModel = mainViewModel,
                                        onLogout = { showLogoutConfirm = true }
                                    )
                                }
                            }
                        }

                        // 🌟 5. 果冻物理弹性滑动胶囊底栏 (Floating Pill Nav Bar with Spring Indicator) 🌟
                        if (!mainViewModel.isShowering) {
                            FloatingPillNavBar(
                                currentTab = currentTab,
                                onTabSelected = { currentTab = it },
                                modifier = Modifier.align(Alignment.BottomCenter)
                            )
                        }
                    }

                    // Toast 和顶层弹窗
                    MudLifeToast(
                        message = mainViewModel.toastMessage,
                        onDismiss = { mainViewModel.toastMessage = null }
                    )

                    if (showKickedDialog) {
                        AlertDialog(
                            onDismissRequest = {},
                            shape = RoundedCornerShape(22.dp),
                            containerColor = AppColors.SolidSurface,
                            title = {
                                Text(
                                    "账号在别处登录",
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            },
                            text = {
                                Text(
                                    "当前设备已被强制下线",
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        showKickedDialog = false
                                        mainViewModel.kickedOut = false
                                        mainViewModel.logout()
                                        isLoggedIn = false
                                        userPhone = ""
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("确定") }
                            }
                        )
                    }

                    if (showLogoutConfirm) {
                        AlertDialog(
                            onDismissRequest = { showLogoutConfirm = false },
                            shape = RoundedCornerShape(22.dp),
                            containerColor = AppColors.SolidSurface,
                            title = { Text("确认退出") },
                            text = {
                                Text(
                                    if (mainViewModel.isShowering)
                                        "当前正在出水中，退出登录不会自动停止供水。是否退出？"
                                    else "确定退出登录？"
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    showLogoutConfirm = false
                                    mainViewModel.logout()
                                    PrefsHelper.clear()
                                    isLoggedIn = false
                                    userPhone = ""
                                }) { Text("退出", color = MaterialTheme.colorScheme.error) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showLogoutConfirm = false }) { Text("取消") }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun updateSystemBars(themeMode: ThemeMode) {
        val isDarkMode = themeMode == ThemeMode.DARK
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isDarkMode
            isAppearanceLightNavigationBars = !isDarkMode
        }
    }
}

/**
 * 🌟 现代 MD3 / KernelSU 标志性果冻弹性滑动胶囊底栏 🌟
 * 内部包含单一独立高亮滑动药丸，跟随手势或点击呈现物理超调弹性位移
 */
@Composable
fun FloatingPillNavBar(
    currentTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDark = (LocalAppColors.current == DarkColors)

    // 底栏悬浮于系统导航手势条上方，适当抬高 22dp，呈现悬空轻盈感，不再偏下
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 22.dp, start = 24.dp, end = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = if (isDark) Color(0xB81A202C) else Color(0xD8FFFFFF),
            border = BorderStroke(1.dp, if (isDark) Color(0x28FFFFFF) else Color(0x18000000)),
            shadowElevation = 10.dp,
            tonalElevation = 0.dp,
            modifier = Modifier.width(260.dp)
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp)
            ) {
                val totalWidth = maxWidth
                val itemWidth = totalWidth / 3

                // 物理弹簧驱动的滑动高亮背景块 (Spring Indicator)
                val targetOffset = itemWidth * currentTab
                val animatedOffset by animateDpAsState(
                    targetValue = targetOffset,
                    animationSpec = spring(
                        dampingRatio = 0.72f, // 果冻微弹超调 (Bouncy overshoot)
                        stiffness = 380f      // 适中敏捷刚度
                    ),
                    label = "SpringPillOffset"
                )

                // 🌟 高亮滑块实体药丸 🌟
                Surface(
                    modifier = Modifier
                        .offset(x = animatedOffset)
                        .width(itemWidth)
                        .height(42.dp),
                    shape = RoundedCornerShape(22.dp),
                    color = if (isDark) Color(0xFFA8C7FA) else Color(0xFF1E40AF),
                    shadowElevation = 2.dp
                ) {}

                // 三个等宽点击选项
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NavBarTabItem(
                        modifier = Modifier.weight(1f),
                        label = "首页",
                        icon = Icons.Default.Home,
                        isSelected = currentTab == 0,
                        isDark = isDark,
                        onClick = {
                            if (currentTab != 0) {
                                HapticHelper.tick(context)
                                onTabSelected(0)
                            }
                        }
                    )
                    NavBarTabItem(
                        modifier = Modifier.weight(1f),
                        label = "账单",
                        icon = Icons.Outlined.ReceiptLong,
                        isSelected = currentTab == 1,
                        isDark = isDark,
                        onClick = {
                            if (currentTab != 1) {
                                HapticHelper.tick(context)
                                onTabSelected(1)
                            }
                        }
                    )
                    NavBarTabItem(
                        modifier = Modifier.weight(1f),
                        label = "我的",
                        icon = Icons.Default.Person,
                        isSelected = currentTab == 2,
                        isDark = isDark,
                        onClick = {
                            if (currentTab != 2) {
                                HapticHelper.tick(context)
                                onTabSelected(2)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun NavBarTabItem(
    modifier: Modifier = Modifier,
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val activeColor = if (isDark) Color(0xFF062E6F) else Color.White
    val inactiveColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    val contentColor by animateColorAsState(
        targetValue = if (isSelected) activeColor else inactiveColor,
        animationSpec = tween(durationMillis = 200),
        label = "TabContentColor"
    )
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.04f else 1.0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 400f),
        label = "TabScale"
    )

    Box(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .scale(scale),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(17.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                color = contentColor,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}
