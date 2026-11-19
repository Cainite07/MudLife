package cn.mudlife.app.timetable

import android.net.Uri

/** The registrar's application behind the university's WebVPN. */
internal object TeachingRoutes {
    const val VPN = "https://webvpn.ujs.edu.cn/"
    const val PROXIED = VPN + "http/77726476706e69737468656265737421fae0598869256243300d8db9d6562d"
    private const val SSO_PATH = "/sso/jziotlogin"
    private const val COURSE_PATH = "/kbcx/xskbcx_cxXskbcxIndex.html"
    private const val EXAM_PATH = "/kwgl/kscx_cxXsksxxIndex.html"
    private const val COURSE_MENU = "$COURSE_PATH?gnmkdm=N2151&layout=default"
    // A saved school menu can have a different WebVPN protocol or port prefix.
    private val proxyPath = Regex("""^(/https?(?:-[0-9]+)?/[a-fA-F0-9]{16,256})(/.*)$""")

    fun login(base: String) = base + SSO_PATH
    fun course(base: String) = base + COURSE_MENU
    fun exams(base: String) = "$base$EXAM_PATH?gnmkdm=N358105&layout=default"

    fun examPage(value: String?): String? {
        val entry = value?.let { page(Uri.parse(it)) } ?: return null
        return exams(entry.base).takeIf { entry.isAcademic && entry.path == EXAM_PATH }
    }
    fun isGatewayLogin(uri: Uri) = uri.toString() == VPN + "login"

    fun isGatewayCallback(uri: Uri): Boolean = isGatewayOrigin(uri) && when (uri.path) {
        "/login" -> uri.getQueryParameter("cas_login") == "true"
        "/wengine-vpn-token-login" -> !uri.getQueryParameter("token").isNullOrBlank()
        else -> false
    }

    fun isGatewayPortal(uri: Uri): Boolean = isGatewayOrigin(uri) && uri.path in setOf("/", "/m/portal")

    private fun isGatewayOrigin(uri: Uri) =
        uri.scheme == "https" && uri.host == "webvpn.ujs.edu.cn" && uri.userInfo == null && uri.port in setOf(-1, 443)

    fun schoolGatewayLogin(): String = Uri.parse("https://pass.ujs.edu.cn/cas/login").buildUpon()
        .appendQueryParameter("service", VPN + "login?cas_login=true")
        .build().toString()

    enum class AuthStep { WEB_VPN, TEACHING }

    fun authStep(uri: Uri): AuthStep? {
        if (isGatewayLogin(uri)) return AuthStep.WEB_VPN
        val currentPage = page(uri)
        if (currentPage?.isAcademic == true && currentPage.isLogin) return AuthStep.TEACHING
        if (uri.path?.endsWith("/cas/login") != true) return null
        val service = uri.getQueryParameter("service")?.let(Uri::parse) ?: return null
        if (uri.scheme == "https" && uri.host == "pass.ujs.edu.cn" &&
            isGatewayCallback(service)) return AuthStep.WEB_VPN
        return AuthStep.TEACHING.takeIf { currentPage != null && page(service)?.isAcademic == true }
    }

    data class Page(val base: String, val path: String) {
        val isLogin get() = path.startsWith("/xtgl/login")
        val isTeachingHomeCandidate get() = path.startsWith("/xtgl/") && !path.contains("login", ignoreCase = true)

        val isAcademic get() = base.substringAfterLast('/').equals(PROXIED.substringAfterLast('/'), ignoreCase = true)
    }

    fun page(uri: Uri): Page? {
        if (uri.scheme != "https" || uri.userInfo != null || uri.port !in setOf(-1, 443)) return null
        val path = uri.encodedPath ?: return null
        return when (uri.host) {
            "webvpn.ujs.edu.cn" -> proxyPath.matchEntire(path)?.let { Page(VPN.dropLast(1) + it.groupValues[1], it.groupValues[2]) }
            else -> null
        }
    }

    fun isTeachingCallback(uri: Uri): Boolean {
        val entry = page(uri) ?: return false
        return entry.isAcademic && entry.path == SSO_PATH && !uri.getQueryParameter("ticket").isNullOrBlank()
    }

    fun bookmark(value: String?): String? {
        val page = value?.let { page(Uri.parse(it)) } ?: return null
        return if (page.isAcademic && page.path == COURSE_PATH) course(page.base) else null
    }

    fun importSourceError(openPage: String, reportedPage: String, owner: String, headerStudent: String): String? {
        val trusted = bookmark(openPage)
        if (trusted == null || bookmark(reportedPage) != trusted) return "请在当前教务入口重新打开个人课表。"
        if (owner.isBlank() || headerStudent != owner) return "教务课表的学号与当前校园卡不一致，请切换到同一账号后再读取。"
        return null
    }

    fun initial(saved: String?) = bookmark(saved) ?: login(PROXIED)

    fun courseFromHome(uri: Uri): String? =
        page(uri)?.takeIf { it.isTeachingHomeCandidate && it.isAcademic }?.let { course(it.base) }

    fun expiredBookmarkLogin(uri: Uri, expectedBase: String?): String? =
        page(uri)?.takeIf { it.isLogin && it.base == expectedBase }?.let { login(it.base) }

}

/** Tracks one teaching-system visit, including authentication that returns through WebVPN. */
internal class TeachingLanding {
    var pending = true
        private set
    private var enteredTeachingLogin = false
    private var authProxyBase: String? = null

    fun reset() {
        pending = true
        enteredTeachingLogin = false
        authProxyBase = null
    }

    fun observe(uri: Uri) {
        if (!pending || !isSchoolOrigin(uri)) return
        if (uri.host == "webvpn.ujs.edu.cn" && uri.path == "/login") {
            enteredTeachingLogin = false
            authProxyBase = null
            return
        }
        val page = TeachingRoutes.page(uri)
        if (uri.path?.endsWith("/cas/login") == true) {
            val service = uri.getQueryParameter("service")?.let(Uri::parse) ?: return
            enteredTeachingLogin = TeachingRoutes.page(service)?.isAcademic == true
            authProxyBase = page?.base.takeIf { enteredTeachingLogin }
        } else if (page?.isAcademic == true && (page.path == "/sso/jziotlogin" || page.isLogin)) {
            enteredTeachingLogin = true
        }
    }

    fun missingPageTarget(uri: Uri, academicBase: String): String? {
        if (!pending || !enteredTeachingLogin || !isSchoolOrigin(uri) || TeachingRoutes.bookmark(uri.toString()) != null) return null
        val page = TeachingRoutes.page(uri)
        if (uri.path?.endsWith("/cas/login") == true || uri.path == "/login" || page?.isLogin == true) return null
        if (page?.path == "/sso/jziotlogin" && uri.encodedQuery == null) return null
        if (page != null && !page.isAcademic && page.base != authProxyBase) return null
        return TeachingRoutes.course(if (page?.isAcademic == true) page.base else academicBase)
    }

    fun consume(): Boolean {
        if (!pending) return false
        pending = false
        return true
    }

    private fun isSchoolOrigin(uri: Uri) = uri.scheme == "https" && uri.userInfo == null &&
        uri.port in setOf(-1, 443) && uri.host in setOf("webvpn.ujs.edu.cn", "pass.ujs.edu.cn")
}
