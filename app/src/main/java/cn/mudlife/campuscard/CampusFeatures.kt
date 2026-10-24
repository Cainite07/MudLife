package cn.mudlife.campuscard

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

data class Bill(val id: String, val title: String, val time: String, val fen: Long?, val type: String,
    val typeName: String, val merchant: String, val payment: String, val balance: Long?, val direction: String = "", val description: String = "") {
    val displayAmount: String get() = fen?.let { BillPolicy.prefix(type, direction) + Money.yuan(kotlin.math.abs(it)) } ?: "—"
}
object BillPolicy {
    fun prefix(type: String, direction: String = "") = when (direction) {
        "1" -> "+"; "2" -> "−"
        else -> when (type) { "1", "4" -> "−"; "2", "3", "5" -> "+"; else -> "" }
    }
    // The search API's type is money direction, not the turnoverType metadata ID.
    val filters = listOf(BillType("", "全部"), BillType("2", "支出"), BillType("1", "收入"))
    fun title(type: String, merchant: String, description: String, payment: String, tradeCode: String): String {
        fun readable(s: String) = s.trim().takeUnless { it.isBlank() || it.lowercase() in setOf("null", "undefined") || it.matches(Regex("[0-9]+(?:[-_:/][0-9]+)*")) }.orEmpty()
        if (type == "2") return when { payment.contains("微信") -> "微信充值"; payment.contains("支付宝") -> "支付宝充值"; payment.contains("银行") -> "银行卡充值"; else -> "校园卡充值" }
        if (type == "3") return "校园卡退款"
        if (type == "5") return "补助入账"
        // Jiangsu University's Hongtu electronic-account bath deductions; leave other Hongtu transactions unchanged.
        if (type == "1" && merchant.trim() == "宏图" && payment == "电子账户消费" && tradeCode == "94") return "洗浴消费"
        readable(merchant).takeIf { it.isNotBlank() }?.let { return it.replace(Regex("_[0-9]+$"), "") }
        val detail = readable(description).replace(Regex("[-－](?:电子账户消费|扫码支付|微信支付缴费)$"), "")
        if (detail.isNotBlank()) return detail
        return readable(payment).ifBlank { "校园卡消费" }
    }
}
data class BillPage(val records: List<Bill>, val page: Int, val pages: Int, val total: Long?)
data class BillSummary(val income: Long?, val expenses: Long?)
data class BillType(val id: String, val name: String)
suspend fun SchoolApi.billSummary(month: YearMonth?): BillSummary {
    val summary = get("/berserker-search/statistics/turnover/count", billDates(month)).getJSONObject("data")
    return BillSummary(summary.longOrNull("income"), summary.longOrNull("expenses"))
}
internal fun billDates(month: YearMonth?): Map<String, String> = if (month == null) emptyMap() else mapOf("timeFrom" to month.atDay(1).toString(), "timeTo" to month.atEndOfMonth().toString())
suspend fun SchoolApi.bills(page: Int, month: YearMonth?, type: String, size: Int = 200): BillPage {
    require(type in BillPolicy.filters.map { it.id })
    val filters = billDates(month).toMutableMap()
    if (type.isNotBlank()) filters["type"] = type
    val data = get("/berserker-search/search/personal/turnover", filters + mapOf("size" to "$size", "current" to "$page")).getJSONObject("data")
    return BillPage(data.optJSONArray("records").objects().map { r ->
        val merchant = r.clean("toMerchant")
        val typeName = r.clean("turnoverType").ifBlank { r.clean("consumeTypeName") }
        Bill(r.clean("orderId"), BillPolicy.title(r.clean("typeId"), merchant, r.clean("resume"), r.clean("payName"), r.clean("tranCode")),
            r.clean("effectdateStr").ifBlank { r.clean("jndatetimeStr") }, r.longOrNull("tranamt"), r.clean("typeId"), typeName,
            merchant, r.clean("payName"), r.longOrNull("cardBalance"), r.clean("typeFrom"), r.clean("resume"))
    }, data.optInt("current", page), data.optInt("pages"), data.longOrNull("total"))
}

