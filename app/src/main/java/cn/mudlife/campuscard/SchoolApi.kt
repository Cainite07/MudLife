package cn.mudlife.campuscard

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class LoginExpired : IOException("学校登录已过期")
class SchoolFailure(message: String) : IOException(message)

data class Account(val type: String, val name: String, val balance: Long?)
data class CampusCard(val account: String, val name: String, val owner: String, val number: String,
    val physicalBalance: Long?, val unsettled: Long?, val accounts: List<Account>, val blocked: Boolean) {
    fun balance(type: String): Long? {
        val electronic = if (accounts.isNotEmpty() && accounts.all { it.balance != null }) accounts.sumOf { it.balance!! } else null
        val physical = if (physicalBalance != null && unsettled != null) Math.addExact(physicalBalance, unsettled) else null
        return when (type) { "1" -> electronic; "2" -> physical; "0" -> if (electronic != null && physical != null) Math.addExact(electronic, physical) else null; else -> null }
    }
}
data class SchoolConfig(val accountType: String, val feeItem: String, val codeRefreshSeconds: Int)
data class CodeMethod(val id: String, val account: String, val payacc: String, val paytype: String, val name: String, val available: Boolean)
data class PaymentCode(val value: String, val validSeconds: Int)
data class RechargeRules(val minFen: Long, val maxFen: Long, val amounts: List<Long>, val custom: Boolean, val wholeYuan: Boolean)

class SchoolApi(private val session: SessionStore) {
    internal val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS).followRedirects(false).build()

    internal suspend fun get(path: String, params: Map<String, String> = emptyMap(), form: Boolean = false, bearerOverride: String? = null): JSONObject = withContext(Dispatchers.IO) {
        val token = (bearerOverride ?: session.token).removePrefix("bearer ")
        if (token.isBlank()) throw LoginExpired()
        val url = (ORIGIN + path).toHttpUrl().newBuilder().addQueryParameter("synAccessSource", "h5")
        if (!form) params.forEach { (k, v) -> url.addQueryParameter(k, v) }
        val builder = Request.Builder().url(url.build()).header("synjones-auth", "bearer $token")
            .header("synAccessSource", "h5").header("Accept", "application/json")
        if (form) builder.post(okhttp3.FormBody.Builder().apply { params.forEach { (k, v) -> add(k, v) }; add("synAccessSource", "h5") }.build())
        val request = builder.build()
        client.newCall(request).execute().use { response ->
            if (LoginPolicy.needsReauthentication(response.code)) throw LoginExpired()
            if (!response.isSuccessful) throw SchoolFailure("学校服务暂时无法访问（${response.code}），登录状态已保留")
            val body = response.body?.string() ?: throw SchoolFailure("学校没有返回数据")
            val result = try { JSONObject(body) } catch (_: Exception) { throw SchoolFailure("学校返回的数据暂时无法识别") }
            if (result.optInt("code") == 401) throw LoginExpired()
            if (result.optInt("code") != 200) throw SchoolFailure(safeMessage(result.optString("msg")))
            result
        }
    }

    suspend fun config(): SchoolConfig {
        val data = get("/berserker-app/frontInfo", mapOf("type" to "h5")).getJSONObject("data")
        val ec = JSONObject(data.getString("getEcardConfig"))
        val fc = JSONObject(data.getString("getFrontConfig"))
        return SchoolConfig(ec.optString("type"), fc.optString("recharge"), fc.optInt("codeRefreshTime", 60).coerceIn(5, 60))
    }

    suspend fun cards(): List<CampusCard> {
        val data = get("/berserker-app/ykt/tsm/getCampusCards").getJSONObject("data")
        checkBusiness(data)
        return data.optJSONArray("card").objects().map { c ->
            CampusCard(c.getString("account"), c.optString("cardname").ifBlank { "校园卡" }, c.optString("name"), c.optString("sno"),
                c.longOrNull("db_balance"), c.longOrNull("unsettle_amount"),
                c.optJSONArray("accinfo").objects().map { a -> Account(a.optString("type"), a.optString("name").ifBlank { "电子账户" }, a.longOrNull("balance")) },
                c.optString("lostflag") == "1" || c.optString("freezeflag") == "1")
        }
    }

    suspend fun methods(): List<CodeMethod> = get("/berserker-app/ykt/tsm/codebarPayinfo").optJSONArray("data").objects().map { p ->
        CodeMethod(p.optString("id"), p.optString("account"), p.optString("payacc"), p.optString("paytype"),
            p.optString("name").ifBlank { p.optString("title").ifBlank { "校园卡付款" } },
            p.optString("status") in setOf("1", "true") && p.optString("account").isNotBlank() && p.optString("paytype").isNotBlank())
    }

    suspend fun code(method: CodeMethod, maxSeconds: Int): PaymentCode {
        val data = get("/berserker-app/ykt/tsm/batchGetBarCodeGet", mapOf("account" to method.account, "payacc" to method.payacc, "paytype" to method.paytype)).getJSONObject("data")
        checkBusiness(data)
        val barcode = data.optJSONArray("barcode")?.optString(0).orEmpty()
        val expires = data.optInt("expires", 0)
        if (barcode.isBlank() || expires <= 0) throw SchoolFailure("学校暂未提供有效付款码，请稍后刷新")
        return PaymentCode(barcode, minOf(expires, maxSeconds))
    }

    suspend fun rechargeRules(feeItem: String): RechargeRules {
        val fee = get("/charge/feeitem/singleFeeitem", mapOf("feeitemid" to feeItem)).getJSONObject("feeitem")
        val flag = fee.optString("flag").getOrNull(7)
        val amounts = fee.optString("layout").split(',').mapNotNull { Money.parseYuan(it.trim()) }
        return RechargeRules(Money.parseYuan(fee.optString("retain_money")) ?: 1L,
            Money.parseYuan(fee.optString("maxmoney")) ?: 999900L, amounts, flag != '1', flag == '2')
    }

    private fun checkBusiness(data: JSONObject) {
        val ret = data.optString("retcode")
        if (ret.isNotBlank() && ret !in setOf("0", "0000")) throw SchoolFailure(safeMessage(data.optString("errmsg")))
    }
    companion object {
        const val ORIGIN = "https://ykt.ujs.edu.cn"
        fun safeMessage(message: String) = message.takeIf { it.isNotBlank() && it.length <= 160 && !it.contains("http") && !it.contains("token", true) } ?: "学校暂未返回可用数据，请稍后重试"
    }
}

internal fun JSONObject.longOrNull(key: String): Long? = if (isNull(key)) null else runCatching { java.math.BigDecimal(get(key).toString()).longValueExact() }.getOrNull()
internal fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
