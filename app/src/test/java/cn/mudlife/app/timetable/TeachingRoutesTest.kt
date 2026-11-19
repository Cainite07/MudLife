package cn.mudlife.app.timetable

import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], manifest = Config.NONE)
class TeachingRoutesTest {
    private val direct = "https://jwxt.ujs.edu.cn"
    private val proxied = TeachingRoutes.PROXIED

    @Test fun firstEntryAndExistingBookmarks() {
        assertEquals(TeachingRoutes.login(proxied), TeachingRoutes.initial(null))
        val saved = TeachingRoutes.course(proxied)
        assertEquals(saved, TeachingRoutes.initial(saved + "&term=old"))
        assertEquals(TeachingRoutes.login(proxied), TeachingRoutes.initial(TeachingRoutes.course(direct)))
    }

    @Test fun importsOnlyFromTheOpenAcademicRouteForTheCurrentStudent() {
        val alternate = proxied.replace("/http/", "/https-8443/")
        val unknown = TeachingRoutes.course(TeachingRoutes.VPN + "http/1234567890abcdef")
        val directPage = TeachingRoutes.course(direct)
        assertNull(TeachingRoutes.bookmark(unknown))
        assertEquals(TeachingRoutes.login(proxied), TeachingRoutes.initial(unknown))
        for (base in listOf(proxied, alternate)) {
            val page = TeachingRoutes.course(base)
            assertNull(TeachingRoutes.importSourceError(page, page + "&term=1", "student-A", "student-A"))
            assertNotNull(TeachingRoutes.importSourceError(page, page, "student-A", "student-B"))
            assertNotNull(TeachingRoutes.importSourceError(page, page, "", ""))
        }
        assertNotNull(TeachingRoutes.importSourceError(directPage, directPage, "A", "A"))
        assertNotNull(TeachingRoutes.importSourceError(TeachingRoutes.course(proxied), TeachingRoutes.course(alternate), "A", "A"))
        assertNotNull(TeachingRoutes.importSourceError(directPage, unknown, "A", "A"))
        assertNotNull(TeachingRoutes.importSourceError(unknown, unknown, "A", "A"))
    }

    @Test fun courseFollowsConfirmedHome() {
        fun from(base: String) =
            TeachingRoutes.courseFromHome(Uri.parse("$base/xtgl/index_initMenu.html"))
        assertNull(from(direct))
        assertEquals(TeachingRoutes.course(proxied), from(proxied))
        val alternatePrefix = proxied.replace("/http/", "/https-8443/")
        assertEquals(TeachingRoutes.course(alternatePrefix), from(alternatePrefix))
        assertNull(from(TeachingRoutes.VPN + "http/1234567890abcdef"))
        assertNull(TeachingRoutes.courseFromHome(Uri.parse("$proxied/xtgl/login_slogin.html")))
    }

    @Test fun expiredCourseReentersSameSystemOnly() {
        fun next(base: String, previous: String?) =
            TeachingRoutes.expiredBookmarkLogin(Uri.parse("$base/xtgl/login_slogin.html"), previous)
        assertNull(next(direct, direct))
        assertEquals(TeachingRoutes.login(proxied), next(proxied, proxied))
        assertNull(next(proxied, direct))
        assertNull(next(proxied, null))
    }

    @Test fun gatewayLoginUsesSchoolAuthenticationInsteadOfWebVpnPassword() {
        assertTrue(TeachingRoutes.isGatewayLogin(Uri.parse(TeachingRoutes.VPN + "login")))
        assertFalse(TeachingRoutes.isGatewayLogin(Uri.parse(TeachingRoutes.VPN)))
        assertFalse(TeachingRoutes.isGatewayLogin(Uri.parse(TeachingRoutes.VPN + "login?cas_login=true")))
        assertFalse(TeachingRoutes.isGatewayLogin(Uri.parse("$proxied/cas/login")))
        val gateway = Uri.parse(TeachingRoutes.schoolGatewayLogin())
        assertEquals("pass.ujs.edu.cn", gateway.host)
        assertEquals("https://webvpn.ujs.edu.cn/login?cas_login=true", gateway.getQueryParameter("service"))
        assertNull(gateway.getQueryParameter("gateway"))
    }

