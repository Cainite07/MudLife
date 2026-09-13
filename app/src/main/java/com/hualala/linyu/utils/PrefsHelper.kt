package com.hualala.linyu.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.hualala.linyu.model.ActiveOrder

object PrefsHelper {
    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    fun init(context: Context) {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            prefs = EncryptedSharedPreferences.create(
                context,
                "linyu_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) {
            // 降级：如果加密初始化失败（如设备不支持），退回明文存储，避免崩溃
            prefs = context.getSharedPreferences("linyu_prefs", Context.MODE_PRIVATE)
        }
    }

    // ── Auth ──
    var loginCode: String get() = prefs.getString("loginCode", "") ?: ""; set(v) = prefs.edit().putString("loginCode", v).apply()
    var userId: String get() = prefs.getString("userId", "") ?: ""; set(v) = prefs.edit().putString("userId", v).apply()
    var accountId: String get() = prefs.getString("accountId", "") ?: ""; set(v) = prefs.edit().putString("accountId", v).apply()
    var projectId: String get() = prefs.getString("projectId", "") ?: ""; set(v) = prefs.edit().putString("projectId", v).apply()
    var telephone: String get() = prefs.getString("telephone", "") ?: ""; set(v) = prefs.edit().putString("telephone", v).apply()
    var userName: String get() = prefs.getString("userName", "") ?: ""; set(v) = prefs.edit().putString("userName", v).apply()
    var studentNo: String get() = prefs.getString("studentNo", "") ?: ""; set(v) = prefs.edit().putString("studentNo", v).apply()
    var schoolName: String get() = prefs.getString("schoolName", "江苏大学") ?: "江苏大学"; set(v) = prefs.edit().putString("schoolName", v).apply()
    val isLoggedIn: Boolean get() = loginCode.isNotEmpty()

    // ── 后勤专区 (江大热水) ──
    var qzhqLoginCode: String get() = prefs.getString("qzhqLoginCode", "") ?: ""; set(v) = prefs.edit().putString("qzhqLoginCode", v).apply()
    var qzhqPhone: String get() = prefs.getString("qzhqPhone", "") ?: ""; set(v) = prefs.edit().putString("qzhqPhone", v).apply()
    var qzhqProjectId: String get() = prefs.getString("qzhqProjectId", "43") ?: "43"; set(v) = prefs.edit().putString("qzhqProjectId", v).apply()
    var qzhqRandomCode: String get() = prefs.getString("qzhqRandomCode", "") ?: ""; set(v) = prefs.edit().putString("qzhqRandomCode", v).apply()
    var qzhqCustomCode: String get() = prefs.getString("qzhqCustomCode", "") ?: ""; set(v) = prefs.edit().putString("qzhqCustomCode", v).apply()
    var qzhqStatus: Int get() = prefs.getInt("qzhqStatus", 1); set(v) = prefs.edit().putInt("qzhqStatus", v).apply()
    val isQzhqLoggedIn: Boolean get() = qzhqLoginCode.isNotEmpty()

    fun saveAuth(lc: String, uid: String, aid: String, pid: String, phone: String, name: String?, sno: String? = null) {
        loginCode = lc; userId = uid; accountId = aid; projectId = pid; telephone = phone; userName = name ?: ""
        if (!sno.isNullOrEmpty()) studentNo = sno
    }
    fun clear() {
        val editor = prefs.edit()
        // 清除所有已知固定 key
        listOf(
            "loginCode", "userId", "accountId", "projectId",
            "telephone", "userName", "studentNo", "schoolName",
            "lastDeviceName", "lastDeviceMac", "lastDeviceSnCode", "lastDeviceEmoji",
            "boundRoom", "recentDevices", "activeOrders", "themeMode", "projectAccounts",
            // 后勤专区凭证
            "qzhqLoginCode", "qzhqPhone", "qzhqProjectId",
            "qzhqRandomCode", "qzhqCustomCode", "qzhqStatus",
            // 校园卡与吹风机
            "campusCardBalance", "campusCardBalanceTime", "useCode", "useCodeStatus"
        ).forEach { editor.remove(it) }
        // 动态前缀 key 使用 getAll() 遍历删除
        prefs.all.keys.filter {
            it.startsWith("startedAt_") || it.startsWith("autoDiscon_")
        }.forEach { editor.remove(it) }
        editor.apply()
    }

