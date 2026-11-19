package cn.mudlife.app.utils

import android.content.SharedPreferences

internal enum class AccountGuideRun { FIRST, SECOND, MANUAL, UPDATE }

internal class AccountGuidePreferences(private val prefs: SharedPreferences) {
    private val manual get() = prefs.getBoolean("accountGuideManual", false)
    private val seen get() = prefs.getBoolean("accountGuideSeen", false)
    private val inProgress get() = prefs.getBoolean("accountGuideInProgress", false)
    private val lastVersion get() = prefs.getInt("accountGuideLastVersion", 0)

    val enabled get() = prefs.getBoolean("accountGuideEnabled", !seen || inProgress)

    fun shouldShow(hasAccount: Boolean, version: Int, installedUpdate: Boolean) =
        updatePending(version, installedUpdate) || enabled && (manual || inProgress || !seen && !hasAccount)

    private fun updatePending(version: Int, installedUpdate: Boolean): Boolean =
        if (lastVersion == 0) installedUpdate else version > lastVersion

    fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean("accountGuideManual", value)
            .putBoolean("accountGuideEnabled", value)
            .remove("accountGuideInProgress").apply()
    }

    fun start(hasAccount: Boolean, version: Int, installedUpdate: Boolean): AccountGuideRun? {
        val updated = updatePending(version, installedUpdate)
        val show = shouldShow(hasAccount, version, installedUpdate)
        val edit = prefs.edit().putInt("accountGuideLastVersion", maxOf(lastVersion, version))
        val mode = when {
            updated -> {
                edit.putBoolean("accountGuideSeen", true)
                    .putBoolean("accountGuideManual", false)
                    .putBoolean("accountGuideEnabled", false)
                    .remove("accountGuideInProgress")
                AccountGuideRun.UPDATE
            }
            !show -> {
                if (hasAccount && !seen && !inProgress) edit.putBoolean("accountGuideSeen", true)
                null
            }
            manual -> AccountGuideRun.MANUAL
            inProgress -> {
                edit.remove("accountGuideInProgress").putBoolean("accountGuideEnabled", false)
                AccountGuideRun.SECOND
            }
            else -> {
                edit.putBoolean("accountGuideInProgress", true).putBoolean("accountGuideEnabled", true)
                AccountGuideRun.FIRST
            }
        }
        edit.apply()
        return mode
    }

    /** Returns whether this automatic run should explain where to turn the guide back on. */
    fun finish(run: AccountGuideRun, completed: Boolean): Boolean {
        if (run != AccountGuideRun.SECOND && (run != AccountGuideRun.FIRST || !completed)) return false
        if (prefs.getBoolean("accountGuideReminderShown", false)) return false
        prefs.edit().putBoolean("accountGuideReminderShown", true)
            .putBoolean("accountGuideSeen", true)
            .putBoolean("accountGuideEnabled", false)
            .remove("accountGuideInProgress").apply()
        return true
    }
}
