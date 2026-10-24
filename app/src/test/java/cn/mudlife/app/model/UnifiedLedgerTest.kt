package cn.mudlife.app.model

import org.junit.Assert.*
import org.junit.Test
import java.time.YearMonth

class UnifiedLedgerTest {
    private fun row(key: String, source: String, time: String = "2026-10-23 20:00:00", fen: Long? = 380, order: String = "") =
        LedgerEntry(key, "洗浴消费", time, fen, false, source, order, "", source == "qzhq", true)

    @Test fun SchoolStatementOwnsCampusDebit() {
        val merged = UnifiedLedger.merge(listOf(row("s", "school")), listOf(row("l", "qzhq")), true)
        assertEquals(listOf("s"), merged.entries.map { it.key })
        assertEquals(380L, UnifiedLedger.sum(merged.entries, false))
        assertTrue(merged.usesSchoolDebits)
    }
    @Test fun DifferentDeviceWalletIsNeverCollapsedByAmountAndTime() {
        val merged = UnifiedLedger.merge(listOf(row("s", "school")), listOf(row("l", "qzxy")), true)
        assertEquals(2, merged.entries.size)
        assertEquals(760L, UnifiedLedger.sum(merged.entries, false))
    }
    @Test fun PostingDelayDoesNotHideTotalsOrCountCampusDebitTwice() {
        val income = row("recharge", "school", fen = 10000).copy(income = true)
        val merged = UnifiedLedger.merge(listOf(row("s", "school"), income), listOf(row("l", "qzhq", "2026-10-23 20:00:30")), true)
        assertEquals(380L, UnifiedLedger.sum(merged.entries, false))
        assertEquals(10000L, UnifiedLedger.sum(merged.entries, true))
    }
    @Test fun SchoolPreservesRepeatedPaymentsAndLogisticsRemainsAvailableSeparately() {
        val logistics = listOf(row("l1", "qzhq"), row("l2", "qzhq"))
        val merged = UnifiedLedger.merge(listOf(row("s1", "school"), row("s2", "school")), logistics, true)
        assertEquals(760L, UnifiedLedger.sum(merged.entries, false))
        assertEquals(2, logistics.size)
    }
    @Test fun WithoutCompleteSchoolStatementLogisticsIsNotDiscarded() {
        val logistics = listOf(row("l", "qzhq"))
        val merged = UnifiedLedger.merge(emptyList(), logistics, false)
        assertEquals(logistics, merged.entries)
        assertFalse(merged.usesSchoolDebits)
    }
    @Test fun CombinedTotalUsesSchoolEvenWhenPostingFallsInAnotherMonth() {
        val school = listOf(row("s", "school", "2026-10-01 00:00:10"))
        val life = listOf(row("l", "qzhq", "2026-09-30 23:59:50"), row("w", "qzxy", fen = 20))
        val merged = UnifiedLedger.merge(school, life, true)
        assertEquals(400L, UnifiedLedger.sum(UnifiedLedger.select(merged.entries, YearMonth.of(2026, 10), ""), false))
        assertEquals(0L, UnifiedLedger.sum(UnifiedLedger.select(merged.entries, YearMonth.of(2026, 9), ""), false))
    }
    @Test fun IncomingFundsAreNotExpensesAndUnknownAmountsAreNotZero() {
        val rows = listOf(row("a", "qzxy", fen = 60), row("b", "school", fen = 10000).copy(income = true))
        assertEquals(60L, UnifiedLedger.sum(rows, false))
        assertEquals(10000L, UnifiedLedger.sum(rows, true))
        assertNull(UnifiedLedger.sum(rows + row("c", "school", fen = null), false))
    }
    @Test fun MonthAndSourceFilteringDoesNotMutateHistory() {
        val rows = listOf(row("s", "school"), row("l", "qzxy"), row("old", "school", "2026-09-23 20:00:00"))
        assertEquals(listOf("s"), UnifiedLedger.select(rows, YearMonth.of(2026, 10), "school").map { it.key })
        assertEquals(3, UnifiedLedger.select(rows, null, "").size)
    }
}
