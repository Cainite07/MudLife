package cn.mudlife.app.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.gson.Gson
import cn.mudlife.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
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
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    private val CHECK_URLS = listOf(
        "https://testingcf.jsdelivr.net/gh/Cainite07/MudLife@main/version.json",
        "https://cdn.jsdelivr.net/gh/Cainite07/MudLife@main/version.json",
        "https://ghfast.top/https://raw.githubusercontent.com/Cainite07/MudLife/main/version.json"
    )

    suspend fun checkUpdate(): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        val ts = System.currentTimeMillis()
        var lastErr: Exception? = null

        for (base in CHECK_URLS) {
            val url = "$base?t=$ts"
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "MudLife-App/${BuildConfig.VERSION_NAME}")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string()
                        if (!body.isNullOrEmpty()) {
                            val info = gson.fromJson(body, UpdateInfo::class.java)
                            if (info != null && info.versionCode > 0) {
                                return@withContext Result.success(info)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                lastErr = e
            }
        }
        Result.failure(lastErr ?: Exception("检测更新失败，请检查网络后重试"))
    }

    suspend fun downloadApk(
        context: Context,
        info: UpdateInfo,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val candidateUrls = mutableListOf<String>()
        candidateUrls.addAll(info.mirrors)
        if (info.downloadUrl.isNotBlank() && !candidateUrls.contains(info.downloadUrl)) {
            candidateUrls.add(info.downloadUrl)
        }

        val dlClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        val destFile = File(context.cacheDir, "mudlife_update_${info.versionCode}.apk")
        var lastErr: Exception? = null

        for (url in candidateUrls) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
                    .build()
                dlClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val body = resp.body ?: return@use
                    val total = body.contentLength()
                    var downloaded = 0L

                    body.byteStream().use { input ->
                        FileOutputStream(destFile).use { output ->
                            val buffer = ByteArray(32 * 1024)
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                downloaded += read
                                if (total > 0) {
                                    val progress = downloaded.toFloat() / total
                                    withContext(Dispatchers.Main) {
                                        onProgress(progress.coerceIn(0f, 1f))
                                    }
                                }
                            }
                            output.flush()
                        }
                    }
                    if (destFile.exists() && destFile.length() > 1024 * 1024) {
                        return@withContext Result.success(destFile)
                    }
                }
            } catch (e: Exception) {
                lastErr = e
            }
        }
        Result.failure(lastErr ?: Exception("安装包下载失败，请稍后重试"))
    }

    fun installApk(context: Context, file: File) {
        try {
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
        } catch (e: Exception) {
            AppLogger.e("Update", "调起安装器失败: ${e.message}")
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
