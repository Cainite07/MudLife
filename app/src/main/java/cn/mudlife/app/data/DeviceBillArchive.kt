package cn.mudlife.app.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import cn.mudlife.app.model.DeviceExpense
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.time.YearMonth

internal data class CachedDeviceMonth(val entries: List<DeviceExpense>, val savedAt: Long, val projects: String)
internal data class DeviceBillSnapshot(val months: Map<YearMonth, CachedDeviceMonth> = emptyMap(), val start: YearMonth? = null)

internal interface DeviceBillPersistence {
    suspend fun read(owner: String): DeviceBillSnapshot
    suspend fun saveMonth(owner: String, month: YearMonth, value: CachedDeviceMonth)
    suspend fun saveStart(owner: String, start: YearMonth)
}

/** Separate from login preferences: large encrypted history never blocks login initialization. */
internal class DeviceBillArchive(context: Context) : DeviceBillPersistence {
    private val app = context.applicationContext
    private val gson = Gson()
    private val preferences by lazy {
        EncryptedSharedPreferences.create(app, "device_bill_archive",
            MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    }
    private fun prefix(owner: String) = MessageDigest.getInstance("SHA-256")
        .digest(owner.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) } + ":"

    override suspend fun read(owner: String): DeviceBillSnapshot = withContext(Dispatchers.IO) {
        val key = prefix(owner)
        val months = preferences.all.mapNotNull { (name, value) ->
            if (!name.startsWith(key) || name == key + "start" || value !is String) return@mapNotNull null
            runCatching {
                val month = YearMonth.parse(name.removePrefix(key))
                val cached = gson.fromJson(value, CachedDeviceMonth::class.java)
                require(cached.savedAt > 0 && cached.projects.isNotBlank())
                require(cached.entries.all { it.bill.time.startsWith(month.toString()) })
                month to cached
            }.getOrNull()
        }.toMap()
        val start = runCatching { YearMonth.parse(preferences.getString(key + "start", "")) }.getOrNull()
        DeviceBillSnapshot(months, start)
    }

    override suspend fun saveMonth(owner: String, month: YearMonth, value: CachedDeviceMonth) = withContext(Dispatchers.IO) {
        check(preferences.edit().putString(prefix(owner) + month, gson.toJson(value)).commit()) { "设备账单保存失败" }
    }

    override suspend fun saveStart(owner: String, start: YearMonth) = withContext(Dispatchers.IO) {
        check(preferences.edit().putString(prefix(owner) + "start", start.toString()).commit()) { "同步范围保存失败" }
    }
}
