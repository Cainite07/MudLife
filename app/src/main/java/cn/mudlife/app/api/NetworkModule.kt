package cn.mudlife.app.api

import cn.mudlife.app.BuildConfig
import cn.mudlife.app.utils.PrefsHelper
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object NetworkModule {
    private var loginCode: String = ""
    private var userId: String = ""
    private var accountId: String = ""
    private var projectId: String = ""
    private var telephone: String = ""
    private val projectAccounts = mutableMapOf<String, String>()

    fun setProjectAccounts(map: Map<String, String>) {
        synchronized(projectAccounts) {
            projectAccounts.clear()
            projectAccounts.putAll(map)
        }
    }

    fun getAccountIdForProject(pid: String?): String? {
        if (pid.isNullOrBlank()) return accountId.ifEmpty { null }
        return synchronized(projectAccounts) {
            projectAccounts[pid] ?: if (pid == projectId) accountId.ifEmpty { null } else null
        }
    }

    /** 从 SharedPreferences 恢复登录态 */
    fun restoreFromPrefs(): Boolean {
        loginCode = PrefsHelper.loginCode
        userId = PrefsHelper.userId
        accountId = PrefsHelper.accountId
        projectId = PrefsHelper.projectId
        telephone = PrefsHelper.telephone
        val savedAccounts = PrefsHelper.getProjectAccounts()
        if (savedAccounts.isNotEmpty()) {
            setProjectAccounts(savedAccounts)
        }
        return loginCode.isNotEmpty()
    }

    fun updateAuth(
        loginCode: String,
        userId: String,
        accountId: String,
        projectId: String,
        telephone: String
    ) {
        this.loginCode = loginCode
        this.userId = userId
        this.accountId = accountId
        this.projectId = projectId
        this.telephone = telephone
    }

    val currentProjectId: String get() = projectId

    fun clearAuth() {
        loginCode = ""
        userId = ""
        accountId = ""
        projectId = ""
        telephone = ""
        synchronized(projectAccounts) { projectAccounts.clear() }
    }

    fun authFields(overrideProjectId: String? = null): Map<String, String> {
        val pid = if (!overrideProjectId.isNullOrBlank() && overrideProjectId != "0" && overrideProjectId != "null") {
            overrideProjectId
        } else {
            projectId
        }
        val map = mutableMapOf(
            "loginCode" to loginCode,
            "userId" to userId,
            "projectId" to pid,
            "telephone" to telephone,
            "telPhone" to telephone,
            "phoneSystem" to "android",
            "version" to "6.5.24"
        )
        // 核心修复：精准匹配目标项目下的专属 accountId（如饮水机 3255 匹配 38184，洗浴 4253 匹配 5816）
        val targetAid = getAccountIdForProject(pid) ?: accountId
        if (targetAid.isNotEmpty()) {
            map["accountId"] = targetAid
        }
        return map
    }

    private val authInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val originalUrl = originalRequest.url

        if (loginCode.isNotEmpty() && originalRequest.method == "GET" && !originalUrl.encodedPath.contains("verification")) {
            val urlBuilder = originalUrl.newBuilder()
                .addQueryParameter("loginCode", loginCode)
                .addQueryParameter("userId", userId)
                .addQueryParameter("telephone", telephone)
                .addQueryParameter("phoneSystem", "android")
                .addQueryParameter("version", "6.5.24")

            // 账户与账单接口按目标项目精准注入 accountId
            val reqProjectId = originalUrl.queryParameter("projectId")
            val targetAid = if (!reqProjectId.isNullOrBlank() && reqProjectId != "0" && reqProjectId != "null") {
                getAccountIdForProject(reqProjectId) ?: accountId
            } else {
                accountId
            }
            if (originalUrl.queryParameter("accountId") == null && targetAid.isNotEmpty()) {
                urlBuilder.addQueryParameter("accountId", targetAid)
            }
            if (originalUrl.queryParameter("projectId") == null && projectId.isNotEmpty()) {
                urlBuilder.addQueryParameter("projectId", projectId)
            }

            chain.proceed(originalRequest.newBuilder().url(urlBuilder.build()).build())
        } else {
            chain.proceed(originalRequest)
        }
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor { chain ->
            val req = chain.request()
            var tryCount = 0
            var resp: okhttp3.Response? = null
            var lastException: Exception? = null
            val canRetry = (req.method == "GET")
            val maxTries = if (canRetry) 2 else 1

            while (tryCount < maxTries) {
                try {
                    resp = chain.proceed(req)
                    break
                } catch (e: Exception) {
                    lastException = e
                    tryCount++
                    if (tryCount < maxTries) {
                        try { Thread.sleep(350) } catch (_: InterruptedException) {}
                    }
                }
            }
            if (resp == null) {
                cn.mudlife.app.utils.AppLogger.e("Network", "网络请求彻底失败: ${req.method} ${req.url}", lastException)
                throw (lastException ?: java.io.IOException("Network request failed"))
            }
            try {
                val path = req.url.encodedPath
                val isPolling = path.contains("/using/query") || path.contains("/queryUsing") || path.contains("/account/user/account")
                val isFailed = !resp.isSuccessful
                val body = resp.peekBody(1024 * 64).string()
                val isBizFail = body.contains("\"success\":false") || (body.contains("\"errorCode\":") && !body.contains("\"errorCode\":0"))
                val isEmptyData = req.method == "GET" && (body.contains("\"data\":[]") || body.contains("\"data\": null") || body.contains("\"data\":null"))

                // 核心降噪：静默高频轮询探活、空数据账单查询的重复日志，只记录常规业务请求、状态变更或失败请求
                if ((!isPolling && !isEmptyData) || isFailed || isBizFail) {
                    cn.mudlife.app.utils.AppLogger.net(req.method, req.url.toString(), resp.code, body)
                }

                if (isBizFail) {
                    cn.mudlife.app.utils.AppLogger.w("API_FAIL", "[${req.method}] ${req.url.encodedPath} 业务失败: ${body.take(200)}")
                }
            } catch (_: Exception) {}
            resp
        }
        .apply {
            if (cn.mudlife.app.BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
            }
        }
        .build()

    val apiService: QzxyService = Retrofit.Builder()
        .baseUrl(QzxyService.BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(QzxyService::class.java)
}
