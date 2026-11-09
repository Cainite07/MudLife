package cn.mudlife.app.timetable

import android.annotation.SuppressLint
import android.animation.ValueAnimator
import android.app.Activity
import android.app.Dialog
import android.content.res.ColorStateList
import androidx.compose.ui.graphics.toArgb
import cn.mudlife.app.ui.theme.DarkColors
import cn.mudlife.app.ui.theme.LightColors
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.Intent
import android.database.sqlite.SQLiteException
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Message
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ScrollView
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.doOnAttach
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import cn.mudlife.app.R
import cn.mudlife.campuscard.SessionStore
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayInputStream

class TimetableImportActivity : Activity() {
    private lateinit var account: String
    private lateinit var browser: WebView
    private lateinit var pages: FrameLayout
    private lateinit var status: TextView
    private lateinit var readButton: Button
    private lateinit var switchEntry: Button
    private var generation = 0
    private var inspection = 0
    private var preview: Dialog? = null
    private var entryMenu: Dialog? = null
    private var lastPresented: Snapshot? = null
    private var triedGatewayHome = false
    private var continueToCourse = true
    private val teachingLanding = TeachingLanding()
    private var academicBase = TeachingRoutes.DIRECT
    private var initialDirectRequest: String? = null
    private var bookmarkBase: String? = null
    private var autoGatewaySession = false
    private var triedGatewaySession = false
    private var offerGatewaySession = false
    private val entryPrefs by lazy { getSharedPreferences("timetable_navigation", MODE_PRIVATE) }
    private val schoolSession by lazy { SessionStore(this) }
    private val selectedTerm by lazy { intent.getStringExtra(TimetableActivity.EXTRA_TERM) }

