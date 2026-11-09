package cn.mudlife.app.timetable

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.Layout
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.core.view.doOnLayout
import cn.mudlife.app.ui.theme.DarkColors
import cn.mudlife.app.ui.theme.LightColors
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt

/** Draws one week. The parent pager handles horizontal navigation. */
internal class WeeklyTimetableView(
    context: Context,
    private val onCourse: (SavedCourse) -> Unit,
    private val onEmpty: (weekday: Int, period: Int) -> Unit,
    private val onCluster: (List<SavedCourse>) -> Unit,
    private val dark: Boolean = timetableThemeIsDark(context),
    private val onBell: ((Int) -> Unit)? = null
) : LinearLayout(context) {
    private val theme = if (dark) DarkColors else LightColors
    private val courseTypeface = Typeface.create("sans-serif", Typeface.NORMAL)
    private val paper = theme.Background.toArgb()
    private val rail = theme.Card.toArgb()
    private val ink = theme.TextPrimary.toArgb()
    private val muted = if (dark) theme.TextSecondary.toArgb() else ColorUtils.blendARGB(ink, theme.TextSecondary.toArgb(), .75f)
    private val rules = ColorUtils.blendARGB(paper, theme.Border.toArgb(), .8f)
    private val divider = theme.Border.toArgb()
    private val todayShade = theme.ActiveBg.toArgb()
    private val todayColumn = if (dark) ColorUtils.blendARGB(paper, todayShade, .48f) else todayShade
    private val todayHeading = theme.Accent.toArgb()
    private val wideAxis = max(dp(38), (sp(10.5f) * 2.9f).roundToInt() + dp(6))
    private val narrowAxis = max(dp(28), (sp(14f) * 1.7f).roundToInt() + dp(4))
    private var rowHeight = 0
    private val headers = LinearLayout(context)
    private val headerDivider = View(context).apply {
        setBackgroundColor(divider)
        visibility = INVISIBLE
    }
    private val columns = LinearLayout(context)
    private val scroll = ScrollView(context)
    val scrollPosition: Int get() = scroll.scrollY

    fun setBottomInset(pixels: Int) {
        if (scroll.paddingBottom != pixels) scroll.setPadding(0, 0, 0, pixels)
    }

    fun scrollToPosition(value: Int) {
        scroll.doOnLayout { scroll.scrollTo(0, value.coerceAtLeast(0)) }
    }

    private var wallpaper = false
    private var selectedColors: Map<String, Int> = emptyMap()
    private var palettePositions: Map<String, Int> = emptyMap()
    private var paletteNames: List<String> = emptyList()
    private var longBreaks: Set<Int> = emptySet()
    private var axisStart: Map<Int, String> = emptyMap()
    private var axisEnd: Map<Int, String> = emptyMap()
    private var axisDetails: Map<Int, String> = emptyMap()
    private var renderedCount = 0
    private var visibleDays: List<Int> = emptyList()
    private var showTimes = true
    private var cardOptions = TimetableDisplayOptions()
    private var inactiveDays: Map<Int, List<SavedCourse>> = emptyMap()
    private var displayedDays: Map<Int, List<SavedCourse>> = emptyMap()
    private val pressTint = ColorStateList.valueOf(ColorUtils.setAlphaComponent(todayHeading, if (dark) 54 else 32))
    private val monthLabel = text("", 11f, muted).apply { gravity = Gravity.CENTER }
    private val dayHeaders = (1..7).map { DayHeader(it) }

    init {
        orientation = VERTICAL
        setBackgroundColor(paper)
        headers.orientation = HORIZONTAL
        headers.addView(monthLabel, LayoutParams(wideAxis, LayoutParams.MATCH_PARENT))
        dayHeaders.forEach { headers.addView(it, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)) }
        columns.orientation = HORIZONTAL
        addView(headers, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(headerDivider, LayoutParams(LayoutParams.MATCH_PARENT, 1))
        scroll.isVerticalScrollBarEnabled = false
        scroll.clipToPadding = false
        scroll.addView(columns)
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }

    fun render(
        monday: LocalDate?,
        activeCourses: List<SavedCourse>,
        periodCount: Int = 12,
        startTimes: Map<Int, String> = emptyMap(),
        today: LocalDate = LocalDate.now(),
        endTimes: Map<Int, String> = emptyMap(),
        courseColors: Map<String, Int> = emptyMap(),
        wallpaperVisible: Boolean = false,
        allCourseNames: List<String> = activeCourses.map { it.course.name },
        options: TimetableDisplayOptions = TimetableDisplayOptions(),
        inactiveCourses: List<SavedCourse> = emptyList(),
        bellDetails: Map<Int, String> = emptyMap()
    ) {
        val ghosts = if (options.showInactive && options.inactiveCardOpacity > 0) inactiveCourses else emptyList()
        val days = (1..7).filter { it <= 5 || it == 6 && options.showSaturday || it == 7 && options.showSunday }
        val naturalCount = maxOf(periodCount, activeCourses.maxOfOrNull { it.course.endPeriod } ?: 1,
            ghosts.maxOfOrNull { it.course.endPeriod } ?: 1)
        val count = (options.periodCount ?: naturalCount).coerceIn(1, TimetableLimits.MAX_PERIODS)
        val changedBackground = wallpaper != wallpaperVisible
        val changedDays = visibleDays != days
        val minHeight = if (options.showBellTimes) (sp(14f) + 2 * sp(10.5f)) * 1.15f
            else (sp(COURSE_TITLE_SP) + sp(COURSE_PLACE_SP)) * 1.2f + dp(7)
        // Leave one line for the title and one per enabled detail even at large system font scales.
        val detailLines = listOf(options.showPlace, options.showTeacher, options.showInactive).count { it }
        val minimumCard = sp(COURSE_TITLE_SP) * 1.2f + detailLines * (sp(COURSE_PLACE_SP) * 1.2f + dp(1)) + dp(5)
        val newRowHeight = maxOf(dp(options.rowHeightDp), minHeight.roundToInt(), minimumCard.roundToInt())
        val rebuildBody = renderedCount != count || changedDays || rowHeight != newRowHeight
        val changedTimes = showTimes != options.showBellTimes
        if (cardOptions.showGridLines != options.showGridLines) {
            headerDivider.visibility = if (options.showGridLines) VISIBLE else INVISIBLE
            for (index in 0 until columns.childCount) columns.getChildAt(index).invalidate()
        }
        val changedStyle = changedBackground || cardOptions.cardOpacity != options.cardOpacity ||
            cardOptions.inactiveCardOpacity != options.inactiveCardOpacity || cardOptions.cornerRadiusDp != options.cornerRadiusDp ||
            cardOptions.showPlace != options.showPlace || cardOptions.showTeacher != options.showTeacher ||
            cardOptions.centerTextHorizontally != options.centerTextHorizontally ||
            cardOptions.centerTextVertically != options.centerTextVertically
        val oldColors = selectedColors
        val oldPositions = palettePositions
        wallpaper = wallpaperVisible
        rowHeight = newRowHeight
        showTimes = options.showBellTimes
        cardOptions = options
        selectedColors = courseColors
        if (paletteNames != allCourseNames) {
            paletteNames = allCourseNames.toList()
            palettePositions = palettePositions(paletteNames)
        }
        if (renderedCount == 0 || changedBackground) {
            setBackgroundColor(if (wallpaper) Color.TRANSPARENT else paper)
            headers.setBackgroundColor(if (wallpaper) Color.TRANSPARENT else paper)
            monthLabel.setBackgroundColor(if (wallpaper) Color.TRANSPARENT else rail)
        }
        val width = if (showTimes) wideAxis else narrowAxis
        if (monthLabel.layoutParams.width != width) {
            monthLabel.layoutParams = monthLabel.layoutParams.apply { this.width = width }
        }
        if (changedDays) {
            dayHeaders.forEach { header -> header.visibility = if (header.weekday in days) VISIBLE else GONE }
        }
        val headerHeight = if (monday == null) dp(40) else max(dp(49), (sp(16f) * 3f).roundToInt())
        if (headers.layoutParams.height != headerHeight) {
            headers.layoutParams = headers.layoutParams.apply { height = headerHeight }
        }
        val month = monday?.let { "${it.monthValue}月" } ?: "节次"
        if (monthLabel.text != month) monthLabel.text = month
        if (rebuildBody) columns.removeAllViews()
        val changedBells = rebuildBody || axisStart != startTimes || axisEnd != endTimes || axisDetails != bellDetails
        if (changedBells || changedBackground || changedTimes) {
            axisStart = startTimes
            axisEnd = endTimes
            axisDetails = bellDetails
            longBreaks = (1 until count).filter { period ->
                val end = endTimes[period]?.let(::minutes)
                val next = startTimes[period + 1]?.let(::minutes)
                end != null && next != null && next - end >= 40
            }.toSet()
            if (!rebuildBody) columns.removeViewAt(0)
            columns.addView(periodAxis(count, startTimes, endTimes), 0, LayoutParams(width, LayoutParams.MATCH_PARENT))
        }
        val byDay = activeCourses.groupBy { it.course.weekday }
        val ghostsByDay = ghosts.groupBy { it.course.weekday }
        for ((index, weekday) in days.withIndex()) {
            val date = monday?.plusDays((weekday - 1).toLong())
            val isToday = date == today
            dayHeaders[weekday - 1].bind(date, isToday, changedBackground || changedDays)
            val column = if (rebuildBody) DayColumn(count).also {
                columns.addView(it, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
            } else columns.getChildAt(index + 1) as DayColumn
            column.updateShade(isToday)
            if (changedBells || changedBackground) column.invalidate()
            val courses = byDay[weekday].orEmpty()
            val inactive = ghostsByDay[weekday].orEmpty()
            fun colorChanged(row: SavedCourse): Boolean {
                val name = row.course.name
                return oldColors[name] != courseColors[name] || oldPositions[name] != palettePositions[name]
            }
            if (rebuildBody || changedStyle || courses != displayedDays[weekday].orEmpty() ||
                inactive != inactiveDays[weekday].orEmpty() || courses.any(::colorChanged) || inactive.any(::colorChanged)) {
                column.updateCells(weekday, courses, inactive, changedStyle)
            }
        }
        displayedDays = byDay
        inactiveDays = ghostsByDay
        renderedCount = count
        visibleDays = days
    }

    private inner class DayHeader(val weekday: Int) : FrameLayout(context) {
        private val label = dayName(weekday)
        private val weekdayLabel = text(label, 11.5f, muted).apply { gravity = Gravity.CENTER }
        private val dateLabel = text("", 14f, muted).apply {
            gravity = Gravity.CENTER
            setFontFeatureSettings("tnum")
        }
        private val header = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            addView(weekdayLabel)
            addView(dateLabel)
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        private val todayMark = View(context).apply { setBackgroundColor(todayHeading) }
        private var boundDate: LocalDate? = null
        private var boundToday: Boolean? = null

        init {
            addView(header, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            addView(todayMark, FrameLayout.LayoutParams(dp(18), dp(2), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        }

        fun bind(date: LocalDate?, isToday: Boolean, changedBackground: Boolean) {
            if (date == boundDate && isToday == boundToday && !changedBackground) return
            if (isToday != boundToday || changedBackground) {
                setBackgroundColor(if (isToday && !wallpaper) todayShade else Color.TRANSPARENT)
                todayMark.visibility = if (isToday) VISIBLE else GONE
                val tint = if (isToday) todayHeading else muted
                weekdayLabel.setTextColor(tint)
                dateLabel.setTextColor(tint)
            }
            if (boundToday == null || (boundDate == null) != (date == null)) {
                weekdayLabel.textSize = if (date == null) 13f else 11.5f
                dateLabel.visibility = if (date == null) GONE else VISIBLE
            }
            if (date != null) dateLabel.text =
                if (date.dayOfMonth == 1 && weekday != 1) "${date.monthValue}/1" else date.dayOfMonth.toString()
            contentDescription = if (date == null) label else
                "${date.monthValue}月${date.dayOfMonth}日，$label" + if (isToday) "，今天" else ""
            boundDate = date
            boundToday = isToday
        }
    }

    private fun periodAxis(count: Int, startTimes: Map<Int, String>, endTimes: Map<Int, String>): View =
        DayColumn(count).apply {
            setBackgroundColor(if (wallpaper) Color.TRANSPARENT else rail)
            for (period in 1..count) {
                val time = startTimes[period].takeIf { showTimes }
                val endTime = endTimes[period].takeIf { showTimes }
                val row = LinearLayout(context).apply {
                    orientation = VERTICAL
                    gravity = Gravity.CENTER
                    addView(text(period.toString(), 12f, muted).apply {
                        gravity = Gravity.CENTER
                        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
                        setFontFeatureSettings("tnum")
                    })
                    for (mark in listOfNotNull(time, endTime).filter(String::isNotBlank)) {
                        addView(text(mark, 10.5f, muted).apply {
                            isSingleLine = true
                            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
                            gravity = Gravity.CENTER
                            setFontFeatureSettings("tnum")
                        })
                    }
                    val detail = axisDetails[period].takeIf { showTimes }
                    contentDescription = if (detail != null) "第${period}节，$detail" else
                        "第${period}节" + (time?.let { "，$it 开始" } ?: "") + (endTime?.let { "，$it 结束" } ?: "")
                    if (showTimes && onBell != null) {
                        foreground = ripple(Color.TRANSPARENT, 4)
                        setOnClickListener {
                            onBell.invoke(period)
                        }
                    }
                }
                addView(row, CellParams(period, period))
            }
        }

    private class CellParams(val first: Int, val last: Int, val inset: Int = 0) :
        FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT) {
        init { leftMargin = inset; rightMargin = inset }
    }

    private data class Cell(
        val start: Int, val end: Int, val entries: List<SavedCourse>, val inactive: Boolean,
        val customTint: Int?, val paletteIndex: Int?
    )

    private inner class DayColumn(private val count: Int) : FrameLayout(context) {
        private var cells: Map<Int, Cell> = emptyMap()
        private var shadeColor: Int? = null
        private val pen = Paint().apply {
            strokeWidth = 1f
        }
        init { setWillNotDraw(false) }

        fun updateShade(isToday: Boolean) {
            val shade = if (isToday) todayColumn else paper
            val fill = if (!wallpaper) shade else if (isToday) ColorUtils.setAlphaComponent(todayShade, 28) else Color.TRANSPARENT
            if (fill != shadeColor) {
                setBackgroundColor(fill)
                shadeColor = fill
            }
        }

        fun updateCells(weekday: Int, courses: List<SavedCourse>, inactive: List<SavedCourse>, restyle: Boolean) {
            val occupied = (1..count).map { period ->
                val active = courses.filter { period in it.course.startPeriod..it.course.endPeriod }
                if (active.isNotEmpty()) active to false else {
                    val others = inactive.filter { period in it.course.startPeriod..it.course.endPeriod }
                    others to others.isNotEmpty()
                }
            }
            val next = mutableMapOf<Int, Cell>()
            var start = 1
            while (start <= count) {
                val slot = occupied[start - 1]
                val (entries, offWeek) = slot
                var end = start
                if (entries.isNotEmpty()) while (end < count && occupied[end] == slot) end++
                val name = entries.firstOrNull()?.course?.name
                next[start] = Cell(start, end, entries, offWeek, name?.let { selectedColors[it] }, name?.let { palettePositions[it] })
                start = end + 1
            }
            // Keep focus and touch targets on unchanged cells, even when a nearby class changes.
            for (index in childCount - 1 downTo 0) {
                val first = (getChildAt(index).layoutParams as CellParams).first
                if (cells[first] != next[first] || restyle && next[first]?.entries?.isNotEmpty() == true) removeViewAt(index)
            }
            next.values.forEachIndexed { index, cell ->
                if (cell != cells[cell.start] || restyle && cell.entries.isNotEmpty()) {
                    val view = if (cell.entries.isEmpty()) View(context).apply {
                        background = ripple(Color.TRANSPARENT, 4)
                        contentDescription = "${dayName(weekday)}第${cell.start}节，添加课程"
                        setOnClickListener {
                            onEmpty(weekday, cell.start)
                        }
                    } else courseCard(weekday, cell)
                    addView(view, index, CellParams(cell.start, cell.end, if (cell.entries.isEmpty()) 0 else dp(2)))
                }
            }
            cells = next
        }
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val minimum = count * rowHeight
            val body = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY)
                max(minimum, MeasureSpec.getSize(heightMeasureSpec)) else minimum
            for (i in 0 until childCount) {
                val params = getChildAt(i).layoutParams as CellParams
                val top = (params.first - 1) * body / count
                params.topMargin = top + params.inset
                params.height = params.last * body / count - top - 2 * params.inset
            }
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(body, MeasureSpec.EXACTLY))
        }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (!cardOptions.showGridLines) return
            pen.color = if (wallpaper) ColorUtils.setAlphaComponent(rules, 100) else rules
            canvas.drawLine(0.5f, 0f, 0.5f, height.toFloat(), pen)
            for (n in 1..count) {
                pen.color = when {
                    n in longBreaks -> divider
                    wallpaper -> ColorUtils.setAlphaComponent(rules, 100)
                    else -> rules
                }
                val y = n * height / count - 0.5f
                canvas.drawLine(0f, y, width.toFloat(), y, pen)
            }
        }
    }

    private class CourseCardLayout(context: Context) : LinearLayout(context) {
        private var lastWidthSpec = -1
        private var lastHeightSpec = -1

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val height = MeasureSpec.getSize(heightMeasureSpec)
            val contentLimit = MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST)
            val title = getChildAt(0) as TextView
            if (lastWidthSpec != widthMeasureSpec || lastHeightSpec != heightMeasureSpec) {
                if (title.maxLines != Int.MAX_VALUE) title.maxLines = Int.MAX_VALUE
                lastWidthSpec = widthMeasureSpec
                lastHeightSpec = heightMeasureSpec
            }
            // Measure a compact group. Only the weighted title gives up space for the details.
            super.onMeasure(widthMeasureSpec, contentLimit)
            val lines = title.layout
            val room = title.measuredHeight
            if (lines != null && room > 0 && lines.height > room) {
                val last = lines.getLineForVertical(room - 1)
                val fit = (if (lines.getLineBottom(last) <= room) last + 1 else last).coerceAtLeast(1)
                if (fit != title.maxLines) {
                    title.maxLines = fit
                    super.onMeasure(widthMeasureSpec, contentLimit)
                }
            }
            // Keep the full touch target while LinearLayout positions the compact group using its gravity.
            setMeasuredDimension(measuredWidth, height)
        }
    }

    private fun courseCard(weekday: Int, cell: Cell): View {
        val (start, end, entries, inactive) = cell
        val course = entries.first().course
        val swatch = PALETTE[palettePositions[course.name] ?: Math.floorMod(course.name.hashCode(), PALETTE.size)]
        val custom = selectedColors[course.name]?.let { ColorUtils.setAlphaComponent(it, 255) }
        val base = when {
            inactive -> theme.SurfaceVariant.toArgb()
            dark -> darkCourseFill(custom ?: swatch)
            custom != null -> custom
            else -> ColorUtils.blendARGB(paper, swatch, .30f)
        }
        // Inactive cards fade as a single layer so their lettering fades exactly as much as their fill.
        val fill = if (inactive) base else ColorUtils.setAlphaComponent(base, (cardOptions.cardOpacity * 255 / 100f).roundToInt())
        val lightest = ColorUtils.compositeColors(fill, if (wallpaper) Color.WHITE else paper)
        val darkest = ColorUtils.compositeColors(fill, if (wallpaper) Color.BLACK else paper)
        // Both extreme wallpapers bound the luminance of every background under the card.
        val solidText = when {
            ColorUtils.calculateLuminance(ink) <= ColorUtils.calculateLuminance(darkest) &&
                ColorUtils.calculateContrast(ink, darkest) >= 4.5 -> ink
            ColorUtils.calculateLuminance(ink) >= ColorUtils.calculateLuminance(lightest) &&
                ColorUtils.calculateContrast(ink, lightest) >= 4.5 -> ink
            ColorUtils.calculateContrast(Color.BLACK, darkest) >= 4.5 -> Color.BLACK
            ColorUtils.calculateContrast(Color.WHITE, lightest) >= 4.5 -> Color.WHITE
            else -> null
        }
        val horizontalGravity = if (cardOptions.centerTextHorizontally) Gravity.CENTER_HORIZONTAL else Gravity.START
        val verticalGravity = if (cardOptions.centerTextVertically) Gravity.CENTER_VERTICAL else Gravity.TOP
        val detailInk = if (dark && solidText != null) {
            val softer = ColorUtils.blendARGB(solidText, base, .18f)
            if (ColorUtils.calculateContrast(softer, lightest) >= 4.5 && ColorUtils.calculateContrast(softer, darkest) >= 4.5)
                softer else solidText
        } else solidText ?: ink
        fun cardText(value: String, size: Float, tint: Int = solidText ?: ink) = text(value, size, tint).apply {
            typeface = courseTypeface
            gravity = horizontalGravity or Gravity.CENTER_VERTICAL
            if (solidText == null) {
                val shadow = if (ColorUtils.calculateLuminance(ink) > .5) Color.BLACK else Color.WHITE
                val density = resources.displayMetrics.density
                setShadowLayer(1.5f * density, 0f, .35f * density, ColorUtils.setAlphaComponent(shadow, 220))
            }
        }
        val clash = entries.size > 1
        val periods = end - start + 1
        return CourseCardLayout(context).apply {
            orientation = VERTICAL
            gravity = horizontalGravity or verticalGravity
            if (inactive) alpha = cardOptions.inactiveCardOpacity / 100f
            background = ripple(fill, cardOptions.cornerRadiusDp)
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            clipToOutline = true
            val verticalPadding = if (periods == 1 || rowHeight < dp(48)) dp(1) else dp(4)
            setPadding(dp(3), verticalPadding, dp(3), verticalPadding)
            val name = cardText(course.name, COURSE_TITLE_SP).apply {
                ellipsize = TextUtils.TruncateAt.END
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            addView(name, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, 1f))
            fun detail(value: String, lines: Int, size: Float = COURSE_LABEL_SP) {
                if (value.isBlank()) return
                addView(cardText(value, size, detailInk).apply {
                    maxLines = lines
                    ellipsize = TextUtils.TruncateAt.END
                    importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
                }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(1) })
            }
            if (clash) {
                detail(if (inactive) "非本周 · ${entries.size}条" else "${entries.size}门重叠", 1)
            } else {
                if (inactive) detail("非本周", 1)
                if (cardOptions.showPlace) detail(compactPlace(course.place, periods == 1), if (periods == 1) 1 else 2, COURSE_PLACE_SP)
                if (cardOptions.showTeacher) detail(course.teacher, if (periods == 1) 1 else 2)
            }
            val span = if (start == end) "第${start}节" else "第${start}至${end}节"
            contentDescription = "${dayName(weekday)}，$span，" + (if (inactive) "非本周，" else "") +
                if (clash) (if (inactive) "${entries.size}条安排，" else "${entries.size}门课程重叠，") +
                    entries.joinToString("、") { it.course.name }
                else listOfNotNull(course.name, course.place.takeIf { cardOptions.showPlace },
                    course.teacher.takeIf { cardOptions.showTeacher }, course.weeks.ifBlank { "每周" })
                    .filter(String::isNotBlank).joinToString("，")
            isFocusable = true
            setOnClickListener {
                if (clash) onCluster(entries) else onCourse(entries.first())
            }
        }
    }

    // Keep the official location intact in the detail sheet.
    private fun compactPlace(place: String, singlePeriod: Boolean): String {
        val raw = place.trim()
        val short = raw.removePrefix("本部").trim().ifEmpty { return raw }
        val match = ROOM.matchEntire(short) ?: return short
        val building = match.groupValues[1].replace(NUMBERED_BUILDING, "$1")
        val room = match.groupValues[2]
        return if (singlePeriod) "$room · $building" else "$building\n$room"
    }

    private fun minutes(mark: String): Int? {
        val split = mark.indexOf(':')
        if (split <= 0) return null
        val hour = mark.substring(0, split).toIntOrNull() ?: return null
        val minute = mark.substring(split + 1).toIntOrNull() ?: return null
        return if (hour in 0..23 && minute in 0..59) hour * 60 + minute else null
    }

    private fun text(value: String, size: Float, tint: Int) = TextView(context).apply {
        text = value
        textSize = size
        setTextColor(tint)
        includeFontPadding = false
        breakStrategy = Layout.BREAK_STRATEGY_SIMPLE
    }
    private fun dayName(n: Int): String = "周" + "一二三四五六日"[n - 1]
    private fun round(fill: Int, radius: Int) = GradientDrawable().apply { setColor(fill); cornerRadius = dp(radius).toFloat() }
    private fun ripple(fill: Int, radius: Int) = RippleDrawable(
        pressTint, round(fill, radius), round(Color.WHITE, radius)
    )
    private fun dp(n: Int) = (n * resources.displayMetrics.density).roundToInt()
    private fun sp(n: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, n, resources.displayMetrics)

    companion object {
        private const val COURSE_TITLE_SP = 13f
        private const val COURSE_PLACE_SP = 12f
        private const val COURSE_LABEL_SP = 11f
        private val ROOM = Regex("""(.+?[楼馆群室区座厅场])\s*[-－]?\s*([A-Za-z]?\d{2,5}[A-Za-z]?)""")
        private val NUMBERED_BUILDING = Regex("""(\d+号)楼$""")
        private val PALETTE = listOf("#478377", "#628BC3", "#CC8555", "#9076AB", "#BA9C42", "#4B949F",
            "#B06F8D", "#8A9E64", "#777FB1", "#BA766F", "#628EA5", "#A88D6B").map(Color::parseColor)

        // Allocate across the whole semester so simultaneous courses are easy to distinguish as weeks change.
        private fun palettePositions(names: List<String>): Map<String, Int> {
            val usage = IntArray(PALETTE.size)
            return names.distinct().sorted().associateWith { name ->
                val start = Math.floorMod(name.hashCode(), PALETTE.size)
                val leastUsed = usage.minOrNull()
                val slot = (PALETTE.indices).map { (start + it) % PALETTE.size }.first { usage[it] == leastUsed }
                usage[slot]++
                slot
            }
        }
    }
}