    // ── 多项目账户映射 (projectId -> accountId) ──
    fun saveProjectAccounts(map: Map<String, String>) {
        val json = gson.toJson(map)
        prefs.edit().putString("projectAccounts", json).apply()
    }

    fun getProjectAccounts(): Map<String, String> {
        val json = prefs.getString("projectAccounts", "{}") ?: "{}"
        return try {
            val type = object : com.google.gson.reflect.TypeToken<Map<String, String>>() {}.type
            gson.fromJson<Map<String, String>>(json, type) ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    // ── Last device ──
    var lastDeviceName: String get() = prefs.getString("lastDeviceName", "") ?: ""; set(v) = prefs.edit().putString("lastDeviceName", v).apply()
    var lastDeviceMac: String get() = prefs.getString("lastDeviceMac", "") ?: ""; set(v) = prefs.edit().putString("lastDeviceMac", v).apply()
    var lastDeviceSnCode: String get() = prefs.getString("lastDeviceSnCode", "") ?: ""; set(v) = prefs.edit().putString("lastDeviceSnCode", v).apply()
    var lastDeviceEmoji: String get() = prefs.getString("lastDeviceEmoji", "🚰") ?: "🚰"; set(v) = prefs.edit().putString("lastDeviceEmoji", v).apply()

    // ── 常用/历史饮水机列表 ──
    fun getRecentDevices(): MutableList<com.hualala.linyu.model.RecentDevice> {
        val json = prefs.getString("recentDevices", "[]") ?: "[]"
        val result = try {
            val array = JsonParser().parse(json).asJsonArray
            val list = mutableListOf<com.hualala.linyu.model.RecentDevice>()
            for (item in array) {
                list.add(gson.fromJson(item, com.hualala.linyu.model.RecentDevice::class.java))
            }
            list
        } catch (_: Exception) { mutableListOf() }

        if (result.isEmpty() && lastDeviceSnCode.isNotEmpty()) {
            val wType = if (lastDeviceSnCode.contains(",G,") || lastDeviceName.contains("冷")) "冷水"
                        else if (lastDeviceSnCode.contains(",M,") || lastDeviceName.contains("热")) "热水"
                        else "直饮水"
            val dev = com.hualala.linyu.model.RecentDevice(
                name = lastDeviceName,
                mac = lastDeviceMac,
                snCode = lastDeviceSnCode,
                emoji = lastDeviceEmoji,
                waterType = wType
            )
            result.add(dev)
        }
        return result
    }

    fun addRecentDevice(device: com.hualala.linyu.model.RecentDevice) {
        val list = getRecentDevices()
        list.removeAll { it.snCode == device.snCode || (it.mac.isNotEmpty() && it.mac == device.mac) }
        list.add(0, device)
        val trimmed = list.take(4)
        prefs.edit().putString("recentDevices", gson.toJson(trimmed)).apply()
        lastDeviceName = device.name
        lastDeviceMac = device.mac
        lastDeviceSnCode = device.snCode
        lastDeviceEmoji = device.emoji
    }

    fun pinRecentDevice(snCode: String) {
        val list = getRecentDevices()
        val index = list.indexOfFirst { it.snCode == snCode }
        if (index > 0) {
            val dev = list.removeAt(index)
            list.add(0, dev)
            prefs.edit().putString("recentDevices", gson.toJson(list)).apply()
        }
    }

    fun deleteRecentDevice(snCode: String) {
        val list = getRecentDevices()
        list.removeAll { it.snCode == snCode }
        prefs.edit().putString("recentDevices", gson.toJson(list)).apply()
        if (lastDeviceSnCode == snCode) {
            lastDeviceName = ""
            lastDeviceMac = ""
            lastDeviceSnCode = ""
            lastDeviceEmoji = "🚰"
        }
    }

    // ── 绑定的寝室（设备筛选关键词） ──
    var boundRoom: String get() = prefs.getString("boundRoom", "") ?: ""; set(v) = prefs.edit().putString("boundRoom", v).apply()

    // ── Active orders list ──
    fun getActiveOrders(): MutableList<ActiveOrder> {
        val json = prefs.getString("activeOrders", "[]") ?: "[]"
        return try {
            val array = JsonParser().parse(json).asJsonArray
            val result = mutableListOf<ActiveOrder>()
            for (item in array) {
                result.add(gson.fromJson(item, ActiveOrder::class.java))
            }
            result
        } catch (_: Exception) { mutableListOf() }
    }

    fun saveActiveOrders(orders: List<ActiveOrder>) {
        prefs.edit().putString("activeOrders", gson.toJson(orders)).apply()
    }

    fun clearActiveOrders() = prefs.edit().remove("activeOrders").apply()

    var themeMode: String
        get() = prefs.getString("themeMode", "SYSTEM") ?: "SYSTEM"
        set(value) = prefs.edit().putString("themeMode", value).apply()

    var manualBalance: String get() = prefs.getString("manualBalance", "") ?: ""; set(v) = prefs.edit().putString("manualBalance", v).apply()
    var manualBalanceTime: Long get() = prefs.getLong("manualBalanceTime", 0L); set(v) = prefs.edit().putLong("manualBalanceTime", v).apply()

    // ── Per-device timer ──
    fun getStartedAt(snCode: String): Long = prefs.getLong("startedAt_$snCode", 0L)
    fun setStartedAt(snCode: String, v: Long) = prefs.edit().putLong("startedAt_$snCode", v).apply()

    // ── 自动关停倒计时（以毫秒时间戳持久化，App 重启后可恢复） ──
    private fun autoDisconKey(snCode: String) = "autoDiscon_$snCode"

    /** 剩余秒数，依据持久化的截止时间戳计算；无记录返回 0 */
    fun getAutoDisconRemain(snCode: String): Int {
        val deadline = prefs.getLong(autoDisconKey(snCode), 0L)
        if (deadline <= 0L) return 0
        val remain = ((deadline - System.currentTimeMillis()) / 1000).toInt()
        return if (remain > 0) remain else 0
    }

    /** 设置剩余秒数，转换为截止时间戳保存 */
    fun setAutoDisconRemain(snCode: String, seconds: Int) {
        if (seconds <= 0) {
            prefs.edit().remove(autoDisconKey(snCode)).apply()
        } else {
            prefs.edit().putLong(autoDisconKey(snCode), System.currentTimeMillis() + seconds * 1000L).apply()
        }
    }

    fun clearAutoDiscon(snCode: String) = prefs.edit().remove(autoDisconKey(snCode)).apply()

    // ── 校园卡余额（洗浴结算同步） ──
    var campusCardBalance: String
        get() = prefs.getString("campusCardBalance", "") ?: ""
        set(v) = prefs.edit().putString("campusCardBalance", v).apply()

    var campusCardBalanceTime: Long
        get() = prefs.getLong("campusCardBalanceTime", 0L)
        set(v) = prefs.edit().putLong("campusCardBalanceTime", v).apply()

    // ── 吹风机使用码（趣智校园原生） ──
    var useCode: String
        get() = prefs.getString("useCode", "") ?: ""
        set(v) = prefs.edit().putString("useCode", v).apply()

    var useCodeStatus: Boolean
        get() = prefs.getBoolean("useCodeStatus", true)
        set(v) = prefs.edit().putBoolean("useCodeStatus", v).apply()
}