    @Test fun onlyTheRegistrarsTicketCallbackCanRecoverFromHttp500() {
        val callback = Uri.parse(TeachingRoutes.login(proxied) + "?ticket=sample")
        assertTrue(TeachingRoutes.isTeachingCallback(callback))
        assertFalse(TeachingRoutes.isTeachingCallback(Uri.parse(TeachingRoutes.login(proxied))))
        assertFalse(TeachingRoutes.isTeachingCallback(Uri.parse(TeachingRoutes.course(proxied) + "&ticket=sample")))
        assertFalse(TeachingRoutes.isTeachingCallback(Uri.parse("https://example.org/sso/jziotlogin?ticket=sample")))
        val landing = TeachingLanding()
        landing.observe(callback)
        assertEquals(TeachingRoutes.course(proxied), landing.missingPageTarget(callback, proxied))
        assertTrue(landing.consume())
        assertNull(landing.missingPageTarget(callback, proxied))
    }

    @Test fun labelsVpnAndTeachingAuthenticationAsSeparateSteps() {
        val proxyCas = TeachingRoutes.VPN + "https/77726476706e69737468656265737421e0f6528f69256243300d8db9d6562d"
        assertEquals(TeachingRoutes.AuthStep.WEB_VPN, TeachingRoutes.authStep(Uri.parse(TeachingRoutes.schoolGatewayLogin())))
        assertEquals(TeachingRoutes.AuthStep.TEACHING, TeachingRoutes.authStep(casPage(proxyCas, TeachingRoutes.login(proxied))))
        assertEquals(TeachingRoutes.AuthStep.TEACHING, TeachingRoutes.authStep(Uri.parse("$proxied/xtgl/login_slogin.html")))
        assertNull(TeachingRoutes.authStep(Uri.parse(TeachingRoutes.course(proxied))))
    }

    @Test fun gatewayCallbackSupportsTokenLoginAndMobilePortal() {
        val vpn = TeachingRoutes.VPN
        assertTrue(TeachingRoutes.isGatewayCallback(Uri.parse(vpn + "login?cas_login=true")))
        assertTrue(TeachingRoutes.isGatewayCallback(Uri.parse(vpn + "wengine-vpn-token-login?token=sample")))
        assertFalse(TeachingRoutes.isGatewayCallback(Uri.parse(vpn + "wengine-vpn-token-login?token=")))
        assertFalse(TeachingRoutes.isGatewayCallback(Uri.parse("https://example.org/wengine-vpn-token-login?token=sample")))
        assertTrue(TeachingRoutes.isGatewayPortal(Uri.parse(vpn)))
        assertTrue(TeachingRoutes.isGatewayPortal(Uri.parse(vpn + "m/portal")))
        assertFalse(TeachingRoutes.isGatewayPortal(Uri.parse(vpn + "m/portal-other")))
    }

    private fun casPage(casBase: String, service: String): Uri =
        Uri.parse("$casBase/cas/login").buildUpon().appendQueryParameter("service", service).build()

    @Test fun teachingLoginRecoversOnlyOnce() {
        val landing = TeachingLanding()
        val signIn = casPage("https://pass.ujs.edu.cn", TeachingRoutes.login(proxied))
        val missing = Uri.parse("$proxied/error/404")
        assertNull(landing.missingPageTarget(missing, proxied))
        landing.observe(signIn)
        assertTrue(landing.pending)
        assertEquals(TeachingRoutes.course(proxied), landing.missingPageTarget(missing, proxied))
        assertTrue(landing.consume())
        assertFalse(landing.consume())
        landing.observe(signIn)
        assertNull(landing.missingPageTarget(missing, proxied))

        landing.reset()
        assertNull(landing.missingPageTarget(missing, proxied))
        landing.observe(signIn)
        assertEquals(TeachingRoutes.course(proxied), landing.missingPageTarget(missing, proxied))
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
        landing.observe(Uri.parse(TeachingRoutes.schoolGatewayLogin()))
        assertNull(landing.missingPageTarget(Uri.parse("$proxied/error/404"), proxied))
        assertNull(landing.missingPageTarget(Uri.parse(TeachingRoutes.VPN + "404"), proxied))
    }
}
