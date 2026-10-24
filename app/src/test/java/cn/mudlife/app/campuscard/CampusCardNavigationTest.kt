package cn.mudlife.app.campuscard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CampusCardNavigationTest {
    @Test fun officialSchoolPagesAndCasCallbackStayInside() {
        listOf(
            CampusCardNavigation.HOME_URL,
            CampusCardNavigation.WALLET_URL,
            CampusCardNavigation.RECHARGE_URL,
            CampusCardNavigation.PAYMENT_URL,
            "https://pass.ujs.edu.cn/cas/login?service=https%3A%2F%2Fykt.ujs.edu.cn%2Fcallback",
            "https://ykt.ujs.edu.cn:443/plat/?name=loginTransit",
            "HTTPS://YKT.UJS.EDU.CN/plat/"
        ).forEach { assertEquals(it, CampusCardNavigation.Decision.Internal, CampusCardNavigation.decide(it)) }
    }

    @Test fun lookalikeAndCredentialContainingAuthoritiesAreBlocked() {
        listOf(
            "https://ykt.ujs.edu.cn.evil.example/plat/",
            "https://evil.example/?next=https://ykt.ujs.edu.cn/plat/",
            "https://ykt.ujs.edu.cn@evil.example/",
            "https://evil.example@ykt.ujs.edu.cn/",
            "https://user:password@ykt.ujs.edu.cn/",
            "https://ykt.ujs.edu.cn./plat/",
            "https://%79kt.ujs.edu.cn/plat/",
            "https://pass.ujs.edu.cn.evil.example/cas/login"
        ).forEach { assertEquals(it, CampusCardNavigation.Decision.Blocked, CampusCardNavigation.decide(it)) }
    }

    @Test fun cleartextLocalFilesAndUnverifiedAppIntentsCannotEscape() {
        listOf(
            "http://ykt.ujs.edu.cn/plat/",
            "http://47.107.37.60/",
            "file:///data/data/cn.mudlife.app/",
            "content://cn.mudlife.app.fileprovider/",
            "javascript:alert(1)",
            "data:text/html,test",
            "weixin://wap/pay?test=1",
            "intent://pay#Intent;scheme=weixin;S.browser_fallback_url=https://evil.example/;end",
            "https://ykt.ujs.edu.cn:444/plat/",
            "//ykt.ujs.edu.cn/plat/",
            "https://ykt.ujs.edu.cn\\@evil.example/",
            "https://ykt.ujs.edu.cn/\nother"
        ).forEach { assertEquals(it, CampusCardNavigation.Decision.Blocked, CampusCardNavigation.decide(it)) }
        assertEquals(CampusCardNavigation.Decision.Blocked, CampusCardNavigation.decide(null))
        assertEquals(CampusCardNavigation.Decision.Blocked, CampusCardNavigation.decide(""))
    }

    @Test fun allKnownConsumptionCodePagesNeedFreshContentOnResume() {
        assertTrue(CampusCardNavigation.isPaymentPage(CampusCardNavigation.PAYMENT_URL))
        assertTrue(CampusCardNavigation.isPaymentPage("https://ykt.ujs.edu.cn/plat/pay/?source=h5"))
        assertTrue(CampusCardNavigation.isPaymentPage("https://ykt.ujs.edu.cn/campus-card/cardDetails?index=0"))
    }

    @Test fun rechargeAndOrderPagesMustNotBeAutomaticallyReloaded() {
        listOf(
            CampusCardNavigation.RECHARGE_URL,
            "https://ykt.ujs.edu.cn/charge/order/thirdOrder",
            "https://ykt.ujs.edu.cn/payment/index?orderid=example",
            "https://pass.ujs.edu.cn/cas/login",
            "https://ykt.ujs.edu.cn/plat/payResult",
            "https://evil.example/plat/pay"
        ).forEach {
            assertFalse(it, CampusCardNavigation.isPaymentPage(it))
            assertNull(it, CampusCardNavigation.resumeDestination(it))
        }
    }

    @Test fun resumeKeepsSelectedWalletWithOneDecodeAndDropsCredentials() {
        assertEquals(
            "${CampusCardNavigation.PAYMENT_URL}?no=123456-01",
            CampusCardNavigation.resumeDestination(
                "${CampusCardNavigation.PAYMENT_URL}?%6E%6F=%31%32%33%34%35%36%2D01&token=secret&ticket=ST-example&code=secret#old-code"
            )
        )
        assertEquals(
            CampusCardNavigation.PAYMENT_URL,
            CampusCardNavigation.resumeDestination("${CampusCardNavigation.PAYMENT_URL}/?ticket=ST-example")
        )
    }

    @Test fun resumeKeepsCompleteCurrentComponentSelectorsAndOldNo() {
        assertEquals(
            "${CampusCardNavigation.PAYMENT_URL}?no=123456-01&payacc=01&payid=42",
            CampusCardNavigation.resumeDestination(
                "${CampusCardNavigation.PAYMENT_URL}?payid=%34%32&payacc=%30%31&no=123456-01&token=secret"
            )
        )
        assertEquals(
            "${CampusCardNavigation.PAYMENT_URL}?payacc=%23%23%23&payid=1",
            CampusCardNavigation.resumeDestination("${CampusCardNavigation.PAYMENT_URL}?payacc=%23%23%23&payid=1")
        )
    }

    @Test fun ambiguousOrInvalidSelectionReturnsToWalletInsteadOfChoosingDefault() {
        listOf(
            "no=123&no=456", "no=123&%6Eo=123", "no=", "no", "no=123+456",
            "no=123%26token%3Dsecret", "no=%2523", "no=%2F%2Fevil.example",
            "no=%E4%B8%AD", "no=${"1".repeat(98)}",
            "payid=1", "payacc=123", "payacc=01&payid=1&payid=2",
            "payacc=01&payid=1&%70ayacc=02", "payacc=01&payid=NaN",
            "payacc=01&payid=2147483648", "payacc=%2523%2523%2523&payid=1",
            "payacc=01&payid=1&lite=1", "no=123&lite=unexpected"
        ).forEach { query ->
            assertEquals(query, CampusCardNavigation.WALLET_URL,
                CampusCardNavigation.resumeDestination("${CampusCardNavigation.PAYMENT_URL}?$query"))
        }
    }

    @Test fun cardDetailsResumeGoesToWalletWithoutChangingPaymentAccount() {
        assertEquals(CampusCardNavigation.WALLET_URL, CampusCardNavigation.resumeDestination(
            "https://ykt.ujs.edu.cn/campus-card/cardDetails/?account=123&token=secret"))
    }

    @Test fun credentialQueriesCannotTurnOtherPagesIntoResumeRequests() {
        listOf(
            "https://ykt.ujs.edu.cn/charge/order/thirdOrder?no=123&return=/plat/pay",
            "https://ykt.ujs.edu.cn/campus-card/cardRecharge/?no=123",
            "https://ykt.ujs.edu.cn/payment/?payacc=01&payid=1",
            "https://pass.ujs.edu.cn/plat/pay?no=123",
            "https://ykt.ujs.edu.cn.evil.example/plat/pay?no=123",
            "https://user@ykt.ujs.edu.cn/plat/pay?no=123"
        ).forEach { assertNull(it, CampusCardNavigation.resumeDestination(it)) }
    }
}
