package cn.mudlife.app.timetable

import android.content.Context
import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import java.time.LocalDate
import kotlin.math.roundToInt
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], manifest = Config.NONE, qualifiers = "mdpi")
class WeeklyTimetableViewTest {
    private val single = SavedCourse("s", TimetableCourse("课程一", 1, 1, 1, "每周", "甲楼101", "王老师"))
    private val double = SavedCourse("d", TimetableCourse("实验课", 1, 3, 4, "每周", "乙楼101", "张老师"))
    private val monday = LocalDate.of(2025, 3, 3)

    private fun render(grid: WeeklyTimetableView, height: Int): ViewGroup {
        grid.render(monday, listOf(single, double), periodCount = 6,
            startTimes = mapOf(1 to "08:00"), endTimes = mapOf(1 to "08:45"),
            options = TimetableDisplayOptions(rowHeightDp = height, showPlace = true, showTeacher = true, showInactive = true))
        val size = View.MeasureSpec.EXACTLY
        grid.measure(View.MeasureSpec.makeMeasureSpec(395, size), View.MeasureSpec.makeMeasureSpec(520, size))
        grid.layout(0, 0, grid.measuredWidth, grid.measuredHeight)
        return (grid.getChildAt(2) as ScrollView).getChildAt(0) as ViewGroup
    }

    @Test fun heightControlsAxisSingleAndSpanningCoursesAfterRebinding() {
        val context = RuntimeEnvironment.getApplication() as Context
        val grid = WeeklyTimetableView(context, {}, { _, _ -> }, {}, dark = false)
        val inset = (2 * context.resources.displayMetrics.density).roundToInt()
        for (chosenDp in listOf(40, 56, 96, 41)) {
            val row = (chosenDp * context.resources.displayMetrics.density).roundToInt()
            val body = render(grid, chosenDp)
            val axis = body.getChildAt(0) as ViewGroup
            val day = body.getChildAt(1) as ViewGroup
            assertEquals(6 * row, body.height)
            assertEquals(body.height, day.height)
            assertEquals(row, axis.getChildAt(0).height)
            assertEquals(2 * row, axis.getChildAt(2).top)
            assertEquals(row - 2 * inset, day.getChildAt(0).height)
            assertEquals(axis.getChildAt(2).top + inset, day.getChildAt(2).top)
            assertEquals(2 * row - 2 * inset, day.getChildAt(2).height)
        }
    }

    @Test fun compactCellKeepsTheCourseAndPeriodReadableWithFullDetailsAvailable() {
        val app = RuntimeEnvironment.getApplication() as Context
        val context = app.createConfigurationContext(Configuration(app.resources.configuration).apply { fontScale = 1.8f })
        var selected: SavedCourse? = null
        var bell: Int? = null
        val grid = WeeklyTimetableView(context, { selected = it }, { _, _ -> }, {}, dark = false, onBell = { bell = it })
        val body = render(grid, 40)
        val period = (body.getChildAt(0) as ViewGroup).getChildAt(0) as ViewGroup
        val card = (body.getChildAt(1) as ViewGroup).getChildAt(0) as ViewGroup
        for (group in listOf(card, period)) {
            val primary = group.getChildAt(0) as TextView
            assertEquals(View.VISIBLE, primary.visibility)
            assertTrue(primary.height > 0)
            assertTrue("首行被裁切: ${primary.text}", primary.height >= primary.layout.getLineBottom(0))
            assertTrue((1 until group.childCount).any { group.getChildAt(it).visibility == View.GONE })
        }
        assertTrue(card.contentDescription.contains("王老师"))
        assertTrue(period.contentDescription.contains("08:45"))
        assertTrue(card.performClick())
        assertEquals(single, selected)
        assertTrue(period.performClick())
        assertEquals(1, bell)
    }
}