object SchoolMarkup {
    // Consume whole quoted attributes so onerror="this.src='fallback'" cannot masquerade as src.
    fun attribute(tag: String, name: String): String = Regex("([A-Za-z_:][-A-Za-z0-9_:.]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')")
        .findAll(tag).firstOrNull { it.groupValues[1].equals(name, true) }
        ?.let { it.groupValues[2].ifEmpty { it.groupValues[3] } }.orEmpty()
}

data class CardText(val text: String, val x: Float, val y: Float, val width: Float, val color: Long, val size: Float)
data class StudentIdentity(val name: String, val number: String, val department: String, val identity: String,
    val background: Bitmap?, val portrait: Bitmap?, val texts: List<CardText>, val portraitX: Float, val portraitY: Float, val portraitWidth: Float)

/** School HTML is parsed as data; no school HTML or JavaScript is rendered in the profile. */
suspend fun SchoolApi.identity(account: String): StudentIdentity = withContext(Dispatchers.IO) {
    val user = get("/berserker-base/user").getJSONObject("data")
    val styles = get("/berserker-app/cardStyle").optJSONObject("data")
    val model = styles?.optJSONObject(account)?.clean("model").orEmpty()
    fun tag(className: String) = Regex("<img\\b[^>]*class=[\"'][^\"']*\\b$className\\b[^\"']*[\"'][^>]*>", RegexOption.IGNORE_CASE).find(model)?.value.orEmpty()
    fun attribute(tag: String, name: String) = SchoolMarkup.attribute(tag, name)
    fun percent(style: String, name: String, default: Float) = Regex("(?:^|;)\\s*$name\\s*:\\s*([0-9.]+)%").find(style)?.groupValues?.get(1)?.toFloatOrNull()?.div(100f) ?: default
    val bg = attribute(tag("card-img-bg"), "src")
    val photo = tag("card-img-user")
    val photoStyle = attribute(photo, "style")
    val texts = Regex("<div\\b[^>]*class=[\"'][^\"']*card-(?:info|text)-custom[^\"']*[\"'][^>]*>(.*?)</div>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).findAll(model).take(20).mapNotNull { match ->
        val opening = match.value.substringBefore('>') + ">"
        val style = attribute(opening, "style")
        val spanStyle = Regex("<span[^>]*style=[\"']([^\"']*)").find(match.groupValues[1])?.groupValues?.get(1).orEmpty()
        val content = Html.fromHtml(match.groupValues[1], Html.FROM_HTML_MODE_LEGACY).toString().trim()
        if (content.isBlank()) null else CardText(content, percent(style, "left", 0f), percent(style, "top", 0f), percent(style, "width", .5f),
            runCatching { android.graphics.Color.parseColor(Regex("(?:^|;)\\s*color\\s*:\\s*(#[A-Fa-f0-9]{6})").find(style)?.groupValues?.get(1) ?: "#000000").toLong() and 0xffffffff }.getOrDefault(0xff000000),
            Regex("font-size:\\s*([0-9.]+)px").find(spanStyle)?.groupValues?.get(1)?.toFloatOrNull() ?: 14f)
    }.toList()
    val images = coroutineScope {
        val background = async { schoolImage(bg) }
        val portrait = async { schoolImage(attribute(photo, "src")) ?: schoolImage(user.clean("avatar")) }
        background.await() to portrait.await()
    }
    StudentIdentity(user.clean("name"), user.clean("sno"), user.clean("departmentName"), user.clean("identityName"),
        images.first, images.second, texts,
        percent(photoStyle, "left", .09f), percent(photoStyle, "top", .33f), percent(photoStyle, "width", .25f))
}
private suspend fun SchoolApi.schoolImage(raw: String): Bitmap? = withContext(Dispatchers.IO) {
    val url = (if (raw.startsWith('/')) SchoolApi.ORIGIN + raw else raw).toHttpUrlOrNull() ?: return@withContext null
    if (url.scheme != "https" || url.host != "ykt.ujs.edu.cn" || url.port != 443 || url.username.isNotBlank() || url.password.isNotBlank()) return@withContext null
    runCatching {
        // School pictures use signed or public URLs; never forward the bearer token to an image host.
        client.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful || r.body == null || r.body!!.contentLength() > 5_000_000) null
            else {
                val bytes = r.body!!.byteStream().use { input ->
                    val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                    while (output.size() <= 5_000_000) { val read = input.read(buffer); if (read < 0) break; output.write(buffer, 0, read) }
                    output.toByteArray()
                }
                if (bytes.size > 5_000_000) null else {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    var sample = 1
                    while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                }
            }
        }
    }.getOrNull()
}

