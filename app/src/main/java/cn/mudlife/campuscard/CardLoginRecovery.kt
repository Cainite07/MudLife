package cn.mudlife.campuscard

/** One automatic GET per login attempt, after a verified campus-card return. */
internal class CardLoginRecovery(automaticAttempted: Boolean = false) {
    enum class Observation { UNCONFIRMED, COMPLETE_EMPTY, FAILED_REQUIRED_ASSET_EMPTY }
    enum class Action { WAIT, REOPEN, OFFER_MANUAL }

    var automaticAttempted = automaticAttempted
        private set

    private var emptySince = 0L
    private var previousSample: Long? = null

    /** Navigation and background time cannot contribute to one document's blank interval. */
    fun resetObservation() {
        previousSample = null
    }

    fun observe(nowMs: Long, verifiedReturn: Boolean, observation: Observation): Action {
        if (!verifiedReturn || observation == Observation.UNCONFIRMED) {
            resetObservation()
            return Action.WAIT
        }

        val previous = previousSample
        if (previous == null || nowMs - previous !in 0L..MAX_SAMPLE_GAP_MS) emptySince = nowMs
        previousSample = nowMs
        val elapsed = nowMs - emptySince
        if (observation == Observation.COMPLETE_EMPTY && elapsed < EMPTY_TIMEOUT_MS) return Action.WAIT

        if (automaticAttempted) return Action.OFFER_MANUAL
        automaticAttempted = true
        resetObservation()
        return Action.REOPEN
    }

    private companion object {
        const val EMPTY_TIMEOUT_MS = 20_000L
        // The WebView is sampled once a second. A missing callback is not proof of blank content.
        const val MAX_SAMPLE_GAP_MS = 5_000L
    }
}
