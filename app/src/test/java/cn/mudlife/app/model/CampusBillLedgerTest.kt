package cn.mudlife.app.model

import cn.mudlife.campuscard.Bill
import org.junit.Assert.*
import org.junit.Test
import java.time.YearMonth

class CampusBillLedgerTest {
    private fun bill(id: String, title: String, fen: Long? = 500, type: String = "1", time: String = "2026-10-24 12:00:00") =
        Bill(id, title, time, fen, type, "", title, "", null)

    @Test fun statementExcludesDrinkingWithoutChangingHistory() {
        val records = listOf(bill("food", "第一食堂"), bill("bath", "洗浴消费"), bill("water", "直饮水"), bill("topup", "趣智充值", type = "2"))
        assertEquals(listOf("food", "bath"), CampusBillLedger.select(records, null).map { it.id })
        assertEquals(4, records.size)
    }

    @Test fun categoriesUseSchoolTransactionsAndUnknownMerchantsRemainOther() {
        assertEquals(CampusBillCategory.FOOD, CampusBillLedger.category(bill("a", "京江餐厅")))
        assertEquals(CampusBillCategory.BATH, CampusBillLedger.category(bill("b", "洗浴消费")))
        assertEquals(CampusBillCategory.RECHARGE, CampusBillLedger.category(bill("c", "支付宝充值", type = "2")))
        assertEquals(CampusBillCategory.OTHER, CampusBillLedger.category(bill("d", "宏图")))
        assertEquals(CampusBillCategory.OTHER, CampusBillLedger.category(bill("e", "校园卡退款", type = "3")))
    }

    @Test fun expensesNeverIncludeRechargeRefundOrSubsidy() {
        val rows = listOf(bill("a", "食堂", 800), bill("b", "洗浴消费", -260), bill("c", "充值", 10000, "2"),
            bill("d", "退款", 200, "3"), bill("e", "补助", null, "5"))
        assertEquals(1060L, CampusBillLedger.expense(rows))
        assertEquals(0L, CampusBillLedger.expense(CampusBillLedger.select(rows, null, CampusBillCategory.RECHARGE)))
    }

    @Test fun incompleteMoneyOrDirectionDoesNotBecomeAZeroTotal() {
        assertNull(CampusBillLedger.expense(listOf(bill("a", "食堂", null))))
        assertNull(CampusBillLedger.expense(listOf(bill("a", "未知", type = "99"))))
        assertNull(CampusBillLedger.expense(listOf(bill("a", "食堂", Long.MIN_VALUE))))
        assertNull(CampusBillLedger.expense(listOf(bill("a", "食堂", Long.MAX_VALUE), bill("b", "食堂", 1))))
    }

    @Test fun monthCategoryAndDedupPreserveSeparateRealPayments() {
        val same = bill("a", "第一食堂")
        val rows = listOf(same, same, bill("b", "第二食堂"), bill("c", "洗浴消费"), bill("d", "食堂", time = "2026-09-01 12:00:00"))
        val selected = CampusBillLedger.select(rows, YearMonth.of(2026, 10), CampusBillCategory.FOOD)
        assertEquals(listOf("a", "b"), selected.map { it.id })
        assertEquals(1000L, CampusBillLedger.expense(selected))
    }
}