data class ElectricOption(val id: String, val name: String)
data class Dorm(val campus: ElectricOption, val region: ElectricOption, val building: ElectricOption, val room: ElectricOption) {
    val title get() = listOf(region.name, building.name, room.name).joinToString(" · ")
    fun fields() = mapOf("campus" to campus.id, "building" to region.id, "floor" to building.id, "room" to room.id)
    fun toJson() = JSONObject().apply { put("feeitem", "408"); listOf(campus, region, building, room).forEachIndexed { i, o -> put(ElectricPolicy.keys[i], JSONObject().put("id", o.id).put("name", o.name)) } }
    companion object {
        fun parse(json: JSONObject): Dorm? = runCatching {
            if (json.optString("feeitem") != "408") return null
            val o = ElectricPolicy.keys.map { key -> json.getJSONObject(key).let { ElectricOption(it.getString("id"), it.getString("name")) } }
            Dorm(o[0], o[1], o[2], o[3]).takeIf { ElectricPolicy.valid(it) }
        }.getOrNull()
    }
}
object ElectricPolicy {
    val keys = listOf("campus", "building", "floor", "room")
    fun valid(d: Dorm) = d.campus.id == "校本部" && d.fields().values.all { it.isNotBlank() && it.length <= 200 }
    fun cost(quantity: String, price: String): Long? = runCatching {
        if (!quantity.matches(Regex("[0-9]{1,5}(\\.[0-9]{1,2})?"))) return null
        val q = BigDecimal(quantity); val p = BigDecimal(price)
        if (q.signum() <= 0 || p.signum() <= 0) return null
        q.multiply(p).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact().takeIf { it > 0 }
    }.getOrNull()
}
data class ElectricRules(val price: String, val minFen: Long, val maxFen: Long, val notice: String)
data class ElectricResult(val options: List<ElectricOption>, val info: List<Pair<String, String>>, val price: String?, val blocked: Boolean, val message: String, val minFen: Long?, val fixedFen: Long?)
suspend fun SchoolApi.electricRules(): ElectricRules {
    val fee = get("/charge/feeitem/singleFeeitem", mapOf("feeitemid" to "408")).getJSONObject("feeitem")
    return ElectricRules(fee.clean("price"), Money.parseYuan(fee.clean("retain_money")) ?: 1,
        Money.parseYuan(fee.clean("maxmoney")) ?: throw SchoolFailure("学校未提供电费限额"),
        Html.fromHtml(fee.clean("remark"), Html.FROM_HTML_MODE_LEGACY).toString().trim())
}
suspend fun SchoolApi.electricOptions(level: Int, fields: Map<String, String>, query: Boolean = false): ElectricResult {
    require(level in 0..4 && fields["campus"] == "校本部")
    val data = get("/charge/feeitem/getThirdData", fields + mapOf("feeitemid" to "408", "type" to if (query) "IEC" else "select", "level" to "$level"), form = true).getJSONObject("map")
    val info = data.optJSONObject("showData")
    return ElectricResult(data.optJSONArray("data").objects().map { ElectricOption(it.clean("value"), it.clean("name")) }.filter { it.id.isNotBlank() },
        info?.keys()?.asSequence()?.map { it to info.clean(it) }?.toList().orEmpty(), data.clean("customPrice").takeIf { it.isNotBlank() },
        data.optBoolean("notPay"), SchoolApi.safeMessage(data.clean("tipinfo")),
        Money.parseYuan(data.clean("minMoney")), Money.parseYuan(data.clean("money")))
}
internal fun JSONObject.clean(key: String): String = if (isNull(key)) "" else optString(key).takeUnless { it == "null" }.orEmpty()
