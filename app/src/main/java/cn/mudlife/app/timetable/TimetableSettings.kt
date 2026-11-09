package cn.mudlife.app.timetable

import android.content.Context
import java.security.MessageDigest
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

internal class TimetableSettings(context: Context, owner: String) {
    init { require(owner.isNotBlank()) }
    private val prefs = context.getSharedPreferences("timetable-settings", Context.MODE_PRIVATE)
    private val account = MessageDigest.getInstance("SHA-256").digest(owner.trim().toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    private var savedBells: Pair<String?, List<BellPeriod>> = null to emptyList()

    fun displayOptions(term: String): TimetableDisplayOptions {
        require(term.isNotBlank())
        val key = "$account/display/$term"
        return TimetableDisplayOptions(
            periodCount = prefs.getInt("$key/periodCount", 0).takeIf { it > 0 }?.coerceAtMost(TimetableLimits.MAX_PERIODS),
            cardOpacity = prefs.getInt("$key/cardOpacity", 100).coerceIn(0, 100),
            showInactive = prefs.getBoolean("$key/showInactive", false),
            showSaturday = prefs.getBoolean("$key/showSaturday", true),
            showSunday = prefs.getBoolean("$key/showSunday", true),
            showBellTimes = prefs.getBoolean("$key/showBellTimes", true),
            showGridLines = prefs.getBoolean("$key/showGridLines", false),
            rowHeightDp = prefs.getInt("$key/rowHeight", 56).coerceIn(40, 96),
            termWeeks = prefs.getInt("$key/termWeeks", 0).takeIf { it > 0 }?.coerceAtMost(TimetableLimits.MAX_WEEKS),
            cornerRadiusDp = prefs.getInt("$key/cornerRadius", 5).coerceIn(0, 24),
            inactiveCardOpacity = prefs.getInt("$key/inactiveOpacity", 60).coerceIn(0, 100),
            showPlace = prefs.getBoolean("$key/showPlace", true),
            showTeacher = prefs.getBoolean("$key/showTeacher", false),
            centerTextHorizontally = prefs.getBoolean("$key/centerTextHorizontally", true),
            centerTextVertically = prefs.getBoolean("$key/centerTextVertically", true)
        )
    }

    fun setDisplayOptions(term: String, value: TimetableDisplayOptions) {
        require(term.isNotBlank())
        val key = "$account/display/$term"
        prefs.edit()
            .putInt("$key/periodCount", value.periodCount ?: 0)
            .putInt("$key/cardOpacity", value.cardOpacity)
            .putBoolean("$key/showInactive", value.showInactive)
            .putBoolean("$key/showSaturday", value.showSaturday)
            .putBoolean("$key/showSunday", value.showSunday)
            .putBoolean("$key/showBellTimes", value.showBellTimes)
            .putBoolean("$key/showGridLines", value.showGridLines)
            .putInt("$key/rowHeight", value.rowHeightDp)
            .putInt("$key/termWeeks", value.termWeeks ?: 0)
            .putInt("$key/cornerRadius", value.cornerRadiusDp)
            .putInt("$key/inactiveOpacity", value.inactiveCardOpacity)
            .putBoolean("$key/showPlace", value.showPlace)
            .putBoolean("$key/showTeacher", value.showTeacher)
            .putBoolean("$key/centerTextHorizontally", value.centerTextHorizontally)
            .putBoolean("$key/centerTextVertically", value.centerTextVertically)
            .apply()
    }

    fun firstMonday(term: String, schoolTerm: String?): LocalDate? {
        val saved = prefs.getString("$account/monday/$term", null)
        return saved?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: UjsTimetable.firstMonday(schoolTerm ?: term)
    }

    fun setFirstMonday(term: String, date: LocalDate) {
        require(term.isNotBlank() && date.dayOfWeek == DayOfWeek.MONDAY) { "请选择该学期第一周的周一" }
        prefs.edit().putString("$account/monday/$term", date.toString()).apply()
    }

    fun clearTerm(term: String) {
        require(term.isNotBlank())
        val key = "$account/display/$term"
        val edit = prefs.edit().remove("$account/monday/$term")
        // Term names can contain separators. Match the whole parent key before the final option name.
        prefs.all.keys.filter { it.substringBeforeLast('/') == key }.forEach { edit.remove(it) }
        edit.apply()
    }

    fun periods(date: LocalDate = LocalDate.now(campusZone), place: String = ""): List<BellPeriod> =
        customPeriods().ifEmpty { UjsTimetable.periods(date, place) }

    fun customPeriods(): List<BellPeriod> {
        val data = prefs.getString("$account/bells", null)
        if (savedBells.first == data) return savedBells.second
        val parsed = try {
            data?.split(';')?.map {
                val fields = it.split(',')
                BellPeriod(fields[0].toInt(), LocalTime.parse(fields[1]), LocalTime.parse(fields[2]))
            }.orEmpty()
        } catch (_: RuntimeException) {
            emptyList()
        }
        savedBells = data to parsed
        return parsed
    }

    /** An empty list restores the university timetable, including its location-dependent bells. */
    fun setPeriods(bells: List<BellPeriod>) {
        val sorted = bells.sortedBy { it.number }
        require(sorted.zipWithNext().all { (a, b) -> a.number < b.number && a.end <= b.start }) {
            "每个节次只能出现一次，且上课时间不可交叠"
        }
        val edit = prefs.edit()
        if (sorted.isEmpty()) edit.remove("$account/bells")
        else edit.putString("$account/bells", sorted.joinToString(";") { "${it.number},${it.start},${it.end}" })
        edit.apply()
    }
}

internal object UjsTimetable {
    // Academic calendar: jwc.ujs.edu.cn/info/1691/29711.htm
    fun firstMonday(term: String): LocalDate? = when (term.replace(" ", "")) {
        "2026-2027学年第1学期", "2026-2027学年第一学期" -> LocalDate.of(2026, 8, 31)
        "2026-2027学年第2学期", "2026-2027学年第二学期" -> LocalDate.of(2027, 2, 22)
        else -> null
    }

