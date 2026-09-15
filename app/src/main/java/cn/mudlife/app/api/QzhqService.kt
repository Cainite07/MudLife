package cn.mudlife.app.api

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * 微信小程序「趣智后勤热水」专线 API 服务契约
 */
interface QzhqService {

    /**
     * 登录后勤专区账号
     */
    @FormUrlEncoded
    @POST("/combination/user/login")
    fun login(
        @Field("telPhone") phone: String,
        @Field("password") passwordHash: String,
        @Field("typeId") typeId: Int = 0,
        @Field("phoneSystem") phoneSystem: String = "WeChat",
        @Field("version") version: String = "5.0.0"
    ): Call<ResponseBody>

    /**
     * 查询后勤洗浴使用码
     */
    @GET("/combination/user/useCode")
    fun getUseCode(
        @Query("telPhone") phone: String,
        @Query("projectId") projectId: String = PROJECT_ID,
        @Query("loginCode") loginCode: String,
        @Query("phoneSystem") phoneSystem: String = "WeChat",
        @Query("version") version: String = "5.0.0"
    ): Call<ResponseBody>

    /**
     * 开关使用码 (1=开启, 0=关闭)
     */
    @FormUrlEncoded
    @POST("/combination/user/useCode/status/update")
    fun updateUseCodeStatus(
        @Field("useCodeStatus") status: Int,
        @Field("telPhone") phone: String,
        @Field("projectId") projectId: String = PROJECT_ID,
        @Field("loginCode") loginCode: String,
        @Field("phoneSystem") phoneSystem: String = "WeChat",
        @Field("version") version: String = "5.0.0"
    ): Call<ResponseBody>

    /**
     * 自定义修改使用码 (5 位新前缀)
     */
    @FormUrlEncoded
    @POST("/combination/user/useCode/set")
    fun setUseCode(
        @Field("securityCode") securityCode: String,
        @Field("telPhone") phone: String,
        @Field("projectId") projectId: String = PROJECT_ID,
        @Field("loginCode") loginCode: String,
        @Field("phoneSystem") phoneSystem: String = "WeChat",
        @Field("version") version: String = "5.0.0"
    ): Call<ResponseBody>

    /**
     * 发送后勤短信验证码
     */
    @GET("/combination/user/verifyCode")
    fun getVerifyCode(
        @Query("telPhone") phone: String,
        @Query("secret") secret: String,
        @Query("typeId") typeId: Int = 3
    ): Call<ResponseBody>

    /**
     * 验证码免密登录后勤专区
     */
    @FormUrlEncoded
    @POST("/combination/sms/login")
    fun smsLogin(
        @Field("phone") phone: String,
        @Field("msg") msg: String,
        @Field("phoneSystem") phoneSystem: String = "WeChat",
        @Field("version") version: String = "5.0.0"
    ): Call<ResponseBody>

    /**
     * 查询后勤洗浴消费明细
     */
    @GET("/combination/wallet/billList")
    fun getWalletBillList(
        @Query("telPhone") phone: String,
        @Query("curNum") curNum: Int = 1,
        @Query("typeId") typeId: Int = 0,
        @Query("beginDate") beginDate: String = "2015-09-01 21:30:28",
        @Query("endDate") endDate: String,
        @Query("isOpUser") isOpUser: Int = 0,
        @Query("projectId") projectId: String = PROJECT_ID,
        @Query("loginCode") loginCode: String,
        @Query("phoneSystem") phoneSystem: String = "WeChat",
        @Query("version") version: String = "5.0.0"
    ): Call<ResponseBody>

    companion object {
        const val BASE_URL = "https://qzhqapi.china-qzxy.cn"
        const val PROJECT_ID = "43"
    }
}
