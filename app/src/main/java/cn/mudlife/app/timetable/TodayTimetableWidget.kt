package cn.mudlife.app.timetable

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import cn.mudlife.app.R
import cn.mudlife.campuscard.SessionStore
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors

internal data class AgendaItem(val name: String, val periods: String, val detail: String)

internal data class TodayAgenda(val date: String, val message: String, val items: List<AgendaItem> = emptyList()) {
    companion object {
        private val shortDate = DateTimeFormatter.ofPattern("M月d日 E", Locale.SIMPLIFIED_CHINESE)
        private val clock = DateTimeFormatter.ofPattern("HH:mm")

        fun read(context: Context, date: LocalDate = LocalDate.now(campusZone)): TodayAgenda {
            val label = date.format(shortDate)
            return try {
                val session = SessionStore(context)
                val owner = session.timetableOwner ?: return TodayAgenda(label,
                    if (session.hasSession) "打开校园卡，确认当前校园账号" else "登录校园账号后查看今日课程")
                TimetableStore(context, owner).use { store ->
                    val term = store.terms().firstOrNull() ?: return TodayAgenda(label, "打开课表，导入或添加课程")
                    val settings = TimetableSettings(context, owner)
                    val courses = store.courses(term)
                    if (courses.isEmpty()) return TodayAgenda(label, "本学期还没有课程")
                    val first = settings.firstMonday(term, store.schoolTerm(term))
                        ?: return TodayAgenda(label, "请在课表中设置学期第一周的周一")
                    forDay(date, TeachingCalendar(first, settings.displayOptions(term).termWeeks), courses) { place -> settings.periods(date, place) }
                }
            } catch (_: Exception) {
                TodayAgenda(label, "暂时无法读取，点此打开课表")
            }
        }

        fun forDay(date: LocalDate, calendar: TeachingCalendar, rows: List<SavedCourse>, bells: (String) -> List<BellPeriod>): TodayAgenda {
            val week = calendar.weekOf(date)
            val label = date.format(shortDate)
            if (week !in 1..calendar.lastWeek) return TodayAgenda(label, if (week < 1) "所选学期尚未开始" else "所选学期已结束")
            val header = "$label · 第${week}周"
            val dayCourses = rows.map { it.course }.filter { it.weekday == date.dayOfWeek.value }
            val items = dayCourses.filter { TeachingWeeks.parse(it.weeks)?.contains(week) == true }
                .sortedWith(compareBy({ it.startPeriod }, { it.endPeriod }, { it.name })).map { course ->
                    val periods = if (course.startPeriod == course.endPeriod) "${course.startPeriod}节" else "${course.startPeriod}–${course.endPeriod}节"
                    val times = bells(course.place)
                    val start = times.find { it.number == course.startPeriod }?.start?.format(clock)
                    val end = times.find { it.number == course.endPeriod }?.end?.format(clock)
                    val time = when {
                        start != null && end != null -> "$start–$end"
                        start != null -> "$start 上课"
                        end != null -> "$end 下课"
                        else -> null
                    }
                    AgendaItem(course.name, periods, listOfNotNull(time, course.place.takeIf(String::isNotBlank)).joinToString(" · "))
                }
            val message = if (items.isEmpty() && dayCourses.any { TeachingWeeks.parse(it.weeks) == null })
                "有课程周次待确认，请打开课表" else "今天没有安排课程"
            return TodayAgenda(header, message, items)
        }
    }
}

private fun itemView(context: Context, item: AgendaItem, open: PendingIntent) = RemoteViews(context.packageName, R.layout.today_timetable_row).apply {
    setTextViewText(R.id.tt_widget_course, item.name)
    setTextViewText(R.id.tt_widget_location_time, if (item.detail.isEmpty()) item.periods else "${item.periods} · ${item.detail}")
    setOnClickPendingIntent(R.id.tt_widget_row, open)
}

internal data class AgendaCardSize(val minHeightDp: Float, val visibleRows: Int, val remainingRows: Int)

internal fun agendaCardSizes(total: Int, fixedHeightPx: Int, rowHeightPx: Int, density: Float): List<AgendaCardSize> {
    // Android accepts up to 16 responsive layouts. Reserve one for a header-only card.
    return (0..minOf(total, 15)).map { count ->
        val height = if (count == 0) 1f else (fixedHeightPx + count * rowHeightPx) / density + 1f
        AgendaCardSize(height, count, total - count)
    }
}

