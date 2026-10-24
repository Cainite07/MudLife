package cn.mudlife.app.model

import com.google.gson.annotations.SerializedName

/**
 * 趣智后勤网关通用响应实体
 */
data class QzhqResponse<T>(
    @SerializedName("errorCode") val errorCode: Int = 0,
    @SerializedName("message") val message: String = "",
    @SerializedName("data") val data: T? = null
) {
    val isSuccess: Boolean get() = errorCode == 0
}

/**
 * 后勤登录响应数据
 */
data class QzhqLoginData(
    @SerializedName("loginCode") val loginCode: String? = null,
    @SerializedName("telPhone") val telPhone: String? = null,
    @SerializedName("projectId") val projectId: Int = 816,
    @SerializedName("studentNo") val studentNo: String? = null,
    @SerializedName("name") val name: String? = null
)

/**
 * 后勤使用码实体
 */
data class QzhqUseCodeData(
    /** Some deployments return three suffix digits; accept eight only when supplied in full. */
    @SerializedName("randomCode") val randomCode: String? = null,
    /** 状态：1 = 已开启，0 = 已关闭 */
    @SerializedName("useCodeStatus") val useCodeStatus: Int = 0
)
