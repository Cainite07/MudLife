package cn.mudlife.campuscard

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.YearMonth

data class BillArchive(val records: List<Bill>, val savedAt: Long)

/** Separate encrypted preferences keep the large history away from startup/login preferences. */
class BillArchiveStore(context: Context) {
    private val app = context.applicationContext
    private val preferences by lazy {
        EncryptedSharedPreferences.create(app, "school_bill_archive",
            MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    }
    suspend fun read(owner: String): BillArchive? = withContext(Dispatchers.IO) {
        runCatching {
            val value = preferences.getString(owner, null) ?: return@withContext null
            val json = JSONObject(value)
            if (json.optInt("version") != 1 || json.optString("owner") != owner) return@withContext null
            BillArchive(json.optJSONArray("records").objects().map { b ->
                Bill(b.clean("id"), b.clean("title"), b.clean("time"), b.longOrNull("fen"), b.clean("type"), b.clean("typeName"),
                    b.clean("merchant"), b.clean("payment"), b.longOrNull("balance"), b.clean("direction"), b.clean("description"))
            }, json.getLong("savedAt"))
        }.getOrNull()
    }
    suspend fun write(owner: String, archive: BillArchive) = withContext(Dispatchers.IO) {
        runCatching {
            val data = JSONArray().apply { archive.records.forEach { b ->
                put(JSONObject().put("id", b.id).put("title", b.title).put("time", b.time).put("fen", b.fen ?: JSONObject.NULL)
                    .put("type", b.type).put("typeName", b.typeName).put("merchant", b.merchant).put("payment", b.payment)
                    .put("balance", b.balance ?: JSONObject.NULL).put("direction", b.direction).put("description", b.description))
            } }
            val value = JSONObject().put("version", 1).put("owner", owner).put("savedAt", archive.savedAt).put("records", data).toString()
            if (value.length <= 6_000_000) preferences.edit().putString(owner, value).commit()
        }
        Unit
    }
    suspend fun clear() = withContext(Dispatchers.IO) { runCatching { preferences.edit().clear().commit() }; Unit }
}

object BillLedger {
    fun direction(bill: Bill): String = bill.direction.takeIf { it == "1" || it == "2" }
        ?: when (BillPolicy.prefix(bill.type)) { "+" -> "1"; "−" -> "2"; else -> "" }
    fun select(records: List<Bill>, month: YearMonth?, direction: String): List<Bill> = records.filter {
        (month == null || it.time.startsWith(month.toString())) && (direction.isBlank() || BillLedger.direction(it) == direction)
    }
    fun summary(records: List<Bill>, month: YearMonth?): BillSummary {
        val selected = select(records, month, "")
        fun sum(direction: String): Long? = runCatching {
            val rows = selected.filter { BillLedger.direction(it) == direction }
            if (rows.any { it.fen == null }) return null
            rows.fold(0L) { total, b -> Math.addExact(total, kotlin.math.abs(b.fen!!.also { require(it != Long.MIN_VALUE) })) }
        }.getOrNull()
        if (selected.any { direction(it).isBlank() }) return BillSummary(null, null)
        return BillSummary(sum("1"), sum("2"))
    }
    fun months(records: List<Bill>): List<YearMonth> = (listOf(YearMonth.now()) + records.mapNotNull {
        runCatching { YearMonth.parse(it.time.take(7)) }.getOrNull()
    }).distinct().sortedDescending()
    fun key(bill: Bill) = listOf(bill.id, bill.time, bill.fen, bill.type, bill.direction, bill.merchant).joinToString("|")
}
