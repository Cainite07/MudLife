package cn.mudlife.app.api

import com.google.gson.Gson
import com.google.gson.JsonParser
import cn.mudlife.app.model.QzhqLoginData
import cn.mudlife.app.model.QzhqProject
import cn.mudlife.app.model.QzhqResponse
import cn.mudlife.app.model.QzhqUseCodeData
import cn.mudlife.app.utils.MD5Utils
import cn.mudlife.app.utils.PrefsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    private val invalidSession = kotlinx.coroutines.flow.MutableStateFlow("")
    val invalidSessions: kotlinx.coroutines.flow.StateFlow<String> = invalidSession

    private suspend fun <T> checkSession(response: QzhqResponse<T>, token: String): QzhqResponse<T> {
        withContext(Dispatchers.Main.immediate) {
            // A late response from an old login must not expire its replacement.
            if (response.expiresSession(token, PrefsHelper.qzhqLoginCode)) {
                PrefsHelper.qzhqSessionExpired = true
                invalidSession.value = token
            }
        }
        return response
    }

    private val gson = Gson()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request()
            val resp = try {
                chain.proceed(req)
            } catch (e: Exception) {
                cn.mudlife.app.utils.AppLogger.e("QzhqNet", "后勤网络请求失败: ${req.method} ${req.url}", e)
                throw e
            }
            try {
                val isFailed = !resp.isSuccessful
                val body = resp.peekBody(1024 * 64).string()
                val isBizFail = body.contains("\"errorCode\":") && !body.contains("\"errorCode\":0")
                val isEmptyData = req.method == "GET" && (body.contains("\"data\":[]") || body.contains("\"data\": null") || body.contains("\"data\":null"))

                // 过滤无业务变动的空数据响应
                if (!isEmptyData || isFailed || isBizFail) {
                    cn.mudlife.app.utils.AppLogger.net(req.method, req.url.toString(), resp.code, body)
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
                    } else if (response.code() == 401) {
                        response.errorBody()?.close()
                        cont.resume("""{"errorCode":401,"message":"登录失效，请重新登录"}""")
                    } else {
                        response.errorBody()?.close()
                        cont.resumeWithException(Exception("HTTP ${response.code()}: ${response.message()}"))
                    }
                }

                override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                    cont.resumeWithException(t)
                }
            })
        }
    }

    private suspend fun <T> parse(json: String, dataClass: Class<T>? = null): QzhqResponse<T> = withContext(Dispatchers.Default) {
        try {
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

    private suspend fun <T> parseList(json: String, itemClass: Class<T>): QzhqResponse<List<T>> = withContext(Dispatchers.Default) {
        try {
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
            cn.mudlife.app.utils.AppLogger.e("QzhqNet", "parseList 数据解析异常: ${e.message}", e)
            QzhqResponse(-1, "数据解析异常: ${e.message}", emptyList())
        }
    }

    suspend fun login(phone: String, passwordRaw: String): QzhqResponse<QzhqLoginData> {
        val encrypted = MD5Utils.encryptPassword(passwordRaw)
        val json = apiService.login(phone, encrypted).awaitString()
        return saveLogin(json, phone)
    }

    suspend fun sendVerifyCode(phone: String): QzhqResponse<Unit> {
        val s = phone.take(3) + phone.takeLast(4) + "klcx"
        val secret = MD5Utils.md5(s).lowercase()
        val json = apiService.getVerifyCode(phone = phone, secret = secret, typeId = 3).awaitString()
        return parse(json, Unit::class.java)
    }

    suspend fun smsLogin(phone: String, smsCode: String): QzhqResponse<QzhqLoginData> {
        val json = apiService.smsLogin(phone = phone, msg = smsCode).awaitString()
        return saveLogin(json, phone)
    }

    private suspend fun saveLogin(json: String, phone: String): QzhqResponse<QzhqLoginData> {
        val resp = parse(json, QzhqLoginData::class.java)
        if (resp.isSuccess) {
            val token = resp.data?.loginCode?.takeIf { it.isNotBlank() }
                ?: return QzhqResponse(-1, "登录响应缺少会话，请重试")
            val previousProject = PrefsHelper.qzhqProjectId.takeIf { phone == PrefsHelper.qzhqPhone }
            PrefsHelper.migrateBathPrefix()
            PrefsHelper.qzhqLoginCode = token
            PrefsHelper.qzhqSessionExpired = false
            invalidSession.value = ""
            PrefsHelper.qzhqPhone = phone
            PrefsHelper.qzhqProjectId = resp.data.projectId?.takeIf { it > 0 }?.toString()
                ?: previousProject ?: QzhqService.PROJECT_ID
        }
        return resp
    }

    suspend fun getProjectGroup(projectId: String): QzhqResponse<List<QzhqProject>> =
        parseList(apiService.getProjectGroup(projectId).awaitString(), QzhqProject::class.java)

    suspend fun getWalletBillList(
        phone: String, loginCode: String, projectId: String, page: Int
    ): QzhqResponse<List<cn.mudlife.app.model.QzhqWalletBillItem>> {
        val now = java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai"))
        val endDate = now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val json = apiService.getWalletBillList(phone = phone, loginCode = loginCode, projectId = projectId,
            endDate = endDate, curNum = page).awaitString()
        return checkSession(parseList(json, cn.mudlife.app.model.QzhqWalletBillItem::class.java), loginCode)
    }

    suspend fun getUseCode(phone: String, loginCode: String, projectId: String): QzhqResponse<QzhqUseCodeData> {
        val json = apiService.getUseCode(phone = phone, projectId = projectId, loginCode = loginCode).awaitString()
        val resp = checkSession(parse(json, QzhqUseCodeData::class.java), loginCode)
        return resp
    }

    suspend fun updateUseCodeStatus(status: Int, phone: String, loginCode: String, projectId: String): QzhqResponse<Unit> {
        val json = apiService.updateUseCodeStatus(status = status, phone = phone, projectId = projectId, loginCode = loginCode).awaitString()
        val resp = checkSession(parse(json, Unit::class.java), loginCode)
        return resp
    }

    suspend fun setUseCode(code: String, phone: String, loginCode: String, projectId: String): QzhqResponse<Unit> {
        val json = apiService.setUseCode(securityCode = code, phone = phone, projectId = projectId, loginCode = loginCode).awaitString()
        return checkSession(parse(json, Unit::class.java), loginCode)
    }
}
