package cn.mudlife.app.timetable

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import cn.mudlife.app.timetable.TimetableActivity.Companion.EXTRA_TERM
import cn.mudlife.app.timetable.TimetableActivity.Companion.EXTRA_ACCOUNT
import cn.mudlife.app.ui.theme.AppColors
import cn.mudlife.app.ui.theme.ThemeMode
import cn.mudlife.app.ui.TimetableTabPage
import cn.mudlife.campuscard.SessionStore
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// Both timetable windows live on the main thread. A widget edit must invalidate the dormant tab.
private var standaloneRevision = 0L

@Composable
internal fun rememberTimetableTab(activity: ComponentActivity, owner: String?): TimetableTabPage? {
    if (owner.isNullOrEmpty()) return null
    val dark = AppColors.isDark
    val tab = remember(activity, owner, dark) { RetainedTimetableTab(activity, owner, dark) }
    DisposableEffect(tab) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) tab.onPause()
        }
        activity.lifecycle.addObserver(observer)
        onDispose {
            activity.lifecycle.removeObserver(observer)
            tab.close()
        }
    }
    return tab
}

private class RetainedTimetableTab(val activity: ComponentActivity, val owner: String, val dark: Boolean) : TimetableTabPage {
    val session by lazy(LazyThreadSafetyMode.NONE) { SessionStore(activity) }
    var controller: TimetableController? = null
    private var wasPaused = false
    private var readRevision = standaloneRevision

    @Composable
    override fun Content(contentPadding: PaddingValues, onAccountChanged: () -> Unit) {
        TimetableContent(activity, owner, this, contentPadding, onAccountChanged)
    }

    fun onPause() { if (controller != null) wasPaused = true }

    fun consumeRefresh(): Boolean {
        val refresh = wasPaused || readRevision != standaloneRevision
        wasPaused = false
        readRevision = standaloneRevision
        return refresh
    }

    fun close() {
        controller?.close()
        controller = null
    }
}

@Composable
internal fun StandaloneTimetable(activity: ComponentActivity, owner: String, onExit: () -> Unit) {
    TimetableContent(activity, owner, null, WindowInsets.safeDrawing.asPaddingValues(), onExit)
}

@Composable
private fun TimetableContent(
    activity: ComponentActivity, owner: String, tab: RetainedTimetableTab?,
    contentPadding: PaddingValues, onExit: () -> Unit
) {
    val exit by rememberUpdatedState(onExit)
    val session = remember(activity, tab) { tab?.session ?: SessionStore(activity) }
    if (owner.isEmpty() || session.timetableOwner != owner) {
        LaunchedEffect(owner) { exit() }
        return
    }
    val standalone = tab == null
    key(owner) {
        TimetableTheme(if (tab == null) timetableThemeMode(activity) else if (tab.dark) ThemeMode.DARK else ThemeMode.LIGHT) {
            val dark = AppColors.isDark
            val paper = AppColors.Background
            fun create(saved: Bundle?) = tab?.controller ?: TimetableController(
                activity, session, owner, dark, paper.toArgb(), saved, standalone
            ) { exit() }.also { tab?.controller = it }
            val controller = rememberSaveable(activity, owner, dark, saver = Saver(
                save = { it: TimetableController -> it.snapshot() },
                restore = { saved -> create(saved) }
            )) { create(null) }
            val importer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                if (it.resultCode == Activity.RESULT_OK) controller.onImported(it.data?.getStringExtra(EXTRA_TERM))
            }
            val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) controller.onWallpaperPicked(uri)
            }
            SideEffect {
                controller.exit = { exit() }
                controller.launchImport = { importer.launch(it) }
                controller.launchPicker = { picker.launch(arrayOf("image/*")) }
            }
            DisposableEffect(controller, activity) {
                var wasPaused = false
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_PAUSE -> wasPaused = true
                        Lifecycle.Event.ON_RESUME -> {
                            controller.onResume(tab?.consumeRefresh() == true || wasPaused)
                            wasPaused = false
                        }
                        else -> Unit
                    }
                }
                activity.lifecycle.addObserver(observer)
                onDispose {
                    activity.lifecycle.removeObserver(observer)
                    controller.detach()
                    if (standalone) controller.close()
                }
            }
            key(dark) {
                Box(Modifier.fillMaxSize().background(paper)) {
                    controller.Screen(standalone, contentPadding)
                }
            }
        }
    }
}

