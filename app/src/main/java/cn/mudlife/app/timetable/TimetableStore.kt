package cn.mudlife.app.timetable

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID

data class TimetableCourse(
    val name: String,
    val weekday: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val weeks: String,
    val place: String = "",
    val teacher: String = ""
) {
    internal fun normalized(): TimetableCourse {
        require(name.isNotBlank()) { "请填写课程名称" }
        require(weekday in 1..7) { "星期应在周一至周日之间" }
        require(startPeriod in 1..30 && endPeriod in startPeriod..30) { "节次应在 1 至 30 之间，结束节次不能早于开始节次" }
        return copy(name = name.trim(), weeks = weeks.trim(), place = place.trim(), teacher = teacher.trim())
    }

    internal fun fields() = JSONObject()
        .put("name", name).put("weekday", weekday)
        .put("start", startPeriod).put("end", endPeriod)
        .put("weeks", weeks).put("place", place).put("teacher", teacher)

}

data class ImportedCourse(val course: TimetableCourse, val sourceKey: String = "")
internal data class SavedCourse(val id: String, val course: TimetableCourse, val isAdjustment: Boolean = false)

private val adjustmentTitle = Regex("""^\s*[【\[［]\s*(?:调课?|补课?)\s*[】\]］]""")

private fun JSONObject.course() = TimetableCourse(getString("name"), getInt("weekday"),
    getInt("start"), getInt("end"), getString("weeks"), getString("place"), getString("teacher"))

/** Preserve identity across location/teacher changes only when both sides of the match are unambiguous. */
internal fun matchAnonymous(
    prior: Map<String, TimetableCourse>, incoming: Set<TimetableCourse>
): Map<String, TimetableCourse> {
    val old = prior.toMutableMap()
    val remaining = incoming.toMutableSet()
    val result = mutableMapOf<String, TimetableCourse>()
    fun matchBy(key: (TimetableCourse) -> Any) {
        val oldGroups = old.entries.groupBy { key(it.value) }
        for ((group, rows) in remaining.groupBy(key)) {
            val previous = oldGroups[group]?.singleOrNull() ?: continue
            val replacement = rows.singleOrNull() ?: continue
            result[previous.key] = replacement
            old.remove(previous.key)
            remaining.remove(replacement)
        }
    }
    matchBy { it }
    matchBy { listOf(it.name, it.weekday, it.startPeriod, it.endPeriod, it.weeks) }
    matchBy { listOf(it.name, it.weekday, it.startPeriod, it.endPeriod) }
    matchBy { listOf(it.name, it.teacher, it.weeks) }
    for (course in remaining) result["a-" + UUID.randomUUID()] = course
    return result
}

private fun digest(value: String): String = Base64.encodeToString(
    MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)),
    Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE
)

private fun databaseName(context: Context, owner: String): String {
    require(owner.isNotBlank()) { "缺少课表所属账号" }
    return File(context.noBackupFilesDir, "timetable-${digest(owner.trim())}.db").absolutePath
}

internal data class TermImportPlan(val destination: String, val existed: Boolean)

internal fun planTermImport(school: String, selected: String?, bindings: Map<String, String?>): TermImportPlan {
    require(school.isNotBlank()) { "缺少教务学期" }
    if (selected != null) require(selected in bindings) { "所选学期已删除，请返回课表重新选择" }
    val target = if (selected != null && (bindings[selected] == null || bindings[selected] == school)) selected else school
    require(bindings[target] == null || bindings[target] == school) { "同名本地学期已绑定其他教务学期，请选择一个空白学期" }
    return TermImportPlan(target, target in bindings)
}

