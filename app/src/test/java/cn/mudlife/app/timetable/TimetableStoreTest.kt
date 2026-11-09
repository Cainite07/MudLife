package cn.mudlife.app.timetable

import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], manifest = Config.NONE)
class TimetableStoreTest {
    private val store = TimetableStore(RuntimeEnvironment.getApplication(), UUID.randomUUID().toString())
    private val school = "2026-2027学年第1学期"
    private val alias = "大三上"
    private val course = TimetableCourse("示例课程", 1, 3, 4, "1–8周", "三江楼", "甲")

    @After fun closeStore() = store.close()

    @Test fun termTextAlignment() {
        val app = RuntimeEnvironment.getApplication()
        val owner = UUID.randomUUID().toString()
        val settings = TimetableSettings(app, owner)
        val defaults = settings.displayOptions(school)
        assertTrue(defaults.centerTextHorizontally)
        assertTrue(defaults.centerTextVertically)

        val leftCenter = defaults.copy(centerTextHorizontally = false)
        val topCenter = defaults.copy(centerTextVertically = false)
        settings.setDisplayOptions(school, leftCenter)
        settings.setDisplayOptions(alias, topCenter)
        val reopened = TimetableSettings(app, owner)
        assertEquals(leftCenter, reopened.displayOptions(school))
        assertEquals(topCenter, reopened.displayOptions(alias))
        reopened.clearTerm(alias)
        assertEquals(defaults, reopened.displayOptions(alias))
    }

    @Test fun renamedAdjustmentKeepsMarker() {
        val adjusted = course.copy(name = "【调】燃烧学(双语)", weeks = "12周")
        val ordinary = course.copy(name = "自动调节原理", weeks = "13周")
        store.importTerm(school, listOf(ImportedCourse(adjusted), ImportedCourse(ordinary)))
        val row = store.courses(school).single { it.isAdjustment }
        assertEquals(adjusted, row.course)
        store.save(school, row.id, adjusted.copy(name = "燃烧学"))
        store.close()

        val saved = store.courses(school)
        assertEquals("燃烧学", saved.single { it.isAdjustment }.course.name)
        assertEquals(ordinary, saved.single { !it.isAdjustment }.course)
    }

    @Test fun importIntoEmptyTermAndResyncKeepsTheLocalEditAndOtherTermIntact() {
        store.importTerm(school, listOf(ImportedCourse(course)))
        assertEquals(alias, store.createTerm(" $alias "))
        store.close()
        assertEquals(alias, store.terms().first())
        assertTrue(store.courses(alias).isEmpty())
        val target = store.importPlan(school, alias)
        assertEquals(alias, store.importTerm(school, listOf(ImportedCourse(course)), alias, target))
        assertEquals(school, store.schoolTerm(alias))

        val row = store.courses(alias).single()
        store.save(alias, row.id, row.course.copy(place = "我改的地点"))
        val updated = course.copy(teacher = "乙")
        store.importTerm(school, listOf(ImportedCourse(updated)), alias, store.importPlan(school, alias))
        assertEquals(SavedCourse(row.id, updated.copy(place = "我改的地点")), store.courses(alias).single())
        assertEquals(course, store.courses(school).single().course)
        assertEquals(listOf(alias, school), store.terms())
    }

    @Test fun deletingAndRecreatingATermClearsCoursesDeletionMarkersAndSchoolBinding() {
        store.importTerm(school, listOf(ImportedCourse(course)))
        store.createTerm(alias)
        val oldPlan = store.importPlan(school, alias)
        store.importTerm(school, listOf(ImportedCourse(course)), alias, oldPlan)
        store.remove(alias, store.courses(alias).single().id)
        store.save(alias, null, course.copy(name = "手动课"))
        store.deleteTerm(alias)
        assertThrows(IllegalArgumentException::class.java) { store.importTerm(school, listOf(ImportedCourse(course)), alias, oldPlan) }
        assertEquals(course, store.courses(school).single().course)
        assertEquals(listOf(school), store.terms())

        store.createTerm(alias)
        assertTrue(store.courses(alias).isEmpty())
        assertNull(store.schoolTerm(alias))
        store.importTerm(school, listOf(ImportedCourse(course)), alias, store.importPlan(school, alias))
        assertEquals(course, store.courses(alias).single().course)
        store.deleteTerm(alias)
        store.deleteTerm(school)
        assertTrue(store.terms().isEmpty())
        store.createTerm("空白")
        store.deleteTerm("空白")
        assertTrue(store.terms().isEmpty())
    }

    @Test fun upgradeFromVersionOneRetainsPersonalChangesAndBindsOnlyOfficialTimetables() {
        SQLiteDatabase.openOrCreateDatabase(File(store.databaseName), null).use { old ->
            old.execSQL("CREATE TABLE terms (term TEXT PRIMARY KEY, last_opened INTEGER NOT NULL)")
            old.execSQL("""CREATE TABLE courses (term TEXT NOT NULL, id TEXT NOT NULL, official TEXT,
                personal TEXT, hidden INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (term, id),
                FOREIGN KEY(term) REFERENCES terms(term))""")
            old.execSQL("INSERT INTO terms VALUES (?,1),(?,2)", arrayOf(school, alias))
            old.execSQL("INSERT INTO courses VALUES (?,?,?,?,0)",
                arrayOf(school, "a-old", course.fields().toString(), """{"place":"我改的地点"}"""))
            old.execSQL("INSERT INTO courses VALUES (?,?,NULL,?,0)", arrayOf(alias, "m-old", course.fields().toString()))
            old.version = 1
        }
        assertEquals(2, store.readableDatabase.version)
        assertEquals(school, store.schoolTerm(school))
        assertNull(store.schoolTerm(alias))
        assertEquals(course.copy(place = "我改的地点"), store.courses(school).single().course)
        assertEquals(course, store.courses(alias).single().course)
    }
}
