package cn.mudlife.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.mudlife.app.api.NetworkModule
import cn.mudlife.app.ui.MainViewModel
import cn.mudlife.app.ui.UnifiedApp
import cn.mudlife.app.ui.theme.*
import cn.mudlife.app.utils.AppLogger
import cn.mudlife.app.utils.PrefsHelper
import cn.mudlife.campuscard.CampusViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                AppLogger.init(this@MainActivity)
                PrefsHelper.init(this@MainActivity)
                NetworkModule.restoreFromPrefs()
            }
            setContent {
                val systemDark = isSystemInDarkTheme()
                val mode = remember { mutableStateOf(if (systemDark) ThemeMode.DARK else ThemeMode.LIGHT) }
                LaunchedEffect(systemDark) { mode.value = if (systemDark) ThemeMode.DARK else ThemeMode.LIGHT }
                val life: MainViewModel = viewModel()
                val campus: CampusViewModel = viewModel()
                CircularRevealThemeHost(mode, onThemeChanged = {}) {
                    SideEffect {
                        WindowCompat.getInsetsController(window, window.decorView).apply {
                            isAppearanceLightStatusBars = mode.value == ThemeMode.LIGHT
                            isAppearanceLightNavigationBars = mode.value == ThemeMode.LIGHT
                        }
                    }
                    UnifiedApp(life, campus)
                }
            }
        }
    }
}
