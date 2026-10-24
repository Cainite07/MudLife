package cn.mudlife.campuscard

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Passwords and payment codes are never persisted. Network errors never erase login. */
class SessionStore(context: Context) {
    private val preferences = EncryptedSharedPreferences.create(
        context.applicationContext, "school_session",
        MasterKey.Builder(context.applicationContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    val token: String get() = preferences.getString("access_token", "").orEmpty()
    val hasSession: Boolean get() = token.isNotBlank()
    fun unifiedPeriod(owner: String): String = preferences.getString("unified_period_$owner", "").orEmpty()
    fun saveUnifiedPeriod(owner: String, value: String) { check(preferences.edit().putString("unified_period_$owner", value).commit()) }
    fun save(token: String) {
        require(token.isNotBlank() && token.length <= 16384 && !token.contains('\n'))
        check(preferences.edit().putString("access_token", token.removePrefix("bearer ")).commit())
    }
    var selectedAccount: String
        get() = preferences.getString("selected_account", "").orEmpty()
        set(value) { preferences.edit().putString("selected_account", value).apply() }
    var pendingCheckout: String
        get() = preferences.getString("pending_checkout", "").orEmpty()
        set(value) { check(preferences.edit().putString("pending_checkout", value).commit()) }
    var pendingFen: Long
        get() = preferences.getLong("pending_fen", 0)
        set(value) { check(preferences.edit().putLong("pending_fen", value).commit()) }
    var startupCode: Boolean
        get() = preferences.getBoolean("startup_code", false)
        set(value) { preferences.edit().putBoolean("startup_code", value).apply() }
    var brightCode: Boolean
        get() = preferences.getBoolean("bright_code", false)
        set(value) { preferences.edit().putBoolean("bright_code", value).apply() }
    var billPeriod: String
        get() = preferences.getString("bill_period_" + selectedAccount, "").orEmpty()
        set(value) { check(preferences.edit().putString("bill_period_" + selectedAccount, value).commit()) }
    var electricFavorites: String
        get() = preferences.getString("electric_favorites_" + selectedAccount, "[]").orEmpty()
        set(value) { preferences.edit().putString("electric_favorites_" + selectedAccount, value).apply() }
    var pendingKind: String
        get() = preferences.getString("pending_kind", "card").orEmpty()
        set(value) { check(preferences.edit().putString("pending_kind", value).commit()) }
    var pendingOrderId: String
        get() = preferences.getString("pending_order_id", "").orEmpty()
        set(value) { check(preferences.edit().putString("pending_order_id", value).commit()) }
    fun clearPending() { check(preferences.edit().remove("pending_checkout").remove("pending_fen").remove("pending_kind").remove("pending_order_id").commit()) }
    fun clear() { check(preferences.edit().clear().commit()) }
}

object Money {
    fun parseYuan(text: String): Long? = runCatching {
        if (!text.matches(Regex("[0-9]{1,5}(\\.[0-9]{1,2})?"))) return null
        java.math.BigDecimal(text).movePointRight(2).longValueExact().takeIf { it > 0 }
    }.getOrNull()
    fun yuan(fen: Long): String = java.math.BigDecimal.valueOf(fen, 2).toPlainString()
}

object LoginPolicy {
    // Only an explicit authentication failure warrants official reauthentication.
    fun needsReauthentication(httpStatus: Int): Boolean = httpStatus == 401
}

object BillPeriodPolicy {
    fun restore(saved: String, today: java.time.YearMonth = java.time.YearMonth.now()): java.time.YearMonth? =
        if (saved == "ALL") null else runCatching { java.time.YearMonth.parse(saved) }.getOrDefault(today)
    fun save(month: java.time.YearMonth?): String = month?.toString() ?: "ALL"
}

object RechargePolicy {
    val presets = listOf(1000L, 5000L, 10000L, 20000L)
    fun allowed(fen: Long?, rules: RechargeRules?): Boolean = fen != null && rules != null &&
        fen in rules.minFen..rules.maxFen && (!rules.wholeYuan || fen % 100L == 0L) && (rules.custom || fen in rules.amounts)
}
