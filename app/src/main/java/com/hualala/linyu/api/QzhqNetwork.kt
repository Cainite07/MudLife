package com.hualala.linyu.api

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.hualala.linyu.model.QzhqLoginData
import com.hualala.linyu.model.QzhqResponse
import com.hualala.linyu.model.QzhqUseCodeData
import com.hualala.linyu.utils.MD5Utils
import com.hualala.linyu.utils.PrefsHelper
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object QzhqNetwork {
    private val gson = Gson()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request()
            val resp = try {
                chain.proceed(req)
            } catch (e: Exception) {
                com.hualala.linyu.utils.AppLogger.e("QzhqNet", "后勤网络请求失败: ${req.method} ${req.url}", e)
                throw e
            }
            try {
                val isFailed = !resp.isSuccessful
                val body = resp.peekBody(1024 * 64).string()
                val isBizFail = body.contains("\"errorCode\":") && !body.contains("\"errorCode\":0")
                val isEmptyData = req.method == "GET" && (body.contains("\"data\":[]") || body.contains("\"data\": null") || body.contains("\"data\":null"))

                // 过滤无业务变动的空数据响应
                if (!isEmptyData || isFailed || isBizFail) {
                    com.hualala.linyu.utils.AppLogger.net(req.method, req.url.toString(), resp.code, body)
                }
            } catch (_: Exception) {}
            resp
        }
        .build()

    val apiService: QzhqService = Retrofit.Builder()
        .baseUrl(QzhqService.BASE_URL)
        .client(okHttpClient)
        .build()
        .create(QzhqService::class.java)

    private suspend fun Call<ResponseBody>.awaitString(): String {
        return suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { this.cancel() }
            this.enqueue(object : Callback<ResponseBody> {
                override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                    if (response.isSuccessful) {
                        val body = response.body()
                        if (body != null) {
                            cont.resume(body.string())
                        } else {
                            cont.resumeWithException(Exception("响应内容为空"))
                        }
                    } else {
                        cont.resumeWithException(Exception("HTTP ${response.code()}: ${response.message()}"))
                    }
                }

                override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                    cont.resumeWithException(t)
                }
            })
        }
    }

    private fun <T> parse(json: String, dataClass: Class<T>? = null): QzhqResponse<T> {
        return try {
            val obj = JsonParser.parseString(json).asJsonObject
            val errorCode = obj.get("errorCode")?.let { if (it.isJsonNull) -1 else it.asInt } ?: -1
            val message = obj.get("message")?.let { if (it.isJsonNull) "" else it.asString } ?: ""
            val dataElement = obj.get("data")
            val data: T? = if (dataElement != null && !dataElement.isJsonNull && dataClass != null) {
                gson.fromJson(dataElement, dataClass)
            } else null
            QzhqResponse(errorCode, message, data)
        } catch (e: Exception) {
            QzhqResponse(-1, "数据解析异常: ${e.message}", null)
        }
    }

    private fun <T> parseList(json: String, itemClass: Class<T>): QzhqResponse<List<T>> {
        return try {
            val obj = JsonParser.parseString(json).asJsonObject
            val errorCode = obj.get("errorCode")?.let { if (it.isJsonNull) -1 else it.asInt } ?: -1
            val message = obj.get("message")?.let { if (it.isJsonNull) "" else it.asString } ?: ""
            val dataElement = obj.get("data")
            val list = mutableListOf<T>()
            if (dataElement != null && dataElement.isJsonArray) {
                for (elem in dataElement.asJsonArray) {
                    val item = gson.fromJson(elem, itemClass)
                    if (item != null) list.add(item)
                }
            }
            QzhqResponse(errorCode, message, list)
        } catch (e: Exception) {
            com.hualala.linyu.utils.AppLogger.e("QzhqNet", "parseList 数据解析异常: ${e.message}", e)
            QzhqResponse(-1, "数据解析异常: ${e.message}", emptyList())
        }
    }

    suspend fun login(phone: String, passwordRaw: String): QzhqResponse<QzhqLoginData> {
        val encrypted = MD5Utils.encryptPassword(passwordRaw)
        val json = apiService.login(phone, encrypted).awaitString()
        val resp = parse(json, QzhqLoginData::class.java)
        if (resp.isSuccess && resp.data != null) {
            resp.data.loginCode?.let { PrefsHelper.qzhqLoginCode = it }
            PrefsHelper.qzhqPhone = phone
            val pid = if (resp.data.projectId > 0) resp.data.projectId.toString() else "43"
            PrefsHelper.qzhqProjectId = pid
        }
        return resp
    }

    suspend fun sendVerifyCode(phone: String): QzhqResponse<Unit> {
        val s = phone.take(3) + phone.takeLast(4) + "klcx"
        val secret = MD5Utils.md5(s).lowercase()
        val json = apiService.getVerifyCode(phone = phone, secret = secret, typeId = 3).awaitString()
        return parse(json, Unit::class.java)
    }

    suspend fun smsLogin(phone: String, smsCode: String): QzhqResponse<QzhqLoginData> {
        val json = apiService.smsLogin(phone = phone, msg = smsCode).awaitString()
        val resp = parse(json, QzhqLoginData::class.java)
        if (resp.isSuccess && resp.data != null) {
            resp.data.loginCode?.let { PrefsHelper.qzhqLoginCode = it }
            PrefsHelper.qzhqPhone = phone
            val pid = if (resp.data.projectId > 0) resp.data.projectId.toString() else "43"
            PrefsHelper.qzhqProjectId = pid
        }
        return resp
    }

    suspend fun getWalletBillList(phone: String, loginCode: String): QzhqResponse<List<com.hualala.linyu.model.QzhqWalletBillItem>> {
        val nowStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA).format(java.util.Date())
        val projectId = PrefsHelper.qzhqProjectId.ifEmpty { QzhqService.PROJECT_ID }
        val allBills = mutableListOf<com.hualala.linyu.model.QzhqWalletBillItem>()
        var curNum = 1
        var lastError: String? = null
        var lastErrorCode = 0

        while (curNum <= 5) {
            try {
                val json = apiService.getWalletBillList(
                    phone = phone,
                    loginCode = loginCode,
                    projectId = projectId,
                    endDate = nowStr,
                    curNum = curNum
                ).awaitString()
                val pageResp = parseList(json, com.hualala.linyu.model.QzhqWalletBillItem::class.java)
                if (pageResp.isSuccess) {
                    val list = pageResp.data ?: emptyList()
                    allBills.addAll(list)
                    if (list.size < 20) {
                        break
                    }
                    curNum++
                } else {
                    lastError = pageResp.message
                    lastErrorCode = pageResp.errorCode
                    com.hualala.linyu.utils.AppLogger.w("QzhqNet", "第 $curNum 页后勤账单获取失败: ${pageResp.message}")
                    break
                }
            } catch (e: Exception) {
                lastError = e.message
                lastErrorCode = -1
                com.hualala.linyu.utils.AppLogger.e("QzhqNet", "拉取第 $curNum 页后勤账单异常: ${e.message}")
                break
            }
        }
        return if (allBills.isNotEmpty()) {
            com.hualala.linyu.utils.AppLogger.i("QzhqNet", "后勤账单加载完毕，共 ${allBills.size} 笔")
            QzhqResponse(0, "成功", allBills)
        } else {
            QzhqResponse(lastErrorCode, lastError ?: "暂无记录", emptyList())
        }
    }

    suspend fun getUseCode(phone: String, loginCode: String): QzhqResponse<QzhqUseCodeData> {
        val projectId = PrefsHelper.qzhqProjectId
        val json = apiService.getUseCode(phone = phone, projectId = projectId, loginCode = loginCode).awaitString()
        val resp = parse(json, QzhqUseCodeData::class.java)
        if (resp.isSuccess && resp.data != null) {
            resp.data.randomCode?.let { PrefsHelper.qzhqRandomCode = it }
            PrefsHelper.qzhqStatus = resp.data.useCodeStatus
        }
        return resp
    }

    suspend fun updateUseCodeStatus(status: Int, phone: String, loginCode: String): QzhqResponse<Unit> {
        val projectId = PrefsHelper.qzhqProjectId
        val json = apiService.updateUseCodeStatus(status = status, phone = phone, projectId = projectId, loginCode = loginCode).awaitString()
        val resp = parse(json, Unit::class.java)
        if (resp.isSuccess) {
            PrefsHelper.qzhqStatus = status
        }
        return resp
    }

    suspend fun setUseCode(code: String, phone: String, loginCode: String): QzhqResponse<Unit> {
        val projectId = PrefsHelper.qzhqProjectId
        val json = apiService.setUseCode(securityCode = code, phone = phone, projectId = projectId, loginCode = loginCode).awaitString()
        return parse(json, Unit::class.java)
    }
}
