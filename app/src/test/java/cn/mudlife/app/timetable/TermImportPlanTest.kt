package cn.mudlife.app.timetable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TermImportPlanTest {
    private val school = "2026-2027学年第1学期"
    private val next = "2026-2027学年第2学期"
    private val local = "大三上"

    @Test fun emptyOrBoundLocalTermReceivesTheImportEvenIfTheSchoolTermAlreadyExists() {
        val expected = TermImportPlan(local, true)
        assertEquals(expected, planTermImport(school, local, mapOf(local to null, school to school)))
        assertEquals(expected, planTermImport(school, local, mapOf(local to school, school to school)))
    }

    @Test fun switchingTheSchoolPagePreservesThePreviouslyBoundLocalTimetable() {
        assertEquals(TermImportPlan(next, false), planTermImport(next, local, mapOf(local to school)))
        assertEquals(TermImportPlan(next, true), planTermImport(next, local, mapOf(local to school, next to next)))
    }

    @Test fun firstImportUsesTheSchoolNameAndADeletedOrConflictingTargetIsRejected() {
        assertEquals(TermImportPlan(school, false), planTermImport(school, null, emptyMap()))
        assertThrows(IllegalArgumentException::class.java) { planTermImport(school, local, emptyMap()) }
        assertThrows(IllegalArgumentException::class.java) { planTermImport(school, null, mapOf(school to next)) }
    }
}
