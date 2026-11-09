package cn.mudlife.app.timetable

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import cn.mudlife.app.MainActivity
import cn.mudlife.campuscard.SessionStore

class TimetableActivity : ComponentActivity() {
    companion object {
        const val EXTRA_ACCOUNT = "cn.mudlife.timetable.ACCOUNT"
        const val EXTRA_TERM = "cn.mudlife.timetable.TERM"
        fun intent(context: Context, accountKey: String) =
            Intent(context, TimetableActivity::class.java).putExtra(EXTRA_ACCOUNT, accountKey)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = SessionStore(this)
        val fromWidget = intent.action == TodayTimetableWidget.OPEN_TODAY
        val owner = if (fromWidget) session.timetableOwner.orEmpty()
            else intent.getStringExtra(EXTRA_ACCOUNT).orEmpty().trim()
        if (owner.isEmpty() || owner != session.timetableOwner) {
            Toast.makeText(this, "请先选择已登录的校园账号", Toast.LENGTH_LONG).show()
            if (fromWidget) startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        val dark = timetableThemeIsDark(this)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
        setContent { StandaloneTimetable(this, owner, onExit = ::finish) }
    }
}
