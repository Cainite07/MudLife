package cn.mudlife.app.model

import org.junit.Assert.*
import org.junit.Test

class QzhqSessionTest {
    @Test fun explicitExpiryInvalidatesOnlyTheRequestingSession() {
        for (code in listOf(8, 401)) {
            val response = QzhqResponse<Unit>(code, "登录失效")
            assertTrue(response.expiresSession("current", "current"))
            assertFalse(response.expiresSession("old", "replacement"))
            assertFalse(response.expiresSession("", ""))
        }
    }

    @Test fun NetworkAndOtherServiceFailuresDoNotLogOutAnAccount() {
        for (code in listOf(0, -1, 403, 500)) {
            assertFalse(QzhqResponse<Unit>(code).expiresSession("current", "current"))
        }
    }
}
