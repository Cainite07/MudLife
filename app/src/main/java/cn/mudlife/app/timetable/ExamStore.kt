package cn.mudlife.app.timetable

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

internal data class ExamTerm(val year: Int, val semester: Int) {
    val label get() = "$year-${year + 1}学年第${semester}学期"
    val semesterCode get() = semester * semester * 3

    companion object {
        private val pattern = Regex("""(\d{4})-(\d{4})学年第([123])学期""")

        fun parse(label: String): ExamTerm? {
            val match = pattern.matchEntire(label) ?: return null
            val (start, end, term) = match.destructured
            return ExamTerm(start.toInt(), term.toInt()).takeIf { it.year + 1 == end.toInt() }
        }

        fun current(date: LocalDate): ExamTerm = when (date.monthValue) {
            in 9..12 -> ExamTerm(date.year, 1)
            1 -> ExamTerm(date.year - 1, 1)
            else -> ExamTerm(date.year - 1, 2)
        }
    }
}

internal data class ExamEntry(
    val course: String,
    val time: String,
    val room: String,
    val campus: String,
    val seat: String,
    val session: String,
    val method: String,
    val note: String
) {
    fun fields() = JSONObject().put("course", course).put("time", time).put("room", room)
        .put("campus", campus).put("seat", seat).put("session", session).put("method", method).put("note", note)

    companion object {
        fun read(row: JSONObject) = ExamEntry(
            row.getString("course").trim().also { require(it.isNotEmpty()) },
            row.getString("time"), row.getString("room"), row.getString("campus"),
            row.getString("seat"), row.getString("session"), row.getString("method"), row.getString("note")
        )
    }
}

internal data class ExamSnapshot(val syncedAt: Long, val rows: List<ExamEntry>)

internal class ExamStore(context: Context, owner: String) {
    private val prefs = context.getSharedPreferences("timetable-exams-${digest(owner)}", Context.MODE_PRIVATE)

    fun cachedTerms() = prefs.all.keys.mapNotNull(ExamTerm::parse)

    fun read(term: ExamTerm): ExamSnapshot? = runCatching {
        val value = JSONObject(prefs.getString(term.label, null) ?: return null)
        ExamSnapshot(value.getLong("syncedAt"), examEntries(value.getJSONArray("rows")))
    }.getOrNull()

    fun save(term: ExamTerm, rows: List<ExamEntry>): Boolean {
        val snapshot = JSONObject().put("syncedAt", System.currentTimeMillis())
            .put("rows", JSONArray(rows.map(ExamEntry::fields)))
        return prefs.edit().putString(term.label, snapshot.toString()).commit()
    }
}

private val examDate = Regex("""(\d{4})[-/.年](\d{1,2})[-/.月](\d{1,2})""")
private val examClock = Regex("""(\d{1,2})[:：](\d{2})""")

internal fun examEntries(rows: JSONArray): List<ExamEntry> = (0 until rows.length()).map {
    ExamEntry.read(rows.getJSONObject(it))
}.distinct().sortedWith(compareBy({ examDateKey(it.time) }, { it.course }))

private fun examDateKey(time: String): Long {
    val date = examDate.find(time)?.groupValues ?: return Long.MAX_VALUE
    val clock = examClock.find(time)?.groupValues
    return date[1].toLong() * 100_000_000 + date[2].toLong() * 1_000_000 + date[3].toLong() * 10_000 +
        (clock?.get(1)?.toLong() ?: 0) * 100 + (clock?.get(2)?.toLong() ?: 0)
}
