package cn.mudlife.app.model

import cn.mudlife.campuscard.Bill
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class MonthlyBillReportTest {
    private val month = YearMonth.of(2025, 2)
    private val asOf = LocalDate.of(2025, 3, 4)
    private fun bill(id: String, time: String, title: String, fen: Long?, type: String = "1") =
        Bill(id, title, time, fen, type, "", title, "", null)

    @Test fun dailyAndCategoryTotalsUseIndividualConfirmedDebits() {
        val water = bill("w", "2025/02/02 09:00:00", "直饮水", 9)
        val bills = listOf(
            water, water, bill("w2", "2025-02-02 10:22", "直饮水", 15),
            bill("f", "2025-02-03", "食堂", -300),
            bill("device:pending:b", "2025-02-02", "洗浴消费", 190),
            bill("top", "2025-02-01", "校园卡充值", 1000, "2"),
            bill("refund", "2025-02-03", "退款", 165, "3"),
            bill("j", "2025-01-15", "食堂", 700)
        )
        val report = MonthlyBillReport.build(month, bills, true, false, asOf)
        assertEquals(324L, report.totalFen)
        assertEquals(3, report.expenseCount)
        assertEquals(28, report.until)
        assertEquals(mapOf(2 to 24L, 3 to 300L), report.dailyTotals())
        assertEquals(mapOf(2 to 24L), report.dailyTotals(CampusBillCategory.WATER))
        assertEquals(listOf(CampusBillCategory.FOOD, CampusBillCategory.WATER), report.categoryTotals.keys.toList())
        assertEquals(24L, report.categoryTotals[CampusBillCategory.WATER])
        assertTrue(report.notes.any { "1 笔充值，共 10.00 元" in it })
    }

    @Test fun incompleteUnknownOverflowAndInvalidDatesDoNotProduceChartsOrRankings() {
        val known = bill("k", "2025-02-02", "食堂", 10)
        val cases = listOf(
            listOf(known) to false,
            listOf(known, bill("x", "2025-02-03", "食堂", null)) to true,
            listOf(known, bill("x", "2025-02-03", "未分类", 90, "99")) to true,
            listOf(known, bill("x", "2025-02-03", "食堂", Long.MAX_VALUE)) to true,
            listOf(known, bill("x", "2025-02-30", "食堂", 90)) to true
        )
        for ((records, complete) in cases) {
            val report = MonthlyBillReport.build(month, records, complete, true, asOf)
            assertNull(report.totalFen)
            assertTrue(report.categoryTotals.isEmpty())
            assertTrue(report.dailyTotals().isEmpty())
            assertEquals(1, report.notes.size)
        }
        val empty = MonthlyBillReport.build(month, emptyList(), true, true, asOf)
        assertEquals(0L, empty.totalFen)
        assertEquals(0, empty.expenseCount)
        assertEquals(1, empty.notes.size)
    }

    @Test fun currentMonthDisplaysTodayButComparesOnlyFinishedDays() {
        val today = LocalDate.of(2025, 2, 4)
        val records = listOf(
            bill("a", "2025-02-01", "食堂", 100), bill("b", "2025-02-02", "食堂", 200),
            bill("c", "2025-02-04", "食堂", 9000),
            bill("p", "2025-01-03", "食堂", 200), bill("q", "2025-01-04", "食堂", 10000)
        )
        val report = MonthlyBillReport.build(month, records, true, true, today)
        assertEquals(4, report.until)
        assertEquals(9300L, report.totalFen)
        assertTrue(report.notes.any { "1–3 日比上月同期多花 1.00 元" in it })
        val noPrior = MonthlyBillReport.build(month, records, true, false, today)
        assertTrue(noPrior.notes.none { "上月" in it })
    }

    @Test fun notesUseRealClockTimesForBathAndDryer() {
        val bathDay = 1..6
        val bath = bathDay.map { bill("b$it", "2025-02-0$it 21:00", "洗浴消费", 210) }
        val dryer = (1..3).map { bill("d$it", "2025-02-0$it 21:08", "吹风机消费", 35) }
        val report = MonthlyBillReport.build(month, bath + dryer, true, false, asOf)
        assertTrue(report.notes.any { "有 3 天，洗浴扣款后 15 分钟内" in it })
        assertTrue(report.notes.any { "有 6 笔是在晚上 8 点" in it })
        val missingTime = MonthlyBillReport.build(month, bath.map { it.copy(time = it.time.take(10)) } + dryer, true, false, asOf)
        assertTrue(missingTime.notes.none { "15 分钟" in it || "晚上 8 点" in it })
    }
}
