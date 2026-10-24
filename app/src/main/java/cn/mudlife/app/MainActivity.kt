package cn.mudlife.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.graphics.drawable.ColorDrawable
import androidx.compose.ui.graphics.toArgb
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
                val mode = remember { mutableStateOf(ThemeMode.restore(PrefsHelper.themeMode)) }
                val life: MainViewModel = viewModel()
                val campus: CampusViewModel = viewModel()
                CircularRevealThemeHost(mode, onThemeChanged = { PrefsHelper.themeMode = it.name }) {
                    val windowDark = LocalThemeWindowDark.current
                    val background = AppColors.Background
                    SideEffect {
                        window.setBackgroundDrawable(ColorDrawable(background.toArgb()))
                        WindowCompat.getInsetsController(window, window.decorView).apply {
                            isAppearanceLightStatusBars = !windowDark
                            isAppearanceLightNavigationBars = !windowDark
                        }
                    }
                    UnifiedApp(life, campus)
                }
            }
        }
    }
}