    private val reader by lazy { assets.open("ujs-timetable.js").bufferedReader().use { it.readText() } }
    private val landingCheck by lazy { assets.open("ujs-teaching-landing.js").bufferedReader().use { it.readText() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        account = intent.getStringExtra(TimetableActivity.EXTRA_ACCOUNT)?.trim().orEmpty()
        if (account.isBlank()) {
            Toast.makeText(this, "请先登录校园卡，再导入个人课表。", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.setBackgroundColor(surface)
        val bar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), 0, dp(9), 0)
        }
        bar.addView(button("返回") { finish() })
        bar.addView(heading("教务同步"), LinearLayout.LayoutParams(0, -2, 1f))
        val entries = button("其他入口") { showEntries() }
        bar.addView(entries)
        bar.minimumHeight = dp(56)
        root.addView(bar, LinearLayout.LayoutParams(-1, -2))
        val step = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(soft)
            setPadding(dp(16), dp(4), dp(8), dp(4))
        }
        status = label("正在连接学校…", 14f).apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        step.addView(status, LinearLayout.LayoutParams(0, -2, 1f))
        switchEntry = button("改用 WebVPN") {
            if (offerGatewaySession) reuseGatewaySession() else startAt(TeachingRoutes.login(TeachingRoutes.PROXIED))
        }
        step.addView(switchEntry)
        readButton = button("读取课表") { inspect(manual = true) }.apply {
            isEnabled = false
            visibility = View.GONE
        }
        step.addView(readButton)
        root.addView(step)
        pages = FrameLayout(this)
        root.addView(pages, LinearLayout.LayoutParams(-1, 0, 1f))
        browser = addPage()
        setContentView(root)
        fitWindow(window, root)
        startAt(TeachingRoutes.initial(entryPrefs.getString(COURSE_BOOKMARK, null)))
    }

    private fun currentAccount(): Boolean {
        if (schoolSession.timetableOwner == account) return true
        if (!isFinishing) {
            inspection++
            preview?.dismiss()
            Toast.makeText(this, "校园卡账号已变更，请重新打开课表。", Toast.LENGTH_LONG).show()
            finish()
        }
        return false
    }

    override fun onResume() {
        super.onResume()
        if (::account.isInitialized && account.isNotEmpty()) currentAccount()
    }

    private val night by lazy { timetableThemeIsDark(this) }
    private val palette by lazy { if (night) DarkColors else LightColors }
    private val surface get() = palette.Background.toArgb()
    private val soft get() = palette.SurfaceVariant.toArgb()
    private val rule get() = palette.Border.toArgb()
    private val ink get() = palette.TextPrimary.toArgb()
    private val muted get() = palette.TextSecondary.toArgb()
    private val primary get() = palette.Accent.toArgb()

    @Suppress("DEPRECATION")
    private fun fitWindow(window: Window, root: View) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            insets
        }
        root.doOnAttach { attached ->
            WindowCompat.getInsetsController(window, attached).apply {
                isAppearanceLightStatusBars = !night
                isAppearanceLightNavigationBars = !night
            }
            ViewCompat.requestApplyInsets(attached)
        }
    }

    private fun heading(value: String) = label(value, 18f).apply {
        typeface = resources.getFont(R.font.timetable_heading)
    }

    private fun label(value: String, size: Float, medium: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(ink)
        if (medium) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private fun button(label: String, filled: Boolean = false, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 14f
        minWidth = 0
        minimumWidth = 0
        minHeight = dp(48)
        minimumHeight = dp(48)
        stateListAnimator = null
        elevation = 0f
        setTextColor(if (filled) surface else primary)
        setPadding(dp(12), 0, dp(12), 0)
        background = RippleDrawable(ColorStateList.valueOf(androidx.core.graphics.ColorUtils.setAlphaComponent(primary, 40)),
            GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(if (filled) primary else Color.TRANSPARENT)
            }, null)
        setOnClickListener { action() }
    }

    private fun pageDialog(
        title: String,
        footer: ((LinearLayout, Dialog) -> Unit)? = null,
        body: (LinearLayout, Dialog) -> Unit
    ): Dialog {
        val theme = if (night) android.R.style.Theme_Material_NoActionBar else android.R.style.Theme_Material_Light_NoActionBar
        val dialog = Dialog(this, theme)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(surface)
        }
        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(56)
            setPadding(dp(6), 0, dp(18), 0)
            addView(button("返回") { dialog.dismiss() })
            addView(heading(title), LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(header)
        divider(root)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(10), dp(20), dp(24))
        }
        body(content, dialog)
        root.addView(ScrollView(this).apply { addView(content) }, LinearLayout.LayoutParams(-1, 0, 1f))
        if (footer != null) {
            divider(root)
            val actions = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(8), dp(16), dp(8))
            }
            footer(actions, dialog)
            root.addView(actions)
        }
        dialog.setContentView(root)
        dialog.window?.let { window ->
            window.setWindowAnimations(if (ValueAnimator.areAnimatorsEnabled()) R.style.TimetablePageWindowMotion else 0)
            window.setLayout(-1, -1)
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            fitWindow(window, root)
        }
        return dialog
    }

    private fun divider(panel: LinearLayout) {
        panel.addView(View(this).apply { setBackgroundColor(rule) }, LinearLayout.LayoutParams(-1, dp(1)))
    }

    private fun showEntries() {
        if (entryMenu != null || preview != null) return
        var selectedEntry = false
        val menu = pageDialog("学校入口") { panel, dialog ->
            for ((title, url) in listOf(
                "本科教务（直连）" to TeachingRoutes.login(TeachingRoutes.DIRECT),
                "本科教务（WebVPN）" to TeachingRoutes.login(TeachingRoutes.PROXIED),
                "WebVPN 资源站点" to TeachingRoutes.VPN,
                "个人课表" to TeachingRoutes.course(academicBase)
            )) {
                panel.addView(button(title) {
                    selectedEntry = true
                    dialog.dismiss()
                    startAt(url)
                }.apply {
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    setTextColor(ink)
                    textSize = 14f
                    setPadding(0, dp(6), 0, dp(6))
                    minimumHeight = dp(56)
                })
                divider(panel)
            }
        }
        entryMenu = menu
        menu.setOnDismissListener {
            entryMenu = null
            if (!selectedEntry && !isFinishing && !isDestroyed && readButton.isEnabled) inspect()
        }
        menu.show()
    }

    private fun canRead(ready: Boolean) {
        readButton.isEnabled = ready
        readButton.visibility = if (ready) View.VISIBLE else View.GONE
        switchEntry.text = if (offerGatewaySession) "沿用学校登录" else "改用 WebVPN"
        switchEntry.visibility = if (!ready && (academicBase == TeachingRoutes.DIRECT || offerGatewaySession)) View.VISIBLE else View.GONE
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun allowed(uri: Uri): Boolean = uri.scheme == "https" && uri.userInfo == null &&
        uri.port in setOf(-1, 443) && uri.host in TRUSTED_HOSTS

    private fun canonicalCoursePage(value: String?) = TeachingRoutes.bookmark(value)

    private fun addPage(): WebView {
        val page = createBrowser()
        pages.addView(page)
        return page
    }

    private fun startAt(url: String) {
        while (pages.childCount > 1) closePopup()
        generation++
        lastPresented = null
        triedGatewayHome = false
        triedGatewaySession = false
        offerGatewaySession = false
        academicBase = TeachingRoutes.page(Uri.parse(url))?.base ?: TeachingRoutes.PROXIED
        initialDirectRequest = url.takeIf { academicBase == TeachingRoutes.DIRECT }
        bookmarkBase = academicBase.takeIf { canonicalCoursePage(url) != null }
        continueToCourse = url != TeachingRoutes.VPN
        autoGatewaySession = continueToCourse && academicBase != TeachingRoutes.DIRECT
        teachingLanding.reset()
        teachingLanding.observe(Uri.parse(url))
        canRead(false)
        browser.loadUrl(url)
    }

    private fun reuseGatewaySession() {
        autoGatewaySession = false
        triedGatewaySession = true
        offerGatewaySession = false
        initialDirectRequest = null
        canRead(false)
        browser.loadUrl(TeachingRoutes.gatewaySession())
    }

    private fun closePopup() {
        if (pages.childCount <= 1) return
        generation++
        preview?.dismiss()
        val old = browser
        pages.removeView(old)
        browser = pages.getChildAt(pages.childCount - 1) as WebView
        browser.visibility = View.VISIBLE
        old.stopLoading()
        old.destroy()
        canRead(canonicalCoursePage(browser.url) != null)
        status.text = help(browser.url)
    }

    private fun help(url: String?): String {
        val uri = url?.let(Uri::parse)
        if (uri?.path?.endsWith("/cas/login") == true) {
            val service = uri.getQueryParameter("service")?.let(Uri::parse)
            return when {
                service?.host == "webvpn.ujs.edu.cn" && service.path == "/login" ->
                    "WebVPN 正在验证学校身份。"
                uri.host == "webvpn.ujs.edu.cn" || academicBase != TeachingRoutes.DIRECT ->
                    "本科教务正在通过 WebVPN 验证学校身份。"
                else -> "本科教务直连正在验证学校身份。"
            }
        }
        return when {
            canonicalCoursePage(url) != null -> "正在核对个人课表…"
            uri?.host == "webvpn.ujs.edu.cn" && uri.path == "/login" -> "请完成 WebVPN 登录。"
            uri?.path?.contains("/xtgl/login") == true -> "可用「其他入口」重新进入本科教务。"
            uri?.host == "webvpn.ujs.edu.cn" && uri.path == "/" -> "请打开「新教务系统」。"
            else -> "进入本科教务后可读取个人课表。"
        }
    }

    private fun openFromTeachingLanding(view: WebView, uri: Uri, httpNotFound: Boolean = false) {
        if (!teachingLanding.pending) return
        val home = TeachingRoutes.courseFromHome(uri)
        val missing = teachingLanding.missingPageTarget(uri, academicBase)
        val course = missing ?: home ?: return
        val started = generation
        fun isCurrent() = view === browser && started == generation && view.url == uri.toString() &&
            teachingLanding.pending && !isFinishing && !isDestroyed
        fun checkLanding(attempt: Int) {
            view.postDelayed({
                if (!isCurrent()) return@postDelayed
                view.evaluateJavascript("($landingCheck)(${home != null}, ${missing != null}, $httpNotFound)") { matched ->
                    if (!isCurrent()) return@evaluateJavascript
                    if (matched == "true" && teachingLanding.consume()) {
                        status.text = "正在打开个人课表…"
                        view.loadUrl(course)
                    } else if (attempt < 5) checkLanding(attempt + 1)
                }
            }, if (attempt == 0) 350L else 700L)
        }
        checkLanding(0)
    }

    // Some gateway callbacks finish on an empty document. Give the official
    // redirect time to complete, then open its homepage once for this attempt.
    private fun recoverEmptyCallback(view: WebView, uri: Uri) {
        if (triedGatewayHome || uri.host != "webvpn.ujs.edu.cn" || uri.path != "/login" ||
            uri.getQueryParameter("cas_login") != "true") return
        val started = generation
        fun isCurrent() = !isFinishing && !isDestroyed && view === browser && generation == started && !triedGatewayHome
        view.postDelayed({
            if (!isCurrent()) return@postDelayed
            view.evaluateJavascript(
                "!!document.body && !document.body.innerText.trim() && " +
                    "!document.body.querySelector('form,iframe,img,canvas,button,a[href],input:not([type=hidden])')"
            ) { empty ->
                if (empty == "true" && isCurrent()) {
                    triedGatewayHome = true
                    view.loadUrl(TeachingRoutes.VPN)
                }
            }
        }, 1500L)
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun createBrowser() = WebView(this).apply {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportMultipleWindows(true)
        }
        // Use the same default WebView profile as the campus-card and school services.
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
        // A query or dropdown selection can replace the timetable without a WebView navigation.
        // Keep normal page gestures and the explicit read action available.
        setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_UP && this === browser && readButton.isEnabled &&
                canonicalCoursePage(url) != null && preview == null) inspect()
            false
        }
        webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean,
                                        resultMsg: Message): Boolean {
                val origin = view.url?.let(Uri::parse) ?: return false
                val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                if (view !== browser || !isUserGesture || !allowed(origin)) return false
                initialDirectRequest = null
                bookmarkBase = null
                generation++
                preview?.dismiss()
                view.visibility = View.GONE
                canRead(false)
                browser = addPage()
                transport.webView = browser
                resultMsg.sendToTarget()
                return true
            }

            override fun onCloseWindow(window: WebView) {
                if (window === browser) closePopup()
            }
        }
        webViewClient = object : WebViewClient() {
            private var failed = false
            private var mainDocument: String? = null
            private var missingDocument: String? = null
            private var finishedDocument: String? = null
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return false
                if (view === browser && !request.hasGesture() && request.isRedirect && request.method == "GET") {
                    if (TeachingRoutes.isPublicDowngrade(initialDirectRequest, request.url)) {
                        startAt(TeachingRoutes.login(TeachingRoutes.PROXIED))
                        return true
                    }
                    if (autoGatewaySession && TeachingRoutes.isGatewayLogin(request.url)) {
                        reuseGatewaySession()
                        return true
                    }
                    val sso = TeachingRoutes.expiredBookmarkLogin(request.url, bookmarkBase)
                    if (sso != null) {
                        bookmarkBase = null
                        if (initialDirectRequest != null) initialDirectRequest = sso
                        view.loadUrl(sso)
                        return true
                    }
                }
                if (!allowed(request.url)) {
                    if (view === browser) status.text = "此入口无法打开。请从「其他入口」进 WebVPN，选「新教务系统」。"
                    return true
                }
                return false
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (request.isForMainFrame && !allowed(request.url)) {
                    return WebResourceResponse("text/plain", "UTF-8", 403, "Forbidden",
                        emptyMap(), ByteArrayInputStream(ByteArray(0)))
                }
                return null
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                failed = false
                mainDocument = url
                missingDocument = null
                finishedDocument = null
                val uri = Uri.parse(url)
                if (url != "about:blank" && !allowed(uri)) {
                    view.stopLoading()
                    if (view === browser && TeachingRoutes.isPublicDowngrade(initialDirectRequest, uri)) {
                        startAt(TeachingRoutes.login(TeachingRoutes.PROXIED))
                        return
                    }
                    reportFailure(view)
                    return
                }
                if (view !== browser) return
                offerGatewaySession = false
                if (uri.path?.endsWith("/cas/login") == true) autoGatewaySession = false
                if (url != initialDirectRequest) initialDirectRequest = null
                if (canonicalCoursePage(url) == null) bookmarkBase = null
                if (uri.host == "webvpn.ujs.edu.cn" && academicBase == TeachingRoutes.DIRECT) academicBase = TeachingRoutes.PROXIED
                TeachingRoutes.page(uri)?.takeIf { it.isAcademic }?.let { academicBase = it.base }
                teachingLanding.observe(uri)
                generation++
                preview?.dismiss()
                canRead(false)
                status.text = when {
                    uri.host == "pass.ujs.edu.cn" -> "正在连接学校统一身份认证…"
                    canonicalCoursePage(url) != null -> "正在打开个人课表…"
                    uri.path.orEmpty().endsWith("/sso/jziotlogin") ->
                        if (academicBase == TeachingRoutes.DIRECT) "正在直连本科教务…" else "正在通过 WebVPN 进入本科教务…"
                    uri.host == "webvpn.ujs.edu.cn" -> "正在连接学校 WebVPN…"
                    else -> "正在打开校方页面…"
                }
            }

            override fun onPageCommitVisible(view: WebView, url: String) {
                if (view !== browser || view.url != url) return
                autoGatewaySession = false
                if (url == initialDirectRequest) initialDirectRequest = null
                if (canonicalCoursePage(url) != null) bookmarkBase = null
            }

            override fun onPageFinished(view: WebView, url: String) {
                val uri = Uri.parse(url)
                if (view !== browser || view.url != url || !allowed(uri) || failed) return
                finishedDocument = url
                if (missingDocument == url) {
                    openFromTeachingLanding(view, uri, httpNotFound = true)
                    return
                }
                autoGatewaySession = false
                initialDirectRequest = null
                offerGatewaySession = !triedGatewaySession && TeachingRoutes.isGatewayLogin(uri)
                if (uri.host == "webvpn.ujs.edu.cn" && uri.path == "/" && continueToCourse) {
                    val started = generation
                    view.evaluateJavascript("!!document.body && document.body.innerText.includes('新教务系统')") { catalog ->
                        if (catalog == "true" && view === browser && started == generation && !isFinishing && !isDestroyed) {
                            continueToCourse = false
                            view.loadUrl(TeachingRoutes.login(academicBase))
                        }
                    }
                }
                canRead(canonicalCoursePage(url) != null)
                status.text = help(url)
                recoverEmptyCallback(view, uri)
                openFromTeachingLanding(view, uri)
                if (canonicalCoursePage(url) != null && preview == null) inspect()
            }

            private fun reportFailure(view: WebView) {
                failed = true
                if (view !== browser) return
                generation++
                inspection++
                preview?.dismiss()
                canRead(false)
                status.text = if (academicBase == TeachingRoutes.DIRECT) "直连未能打开，可改用 WebVPN。"
                    else "WebVPN 页面未能打开，可从「其他入口」打开资源站点。"
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (!request.isForMainFrame) return
                if (view === browser && (TeachingRoutes.isConnectionFailure(initialDirectRequest, request.url, error.errorCode) ||
                    TeachingRoutes.isPublicDowngrade(initialDirectRequest, request.url))) {
                    startAt(TeachingRoutes.login(TeachingRoutes.PROXIED))
                } else {
                    reportFailure(view)
                }
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (!request.isForMainFrame || view !== browser) return
                val url = request.url.toString()
                if (url != mainDocument && url != view.url) return
                if (response.statusCode == 404 && teachingLanding.missingPageTarget(request.url, academicBase) != null) {
                    missingDocument = url
                    if (finishedDocument == url) openFromTeachingLanding(view, request.url, httpNotFound = true)
                } else {
                    reportFailure(view)
                }
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
                if (view === browser && error.url == initialDirectRequest) initialDirectRequest = null
                if (error.url == view.url) reportFailure(view)
            }
        }
    }

    private data class Snapshot(val term: String, val records: List<ImportedCourse>,
                                val pending: Int, val repeated: Int, val page: String?)
    private data class ConfirmedImport(val snapshot: Snapshot, val plan: TermImportPlan)

    private fun snapshot(result: JSONObject): Snapshot {
        val term = result.getString("term").trim()
        val rows = result.getJSONArray("rows")
        val records = (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            ImportedCourse(TimetableCourse(
                name = row.getString("name"), weekday = row.getInt("weekday"),
                startPeriod = row.getInt("startPeriod"), endPeriod = row.getInt("endPeriod"),
                weeks = row.getString("weeks"), place = row.getString("place"),
                teacher = row.getString("teacher")
            ).normalized())
        }.distinct()
        require(term.isNotBlank() && records.isNotEmpty())
        return Snapshot(term, records, result.getInt("pendingCount"), rows.length() - records.size,
            canonicalCoursePage(result.optString("coursePage")))
    }

    private fun inspect(manual: Boolean = false, confirmed: ConfirmedImport? = null) {
        val view = browser
        val url = view.url
        val openPage = canonicalCoursePage(url) ?: return
        if (!readButton.isEnabled) return
        val request = ++inspection
        val atGeneration = generation
        val script = "JSON.stringify(($reader)(${JSONObject.quote(openPage)}))"
        fun current() = !isFinishing && !isDestroyed && request == inspection &&
            atGeneration == generation && view === browser && url == view.url
        var previous: Snapshot? = null
        if (manual || confirmed != null) status.text = "正在核对课表…"
        fun sample(attempt: Int) {
            if (!current()) return
            view.evaluateJavascript(script) { raw ->
                if (!current()) return@evaluateJavascript
                fun retry() {
                    view.postDelayed({ sample(attempt + 1) }, 550L)
                }
                try {
                    val value = JSONTokener(raw ?: "null").nextValue() as? String ?: throw JSONException("empty result")
                    val result = JSONObject(value)
                    if (result.has("error")) {
                        previous = null
                        status.text = result.getString("error")
                        if (result.optBoolean("waiting") && attempt < 16) retry()
                        return@evaluateJavascript
                    }
                    val sourceError = TeachingRoutes.importSourceError(openPage, result.optString("coursePage"),
                        account, result.optString("student"))
                    if (sourceError != null) {
                        status.text = sourceError
                        return@evaluateJavascript
                    }
                    val row = snapshot(result)
                    if (confirmed != null && confirmed.snapshot == row) {
                        save(confirmed)
                    } else if (previous != row) {
                        previous = row
                        if (attempt < 16) retry() else status.text = "课表仍在更新，请稍后点“读取课表”。"
                    } else if (manual || confirmed != null || row != lastPresented) {
                        if (preview != null || entryMenu != null) return@evaluateJavascript
                        lastPresented = row
                        showPreview(row)
                    } else {
                        status.text = "可切换学期，也可读取当前课表。"
                    }
                } catch (_: JSONException) {
                    status.text = "读取不完整，请重新打开个人课表。"
                } catch (_: IllegalArgumentException) {
                    status.text = "课程字段不完整，请在学校页面核对课表。"
                }
            }
        }
        view.postDelayed({ sample(0) }, if (manual || confirmed != null) 0L else 350L)
    }

    private fun showPreview(row: Snapshot) {
        val (term, records, pending, repeated) = row
        val plan = try {
            TimetableStore(this, account).use { it.importPlan(term, selectedTerm) }
        } catch (e: IllegalArgumentException) {
            status.text = e.message
            return
        } catch (_: SQLiteException) {
            status.text = "无法读取本地学期，请重试。"
            return
        }
        status.text = "已读取 ${records.size} 条上课安排，等待确认。"
        val dialog = pageDialog("确认导入个人课表", footer = { actions, dialog ->
            actions.addView(button("选择其他学期") { dialog.dismiss() }, LinearLayout.LayoutParams(0, -2, 1f))
            actions.addView(button("确认导入", true) { dialog.dismiss(); inspect(confirmed = ConfirmedImport(row, plan)) },
                LinearLayout.LayoutParams(0, -2, 1f))
        }) { panel, _ ->
            panel.addView(label("导入到「${plan.destination}」", 16f, true).apply { setPadding(0, dp(8), 0, dp(4)) })
            if (term != plan.destination) panel.addView(label("教务学期 · $term", 13f).apply { setTextColor(muted) })
            val summary = buildString {
                append("${records.size} 条上课安排 · 保留本地修改")
                if (pending != 0) append(" · 另有 ${pending} 条待筛选")
                if (repeated != 0) append(" · 已合并 ${repeated} 条重复安排")
            }
            panel.addView(label(summary, 13f).apply { setTextColor(muted); setPadding(0, 0, 0, dp(16)) })
            divider(panel)
            for (record in records.sortedWith(compareBy({ it.course.weekday }, { it.course.startPeriod }))) {
                val c = record.course
                panel.addView(label(c.name, 14f).apply { typeface = Typeface.SANS_SERIF; setPadding(0, dp(12), 0, 0) })
                val periods = if (c.startPeriod == c.endPeriod) "${c.startPeriod}" else "${c.startPeriod}–${c.endPeriod}"
                val day = "周${"一二三四五六日"[c.weekday - 1]} · 第${periods}节"
                panel.addView(label(listOf(day, c.weeks, c.place).filter(String::isNotBlank).joinToString(" · "), 13f)
                    .apply { setTextColor(muted); setPadding(0, dp(3), 0, dp(12)) })
                divider(panel)
            }
        }
        dialog.setOnDismissListener {
            preview = null
            if (!isFinishing && readButton.isEnabled && status.text.toString().endsWith("等待确认。"))
                status.text = "可切换学期后重新读取。"
        }
        preview = dialog
        dialog.show()
    }

    private fun save(confirmed: ConfirmedImport) {
        if (!currentAccount()) return
        val (term, records, _, _, page) = confirmed.snapshot
        try {
            val destination = TimetableStore(this, account).use { it.importTerm(term, records, selectedTerm, confirmed.plan) }
            if (page != null) entryPrefs.edit().putString(COURSE_BOOKMARK, page).apply()
            setResult(RESULT_OK, Intent().putExtra(TimetableActivity.EXTRA_TERM, destination))
            Toast.makeText(this, "已导入 ${records.size} 条上课安排", Toast.LENGTH_SHORT).show()
            finish()
        } catch (_: SQLiteException) {
            status.text = "课表保存失败，请检查手机存储空间后重试。"
        } catch (e: IllegalArgumentException) {
            status.text = e.message ?: "课程数据未能通过校验，请重新读取。"
        }
    }

    @Deprecated("For platform back navigation")
    override fun onBackPressed() = goBack()

    private fun goBack() {
        when {
            !::browser.isInitialized -> finish()
            browser.canGoBack() -> browser.goBack()
            pages.childCount > 1 -> closePopup()
            else -> finish()
        }
    }

    override fun onDestroy() {
        entryMenu?.setOnDismissListener(null)
        entryMenu?.dismiss()
        preview?.dismiss()
        if (::pages.isInitialized) {
            while (pages.childCount > 0) {
                val page = pages.getChildAt(pages.childCount - 1) as WebView
                pages.removeView(page)
                page.stopLoading()
                page.destroy()
            }
        }
        super.onDestroy()
    }

    companion object {
        private const val COURSE_BOOKMARK = "confirmed_course_page"
        private val COURSE_HOSTS = setOf("jwxt.ujs.edu.cn", "webvpn.ujs.edu.cn")
        private val TRUSTED_HOSTS = COURSE_HOSTS + setOf("jwc.ujs.edu.cn", "pass.ujs.edu.cn")
    }
}