    // Student timetables in school notices 校办〔2026〕6号 and 17号.
    // jwc.ujs.edu.cn/info/1691/29091.htm and /info/1691/30721.htm (attachment, p. 2).
    private val summerStart = LocalDate.of(2026, 4, 7)
    private val winterStart = LocalDate.of(2026, 10, 8)
    private val early = listOf("主楼", "主A", "主Ａ", "生环楼", "汽车能动", "京江楼")
    // The notice calls these 京江楼; the academic system uses e.g. 本部京江3号楼3101.
    private val jingjiangBuilding = Regex("""京江\s*第?\s*[一二三四五六七八九十0-9０-９]+\s*号?\s*(?:教学)?楼""")
    private val middle = listOf("三江", "材料楼", "机械楼", "新校区")
    private val late = listOf("三山", "讲堂", "实践楼")
    private val separate = listOf("长山", "附属医院", "基础工程训练中心", "附属学校")

    fun periods(date: LocalDate, place: String = ""): List<BellPeriod> {
        if (date < summerStart || separate.any(place::contains)) return emptyList()
        val north = "北固" in place
        val third = when {
            north || early.any(place::contains) || jingjiangBuilding.containsMatchIn(place) -> 600
            middle.any(place::contains) -> 610
            late.any(place::contains) -> 620
            else -> null
        }
        val main = !north && ("本部" in place || third != null)
        val summer = date < winterStart
        val season = if (summer) 30 else 0
        return buildList {
            fun addBell(number: Int, minutes: Int) {
                val start = LocalTime.of(minutes / 60, minutes % 60)
                add(BellPeriod(number, start, start.plusMinutes(45)))
            }
            addBell(1, 480)
            addBell(2, 535)
            if (third != null) { addBell(3, third); addBell(4, third + 55) }
            addBell(5, 810 + season)
            addBell(6, 865 + season)
            if (north || main) {
                val seventh = (if (north) 920 else 930) + season
                addBell(7, seventh)
                addBell(8, seventh + 55)
            }
            addBell(9, 1110 + season)
            addBell(10, 1165 + season)
            addBell(11, 1220 + season)
        }
    }
}