private class TimetableController(
    private val activity: ComponentActivity,
    private val session: SessionStore,
    private val owner: String,
    private val dark: Boolean,
    paper: Int,
    saved: Bundle?,
    private val standalone: Boolean,
    var exit: () -> Unit
) {
    var launchImport: (Intent) -> Unit = {}
    var launchPicker: () -> Unit = {}
    private val store = TimetableStore(activity, owner)
    private val settings = TimetableSettings(activity, owner)
    private val appearance = TimetableAppearance(activity, owner, openPicker = { launchPicker() }) {
        refreshAppearance()
        refreshWeekPages()
    }
    private val pager: TimetableWeekPager
    private val photo: ImageView
    private var heading by mutableStateOf(WeekHeading())
    private val menus = mutableStateListOf<TimetableMenuEntry>()
    private var nextMenuId = 0
    private var terms by mutableStateOf(emptyList<String>())
    private var weeksByExpression = emptyMap<String, Set<Int>?>()
    private var unknownWeeks = 0
    private var names = emptyList<String>()
    private var colors = emptyMap<String, Int>()
    private var wallpaper by mutableStateOf(false)
    private var displayOptions by mutableStateOf(TimetableDisplayOptions())
    private var noteAction: (() -> Unit)? = null
    private var rows by mutableStateOf(emptyList<SavedCourse>())
    private var week = 0
    private var lastWeek = 20
    private var viewedTerm: String? = null
    private var firstMonday by mutableStateOf<LocalDate?>(null)
    private val today get() = LocalDate.now(campusZone)
    private val shortDate = DateTimeFormatter.ofPattern("M月d日")
    private var term by mutableStateOf<String?>(null)
    private val weekdays = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    private var renderedDay: LocalDate? = null

    init {
        term = saved?.getString(EXTRA_TERM)
        viewedTerm = term
        week = saved?.getInt("week") ?: 0
        photo = ImageView(activity).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            // A continuous wash keeps the header and system icons legible without hiding the image at the edges.
            val alphas = if (dark) intArrayOf(234, 125, 156) else intArrayOf(193, 24, 78)
            val wash = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                IntArray(alphas.size) { ColorUtils.setAlphaComponent(paper, alphas[it]) })
            foreground = if (dark) {
                // Feather the backing of the small bell times into the photo, including on a bright wallpaper.
                val startEdge = if (resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL)
                    GradientDrawable.Orientation.RIGHT_LEFT else GradientDrawable.Orientation.LEFT_RIGHT
                val axis = GradientDrawable(startEdge,
                    intArrayOf(ColorUtils.setAlphaComponent(paper, 188), ColorUtils.setAlphaComponent(paper, 172),
                        ColorUtils.setAlphaComponent(paper, 0)))
                LayerDrawable(arrayOf(wash, axis)).apply {
                    setLayerWidth(1, (92 * resources.displayMetrics.density).toInt())
                    setLayerGravity(1, Gravity.START or Gravity.FILL_VERTICAL)
                }
            } else wash
        }
        pager = TimetableWeekPager(activity, {
            WeeklyTimetableView(activity, ::details, ::addCourse,
                { cluster -> openMenu(TimetablePanel.Overlaps(cluster)) }, dark,
                onBell = { openMenu(TimetablePanel.BellDetail(it)) })
        }, ::bindWeek) { selected ->
            week = selected
            updateHeading()
        }
        refresh(updateWidget = false)
        @Suppress("DEPRECATION")
        val savedMenus = saved?.getParcelableArrayList<Bundle>("timetableMenus")
        if (savedMenus != null && saved.getString(EXTRA_TERM) == term) {
            menus.addAll(restoreMenus(savedMenus, rows))
            nextMenuId = (menus.maxOfOrNull { it.id } ?: -1) + 1
        }
    }

    fun onImported(selectedTerm: String?) {
        term = selectedTerm ?: term
        refresh()
    }

    fun onWallpaperPicked(uri: Uri) = checked { appearance.setWallpaper(uri) }

    @Composable
    fun Screen(standalone: Boolean, contentPadding: PaddingValues) {
        AndroidView(factory = { photo.newHost() }, modifier = Modifier.fillMaxSize())
        showScreens(pager.view, standalone, contentPadding)
    }

    private fun refresh(updateWidget: Boolean = true) {
        checked {
            terms = store.terms()
            term = term?.takeIf { it in terms } ?: terms.firstOrNull()
            val selected = term
            firstMonday = selected?.let { settings.firstMonday(it, store.schoolTerm(it)) }
            rows = selected?.let(store::courses).orEmpty()
            weeksByExpression = rows.map { it.course.weeks }.distinct().associateWith(TeachingWeeks::parse)
            unknownWeeks = rows.count { weeksByExpression[it.course.weeks] == null }
            names = rows.map { it.course.name }.distinct()
            if (selected != null) {
                displayOptions = settings.displayOptions(selected)
                if (viewedTerm != selected || week == 0) week = calendar()?.weekOf(today) ?: 1
                viewedTerm = selected
            } else {
                displayOptions = TimetableDisplayOptions()
                viewedTerm = null
                week = 0
            }
            refreshAppearance()
            refreshWeekPages()
            if (updateWidget) TodayTimetableWidget.refresh(activity)
        }
    }

    private fun refreshAppearance() {
        wallpaper = appearance.hasWallpaper
        colors = appearance.colors(names)
        appearance.applyTo(photo)
    }


    private fun calendar() = firstMonday?.let { TeachingCalendar(it, displayOptions.termWeeks) }

    private fun refreshWeekPages() {
        if (standalone) standaloneRevision++
        renderedDay = today
        if (term == null) {
            pager.refresh(0, 1)
            heading = WeekHeading()
            return
        }
        lastWeek = displayOptions.termWeeks ?: TimetableLimits.MAX_WEEKS
        week = week.coerceIn(1, lastWeek)
        pager.refresh(if (rows.isEmpty()) 0 else lastWeek, week)
        updateHeading()
    }

    fun onResume(returning: Boolean) {
        if (owner != session.timetableOwner) {
            exit()
            return
        }
        val dayChanged = renderedDay != null && renderedDay != today
        if (dayChanged || returning) {
            val previousTerm = term
            val first = calendar()
            if (dayChanged && first != null && renderedDay?.let(first::weekOf) == week) week = 0
            refresh(updateWidget = dayChanged)
            if (previousTerm != term) menus.clear()
            else if (menus.isNotEmpty()) {
                // Keep open drafts unless the other timetable window removed or edited their source course.
                val valid = menus.takeWhile {
                    val source = (it.panel as? TimetablePanel.EditCourse)?.existing
                    source == null || rows.any { row -> row == source }
                }
                val rebound = restoreMenus(saveMenus(valid), rows)
                if (menus != rebound) { menus.clear(); menus.addAll(rebound) }
            }
        } else if (term != null) updateHeading()
    }

    private fun bindWeek(grid: WeeklyTimetableView, number: Int) {
        val monday = calendar()?.dateOf(number)
        val active = rows.filter { weeksByExpression[it.course.weeks]?.contains(number) == true }
        val inactive = if (displayOptions.showInactive) rows.filter {
            !it.isAdjustment && weeksByExpression[it.course.weeks]?.contains(number) == false
        } else emptyList()
        val bells = weekBellAxis(monday ?: today.with(java.time.DayOfWeek.MONDAY), active, settings.customPeriods())
        grid.render(monday, active, automaticPeriodCount(rows),
            bells.mapValues { it.value.startLabel }, today, bells.mapValues { it.value.endLabel },
            colors, wallpaper, allCourseNames = names, options = displayOptions, inactiveCourses = inactive,
            bellDetails = bells.mapValues { it.value.description })
    }

    private fun automaticPeriodCount(courses: List<SavedCourse>) = maxOf(11,
        settings.customPeriods().maxOfOrNull { it.number } ?: 0, courses.maxOfOrNull { it.course.endPeriod } ?: 0)
        .coerceAtMost(TimetableLimits.MAX_PERIODS)

    private fun updateHeading() {
        val calendar = calendar()
        val monday = calendar?.dateOf(week)
        val active = rows.filter { weeksByExpression[it.course.weeks]?.contains(week) == true }
        val timeSpan = monday?.let { "${it.format(shortDate)} – ${it.plusDays(6).format(shortDate)}" }
        val moment = if (calendar?.weekOf(today) == week)
            calendar.nextClass(active.filter { it.course.weekday == today.dayOfWeek.value }, LocalDateTime.now(campusZone), settings::periods) else null
        val summary = when {
            unknownWeeks > 0 -> "${unknownWeeks} 条安排待确认周次"
            moment != null && moment.starts.toLocalDate() == today ->
                "${if (moment.isOngoing) "上课中" else "下一节 ${moment.starts.toLocalTime()}"}  ${moment.row.course.name}"
            active.isEmpty() -> "本周没有课"
            else -> "本周 ${active.size} 项安排"
        }
        noteAction = when {
            calendar == null -> ::setFirstDay
            unknownWeeks > 0 -> { { openMenu(TimetablePanel.AllCourses) } }
            moment != null && moment.starts.toLocalDate() == today -> { { details(moment.row) } }
            else -> null
        }
        heading = WeekHeading(week, lastWeek, timeSpan ?: "设置开学日期", summary, calendar?.weekOf(today) == week)
    }

    private fun moveWeek(delta: Int) {
        val target = (week + delta).coerceIn(1, lastWeek)
        if (target == week) return
        pager.select(target, smooth = true)
    }

    private fun goToCurrentWeek() {
        val calendar = calendar() ?: return setFirstDay()
        val actual = calendar.weekOf(today)
        if (actual !in 1..lastWeek) {
            message("今天不在这个学期内")
            return
        }
        if (week == actual) return
        pager.select(actual, smooth = kotlin.math.abs(actual - week) == 1)
    }

    private fun setFirstDay() {
        if (term != null) openMenu(TimetablePanel.StartDate)
    }

    private fun openImport() {
        launchImport(Intent(activity, TimetableImportActivity::class.java).putExtra(EXTRA_ACCOUNT, owner).putExtra(EXTRA_TERM, term))
    }

    private fun openMenu(page: TimetablePanel) {
        menus.add(TimetableMenuEntry(nextMenuId++, page))
    }

    @Composable
    private fun showScreens(body: View, standalone: Boolean, contentPadding: PaddingValues) {
        val active = menus.lastOrNull()
        val holder = rememberSaveableStateHolder()
        // Keep drafts and scroll positions for entries that can still be revisited.
        val savedEntries = remember { mutableSetOf<Int>() }
        SideEffect {
            val current = menus.mapTo(mutableSetOf()) { it.id }
            savedEntries.filterNot { it in current }.forEach(holder::removeState)
            savedEntries.clear()
            savedEntries.addAll(current)
        }
        TimetablePages(active, contentPadding, back = { if (menus.isNotEmpty()) menus.removeAt(menus.lastIndex) }, close = { menus.clear() }, home = { interactive ->
            val direction = LocalLayoutDirection.current
            val scrollBottom = if (standalone) 0.dp else contentPadding.calculateBottomPadding()
            val scrollBottomPx = with(LocalDensity.current) { scrollBottom.roundToPx() }
            val framePadding = if (standalone) contentPadding else PaddingValues(
                start = contentPadding.calculateStartPadding(direction),
                top = contentPadding.calculateTopPadding(),
                end = contentPadding.calculateEndPadding(direction)
            )
            Column(Modifier.fillMaxSize().padding(framePadding).consumeWindowInsets(contentPadding)) {
                TimetableHeader(term, rows.isNotEmpty(), heading, exit.takeIf { standalone }, { openMenu(TimetablePanel.Seasons) },
                    { addCourse() }, { openMenu(TimetablePanel.Tools) }, { openMenu(TimetablePanel.Weeks) }, ::moveWeek,
                    ::goToCurrentWeek, noteAction, wallpaper)
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    AndroidView(factory = { body.newHost() }, modifier = Modifier.fillMaxSize(), update = {
                        pager.setBottomInset(scrollBottomPx)
                        it.importantForAccessibility = if (interactive && rows.isNotEmpty()) View.IMPORTANT_FOR_ACCESSIBILITY_YES
                            else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                    })
                    if (rows.isEmpty()) Box(Modifier.fillMaxSize().padding(bottom = scrollBottom)) {
                        EmptyTimetable(term, ::openImport) { addCourse() }
                    }
                }
            }
        }) { page ->
            val nested = remember(page.id) { menus.firstOrNull()?.id != page.id }
            val pop = { if (menus.lastOrNull()?.id == page.id) { menus.removeAt(menus.lastIndex); Unit } }
            val dismiss = { if (menus.lastOrNull()?.id == page.id) menus.clear() }
            TimetableMenuHost(back = pop.takeIf { nested }) {
                holder.SaveableStateProvider(page.id) { drawPanel(page.panel, dismiss, pop) }
            }
        }
    }

    @Composable
    private fun drawPanel(page: TimetablePanel, dismiss: () -> Unit, pop: () -> Unit) {
        when (page) {
            TimetablePanel.Tools -> TimetableActions(term, rows.size, { openMenu(TimetablePanel.Seasons) },
                { addCourse() }, { dismiss(); openImport() }, { openMenu(TimetablePanel.AllCourses) },
                { openMenu(TimetablePanel.Display) }, { openMenu(TimetablePanel.Bells) },
                { openMenu(TimetablePanel.AddWidget) })
            TimetablePanel.AddWidget -> AddWidgetPanel(dismiss, remember(activity) { TodayTimetableWidget.supportsPin(activity) }) {
                TodayTimetableWidget.requestPin(activity)
            }
            TimetablePanel.Display -> DisplayOptionsPanel(dismiss) { openMenu(TimetablePanel.DisplayGroup(it)) }
            is TimetablePanel.DisplayGroup -> DisplayGroupPanel(page.group, displayOptions, dismiss, ::setFirstDay, {
                if (wallpaper) openMenu(TimetablePanel.Wallpaper) else appearance.chooseWallpaper()
            }) { value, feedback ->
                val selected = term
                if (selected != null && value != displayOptions) checked {
                    settings.setDisplayOptions(selected, value)
                    feedback?.play(activity)
                    val updateWidget = value.termWeeks != displayOptions.termWeeks
                    displayOptions = value
                    refreshWeekPages()
                    if (updateWidget) TodayTimetableWidget.refresh(activity)
                }
            }
            TimetablePanel.Weeks -> TimetablePage("选择教学周", dismiss) {
                val first = firstMonday
                WeekChoices(week, first?.let { TeachingCalendar(it).weekOf(today) }, lastWeek, first) {
                    if (week != it) pager.select(it, smooth = false)
                    dismiss()
                }
            }
            TimetablePanel.AllCourses -> TimetablePage("全部课程", dismiss, scrollBody = false) {
                CourseDirectory(rows, weekdays, ::details)
            }
            TimetablePanel.Seasons -> TermPickerPanel(terms, term, dismiss,
                create = { openMenu(TimetablePanel.NewTerm) }, select = { label ->
                    checked {
                        if (term != label) { store.selectTerm(label); term = label; refresh() }
                        dismiss()
                    }
                }, remove = { label -> checked {
                    store.deleteTerm(label)
                    settings.clearTerm(label)
                    if (term == label) term = null
                    refresh()
                    if (terms.isEmpty()) dismiss()
                    TimetableFeedback.Saved.play(activity)
                }
            })
            TimetablePanel.StartDate -> term?.let { selected ->
                StartDatePanel(selected, firstMonday, today, dismiss) { monday ->
                    checked {
                        if (firstMonday != monday) {
                            settings.setFirstMonday(selected, monday)
                            TimetableFeedback.Saved.play(activity)
                            week = 0
                            refresh()
                        }
                        pop()
                    }
                }
            }
            TimetablePanel.NewTerm -> NewTermPanel(dismiss) { name ->
                checked {
                    term = store.createTerm(name)
                    refresh()
                    dismiss()
                    TimetableFeedback.Saved.play(activity)
                }
            }
            TimetablePanel.Wallpaper -> TimetablePage("课表背景", dismiss) {
                MenuSection {
                    MenuRow(Icons.Outlined.Image, "更换图片") { appearance.chooseWallpaper() }
                    MenuRow(Icons.Outlined.RestartAlt, "恢复默认背景") { appearance.removeWallpaper(); pop() }
                }
            }
            TimetablePanel.Bells -> BellSettingsPanel(settings.customPeriods(), dismiss,
                automatic = { checked {
                    if (settings.customPeriods().isNotEmpty()) {
                        settings.setPeriods(emptyList())
                        TimetableFeedback.Saved.play(activity)
                        refresh()
                    }
                    pop()
                } },
                edit = { openMenu(TimetablePanel.BellEditor(it)) })
            is TimetablePanel.BellDetail -> {
                val monday = calendar()?.dateOf(week) ?: today.with(java.time.DayOfWeek.MONDAY)
                val active = rows.filter { weeksByExpression[it.course.weeks]?.contains(week) == true }
                PeriodBellPanel(page.number, weekBellAxis(monday, active, settings.customPeriods())[page.number], dismiss)
            }
            is TimetablePanel.BellEditor -> {
                val initial = remember(page) {
                    page.template?.let { UjsTimetable.periods(today, it) }
                        ?: settings.customPeriods().ifEmpty { UjsTimetable.periods(today, "主楼") }
                }
                BellEditorPanel(initial, page.template, dismiss, periodLimit = displayOptions.periodCount ?: TimetableLimits.MAX_PERIODS) { values ->
                    checked {
                        if (values != settings.customPeriods()) {
                            settings.setPeriods(values)
                            TimetableFeedback.Saved.play(activity)
                            refresh()
                        }
                        pop()
                    }
                }
            }
            is TimetablePanel.EditCourse -> {
                val sameTerm = term == page.term
                val options = if (sameTerm) displayOptions else remember(page.term) { settings.displayOptions(page.term) }
                val weeks = if (sameTerm) lastWeek else options.termWeeks ?: TimetableLimits.MAX_WEEKS
                val periods = options.periodCount ?: automaticPeriodCount(if (sameTerm) rows else emptyList())
                TimetableCourseEditor(page, weeks, periods, dismiss, save = { course ->
                    checked {
                        store.save(page.term, page.existing?.id, course)
                        if (page.existing?.course != course) TimetableFeedback.Saved.play(activity)
                        term = page.term
                        refresh()
                        if (page.existing == null) dismiss() else pop()
                    }
                }, remove = {
                    page.existing?.let { row ->
                        checked {
                            store.remove(page.term, row.id)
                            TimetableFeedback.Saved.play(activity)
                            refresh()
                            while (menus.lastOrNull()?.panel.let {
                                it is TimetablePanel.EditCourse || it is TimetablePanel.Course || it is TimetablePanel.Overlaps
                            }) menus.removeAt(menus.lastIndex)
                        }
                    }
                })
            }
            is TimetablePanel.Overlaps -> TimetablePage("同一时段的课程", dismiss) {
                MenuSection { page.rows.mapNotNull { original -> rows.find { it.id == original.id } }.forEach { item ->
                    val course = item.course
                    MenuRow(Icons.Outlined.MenuBook, course.name,
                        listOf(periodText(course), course.place).filter(String::isNotBlank).joinToString(" · "),
                        titleStyle = TimetableType.courseName) { details(item) }
                } }
            }
            is TimetablePanel.Colors -> CourseColorsPanel(page.row.course.name, appearance, dismiss, pop)
            is TimetablePanel.Course -> {
                val row = rows.find { it.id == page.row.id } ?: return
                val selected = term ?: return
                val c = row.course
                val date = calendar()?.dateOf(week, c.weekday) ?: today
                val bells = settings.periods(date, c.place).associateBy { it.number }
                val start = bells[c.startPeriod]?.start
                val end = bells[c.endPeriod]?.end
                TimetablePage(c.name, dismiss, titleStyle = TimetableType.courseTitle) {
                    CourseDetailContent(c, weekdays[c.weekday - 1], periodText(c),
                        if (start != null && end != null) "$start–$end" else null,
                        { courseDialog(selected, row) }, { openMenu(TimetablePanel.Colors(row)) })
                }
            }
        }
    }

    private fun periodText(c: TimetableCourse) =
        if (c.startPeriod == c.endPeriod) "第 ${c.startPeriod} 节" else "第 ${c.startPeriod}–${c.endPeriod} 节"

    private fun details(row: SavedCourse) { openMenu(TimetablePanel.Course(row)) }

    private fun addCourse(day: Int? = null, period: Int? = null) {
        val selected = term
        if (selected == null) openMenu(TimetablePanel.NewTerm) else courseDialog(selected, null, day, period)
    }

    private fun courseDialog(term: String, existing: SavedCourse?, defaultDay: Int? = null, defaultPeriod: Int? = null) {
        openMenu(TimetablePanel.EditCourse(term, existing, defaultDay ?: today.dayOfWeek.value, defaultPeriod ?: 1,
            if (defaultDay == null) null else week))
    }

    private inline fun checked(action: () -> Unit) {
        if (owner != session.timetableOwner) {
            exit()
            return
        }
        try {
            action()
        } catch (e: IllegalArgumentException) {
            message(e.message ?: "课表内容无效")
        } catch (_: Exception) {
            message("无法读写本地课表，请稍后重试")
        }
    }

    private fun message(value: String) = Toast.makeText(activity, value, Toast.LENGTH_LONG).show()

    fun snapshot() = Bundle().apply {
        putString(EXTRA_TERM, term)
        putInt("week", week)
        putParcelableArrayList("timetableMenus", saveMenus(menus))
    }

    fun close() {
        appearance.close()
        store.close()
    }

    fun detach() {
        exit = {}
        launchImport = {}
        launchPicker = {}
    }
}

/** The previous AndroidView holder can still parent its native child after its composition was removed. */
private fun <V : View> V.newHost(): V = apply { (parent as? ViewGroup)?.removeView(this) }
