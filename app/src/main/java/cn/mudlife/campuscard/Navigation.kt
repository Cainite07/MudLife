package cn.mudlife.campuscard

import java.net.URI
import java.net.URLDecoder

object Navigation {
    class CheckoutReference(val orderId: String, val token: String)
    private val hosts = setOf("ykt.ujs.edu.cn", "pass.ujs.edu.cn", "ecardpay.ujs.edu.cn", "wx.tenpay.com")
    fun web(url: String?): Boolean {
        val u = runCatching { URI(url ?: return false) }.getOrNull() ?: return false
        val host = u.host?.lowercase() ?: return false
        return u.scheme.equals("https", true) && host in hosts && u.rawUserInfo == null &&
            u.rawAuthority.lowercase() in setOf(host, "$host:443") &&
            (host != "wx.tenpay.com" || u.path == "/cgi-bin/mmpayweb-bin/checkmweb")
    }
    fun wechat(url: String?): Boolean {
        val u = runCatching { URI(url ?: return false) }.getOrNull() ?: return false
        if (u.scheme != "weixin" || u.rawAuthority != "wap" || u.path != "/pay" || u.rawFragment != null) return false
        val ids = u.rawQuery.orEmpty().split('&').mapNotNull { part ->
            val fields = part.split('=', limit = 2)
            val key = decode(fields[0]) ?: return false
            when {
                key in setOf("prepayid", "prepay_id") -> fields.getOrNull(1)?.let(::decode).orEmpty()
                fields.size == 1 && key.startsWith("prepayid=") -> key.substringAfter('=').takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,256}")) }.orEmpty()
                else -> null
            }
        }
        return ids.size == 1 && ids[0].isNotBlank()
    }
    fun checkout(url: String?): Boolean = checkoutReference(url) != null
    fun checkoutReference(url: String?): CheckoutReference? {
        if (!web(url)) return null
        val u = URI(url)
        if (u.host != "ykt.ujs.edu.cn" || u.path !in setOf("/payment/", "/payment/index") || u.rawFragment != null) return null
        val fields = u.rawQuery.orEmpty().split('&').map { part ->
            val pair = part.split('=', limit = 2)
            (decode(pair[0]) ?: return null) to (decode(pair.getOrElse(1) { "" }) ?: return null)
        }
        if (fields.count { it.first == "orderid" } != 1 || fields.count { it.first == "token" } != 1) return null
        val id = fields.single { it.first == "orderid" }.second
        val token = fields.single { it.first == "token" }.second
        if (!id.matches(Regex("[A-Za-z0-9_-]{1,200}")) || token.isBlank() || token.length > 16384 || token.any { it.isISOControl() }) return null
        return CheckoutReference(id, token)
    }
    private fun decode(s: String): String? = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrNull()
}
