package cn.mudlife.app.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.gson.Gson
import cn.mudlife.app.BuildConfig
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val versionCode: Int = 0,
    val versionName: String = "",
    val releaseDate: String = "",
    val title: String = "",
    val changelog: String = "",
    val downloadUrl: String = "",
    val mirrors: List<String> = emptyList()
) {
    val hasUpdate: Boolean
        get() = versionCode > BuildConfig.VERSION_CODE
}

object UpdateManager {

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    internal val CHECK_URLS = listOf(
        "https://gitee.com/cainite/MudLife/raw/main/version.json",
        "https://fastly.jsdelivr.net/gh/Cainite07/MudLife@main/version.json",
        "https://gcore.jsdelivr.net/gh/Cainite07/MudLife@main/version.json",
        "https://testingcf.jsdelivr.net/gh/Cainite07/MudLife@main/version.json",
        "https://cdn.jsdelivr.net/gh/Cainite07/MudLife@main/version.json",
        "https://ghproxy.net/https://raw.githubusercontent.com/Cainite07/MudLife/main/version.json",
        "https://raw.githubusercontent.com/Cainite07/MudLife/main/version.json"
    )

    internal fun parseManifest(text: String): UpdateInfo? = runCatching {
        val raw = gson.fromJson(text, UpdateInfo::class.java) ?: return null
        val name = raw.versionName.orEmpty().trim()
        val mirrors = raw.mirrors.orEmpty().filterNotNull().filter { it.toHttpUrlOrNull()?.isHttps == true }.distinct()
        val url = raw.downloadUrl.orEmpty().takeIf { it.toHttpUrlOrNull()?.isHttps == true }.orEmpty()
        if (raw.versionCode <= 0 || name.isBlank() || (url.isBlank() && mirrors.isEmpty())) return null
        raw.copy(versionName = name, title = raw.title.orEmpty(), changelog = raw.changelog.orEmpty(),
            releaseDate = raw.releaseDate.orEmpty(), downloadUrl = url, mirrors = mirrors)
    }.getOrNull()

    internal fun downloadUrls(info: UpdateInfo): List<String> {
        val official = info.downloadUrl.toHttpUrlOrNull()?.takeIf { it.isHttps && it.host == "github.com" && it.encodedPath.contains("/releases/download/") }
        val domestic = official?.takeIf { it.encodedPath.startsWith("/Cainite07/MudLife/releases/download/") }
            ?.let { "https://gitee.com/cainite/MudLife/releases/download/" + it.encodedPath.substringAfter("/releases/download/") }
        fun proxy(url: String) = url.toHttpUrlOrNull()?.host in setOf("ghproxy.net", "ghfast.top")
        return (listOfNotNull(domestic) + info.mirrors.filterNot(::proxy) + listOfNotNull(official?.toString()) +
            info.mirrors.filter(::proxy) + listOfNotNull(official?.let { "https://ghproxy.net/$it" },
                official?.let { "https://ghfast.top/$it" }) + info.downloadUrl)
            .filter { it.toHttpUrlOrNull()?.isHttps == true }.distinct()
    }

    suspend fun checkUpdate(): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        if (cn.mudlife.app.BuildConfig.DEBUG) {
            return@withContext Result.failure(IllegalStateException("修复预览版请使用单独提供的测试包更新"))
        }
        fun fetch(base: String): UpdateInfo? = try {
            val req = Request.Builder().url(base)
                .header("User-Agent", "MudLife-App/${BuildConfig.VERSION_NAME}").build()
            client.newCall(req).execute().use { response ->
                if (!response.isSuccessful) null
                else response.body?.string()?.let(::parseManifest)
            }
        } catch (cancelled: CancellationException) { throw cancelled }
          catch (_: Exception) { null }

        // Stable CDN paths avoid cache-busting query strings rejected on some direct routes.
        // All sources share a bounded deadline; a failed node cannot suppress another reply.
        val info = coroutineScope { CHECK_URLS.map { url -> async(Dispatchers.IO) { fetch(url) } }.awaitAll() }
            .filterNotNull().maxByOrNull { it.versionCode }
        if (info != null) Result.success(info)
        else Result.failure(Exception("检测更新失败，请稍后重试"))
    }

    suspend fun downloadApk(
        context: Context,
        info: UpdateInfo,
        onProgress: (UpdateDownloadProgress) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        if (cn.mudlife.app.BuildConfig.DEBUG) {
            return@withContext Result.failure(IllegalStateException("修复预览版不下载正式版更新"))
        }
        val destFile = File(context.cacheDir, "mudlife_update_${info.versionCode}.apk")
        if (destFile.isFile && runCatching { validateUpdate(context, destFile, info.versionCode) }.isSuccess) {
            return@withContext Result.success(destFile)
        }
        destFile.delete()
        try {
            val downloader = UpdateDownloader(OkHttpClient.Builder().connectTimeout(6, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS).callTimeout(5, TimeUnit.MINUTES).build())
            downloader.download(downloadUrls(info), destFile,
                onProgress = { state -> withContext(Dispatchers.Main) { onProgress(state) } },
                validate = { validateUpdate(context, it, info.versionCode) })
            Result.success(destFile)
        } catch (cancelled: CancellationException) { throw cancelled }
          catch (e: Exception) { Result.failure(e) }
    }

    fun installApk(context: Context, file: File): Result<Unit> = runCatching {
        check(!BuildConfig.DEBUG) { "修复预览版不安装正式版更新" }
            validateUpdate(context, file)
            if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
                context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                error("请允许泥浆生活安装应用，返回后再次点更新")
            }
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
    }

    @Suppress("DEPRECATION")
    private fun validateUpdate(context: Context, file: File, expectedVersion: Int? = null) {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
            else PackageManager.GET_SIGNATURES
        val pm = context.packageManager
        val candidate = pm.getPackageArchiveInfo(file.absolutePath, flags)
            ?: error("安装包无法识别")
        check(candidate.packageName == context.packageName) { "安装包不属于当前应用" }
        val installed = pm.getPackageInfo(context.packageName, flags)
        val nextVersion = if (Build.VERSION.SDK_INT >= 28) candidate.longVersionCode else candidate.versionCode.toLong()
        check(nextVersion > BuildConfig.VERSION_CODE) { "安装包版本不是更新版本" }
        check(expectedVersion == null || nextVersion == expectedVersion.toLong()) { "安装包与更新清单版本不一致" }
        fun signatures(info: android.content.pm.PackageInfo): Set<String> {
            val signers = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
            return signers.orEmpty().map { signer ->
                java.security.MessageDigest.getInstance("SHA-256").digest(signer.toByteArray())
                    .joinToString("") { "%02x".format(it) }
            }.toSet()
        }
        val expected = signatures(installed)
        check(expected.isNotEmpty() && expected == signatures(candidate)) {
            "更新包签名与当前版本不一致，请使用新签名系列的安装包"
        }
    }

    fun openBrowser(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }
}
