package cn.mudlife.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.mudlife.app.utils.PrefsHelper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Run seed and restore in separate instrumentation processes, without an Activity or API calls. */
@RunWith(AndroidJUnit4::class)
class BathPrefixPersistenceTest {
    @Test fun cleanupFixtureDataOnly() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        PrefsHelper.init(context)
        val before = PrefsHelper.getActiveOrders()
        val after = before.filterNot { it.snCode == "fixture-sn" && it.orderNo == "fixture-order" }
        PrefsHelper.saveActiveOrders(after)
        val prefs = PrefsHelper::class.java.getDeclaredField("prefs").apply { isAccessible = true }
            .get(null) as android.content.SharedPreferences
        val editor = prefs.edit()
        listOf("bathPrefix_13000000000_43", "bathPrefix_13000000001_43", "bathPrefix_13000000000_44",
            "startedAt_fixture-sn", "autoDiscon_fixture-sn").forEach { editor.remove(it) }
        assertTrue(editor.commit())
        println("Removed fixture orders: ${before.size - after.size}; real account credentials unchanged")
    }

    @Test fun seedBeforeProcessExit() {
        PrefsHelper.init(InstrumentationRegistry.getInstrumentation().targetContext)
        PrefsHelper.qzhqPhone = "13000000000"
        PrefsHelper.qzhqProjectId = "43"
        PrefsHelper.qzhqCustomCode = "13579"
        assertEquals("13579", PrefsHelper.qzhqCustomCode)
    }

    @Test fun restoreInNewProcessAndSeparateAccounts() {
        PrefsHelper.init(InstrumentationRegistry.getInstrumentation().targetContext)
        assertEquals("13579", PrefsHelper.qzhqCustomCode)
        PrefsHelper.qzhqPhone = "13000000001"
        assertEquals("", PrefsHelper.qzhqCustomCode)
        PrefsHelper.qzhqCustomCode = "00246"
        PrefsHelper.qzhqPhone = "13000000000"
        assertEquals("13579", PrefsHelper.qzhqCustomCode)
        PrefsHelper.qzhqProjectId = "44"
        assertEquals("", PrefsHelper.qzhqCustomCode)
        PrefsHelper.qzhqProjectId = "43"
        assertEquals("13579", PrefsHelper.qzhqCustomCode)
        // Leave the independent preview installation with no fixture login data.
        PrefsHelper.clear()
    }
}
