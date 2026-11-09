package cn.mudlife.app.timetable

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class TeachingCalendarTest {
    private val monday = LocalDate.of(2026, 8, 31)
    private val calendar = TeachingCalendar(monday)

    @Test fun discontinuousAndParityRanges() {
        assertEquals(setOf(1, 2, 3, 5, 6, 7, 8), TeachingWeeks.parse("1-3周,5-8周"))
        assertEquals(setOf(2, 4, 6, 10, 11), TeachingWeeks.parse("第１～６周（双周）、10周；11周"))
        assertEquals(setOf(1, 3, 5), TeachingWeeks.parse("1至6(单)"))
        assertEquals(26, TeachingWeeks.parse("双周")!!.size)
        assertEquals((1..53).toSet(), TeachingWeeks.parse(" "))
        for (wrong in listOf("3-1周", "54", "1周,abc", "2周(单)", "1,,3", "1周,")) assertNull(TeachingWeeks.parse(wrong))
    }

    @Test fun campusCalendarHasRealWeekBoundaries() {
        assertEquals(monday, UjsTimetable.firstMonday("2026-2027学年第1学期"))
        assertNull(UjsTimetable.firstMonday("自定义课表"))
        assertEquals(0, calendar.weekOf(monday.minusDays(1)))
        assertEquals(-1, calendar.weekOf(monday.minusDays(8)))
        assertEquals(4, calendar.weekOf(LocalDate.of(2026, 9, 23)))
        assertEquals(LocalDate.of(2026, 11, 8), calendar.dateOf(10, 7))
        assertEquals(LocalDate.of(2027, 2, 22), UjsTimetable.firstMonday("2026-2027学年第2学期"))
    }

    @Test fun officialBellsDependOnTeachingBuildingAndActualEffectiveDate() {
        val oct = LocalDate.of(2026, 10, 8)
        fun start(date: LocalDate, place: String, n: Int) = UjsTimetable.periods(date, place).find { it.number == n }?.start
        assertEquals(LocalTime.of(14, 0), start(oct.minusDays(1), "三山楼", 5))
        assertEquals(LocalTime.of(13, 30), start(oct, "三山楼", 5))
        assertEquals(LocalTime.of(10, 20), start(oct, "三山楼301", 3))
        assertEquals(LocalTime.of(10, 10), start(oct, "三江楼", 3))
        assertEquals(LocalTime.of(10, 0), start(oct, "主A楼", 3))
        assertEquals(LocalTime.of(16, 15), start(oct, "北固校区", 8))
        assertEquals(LocalTime.of(16, 25), start(oct, "三江楼", 8))
        assertNull(start(oct, "", 3))
        assertNull(start(oct, "实验室", 8))
        assertNull(start(oct, "长山校区", 1))
    }

    @Test fun liveAndNextLessonsRespectRecessAndWeekSelection() {
        val row = SavedCourse("c", TimetableCourse("课程", 1, 1, 2, "1,3周", "三江楼"))
        fun next(time: LocalDateTime) = calendar.nextClass(listOf(row), time, UjsTimetable::periods)
        val start = monday.atTime(8, 0)
        assertEquals(true, next(start)!!.isOngoing)
        val breakNext = next(monday.atTime(8, 45))!!
        assertFalse(breakNext.isOngoing)
        assertEquals(monday.atTime(8, 55), breakNext.starts)
        assertEquals(monday.plusWeeks(2).atTime(8, 0), next(monday.atTime(9, 40))!!.starts)
        assertNull(next(monday.plusWeeks(2).atTime(9, 40)))
    }

    @Test fun numberedJingjiangRoomsReceiveTheOfficialThirdAndFourthBells() {
        val week = LocalDate.of(2026, 11, 9)
        val rows = listOf("本部京江1号楼2207", "京江3号楼3101", "京江２号楼2105").mapIndexed { i, place ->
            SavedCourse(i.toString(), TimetableCourse("课程", i + 1, 3, 4, "11周", place))
        }
        val axis = weekBellAxis(week, rows, emptyList())
        assertEquals("10:00", axis.getValue(3).startLabel)
        assertEquals("10:45", axis.getValue(3).endLabel)
        assertEquals("10:55", axis.getValue(4).startLabel)
        assertEquals("11:40", axis.getValue(4).endLabel)
        assertEquals(3, axis.getValue(3).choices.single().locations.size)
        assertFalse(UjsTimetable.periods(week, "京江学院体育馆").any { it.number == 3 })
    }

    @Test fun theAxisSeparatesRealBuildingTimesAndLeavesUnknownLocationsUnconfirmed() {
        val week = LocalDate.of(2026, 11, 9)
        fun row(i: Int, room: String) = SavedCourse(i.toString(), TimetableCourse("课程", i, 3, 4, "11周", room))
        val mixed = listOf(row(1, "京江3号楼3101"), row(2, "三山楼301"))
        val third = weekBellAxis(week, mixed, emptyList()).getValue(3)
        assertEquals("分楼栋", third.endLabel)
        assertEquals(listOf("10:00", "10:20"), third.choices.map { it.bell.start.toString() })
        assertEquals(listOf("周二 · 三山楼301"), third.choices.last().locations)
        val partial = weekBellAxis(week, mixed + row(3, "实验室"), emptyList()).getValue(3)
        assertEquals("有待定", partial.endLabel)
        assertEquals(listOf("周三 · 实验室"), partial.unresolved)
        val empty = weekBellAxis(week, emptyList(), emptyList()).getValue(3)
        assertEquals(listOf("10:00", "10:10", "10:20"), empty.choices.map { it.bell.start.toString() })
    }

    @Test fun theAxisReflectsMidweekSeasonChangesAndRespectsPartialCustomSchedules() {
        val week = LocalDate.of(2026, 10, 5)
        val rows = listOf(1, 5).map { day ->
            SavedCourse(day.toString(), TimetableCourse("课程", day, 5, 6, "每周", "三江楼"))
        }
        val axis = weekBellAxis(week, rows, emptyList())
        assertEquals("分日期", axis.getValue(5).endLabel)
        assertEquals(listOf("13:30", "14:00"), axis.getValue(5).choices.map { it.bell.start.toString() })
        assertEquals("分楼栋", axis.getValue(3).endLabel)
        val custom = listOf(BellPeriod(4, LocalTime.of(11, 7), LocalTime.of(11, 52)))
        val override = weekBellAxis(week, rows, custom)
        assertEquals(setOf(4), override.keys)
        assertEquals("11:07", override.getValue(4).startLabel)
    }

    @Test fun desktopAgendaUsesTodaysWeekDayPeriodAndPlace() {
        val date = LocalDate.of(2026, 11, 9) // week 11, Monday
        val rows = listOf(
            SavedCourse("b", TimetableCourse("下午课", 1, 5, 6, "9,11周", "三山楼")),
            SavedCourse("a", TimetableCourse("上午课", 1, 3, 4, "1-19周(单)", "三江楼")),
            SavedCourse("c", TimetableCourse("双周课", 1, 1, 2, "双周")),
            SavedCourse("d", TimetableCourse("周二课", 2, 1, 2, "")),
            SavedCourse("e", TimetableCourse("晚课", 1, 9, 10, "每周", "长山校区"))
        )
        val today = TodayAgenda.forDay(date, calendar, rows) { UjsTimetable.periods(date, it) }
        assertTrue(today.date.contains("第11周"))
        assertEquals(listOf("上午课", "下午课", "晚课"), today.items.map { it.name })
        assertEquals("3–4节", today.items.first().periods)
        assertEquals("10:10–11:50 · 三江楼", today.items.first().detail)
        assertEquals("长山校区", today.items.last().detail)
        assertEquals("今天没有安排课程", TodayAgenda.forDay(date.plusDays(2), calendar, rows) { emptyList() }.message)
        assertEquals("所选学期已结束", TodayAgenda.forDay(date.plusWeeks(20), calendar, rows) { emptyList() }.message)
    }

    @Test fun shortenedTermEndsAfterItsLastSundayForTheWidgetAndNextClass() {
        val short = TeachingCalendar(monday, 2)
        val rows = listOf(SavedCourse("s", TimetableCourse("周日课程", 7, 1, 1, "每周", "三江楼")))
        val lastSunday = monday.plusDays(13)
        fun agenda(day: LocalDate) = TodayAgenda.forDay(day, short, rows) { UjsTimetable.periods(day, it) }
        assertEquals(listOf("周日课程"), agenda(lastSunday).items.map { it.name })
        assertEquals("所选学期已结束", agenda(lastSunday.plusDays(1)).message)
        assertNull(short.nextClass(rows, lastSunday.atTime(23, 0), UjsTimetable::periods))
    }

    @Test fun olderLongTermEndsAfterWeekTwentyWithoutDiscardingImportedWeeks() {
        val oldLimit = TeachingCalendar(monday, 53)
        val course = TimetableCourse("补课", 1, 1, 1, "20,21,53周", "三江楼")
        val rows = listOf(SavedCourse("l", course))
        val week20 = monday.plusWeeks(19)
        val week21 = week20.plusWeeks(1)
        assertEquals(20, calendar.lastWeek)
        assertEquals(20, oldLimit.lastWeek)
        assertEquals(setOf(20, 21, 53), TeachingWeeks.parse(course.weeks))
        assertEquals(listOf("补课"), TodayAgenda.forDay(week20, oldLimit, rows) { UjsTimetable.periods(week20, it) }.items.map { it.name })
        assertEquals("所选学期已结束", TodayAgenda.forDay(week21, oldLimit, rows) { emptyList() }.message)
        assertNull(oldLimit.nextClass(rows, week20.atTime(23, 0), UjsTimetable::periods))
    }

    @Test fun bellAxisStopsAtFifteenEvenWithLongImportedCoursesAndOlderCustomBells() {
        val rows = listOf(SavedCourse("l", TimetableCourse("旧安排", 1, 14, 30, "1-30周")))
        assertEquals((1..15).toSet(), weekBellAxis(monday, rows, emptyList()).keys)
        val oldBells = listOf(BellPeriod(15, LocalTime.of(20, 0), LocalTime.of(20, 45)),
            BellPeriod(16, LocalTime.of(21, 0), LocalTime.of(21, 45)))
        assertEquals(setOf(15), weekBellAxis(monday, rows, oldBells).keys)
        assertEquals(30, rows.single().course.endPeriod)
        assertEquals(listOf(15, 16), oldBells.map { it.number })
    }

    @Test fun desktopCardResizingShowsOnlyWholeCoursesAndUpdatesTheOverflowCount() {
        val sizes = agendaCardSizes(total = 3, fixedHeightPx = 96, rowHeightPx = 86, density = 2f)
        fun cardAt(heightDp: Int) = sizes.last { it.minHeightDp <= heightDp }
        val short = cardAt(140)
        assertEquals(2, short.visibleRows)
        assertEquals(1, short.remainingRows)
        val tall = cardAt(181)
        assertEquals(3, tall.visibleRows)
        assertEquals(0, tall.remainingRows)
    }
}
