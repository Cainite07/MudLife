package cn.mudlife.app.timetable

import android.app.Activity
import android.app.Application
import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.webkit.WebStorage
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.webkit.WebStorageCompat
import androidx.webkit.WebViewFeature
import cn.mudlife.campuscard.SessionStore

/** One user-initiated sign-out, scoped to the university's site in this app's WebView. */
class SchoolAuthResetActivity : ComponentActivity() {
    private val reset by viewModels<SchoolAuthResetModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            text = "正在清除江苏大学网页登录…"
            gravity = Gravity.CENTER
            textSize = 16f
        })
        // WebView cannot cancel site-data deletion once started. Return its actual result.
        val pendingReset = onBackPressedDispatcher.addCallback(this) {}
        reset.result.observe(this) { error ->
            pendingReset.isEnabled = false
            if (error.isEmpty()) {
                setResult(Activity.RESULT_OK)
                finish()
            } else {
                AlertDialog.Builder(this).setTitle("学校登录未重置").setMessage(error)
                    .setPositiveButton("返回") { _, _ -> finish() }.setOnCancelListener { finish() }.show()
            }
        }
        reset.begin()
    }
}

class SchoolAuthResetModel(app: Application) : AndroidViewModel(app) {
    val result = MutableLiveData<String>()
    private var started = false

    fun begin() {
        if (started) return
        started = true
        val app = getApplication<Application>()
        val session = SessionStore(app)
        if (session.pendingCheckout.isNotBlank()) {
            result.value = "校园卡有一笔待核对的充值。请先在充值页核对原订单，再重新认证。"
            return
        }
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)) {
            result.value = "当前的系统 WebView 不支持按学校网站清除登录。请更新 Android 系统 WebView 后再试。"
            return
        }
        try {
            WebStorageCompat.deleteBrowsingDataForSite(WebStorage.getInstance(), "https://ujs.edu.cn") {
                runCatching {
                    session.clearSchoolAuthentication()
                    check(app.getSharedPreferences("timetable_navigation", Application.MODE_PRIVATE)
                        .edit().remove("confirmed_course_page").commit())
                }.onSuccess { result.value = "" }.onFailure { result.value = "学校网页已退出，本机认证清理未完成，请重试。" }
            }
        } catch (_: RuntimeException) {
            result.value = "系统未能开始清理学校网页，请稍后重试。"
        }
    }
}
