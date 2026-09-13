package com.hualala.linyu.utils

import java.security.MessageDigest

/**
 * 趣智校园动态请求签名工具
 * 算法逆向自官方 APK (com.klcxkj.zqxy) 内存脱壳 DEX:
 * LoginCodeActivity.w1 -> sj.e0.j
 *
 * 验证码签名规则：
 * raw = phone.substring(0, 3) + phone.substring(7, 11) + "klcx"
 * secret = MD5(raw) (32 位小写十六进制)
 */
object SignUtils {
    fun sign(telephone: String): String {
        require(telephone.length == 11) { "手机号必须为11位" }
        val raw = telephone.substring(0, 3) + telephone.substring(7, 11) + "klcx"
        return md5(raw)
    }

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        return digest.digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
