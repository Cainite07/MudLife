package cn.mudlife.app.timetable

import java.text.Normalizer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

internal val campusZone: ZoneId = ZoneId.of("Asia/Shanghai")

internal object TimetableLimits {
    const val MAX_PERIODS = 15
    const val MAX_WEEKS = 20
}

internal object TeachingWeeks {
    // Older records and the school's original week expressions can extend beyond the visible term.
    const val MAX = 53
    private val all = (1..MAX).toSet()
    private val separator = Regex("[,、;]")
    private val range = Regex("""第?(\d{1,2})(?:[-－–—~～至](\d{1,2}))?周?(?:\(([单双])周?\)|([单双])周?)?""")

    /** Null means unrecognised, so an older hand-entered value can be corrected without silently hiding it. */
    fun parse(text: String): Set<Int>? {
        val value = Normalizer.normalize(text, Normalizer.Form.NFKC).filterNot { it.isWhitespace() }
        if (value in setOf("", "每周", "全部", "全部周次", "全周")) return all
        if (value == "单周" || value == "双周") return all.filterTo(mutableSetOf()) { matchesParity(it, value.first()) }
        val result = sortedSetOf<Int>()
        for (part in value.split(separator)) {
            val m = range.matchEntire(part) ?: return null
            val first = m.groupValues[1].toInt()
            val last = m.groupValues[2].toIntOrNull() ?: first
            if (first !in 1..MAX || last !in first..MAX) return null
            val parity = (m.groupValues[3] + m.groupValues[4]).firstOrNull()
            val weeks = (first..last).filter { matchesParity(it, parity) }
            if (weeks.isEmpty()) return null
            result.addAll(weeks)
        }
        return result
    }

    private fun matchesParity(week: Int, parity: Char?) = when (parity) {
        '单' -> week % 2 == 1
        '双' -> week % 2 == 0
        else -> true
    }
}

internal data class BellPeriod(val number: Int, val start: LocalTime, val end: LocalTime) {
    init {
        require(number in 1..30 && end > start) { "请输入有效的节次及起止时间" }
    }
}

internal data class ClassMoment(
    val row: SavedCourse, val starts: LocalDateTime, val ends: LocalDateTime, val isOngoing: Boolean
)

internal class TeachingCalendar(val firstMonday: LocalDate, configuredWeeks: Int? = null) {
    init { require(firstMonday.dayOfWeek == DayOfWeek.MONDAY) { "开学日期应为第一周的周一" } }

    val lastWeek = configuredWeeks?.coerceIn(1, TimetableLimits.MAX_WEEKS) ?: TimetableLimits.MAX_WEEKS

    fun weekOf(date: LocalDate): Int = (Math.floorDiv(ChronoUnit.DAYS.between(firstMonday, date), 7L) + 1).toInt()

    fun dateOf(week: Int, weekday: Int = 1): LocalDate {
        require(week in 1..TeachingWeeks.MAX && weekday in 1..7)
        return firstMonday.plusWeeks(week.toLong() - 1).plusDays(weekday.toLong() - 1)
    }

    fun nextClass(rows: List<SavedCourse>, bells: List<BellPeriod>, now: LocalDateTime): ClassMoment? =
        nextClass(rows, now) { _, _ -> bells }

    /** A break between two bells is not class time, even if both periods belong to the same course. */
    fun nextClass(
        rows: List<SavedCourse>, now: LocalDateTime, bells: (LocalDate, String) -> List<BellPeriod>
    ): ClassMoment? {
        val today = now.toLocalDate()
        val firstWeek = weekOf(today).coerceAtLeast(1)
        var next: ClassMoment? = null
        var uncertain: Pair<LocalDate, Int>? = null
        for (row in rows) {
            val c = row.course
            val weeks = TeachingWeeks.parse(c.weeks) ?: continue
            dates@ for (week in weeks) {
                if (week !in firstWeek..lastWeek) continue
                val day = dateOf(week, c.weekday)
                if (day < today) continue
                if (next?.starts?.toLocalDate()?.let { day.isAfter(it) } == true) break
                val dayBells = bells(day, c.place).associateBy { it.number }
                for (period in c.startPeriod..c.endPeriod) {
                    val bell = dayBells[period]
                    if (bell == null) {
                        // A later known bell that already started rules out this unknown period.
                        if (day == today && dayBells.any { (n, b) -> n > period && b.start <= now.toLocalTime() }) continue
                        val earlier = uncertain
                        if (earlier == null || day < earlier.first || day == earlier.first && period < earlier.second)
                            uncertain = day to period
                        break@dates
                    }
                    val start = day.atTime(bell.start)
                    val end = day.atTime(bell.end)
                    if (end <= now) continue
                    if (next == null || start < next.starts) next = ClassMoment(row, start, end, start <= now)
                    break@dates
                }
            }
        }
        val known = next ?: return null
        val unknown = uncertain ?: return known
        val knownDay = known.starts.toLocalDate()
        return known.takeUnless { unknown.first < knownDay ||
            unknown.first == knownDay && unknown.second <= known.row.course.endPeriod && !known.isOngoing }
    }
}
