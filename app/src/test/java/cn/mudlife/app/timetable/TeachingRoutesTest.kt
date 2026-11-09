package cn.mudlife.app.timetable

import android.net.Uri
import android.webkit.WebViewClient
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], manifest = Config.NONE)
class TeachingRoutesTest {
    private val direct = TeachingRoutes.DIRECT
    private val proxied = TeachingRoutes.PROXIED

    @Test fun firstEntryAndExistingBookmarks() {
        assertEquals("https://jwxt.ujs.edu.cn/sso/jziotlogin", TeachingRoutes.initial(null))
        val saved = TeachingRoutes.course(proxied)
        assertEquals(saved, TeachingRoutes.initial(saved + "&term=old"))
        assertEquals(TeachingRoutes.login(direct), TeachingRoutes.initial(TeachingRoutes.login(proxied)))
    }

    @Test fun importsOnlyFromTheOpenAcademicRouteForTheCurrentStudent() {
        val alternate = proxied.replace("/http/", "/https-8443/")
        val unknown = TeachingRoutes.course(TeachingRoutes.VPN + "http/1234567890abcdef")
        val directPage = TeachingRoutes.course(direct)
        assertNull(TeachingRoutes.bookmark(unknown))
        assertEquals(TeachingRoutes.login(direct), TeachingRoutes.initial(unknown))
        for (base in listOf(direct, proxied, alternate)) {
            val page = TeachingRoutes.course(base)
            assertNull(TeachingRoutes.importSourceError(page, page + "&term=1", "student-A", "student-A"))
            assertNotNull(TeachingRoutes.importSourceError(page, page, "student-A", "student-B"))
            assertNotNull(TeachingRoutes.importSourceError(page, page, "", ""))
        }
        assertNotNull(TeachingRoutes.importSourceError(directPage, TeachingRoutes.course(proxied), "A", "A"))
        assertNotNull(TeachingRoutes.importSourceError(directPage, unknown, "A", "A"))
        assertNotNull(TeachingRoutes.importSourceError(unknown, unknown, "A", "A"))
    }

    @Test fun courseFollowsConfirmedHome() {
        fun from(base: String) =
            TeachingRoutes.courseFromHome(Uri.parse("$base/xtgl/index_initMenu.html"))
        assertEquals(TeachingRoutes.course(direct), from(direct))
        assertEquals(TeachingRoutes.course(proxied), from(proxied))
        val alternatePrefix = proxied.replace("/http/", "/https-8443/")
        assertEquals(TeachingRoutes.course(alternatePrefix), from(alternatePrefix))
        assertNull(from(TeachingRoutes.VPN + "http/1234567890abcdef"))
        assertNull(TeachingRoutes.courseFromHome(Uri.parse("$direct/xtgl/login_slogin.html")))
    }

    @Test fun expiredCourseReentersSameSystemOnly() {
        fun next(base: String, previous: String?) =
            TeachingRoutes.expiredBookmarkLogin(Uri.parse("$base/xtgl/login_slogin.html"), previous)
        assertEquals(TeachingRoutes.login(direct), next(direct, direct))
        assertEquals(TeachingRoutes.login(proxied), next(proxied, proxied))
        assertNull(next(proxied, direct))
        assertNull(next(direct, null))
    }

    @Test fun fallbackOnlyForUnrenderedDirectEntry() {
        val entry = TeachingRoutes.login(direct)
        val http = Uri.parse(entry.replace("https:", "http:"))
        assertTrue(TeachingRoutes.isPublicDowngrade(entry, http))
        assertFalse(TeachingRoutes.isPublicDowngrade(entry, http.buildUpon().appendQueryParameter("next", "menu").build()))
        assertFalse(TeachingRoutes.isPublicDowngrade(entry, http.buildUpon().fragment("menu").build()))
        assertFalse(TeachingRoutes.isPublicDowngrade(null, http))
        val request = Uri.parse(entry)
        assertTrue(TeachingRoutes.isConnectionFailure(entry, request, WebViewClient.ERROR_CONNECT))
        assertFalse(TeachingRoutes.isConnectionFailure(null, request, WebViewClient.ERROR_TIMEOUT))
        assertFalse(TeachingRoutes.isConnectionFailure(entry, Uri.parse("https://pass.ujs.edu.cn/cas/login"), WebViewClient.ERROR_TIMEOUT))
        assertFalse(TeachingRoutes.isConnectionFailure(entry, request, WebViewClient.ERROR_FAILED_SSL_HANDSHAKE))
    }

    @Test fun silentSessionIsForTheGatewayLoginOnly() {
        assertTrue(TeachingRoutes.isGatewayLogin(Uri.parse(TeachingRoutes.VPN + "login")))
        assertFalse(TeachingRoutes.isGatewayLogin(Uri.parse(TeachingRoutes.VPN)))
        assertFalse(TeachingRoutes.isGatewayLogin(Uri.parse(TeachingRoutes.VPN + "login?cas_login=true")))
        assertFalse(TeachingRoutes.isGatewayLogin(Uri.parse("$proxied/cas/login")))
        val gateway = Uri.parse(TeachingRoutes.gatewaySession())
        assertEquals("https://webvpn.ujs.edu.cn/login?cas_login=true", gateway.getQueryParameter("service"))
        assertEquals("true", gateway.getQueryParameter("gateway"))
    }

    private fun casPage(casBase: String, service: String): Uri =
        Uri.parse("$casBase/cas/login").buildUpon().appendQueryParameter("service", service).build()

    @Test fun directLoginRecoversOnlyOnce() {
        val landing = TeachingLanding()
        val signIn = casPage("https://pass.ujs.edu.cn", TeachingRoutes.login(direct))
        val missing = Uri.parse("$direct/error/404")
        assertNull(landing.missingPageTarget(missing, direct))
        landing.observe(signIn)
        assertTrue(landing.pending)
        assertEquals(TeachingRoutes.course(direct), landing.missingPageTarget(missing, direct))
        assertTrue(landing.consume())
        assertFalse(landing.consume())
        landing.observe(signIn)
        assertNull(landing.missingPageTarget(missing, direct))

        landing.reset()
        assertNull(landing.missingPageTarget(missing, direct))
        landing.observe(signIn)
        assertEquals(TeachingRoutes.course(direct), landing.missingPageTarget(missing, direct))
    }

    @Test fun proxyKeepsTheTeachingRoute() {
        val landing = TeachingLanding()
        val proxyCas = TeachingRoutes.VPN + "https/77726476706e69737468656265737421e0f6528f69256243300d8db9d6562d"
        val academic = proxied.replace("/http/", "/https-8443/")
        val signIn = casPage(proxyCas, TeachingRoutes.login(academic))
        landing.observe(signIn)
        assertNull(landing.missingPageTarget(signIn, academic))
        assertNull(landing.missingPageTarget(Uri.parse(TeachingRoutes.course(academic)), academic))
        assertNull(landing.missingPageTarget(Uri.parse("https://example.org/404"), academic))
        assertEquals(TeachingRoutes.course(academic),
            landing.missingPageTarget(Uri.parse("$academic/error/404"), academic))
    }

    @Test fun vpnLoginDoesNotArmCourseRecovery() {
        val landing = TeachingLanding()
        landing.observe(Uri.parse(TeachingRoutes.VPN + "login"))
        landing.observe(Uri.parse(TeachingRoutes.gatewaySession()))
        assertNull(landing.missingPageTarget(Uri.parse("$proxied/error/404"), proxied))
        assertNull(landing.missingPageTarget(Uri.parse(TeachingRoutes.VPN + "404"), proxied))
    }
}
