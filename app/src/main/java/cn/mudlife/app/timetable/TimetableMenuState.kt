package cn.mudlife.app.timetable

import android.os.Bundle

internal data class TimetableMenuEntry(val id: Int, val panel: TimetablePanel)

internal val timetableBellTemplates = listOf("本部主楼", "三江楼", "三山楼", "北固校区")

internal fun saveMenus(entries: List<TimetableMenuEntry>): ArrayList<Bundle> = entries.mapTo(ArrayList(entries.size)) { entry ->
    Bundle().apply {
        putInt("id", entry.id)
        val type = when (val page = entry.panel) {
            TimetablePanel.Tools -> "tools"
            TimetablePanel.AddWidget -> "add-widget"
            TimetablePanel.Display -> "display"
            is TimetablePanel.DisplayGroup -> {
                putString("group", page.group.name)
                "display-group"
            }
            is TimetablePanel.BellDetail -> {
                putInt("number", page.number)
                "bell-detail"
            }
            TimetablePanel.StartDate -> "start-date"
            TimetablePanel.Weeks -> "weeks"
            TimetablePanel.AllCourses -> "directory"
            TimetablePanel.Seasons -> "terms"
            TimetablePanel.NewTerm -> "new-term"
            TimetablePanel.Bells -> "bells"
            TimetablePanel.Wallpaper -> "wallpaper"
            is TimetablePanel.BellEditor -> {
                putString("template", page.template)
                "bell-editor"
            }
            is TimetablePanel.Overlaps -> {
                putStringArrayList("courses", ArrayList(page.rows.map { it.id }))
                "overlaps"
            }
            is TimetablePanel.Course -> {
                putString("course", page.row.id)
                "course"
            }
            is TimetablePanel.Colors -> {
                putString("course", page.row.id)
                "colors"
            }
            is TimetablePanel.EditCourse -> {
                putString("term", page.term)
                putInt("day", page.day)
                putInt("period", page.period)
                page.week?.let { putInt("week", it) }
                if (page.existing == null) "new-course" else {
                    putString("course", page.existing.id)
                    "edit-course"
                }
            }
        }
        putString("type", type)
    }
}

internal fun restoreMenus(saved: List<Bundle>, rows: List<SavedCourse>): List<TimetableMenuEntry> {
    val courses = rows.associateBy { it.id }
    return buildList {
        for (state in saved) {
            val id = state.getInt("id", -1)
            if (id < 0 || id <= (lastOrNull()?.id ?: -1)) break
            val panel = state.restorePanel(courses) ?: break
            add(TimetableMenuEntry(id, panel))
        }
    }
}

private fun Bundle.restorePanel(courses: Map<String, SavedCourse>): TimetablePanel? {
    return when (val type = getString("type")) {
        "tools" -> TimetablePanel.Tools
        "add-widget" -> TimetablePanel.AddWidget
        "display" -> TimetablePanel.Display
        "display-group" -> TimetableDisplayGroup.entries.find { it.name == getString("group") }?.let(TimetablePanel::DisplayGroup)
        "bell-detail" -> getInt("number").takeIf { it in 1..30 }?.let(TimetablePanel::BellDetail)
        "start-date" -> TimetablePanel.StartDate
        "weeks" -> TimetablePanel.Weeks
        "directory" -> TimetablePanel.AllCourses
        "terms" -> TimetablePanel.Seasons
        "new-term" -> TimetablePanel.NewTerm
        "bells" -> TimetablePanel.Bells
        "wallpaper" -> TimetablePanel.Wallpaper
        "bell-editor" -> {
            val template = getString("template")
            if (template != null && template !in timetableBellTemplates) return null
            TimetablePanel.BellEditor(template)
        }
        "overlaps" -> {
            val ids = getStringArrayList("courses") ?: return null
            if (ids.size < 2 || ids.distinct().size != ids.size) return null
            TimetablePanel.Overlaps(ids.map { courses[it] ?: return null })
        }
        "course" -> courses[getString("course")]?.let { TimetablePanel.Course(it) }
        "colors" -> courses[getString("course")]?.let { TimetablePanel.Colors(it) }
        "new-course", "edit-course" -> {
            val term = getString("term")?.takeIf { it.isNotBlank() } ?: return null
            val day = getInt("day")
            val period = getInt("period")
            val week = if (containsKey("week")) getInt("week") else null
            if (day !in 1..7 || period !in 1..30 || (week != null && week !in 1..TeachingWeeks.MAX)) return null
            val existing = if (type == "edit-course") courses[getString("course")] ?: return null else null
            TimetablePanel.EditCourse(term, existing, day, period, week)
        }
        else -> null
    }
}
