package cn.mudlife.app

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.content.res.ColorStateList
import com.google.android.material.button.MaterialButton
import android.graphics.drawable.ColorDrawable
import android.net.http.SslError
import android.os.Bundle
import android.os.Message
import android.view.Gravity
import android.view.View
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import cn.mudlife.app.campuscard.CampusCardNavigation
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicLong

/** Official school pages own authentication, account data and payment-code refresh. */
class CampusCardActivity : ComponentActivity() {
    private lateinit var webContainer: FrameLayout
    private lateinit var cover: LinearLayout
    private lateinit var coverMessage: TextView
    private lateinit var progress: ProgressBar
    private lateinit var serviceLabel: TextView
    private var browser: WebView? = null
    private var foreground = false
    private var loading = true
    private var pageError: String? = null
    private val navigationGeneration = AtomicLong()
    private var documentGeneration = 0L
    private var documentUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setBackgroundDrawable(ColorDrawable(Color.WHITE))
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        buildLayout()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goBackOrClose()
        })
        // Do not restore URLs, form bodies, login tickets or rendered payment codes.
        navigateTo(CampusCardNavigation.HOME_URL)
    }

    private fun buildLayout() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(244, 238, 221))
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val padding = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime()
            )
            view.setPadding(padding.left, padding.top, padding.right, padding.bottom)
            insets
        }
        val toolbar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), 0, dp(6), 0)
        }
        toolbar.addView(button("返回") { goBackOrClose() })
        toolbar.addView(TextView(this).apply {
            text = "江苏大学一卡通"
            textSize = 18f
            setTextColor(Color.rgb(30, 41, 59))
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(0, dp(56), 1f))
        toolbar.addView(button("关闭") { finish() })
        root.addView(toolbar, LinearLayout.LayoutParams(-1, dp(56)))

        val shortcuts = LinearLayout(this).apply {
            setPadding(dp(8), 0, dp(8), 0)
        }
        listOf(
            "余额 / 卡包" to CampusCardNavigation.WALLET_URL,
            "充值" to CampusCardNavigation.RECHARGE_URL,
            "付款码" to CampusCardNavigation.PAYMENT_URL
        ).forEach { (label, url) ->
            shortcuts.addView(button(label) { navigateTo(url) },
                LinearLayout.LayoutParams(0, dp(48), 1f))
        }
        root.addView(shortcuts)
        serviceLabel = TextView(this).apply {
            text = "学校官方服务 · ykt.ujs.edu.cn"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(100, 116, 139))
            setPadding(0, dp(4), 0, dp(8))
        }
        root.addView(serviceLabel)

        webContainer = FrameLayout(this)
        root.addView(webContainer, LinearLayout.LayoutParams(-1, 0, 1f))
        cover = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(24), dp(28), dp(24))
            setBackgroundColor(Color.WHITE)
            isClickable = true
        }
        progress = ProgressBar(this)
        cover.addView(progress)
        coverMessage = TextView(this).apply {
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(51, 65, 85))
            setPadding(0, dp(18), 0, dp(18))
        }
        cover.addView(coverMessage)
        cover.addView(button("返回学校首页") { navigateTo(CampusCardNavigation.HOME_URL) })
        webContainer.addView(cover, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        ViewCompat.requestApplyInsets(root)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createBrowser(): WebView = WebView(this).apply {
        WebView.setWebContentsDebuggingEnabled(false)
        setBackgroundColor(Color.WHITE)
        visibility = View.INVISIBLE
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            @Suppress("DEPRECATION")
            saveFormData = false
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            safeBrowsingEnabled = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mediaPlaybackRequiresUserGesture = true
            setGeolocationEnabled(false)
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
        webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) = request.deny()

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?, callback: GeolocationPermissions.Callback?
            ) {
                callback?.invoke(origin, false, false)
            }
        }
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                // This preflight includes direct main-document POSTs, which do not
                // reach shouldOverrideUrlLoading. Resources and XHR stay untouched.
                // Android reports only the initial URL here, not every redirect;
                // navigation callbacks below still check the resulting document.
                if (!request.isForMainFrame || CampusCardNavigation.decide(request.url.toString()) ==
                    CampusCardNavigation.Decision.Internal) return null
                val generation = navigationGeneration.get()
                view.post {
                    if (view === browser && !isFinishing && !isDestroyed &&
                        generation == navigationGeneration.get()) {
                        showBlockedNavigation(request.url.toString())
                    }
                }
                return WebResourceResponse(
                    "text/html", "UTF-8", 403, "Blocked", mapOf("Cache-Control" to "no-store"),
                    ByteArrayInputStream("<!doctype html><meta charset=utf-8><p>此地址暂不可在此打开。</p>".toByteArray(Charsets.UTF_8))
                )
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val blocked = CampusCardNavigation.decide(request.url.toString()) ==
                    CampusCardNavigation.Decision.Blocked
                if (blocked && request.isForMainFrame) showBlockedNavigation(request.url.toString())
                return blocked
            }

            @Deprecated("Compatibility callback for older WebView providers")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                val blocked = CampusCardNavigation.decide(url) == CampusCardNavigation.Decision.Blocked
                if (blocked) showBlockedNavigation(url)
                return blocked
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                if (view !== browser) return
                if (CampusCardNavigation.decide(url) == CampusCardNavigation.Decision.Blocked) {
                    view.stopLoading()
                    showBlockedNavigation(url)
                    return
                }
                navigationGeneration.incrementAndGet()
                documentGeneration++
                documentUrl = url
                serviceLabel.text = if (android.net.Uri.parse(url).host.equals("pass.ujs.edu.cn", true)) {
                    "学校统一身份认证 · pass.ujs.edu.cn"
                } else {
                    "学校官方服务 · ykt.ujs.edu.cn"
                }
                pageError = null
                loading = true
                updateCover()
            }

            override fun onPageCommitVisible(view: WebView, url: String?) {
                // The old document can no longer be drawn, including a stale payment code.
                if (view !== browser || pageError != null || isDestroyed || isFinishing ||
                    (view.url != url && documentUrl != url)) return
                if (CampusCardNavigation.decide(url) == CampusCardNavigation.Decision.Internal) {
                    loading = false
                    updateCover()
                }
            }

            override fun onPageFinished(view: WebView, url: String?) {
                if (view !== browser || pageError != null || (view.url != url && documentUrl != url) ||
                    CampusCardNavigation.decide(url) != CampusCardNavigation.Decision.Internal) return
                val generation = documentGeneration
                // Some redirect/SPA sequences do not produce a matching commit callback.
                // Reveal only once the current document has a fresh drawable frame.
                view.postVisualStateCallback(generation, object : WebView.VisualStateCallback() {
                    override fun onComplete(requestId: Long) {
                        if (view === browser && !isDestroyed && !isFinishing &&
                            requestId == documentGeneration && pageError == null &&
                            CampusCardNavigation.decide(view.url) == CampusCardNavigation.Decision.Internal) {
                            loading = false
                            updateCover()
                        }
                    }
                })
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                if (view === browser && CampusCardNavigation.decide(url) == CampusCardNavigation.Decision.Internal) {
                    // Also invalidate queued notices after a same-document SPA move.
                    navigationGeneration.incrementAndGet()
                }
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
                showFailure("无法确认学校网页的安全连接。请稍后从学校首页重新进入。")
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (view === browser && request.isForMainFrame &&
                    CampusCardNavigation.decide(request.url.toString()) == CampusCardNavigation.Decision.Internal) {
                    showFailure("学校网页未能打开，请检查网络后返回学校首页。")
                }
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                // A local blocked response already has the specific navigation notice.
                if (view === browser && request.isForMainFrame &&
                    CampusCardNavigation.decide(request.url.toString()) == CampusCardNavigation.Decision.Internal) {
                    showFailure("学校服务暂时无法打开，请稍后返回学校首页。")
                }
            }

            override fun onFormResubmission(view: WebView, dontResend: Message, resend: Message) {
                dontResend.sendToTarget()
                showFailure("为避免重复提交，已停止重新发送表单。请返回学校首页查看账户或订单状态。")
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                if (browser === view) browser = null
                webContainer.removeView(view)
                view.destroy()
                showFailure("学校网页已停止运行，请返回学校首页重新打开。")
                return true
            }
        }
        setDownloadListener { _, _, _, _, _ ->
            showFailure("此页面正在请求下载文件，本版暂不支持。请通过学校官方入口下载。")
        }
        webContainer.addView(this, 0, FrameLayout.LayoutParams(-1, -1))
    }

    private fun navigateTo(url: String) {
        if (CampusCardNavigation.decide(url) != CampusCardNavigation.Decision.Internal) {
            showBlockedNavigation(url)
            return
        }
        val view = browser ?: createBrowser().also { browser = it }
        navigationGeneration.incrementAndGet()
        documentGeneration++
        documentUrl = url
        pageError = null
        loading = true
        updateCover()
        // These callers use fixed official GET pages; never reload a form submission.
        view.stopLoading()
        view.loadUrl(url)
    }

    private fun goBackOrClose() {
        val view = browser
        if (pageError != null) {
            navigateTo(CampusCardNavigation.HOME_URL)
        } else if (view != null && view.canGoBack()) {
            navigationGeneration.incrementAndGet()
            // Same-document SPA history may not emit onPageCommitVisible. A real
            // document navigation is concealed by onPageStarted instead.
            view.goBack()
        } else {
            finish()
        }
    }

    private fun showBlockedNavigation(url: String?) {
        val destination = runCatching { java.net.URI(url.orEmpty()) }.getOrNull()
        // Show only the destination origin; never expose tickets or account parameters.
        val origin = destination?.host?.let { host ->
            "${destination.scheme}://$host" + if (destination.port != -1) ":${destination.port}" else ""
        } ?: (destination?.scheme ?: "未知页面类型")
        showFailure("暂时无法在应用内打开这个登录或服务页面（$origin）。请返回学校首页后重试。")
    }

    private fun showFailure(message: String) {
        pageError = message
        loading = false
        updateCover()
    }

    private fun updateCover() {
        val concealed = !foreground || loading || pageError != null
        // Keep rendering beneath the opaque cover while foregrounded; making a
        // loading WebView invisible can postpone its visual completion callback.
        browser?.visibility = if (foreground) View.VISIBLE else View.INVISIBLE
        browser?.importantForAccessibility = if (concealed) {
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        } else {
            View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        }
        cover.visibility = if (concealed) View.VISIBLE else View.GONE
        progress.visibility = if (foreground && loading && pageError == null) View.VISIBLE else View.GONE
        coverMessage.text = when {
            !foreground -> "学校服务已暂停显示"
            pageError != null -> pageError
            else -> "正在打开学校服务…"
        }
    }

    override fun onPause() {
        foreground = false
        updateCover()
        browser?.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        foreground = true
        browser?.onResume()
        val resumeUrl = CampusCardNavigation.resumeDestination(browser?.url)
        if (resumeUrl != null) {
            // Retain verified selection parameters on a new GET, without tickets or
            // tokens. Ambiguous selections and card details return to the wallet.
            navigateTo(resumeUrl)
        } else {
            updateCover()
        }
    }

    override fun onDestroy() {
        val view = browser
        browser = null
        navigationGeneration.incrementAndGet()
        documentGeneration++
        if (view != null) {
            webContainer.removeView(view)
            view.stopLoading()
            view.destroy()
        }
        super.onDestroy()
    }

    private fun button(label: String, action: () -> Unit) = MaterialButton(this).apply {
        cornerRadius = dp(14)
        backgroundTintList = ColorStateList.valueOf(Color.rgb(239, 232, 216))
        setTextColor(Color.rgb(66, 77, 52))
        text = label
        textSize = 13f
        isAllCaps = false
        minWidth = 0
        minimumWidth = 0
        setPadding(dp(10), 0, dp(10), 0)
        setOnClickListener { action() }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
