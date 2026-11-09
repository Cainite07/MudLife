package cn.mudlife.campuscard

import cn.mudlife.campuscard.CardLoginRecovery.Action.*
import cn.mudlife.campuscard.CardLoginRecovery.Observation.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardLoginRecoveryTest {
    @Test fun verifiedBlankDocumentReopensOnceAfterTwentySeconds() {
        val recovery = CardLoginRecovery()
        for (seconds in 0L..19L) {
            assertEquals(WAIT, recovery.observe(seconds * 1000, true, COMPLETE_EMPTY))
        }
        assertEquals(REOPEN, recovery.observe(20_000, true, COMPLETE_EMPTY))
        assertTrue(recovery.automaticAttempted)
        recovery.resetObservation()
        for (seconds in 21L..40L) {
            assertEquals(WAIT, recovery.observe(seconds * 1000, true, COMPLETE_EMPTY))
        }
        assertEquals(OFFER_MANUAL, recovery.observe(41_000, true, COMPLETE_EMPTY))
    }

    @Test fun requiredAssetFailureNeedsVerifiedBlankReturnAndSurvivesRotation() {
        val recovery = CardLoginRecovery()
        assertEquals(WAIT, recovery.observe(1000, false, FAILED_REQUIRED_ASSET_EMPTY))
        assertFalse(recovery.automaticAttempted)
        assertEquals(REOPEN, recovery.observe(2000, true, FAILED_REQUIRED_ASSET_EMPTY))
        val rotated = CardLoginRecovery(recovery.automaticAttempted)
        assertEquals(OFFER_MANUAL, rotated.observe(3000, true, FAILED_REQUIRED_ASSET_EMPTY))
        assertEquals(WAIT, rotated.observe(4000, true, UNCONFIRMED))
    }

    @Test fun loadingContentAndUnverifiedPagesCannotAccumulateBlankTime() {
        val recovery = CardLoginRecovery()
        for (seconds in 0L..19L) recovery.observe(seconds * 1000, true, COMPLETE_EMPTY)
        assertEquals(WAIT, recovery.observe(20_000, true, UNCONFIRMED))
        assertEquals(WAIT, recovery.observe(21_000, true, COMPLETE_EMPTY))
        for (seconds in 22L..60L) {
            assertEquals(WAIT, recovery.observe(seconds * 1000, false, COMPLETE_EMPTY))
        }
        assertEquals(WAIT, recovery.observe(61_000, true, COMPLETE_EMPTY))
        assertFalse(recovery.automaticAttempted)
    }

    @Test fun navigationPauseAndMissedCallbacksRequireFreshObservations() {
        val recovery = CardLoginRecovery()
        for (seconds in 0L..19L) recovery.observe(seconds * 1000, true, COMPLETE_EMPTY)
        recovery.resetObservation()
        for (seconds in 200L..219L) {
            assertEquals(WAIT, recovery.observe(seconds * 1000, true, COMPLETE_EMPTY))
        }
        for (seconds in 230L..249L) {
            assertEquals(WAIT, recovery.observe(seconds * 1000, true, COMPLETE_EMPTY))
        }
        assertEquals(REOPEN, recovery.observe(250_000, true, COMPLETE_EMPTY))
    }
}
