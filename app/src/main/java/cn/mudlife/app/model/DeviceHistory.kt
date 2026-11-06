package cn.mudlife.app.model

import java.time.YearMonth

internal object DeviceHistory {
    fun months(start: YearMonth, end: YearMonth): List<YearMonth> {
        require(!start.isAfter(end))
        return generateSequence(end) { it.minusMonths(1).takeUnless { month -> month.isBefore(start) } }.toList()
    }

    fun fresh(month: YearMonth, today: YearMonth, savedAt: Long, now: Long): Boolean {
        val lifetime = if (month >= today.minusMonths(1)) 60_000L else 30L * 24 * 60 * 60 * 1000
        return savedAt > 0 && now >= savedAt && now - savedAt < lifetime
    }
}
