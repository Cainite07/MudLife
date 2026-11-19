package cn.mudlife.app.utils

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], manifest = Config.NONE)
class AccountGuidePreferencesTest {
    private val prefs = RuntimeEnvironment.getApplication()
        .getSharedPreferences("guide-policy", Context.MODE_PRIVATE)
    private fun guide() = AccountGuidePreferences(prefs)

    @Test fun automaticGuideStopsAfterTwoStartsAndExplainsOnce() {
        assertEquals(AccountGuideRun.FIRST, guide().start(false, 44, false))
        assertEquals(AccountGuideRun.SECOND, guide().start(true, 44, false))
        assertFalse(guide().enabled)
        assertTrue(guide().finish(AccountGuideRun.SECOND, completed = false))
        assertFalse(guide().finish(AccountGuideRun.SECOND, completed = false))
        assertNull(guide().start(true, 44, false))
    }

    @Test fun manualGuideNeverClosesOrExplainsEvenAfterCompletingTheInitialGuide() {
        assertEquals(AccountGuideRun.FIRST, guide().start(false, 44, false))
        assertTrue(guide().finish(AccountGuideRun.FIRST, completed = true))
        assertFalse(guide().enabled)

        guide().setEnabled(true)
        repeat(4) {
            assertEquals(AccountGuideRun.MANUAL, guide().start(true, 44, false))
            assertFalse(guide().finish(AccountGuideRun.MANUAL, completed = true))
            assertTrue(guide().enabled)
        }
        guide().setEnabled(false)
        assertNull(guide().start(false, 44, false))
    }

    @Test fun everyNewVersionShowsOnceWithoutPromptEvenWhenTheGuideWasTurnedOff() {
        assertEquals(AccountGuideRun.FIRST, guide().start(false, 44, false))
        guide().setEnabled(false)
        assertEquals(AccountGuideRun.UPDATE, guide().start(true, 45, true))
        assertFalse(guide().finish(AccountGuideRun.UPDATE, completed = true))
        assertFalse(guide().enabled)
        assertNull(guide().start(true, 45, true))
        assertEquals(AccountGuideRun.UPDATE, guide().start(false, 46, true))
        assertFalse(guide().finish(AccountGuideRun.UPDATE, completed = false))
        assertNull(guide().start(false, 46, true))
    }

    @Test fun firstUpgradeFromALegacyInstallStillShowsTheGuide() {
        assertEquals(AccountGuideRun.UPDATE, guide().start(true, 44, true))
        assertNull(guide().start(true, 44, true))
    }

    @Test fun existingAccountSkipsDefaultGuideOnFreshInstallButCanOptIn() {
        assertNull(guide().start(true, 44, false))
        assertFalse(guide().enabled)
        guide().setEnabled(true)
        assertEquals(AccountGuideRun.MANUAL, guide().start(true, 44, false))
    }
}