class TimetableStore(context: Context, accountKey: String) :
    SQLiteOpenHelper(context.applicationContext, databaseName(context, accountKey), null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE terms (term TEXT PRIMARY KEY, last_opened INTEGER NOT NULL, school_term TEXT)")
        db.execSQL(
            """CREATE TABLE courses (term TEXT NOT NULL, id TEXT NOT NULL,
                official TEXT, personal TEXT, hidden INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (term, id), FOREIGN KEY (term) REFERENCES terms(term))"""
        )
    }

    override fun onConfigure(db: SQLiteDatabase) = db.setForeignKeyConstraintsEnabled(true)
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion == 1 && newVersion == 2) {
            db.execSQL("ALTER TABLE terms ADD COLUMN school_term TEXT")
            // Imported records include hidden courses and locally edited official courses.
            db.execSQL("""UPDATE terms SET school_term=term WHERE EXISTS
                (SELECT 1 FROM courses WHERE courses.term=terms.term AND official IS NOT NULL)""")
        } else error("不支持的本地课表版本")
    }

    internal fun terms(): List<String> = readableDatabase.rawQuery(
        "SELECT term FROM terms ORDER BY last_opened DESC, term DESC", null
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }

    internal fun selectTerm(term: String) = markOpened(writableDatabase, term)

    private fun markOpened(db: SQLiteDatabase, term: String) {
        // The selected term also drives the widget. Two writes in one millisecond must still have a definite order.
        db.compileStatement("UPDATE terms SET last_opened=max(?, (SELECT max(last_opened) FROM terms) + 1) WHERE term=?").use {
            it.bindLong(1, System.currentTimeMillis())
            it.bindString(2, term)
            require(it.executeUpdateDelete() == 1) { "这个学期已删除，请返回课表重新选择" }
        }
    }

    internal fun createTerm(name: String): String {
        val term = name.trim()
        require(term.isNotEmpty()) { "请填写学期名称" }
        write { db ->
            val values = ContentValues().apply { put("term", term); put("last_opened", 0L) }
            require(db.insertWithOnConflict("terms", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L) { "已有同名学期" }
            markOpened(db, term)
        }
        return term
    }

    internal fun deleteTerm(term: String) {
        write { db ->
            db.delete("courses", "term=?", arrayOf(term))
            db.delete("terms", "term=?", arrayOf(term))
        }
    }

    internal fun schoolTerm(term: String): String? = readableDatabase.rawQuery(
        "SELECT school_term FROM terms WHERE term=?", arrayOf(term)
    ).use { if (it.moveToFirst()) it.getString(0) else null }

    internal fun importPlan(school: String, selected: String?): TermImportPlan =
        importPlan(readableDatabase, school, selected)

    private fun importPlan(db: SQLiteDatabase, school: String, selected: String?): TermImportPlan {
        val bindings = db.rawQuery("SELECT term,school_term FROM terms", null).use { cursor ->
            buildMap<String, String?> { while (cursor.moveToNext()) put(cursor.getString(0), cursor.getString(1)) }
        }
        return planTermImport(school, selected, bindings)
    }

    internal fun courses(term: String): List<SavedCourse> = readableDatabase.rawQuery(
        "SELECT id, official, personal FROM courses WHERE term=? AND hidden=0", arrayOf(term)
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                val fields = JSONObject(cursor.getString(1) ?: "{}")
                val personal = JSONObject(cursor.getString(2) ?: "{}")
                // Keep the school's adjustment marker even after the user renames the course.
                val isAdjustment = adjustmentTitle.containsMatchIn(fields.optString("name")) ||
                    adjustmentTitle.containsMatchIn(personal.optString("name"))
                personal.keys().forEach { fields.put(it, personal.get(it)) }
                add(SavedCourse(cursor.getString(0), fields.course(), isAdjustment))
            }
        }.sortedWith(compareBy({ it.course.weekday }, { it.course.startPeriod }, { it.course.endPeriod }, { it.course.name }))
    }

    /** The caller supplies one complete official term. Invalid or failed imports leave the previous snapshot intact. */
    fun importTerm(term: String, courses: List<ImportedCourse>) {
        importTerm(term, courses, null, null)
    }

    internal fun importTerm(term: String, courses: List<ImportedCourse>, selected: String?, confirmed: TermImportPlan?): String {
        val school = term.trim()
        require(school.isNotEmpty() && courses.isNotEmpty()) { "未取得完整学期课表" }
        val rows = linkedMapOf<String, TimetableCourse>()
        val anonymous = mutableSetOf<TimetableCourse>()
        for ((raw, sourceKey) in courses) {
            val course = raw.normalized()
            if (sourceKey.isBlank()) {
                anonymous.add(course)
            } else {
                val previous = rows.put("i-" + digest(sourceKey.trim()), course)
                require(previous == null || previous == course) { "教务课表的课程标识重复，请重新读取" }
            }
        }
        return write { db ->
            val plan = importPlan(db, school, selected)
            require(confirmed == null || plan == confirmed) { "目标学期发生变化，请重新读取课表" }
            val label = plan.destination
            val values = ContentValues().apply { put("school_term", school) }
            if (plan.existed) db.update("terms", values, "term=?", arrayOf(label))
            else {
                values.put("term", label)
                values.put("last_opened", 0L)
                db.insertOrThrow("terms", null, values)
            }
            markOpened(db, label)
            if (anonymous.isNotEmpty()) {
                val prior = db.rawQuery("SELECT id,official FROM courses WHERE term=? AND id LIKE 'a-%'", arrayOf(label)).use {
                    buildMap { while (it.moveToNext()) put(it.getString(0), JSONObject(it.getString(1)).course()) }
                }
                rows.putAll(matchAnonymous(prior, anonymous))
            }
            // Rebuild untouched rows. Edits, deletion markers and manual entries survive every import.
            db.delete("courses", "term=? AND official IS NOT NULL AND personal IS NULL AND hidden=0", arrayOf(label))
            for ((id, course) in rows) {
                val values = ContentValues().apply { put("official", course.fields().toString()) }
                if (db.update("courses", values, "term=? AND id=?", arrayOf(label, id)) == 0) {
                    values.put("term", label)
                    values.put("id", id)
                    db.insertOrThrow("courses", null, values)
                }
            }
            label
        }
    }

    internal fun save(term: String, id: String?, course: TimetableCourse) {
        val label = term.trim()
        require(label.isNotEmpty()) { "请填写学期名称" }
        val fields = course.normalized().fields()
        write { db ->
            markOpened(db, label)
            if (id == null) {
                val values = ContentValues().apply {
                    put("term", label)
                    put("id", "m-" + UUID.randomUUID())
                    put("personal", fields.toString())
                }
                db.insertOrThrow("courses", null, values)
            } else {
                val key = arrayOf(label, id)
                val official = db.rawQuery(
                    "SELECT official FROM courses WHERE term=? AND id=? AND hidden=0", key
                ).use {
                    check(it.moveToFirst()) { "课程已更改，请重新打开" }
                    JSONObject(it.getString(0) ?: "{}")
                }
                val personal = JSONObject()
                fields.keys().forEach { if (fields.get(it) != official.opt(it)) personal.put(it, fields.get(it)) }
                if (personal.has("start") || personal.has("end")) {
                    personal.put("start", fields.getInt("start")).put("end", fields.getInt("end"))
                }
                val values = ContentValues().apply {
                    if (personal.length() == 0) putNull("personal") else put("personal", personal.toString())
                }
                db.update("courses", values, "term=? AND id=?", key)
            }
        }
    }

    internal fun remove(term: String, id: String) {
        write { db ->
            val key = arrayOf(term, id)
            if (db.delete("courses", "term=? AND id=? AND official IS NULL", key) == 0) {
                db.execSQL("UPDATE courses SET hidden=1, personal=NULL WHERE term=? AND id=?", key)
            }
        }
    }

    private inline fun <T> write(block: (SQLiteDatabase) -> T): T {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val result = block(db)
            db.setTransactionSuccessful()
            return result
        } finally {
            db.endTransaction()
        }
    }
}
