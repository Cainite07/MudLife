package cn.mudlife.app.timetable

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

internal data class WeekBellChoice(val bell: BellPeriod, val locations: List<String>)

internal data class WeekBellSlot(
    val choices: List<WeekBellChoice>,
    val unresolved: List<String>,
    val source: String,
    val variesByDate: Boolean
) {
    val startLabel: String get() = choices.firstOrNull()?.bell?.start?.toString() ?: "待定"
    val endLabel: String get() = when {
        choices.isEmpty() -> ""
        unresolved.isNotEmpty() -> "有待定"
        choices.size == 1 -> choices.first().bell.end.toString()
        variesByDate -> "分日期"
        else -> "分楼栋"
    }
    val description: String get() = when {
        choices.isEmpty() -> "时间待确认"
        choices.size == 1 && unresolved.isEmpty() -> "${choices.first().bell.start} 开始，${choices.first().bell.end} 结束"
        else -> "已知开课时间 ${choices.joinToString("、") { it.bell.start.toString() }}，${endLabel}，点按查看"
    }
}

// Each reference covers one row of the university's differentiated bell table.
private val schoolBellLocations = mapOf(
    "主楼" to "主楼、主A楼、生环楼、汽车能动楼、京江楼",
    "三江楼" to "三江楼、材料楼、机械楼、新校区各教学楼",
    "三山楼" to "三山楼、讲堂群、实践楼",
    "北固" to "北固校区"
)

private data class DayBells(val label: String, val periods: Map<Int, BellPeriod>, val dateTable: LocalDate)

internal fun weekBellAxis(
    monday: LocalDate, active: List<SavedCourse>, customPeriods: List<BellPeriod>
): Map<Int, WeekBellSlot> {
    if (customPeriods.isNotEmpty()) return customPeriods.filter { it.number <= TimetableLimits.MAX_PERIODS }.associate { bell ->
        bell.number to WeekBellSlot(listOf(WeekBellChoice(bell, emptyList())), emptyList(), "自定义作息", false)
    }
    val first = monday.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val dates = (0L..6L).map(first::plusDays)
    // The autumn or spring bells can change halfway through a teaching week.
    val dateGroups = dates.groupBy { UjsTimetable.periods(it, "主楼") }
    val dateTable = dateGroups.values.flatMap { group -> group.map { it to group.first() } }.toMap()
    val changes = if (dateGroups.size > 1) (1..11).filter { number ->
        dateGroups.keys.map { bells -> bells.find { it.number == number } }.distinct().size > 1
    } else emptyList()
    fun day(date: LocalDate) = "周" + "一二三四五六日"[date.dayOfWeek.value - 1]
    val reference = dateGroups.values.flatMap { days ->
        val firstDay = days.first()
        val dateLabel = if (dateGroups.size > 1) {
            if (firstDay == days.last()) "${day(firstDay)} · " else "${day(firstDay)}至${day(days.last())} · "
        } else ""
        schoolBellLocations.map { (place, label) ->
            DayBells(dateLabel + label, UjsTimetable.periods(firstDay, place).associateBy { it.number }, firstDay)
        }
    }
    val classes = active.map { row ->
        val c = row.course
        val date = dates[c.weekday - 1]
        val label = "${day(date)} · ${c.place.ifBlank { c.name + "（教室待定）" }}"
        c to DayBells(label, UjsTimetable.periods(date, c.place).associateBy { it.number }, dateTable.getValue(date))
    }
    val count = maxOf(11, active.maxOfOrNull { it.course.endPeriod } ?: 0).coerceAtMost(TimetableLimits.MAX_PERIODS)
    return (1..count).associateWith { number ->
        val scheduled = classes.filter { (c, _) -> number in c.startPeriod..c.endPeriod }.map { it.second }
        val candidates = scheduled.ifEmpty { reference }
        val byTime = candidates.groupBy { it.periods[number] }
        val choices = byTime.mapNotNull { (bell, locations) ->
            bell?.let { WeekBellChoice(it, locations.map(DayBells::label).distinct()) }
        }.sortedBy { it.bell.start }
        val unknown = byTime[null].orEmpty().map(DayBells::label).distinct()
        val source = if (scheduled.isEmpty()) "学校作息" else "本周上课地点"
        val spansDateTables = number in changes && candidates.any { it.dateTable != candidates.first().dateTable }
        WeekBellSlot(choices, unknown, source, spansDateTables)
    }
}