private fun agendaCard(context: Context, agenda: TodayAgenda, size: AgendaCardSize, open: PendingIntent) =
    RemoteViews(context.packageName, R.layout.today_timetable_widget).apply {
        setTextViewText(R.id.tt_widget_date, agenda.date)
        setTextViewText(R.id.tt_widget_empty, agenda.message)
        setViewVisibility(R.id.tt_widget_empty, if (agenda.items.isEmpty()) View.VISIBLE else View.GONE)
        setTextViewText(R.id.tt_widget_more, context.getString(R.string.timetable_widget_more, size.remainingRows))
        setViewVisibility(R.id.tt_widget_more, if (size.remainingRows > 0) View.VISIBLE else View.GONE)
        removeAllViews(R.id.tt_widget_rows)
        for (index in 0 until size.visibleRows) addView(R.id.tt_widget_rows, itemView(context, agenda.items[index], open))
        setOnClickPendingIntent(R.id.tt_widget_root, null)
        setOnClickPendingIntent(R.id.tt_widget_header, open)
        setOnClickPendingIntent(R.id.tt_widget_empty, open)
    }

private fun sizedAgendaCard(context: Context, agenda: TodayAgenda, open: PendingIntent, options: Bundle): RemoteViews {
    val resources = context.resources
    val fixedHeight = 2 * resources.getDimensionPixelSize(R.dimen.tt_widget_vertical_padding) +
        resources.getDimensionPixelSize(R.dimen.tt_widget_header_height) + resources.getDimensionPixelSize(R.dimen.tt_widget_rule_height)
    val rowHeight = resources.getDimensionPixelSize(R.dimen.tt_widget_row_height)
    val sizes = agendaCardSizes(agenda.items.size, fixedHeight, rowHeight, resources.displayMetrics.density)
    if (sizes.size == 1) return agendaCard(context, agenda, sizes.first(), open)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val layouts = sizes.associate { size ->
            SizeF(1f, size.minHeightDp) to agendaCard(context, agenda, size, open)
        }
        return RemoteViews(layouts)
    }
    // Older launchers report landscape and portrait heights as a min/max pair in dp.
    val landscapeHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT).takeIf { it > 0 } ?: 90
    val portraitHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 } ?: landscapeHeight
    val landscapeSize = sizes.last { it.minHeightDp <= landscapeHeight }
    val portraitSize = sizes.last { it.minHeightDp <= portraitHeight }
    val portrait = agendaCard(context, agenda, portraitSize, open)
    return if (landscapeSize == portraitSize) portrait else RemoteViews(agendaCard(context, agenda, landscapeSize, open), portrait)
}

class TodayTimetableWidget : AppWidgetProvider() {
    companion object {
        const val OPEN_TODAY = "cn.mudlife.app.timetable.OPEN_TODAY"
        private const val REFRESH = "cn.mudlife.app.timetable.REFRESH_WIDGET"
        private const val ACCOUNT_CHANGED = "cn.mudlife.app.TIMETABLE_ACCOUNT_CHANGED"
        private val worker = Executors.newSingleThreadExecutor()

        private fun component(context: Context) = ComponentName(context, TodayTimetableWidget::class.java)
        private fun refreshIntent(context: Context) = Intent(REFRESH).setComponent(component(context))

        fun refresh(context: Context) = context.sendBroadcast(refreshIntent(context))

        fun pin(activity: Activity) {
            val manager = AppWidgetManager.getInstance(activity)
            val requested = runCatching {
                manager.isRequestPinAppWidgetSupported && manager.requestPinAppWidget(component(activity), null, null)
            }.getOrDefault(false)
            if (!requested) Toast.makeText(activity, "长按桌面空白处，打开「小组件」并添加江大生活的「今日课表」", Toast.LENGTH_LONG).show()
        }

        private fun midnight(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context, 0, refreshIntent(context), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            REFRESH, ACCOUNT_CHANGED, Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED ->
                update(context, AppWidgetManager.getInstance(context).getAppWidgetIds(component(context)))
            else -> super.onReceive(context, intent)
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = update(context, ids)

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) update(context, intArrayOf(id))
    }

    override fun onDisabled(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(midnight(context))
    }

    private fun update(context: Context, ids: IntArray) {
        if (ids.isEmpty()) return
        val result = goAsync()
        worker.execute {
            try {
                val manager = AppWidgetManager.getInstance(context)
                val today = LocalDate.now(campusZone)
                val model = TodayAgenda.read(context, today)
                val open = Intent(context, TimetableActivity::class.java).setAction(OPEN_TODAY)
                val click = PendingIntent.getActivity(context, 1, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                for (id in ids) {
                    manager.updateAppWidget(id, sizedAgendaCard(context, model, click, manager.getAppWidgetOptions(id)))
                }
                // One inexact Shanghai-midnight update plus the system's periodic home-screen refresh.
                val start = today.plusDays(1).atStartOfDay(campusZone).toInstant().toEpochMilli()
                context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC, start, midnight(context))
            } finally {
                result.finish()
            }
        }
    }
}
