package cn.mudlife.app.campuscard

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

/** Navigation only: page resources and the school's own requests stay in WebView. */
object CampusCardNavigation {
    const val HOME_URL = "https://ykt.ujs.edu.cn/plat/"
    const val WALLET_URL = "https://ykt.ujs.edu.cn/campus-card/campusCard"
    const val RECHARGE_URL = "https://ykt.ujs.edu.cn/campus-card/cardRecharge/"
    const val PAYMENT_URL = "https://ykt.ujs.edu.cn/plat/pay"

    enum class Decision { Internal, Blocked }

    fun decide(url: String?): Decision =
        if (allowedUri(url) != null) Decision.Internal else Decision.Blocked

    fun isPaymentPage(url: String?): Boolean = allowedUri(url)?.let {
        it.host.equals("ykt.ujs.edu.cn", ignoreCase = true) &&
            it.normalize().path.trimEnd('/') in setOf("/plat/pay", "/campus-card/cardDetails")
    } ?: false

    /** A new GET destination only for known code screens; never replay arbitrary pages. */
    fun resumeDestination(url: String?): String? {
        val uri = allowedUri(url) ?: return null
        if (!uri.host.equals("ykt.ujs.edu.cn", ignoreCase = true)) return null
        when (uri.normalize().path.trimEnd('/')) {
            "/campus-card/cardDetails" -> return WALLET_URL
            "/plat/pay" -> Unit
            else -> return null
        }

        val selection = linkedMapOf<String, String>()
        val knownSelectionKeys = setOf("no", "payacc", "payid")
        for (part in uri.rawQuery?.split('&').orEmpty()) {
            val fields = part.split('=', limit = 2)
            val key = decodeParameter(fields[0]) ?: return WALLET_URL
            if (key == "lite") {
                // Lite codes use another API and query-dependent selection. Re-select
                // from the wallet rather than silently turning them into normal codes.
                if (fields.size != 2 || decodeParameter(fields[1]) != "0") return WALLET_URL
            }
            if (key !in knownSelectionKeys) continue
            if (key in selection || fields.size != 2) return WALLET_URL
            val value = decodeParameter(fields[1]) ?: return WALLET_URL
            val valid = when (key) {
                "payid" -> value.matches(Regex("[0-9]{1,10}")) && value.toLong() <= Int.MAX_VALUE
                "payacc" -> value == "###" || value.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,95}"))
                else -> value.matches(Regex("[A-Za-z0-9]{1,64}(?:-[A-Za-z0-9]{1,32})?"))
            }
            if (!valid) return WALLET_URL
            selection[key] = value
        }
        // The current component receives payacc/payid together. A partial selector
        // cannot be safely interpreted; let the user select a wallet again.
        if (("payacc" in selection) != ("payid" in selection)) return WALLET_URL
        val query = knownSelectionKeys.mapNotNull { key ->
            selection[key]?.let { "$key=${URLEncoder.encode(it, "UTF-8")}" }
        }.joinToString("&")
        return if (query.isEmpty()) PAYMENT_URL else "$PAYMENT_URL?$query"
    }

    private fun decodeParameter(value: String): String? = try {
        URLDecoder.decode(value, "UTF-8")
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun allowedUri(url: String?): URI? {
        if (url.isNullOrEmpty()) return null
        val uri = try {
            URI(url)
        } catch (_: Exception) {
            return null
        }
        if (uri.isOpaque || !uri.scheme.equals("https", ignoreCase = true)) return null
        if (uri.rawUserInfo != null || (uri.port != -1 && uri.port != 443)) return null
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        if (host != "ykt.ujs.edu.cn" && host != "pass.ujs.edu.cn") return null
        // Reject ambiguous empty/zero-padded port syntax as well as spoofed authorities.
        val authority = uri.rawAuthority.lowercase(Locale.ROOT)
        if (authority != host && authority != "$host:443") return null
        return uri
    }
}
