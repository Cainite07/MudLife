package cn.mudlife.campuscard

import org.junit.Assert.*
import org.junit.Test

class SessionPolicyTest {
    @Test fun billPeriodDefaultsToCurrentMonthAndPreservesExplicitSelections() {
        val today = java.time.YearMonth.of(2026, 10)
        assertEquals(today, BillPeriodPolicy.restore("", today))
        assertEquals(today, BillPeriodPolicy.restore("bad", today))
        assertNull(BillPeriodPolicy.restore(BillPeriodPolicy.save(null), today))
        val previous = today.minusMonths(1)
        assertEquals(previous, BillPeriodPolicy.restore(BillPeriodPolicy.save(previous), today.plusMonths(2)))
    }
    @Test fun requestedRechargePresetsStillRespectSchoolConstraints() {
        assertEquals(listOf(1000L, 5000L, 10000L, 20000L), RechargePolicy.presets)
        val rules = RechargeRules(100, 20000, listOf(1000, 5000), true, true)
        RechargePolicy.presets.forEach { assertTrue(RechargePolicy.allowed(it, rules)) }
        assertTrue(RechargePolicy.allowed(Money.parseYuan("37"), rules))
        assertFalse(RechargePolicy.allowed(20001, rules))
        assertFalse(RechargePolicy.allowed(101, rules))
        assertFalse(RechargePolicy.allowed(10000, rules.copy(custom = false)))
        assertFalse(RechargePolicy.allowed(null, rules))
        assertFalse(RechargePolicy.allowed(1000, null))
    }
    @Test fun localLedgerKeepsQrExpensesAndRefundIncomeAcrossMonths() {
        val records = listOf(
            Bill("a", "消费", "2026-09-01 10:00", 120, "4", "", "", "", null, "2"),
            Bill("b", "消费", "2026-10-01 10:00", 230, "1", "", "", "", null, "2"),
            Bill("c", "退款", "2026-10-02 10:00", 80, "3", "", "", "", null, "1"),
            Bill("d", "充值", "2026-10-03 10:00", 1000, "2", "", "", "", null, "1"))
        assertEquals(350L, BillLedger.summary(records, null).expenses)
        assertEquals(1080L, BillLedger.summary(records, null).income)
        assertEquals(230L, BillLedger.summary(records, java.time.YearMonth.of(2026, 10)).expenses)
        assertEquals(2, BillLedger.select(records, null, "2").size)
        assertEquals(1, BillLedger.select(records, java.time.YearMonth.of(2026, 9), "").size)
        assertNull(BillLedger.summary(records + records[0].copy(fen = null), null).expenses)
    }
    @Test fun checkoutTokenIsScopedToOneVerifiedSchoolOrder() {
        val good = "https://ykt.ujs.edu.cn/payment/?orderid=example123&token=example-token"
        assertEquals("example123", Navigation.checkoutReference(good)?.orderId)
        assertEquals("example-token", Navigation.checkoutReference(good)?.token)
        listOf(good + "&%74oken=another", good.replace("token=example-token", "token="), good.replace("example-token", "bad%0D%0Aheader"), good.replace("ykt.ujs.edu.cn", "evil.example"), good + "#fragment").forEach { assertNull(Navigation.checkoutReference(it)) }
    }
    @Test fun onlyExplicitPaidStatusAndMatchingYuanAmountConfirmPayment() {
        assertEquals(PaymentState.PAID, PaymentPolicy.state(1, 100, "1.00"))
        assertEquals(PaymentState.AWAITING, PaymentPolicy.state(0, 100, "1.00"))
        assertEquals(PaymentState.TIMED_OUT, PaymentPolicy.state(2, 100, "1.00"))
        assertEquals(PaymentState.UNKNOWN, PaymentPolicy.state(1, 100, "100"))
        assertEquals(PaymentState.UNKNOWN, PaymentPolicy.state(3, 100, "1.00"))
        assertEquals(PaymentState.UNKNOWN, PaymentPolicy.state(1, 0, "1.00"))
    }
    @Test fun actualPortraitSrcWinsOverOnerrorFallback() {
        val tag = "<img class=\"card-img-user\" onerror=\"this.src='https://example.test/default.png';this.onerror=null\" src=\"https://example.test/student.png\" style=\"width:25%\">"
        assertEquals("https://example.test/student.png", SchoolMarkup.attribute(tag, "src"))
        assertEquals("width:25%", SchoolMarkup.attribute(tag, "style"))
        assertEquals("student.png", SchoolMarkup.attribute("<img SRC = 'student.png'>", "src"))
        assertEquals("", SchoolMarkup.attribute("<img onerror=\"this.src='fallback.png'\">", "src"))
    }
    @Test fun billTitlesDescribeTransactionsRatherThanTerminalCodes() {
        assertEquals("微信充值", BillPolicy.title("2", "", "微信支付转账", "微信支付转账", "1L"))
        assertEquals("校园卡充值", BillPolicy.title("2", "0-123", "", "", ""))
        assertEquals("洗浴消费", BillPolicy.title("1", "宏图", "宏图-电子账户消费", "电子账户消费", "94"))
        assertEquals("宏图", BillPolicy.title("4", "宏图", "宏图-扫码支付", "扫码支付", "99"))
        assertEquals("食堂小面", BillPolicy.title("4", "食堂小面_2", "食堂小面_2-扫码支付", "扫码支付", "99"))
        assertEquals("校园卡消费", BillPolicy.title("1", "0-123", "", "", ""))
    }
    @Test fun allMonthsHasNoArtificialDateLimitAndFiltersUseDirection() {
        assertTrue(billDates(null).isEmpty())
        assertEquals(mapOf("timeFrom" to "2024-02-01", "timeTo" to "2024-02-29"), billDates(java.time.YearMonth.of(2024, 2)))
        assertEquals(listOf("全部", "支出", "收入"), BillPolicy.filters.map { it.name })
        assertEquals("2", BillPolicy.filters.first { it.name == "支出" }.id)
        assertEquals("1", BillPolicy.filters.first { it.name == "收入" }.id)
        assertEquals("−", BillPolicy.prefix("4", "2"))
        assertEquals("+", BillPolicy.prefix("1", "1"))
    }
    @Test fun electricityUsesSchoolUnitPriceAndCentRounding() {
        assertEquals(5400L, ElectricPolicy.cost("100", "0.540"))
        assertEquals(1L, ElectricPolicy.cost("0.01", "0.54"))
        assertEquals(2L, ElectricPolicy.cost("0.03", "0.54"))
        listOf("0", "-1", "1e2", "NaN", "1.001").forEach { assertNull(ElectricPolicy.cost(it, "0.54")) }
        assertNull(ElectricPolicy.cost("10", "0"))
    }
    @Test fun onlyMainCampusDormsCanBeUsed() {
        val d = Dorm(ElectricOption("校本部", "校本部"), ElectricOption("region-id", "区域"), ElectricOption("block-id", "楼栋"), ElectricOption("room-id", "房间"))
        assertEquals("room-id", d.fields()["room"])
        assertTrue(ElectricPolicy.valid(d))
        assertFalse(ElectricPolicy.valid(d.copy(campus = ElectricOption("京江", "京江"))))
        assertFalse(ElectricPolicy.valid(d.copy(room = ElectricOption("", "房间"))))
    }
    @Test fun positiveServerAmountsDoNotTurnConsumptionIntoIncome() {
        assertEquals("−1.23", Bill("", "", "", 123, "1", "", "", "", null).displayAmount)
        assertEquals("−1.23", Bill("", "", "", 123, "4", "", "", "", null).displayAmount)
        assertEquals("+1.23", Bill("", "", "", 123, "3", "", "", "", null).displayAmount)
        assertEquals("1.23", Bill("", "", "", 123, "unknown", "", "", "", null).displayAmount)
    }
    @Test fun encodedWechatHandoffIsSupportedWithoutArbitraryIntents() {
        assertTrue(Navigation.wechat("weixin://wap/pay?prepayid%3DwxExample123&package=1234567890&noncestr=test&sign=test"))
        listOf("weixin://dl/business?prepayid=test", "weixin://wap/pay?prepayid%3Dtest&prepayid=another", "weixin://wap/pay?prepayid%253Dtest", "weixin://wap/pay?other=test").forEach { assertFalse(Navigation.wechat(it)) }
        assertFalse(Navigation.web("https://ykt.ujs.edu.cn.evil.example/"))
    }
    @Test fun outagesDoNotLogUsersOut() {
        listOf(0, 200, 403, 429, 500, 502, 503).forEach { assertFalse(LoginPolicy.needsReauthentication(it)) }
        assertTrue(LoginPolicy.needsReauthentication(401))
    }
    @Test fun rechargeUsesExactCents() {
        assertEquals(1L, Money.parseYuan("0.01"))
        assertEquals(100L, Money.parseYuan("1"))
        assertEquals(12345L, Money.parseYuan("123.45"))
        listOf("0", "-1", "1.001", "1e2", "NaN", "Infinity", "").forEach { assertNull(Money.parseYuan(it)) }
        assertEquals("123.45", Money.yuan(12345))
    }
    @Test fun accountTypesAreNotMixed() {
        val c = CampusCard("example", "card", "", "", 100L, 20L, listOf(Account("01", "电子", 300L)), false)
        assertEquals(300L, c.balance("1"))
        assertEquals(120L, c.balance("2"))
        assertEquals(420L, c.balance("0"))
        assertNull(c.balance("unknown"))
        assertNull(c.copy(accounts = emptyList()).balance("1"))
    }
}
