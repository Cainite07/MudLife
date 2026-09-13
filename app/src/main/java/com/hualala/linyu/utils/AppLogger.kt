package com.hualala.linyu.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import com.hualala.linyu.BuildConfig
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedList
import java.util.Locale

enum class LogLevel(val label: String) {
    SYS("SYS"),
    INFO("INFO"),
    DEBUG("DEBUG"),
    WARN("WARN"),
    ERROR("ERR"),
    NET("NET")
}

data class LogEntry(
    val id: Long,
    var timeMillis: Long = System.currentTimeMillis(),
    val level: LogLevel,
    val tag: String,
    val message: String,
    val stackTrace: String? = null,
    var repeatCount: Int = 1
) {
    val timeFormatted: String get() {
        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        return sdf.format(Date(timeMillis))
    }

    val fullDateFormatted: String get() {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
        return sdf.format(Date(timeMillis))
    }
}

object AppLogger {
    private const val TAG = "MudLife"
    private const val MAX_MEMORY_LOGS = 1000
    private const val MAX_FILE_SIZE = 2 * 1024 * 1024 // 2MB 自动滚动截断

    private val writeExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    private val REGEX_LOGIN_CODE_URL = Regex("loginCode=([^&]+)")
    private val REGEX_LOGIN_CODE_JSON = Regex(""""loginCode"\s*:\s*"([^"]+)"""")
    private val REGEX_TOKEN_URL = Regex("token=([^&]+)")
    private val REGEX_TOKEN_JSON = Regex(""""token"\s*:\s*"([^"]+)"""")
    private val REGEX_PHONE = Regex("(1[3-9]\\d)\\d{4}(\\d{4})")
    private val REGEX_PASSWORD_URL = Regex("password=([^&]+)")
    private val REGEX_PASSWORD_JSON = Regex(""""password"\s*:\s*"([^"]+)"""")

    private val logList = LinkedList<LogEntry>()
    private var nextId = 1L
    private var appContext: Context? = null
    private var logFile: File? = null

    // Compose 响应式版本号，每次追加日志时递增，驱动 UI 实时重组
    var versionState by mutableStateOf(0L)
        private set

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        logFile = File(context.filesDir, "mudlife_debug.log")

        // 记录启动与环境信息
        val deviceInfo = "App v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE}) | " +
                "Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})"
        log(LogLevel.SYS, "Startup", deviceInfo)

        // 注册未捕获异常全局拦截器（崩溃自愈与持久化）
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                e("UncaughtCrash", "线程 [${thread.name}] 发生未捕获异常崩溃", throwable)
            } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    fun sys(tag: String, msg: String) = log(LogLevel.SYS, tag, msg)
    fun i(tag: String, msg: String) = log(LogLevel.INFO, tag, msg)
    fun d(tag: String, msg: String) = log(LogLevel.DEBUG, tag, msg)
    fun w(tag: String, msg: String) = log(LogLevel.WARN, tag, msg)
    fun e(tag: String, msg: String, tr: Throwable? = null) = log(LogLevel.ERROR, tag, msg, tr)

    fun net(method: String, url: String, code: Int? = null, bodySnippet: String? = null) {
        val maskedUrl = maskSensitive(url)
        val sb = StringBuilder("[$method] $maskedUrl")
        if (code != null) sb.append(" -> $code")
        if (!bodySnippet.isNullOrBlank()) {
            sb.append("\n  Body: ").append(maskSensitive(bodySnippet.take(500)))
        }
        log(LogLevel.NET, "HTTP", sb.toString())
    }

    @Synchronized
    private fun log(level: LogLevel, tag: String, message: String, tr: Throwable? = null) {
        val maskedMsg = maskSensitive(message)
        val stackTrace = tr?.let {
            val sw = StringWriter()
            it.printStackTrace(PrintWriter(sw))
            maskSensitive(sw.toString())
        }

        // 核心降噪：相邻相同日志就地折叠计数，不浪费 1000 条内存槽位与磁盘 I/O
        val last = logList.lastOrNull()
        if (last != null && last.level == level && last.tag == tag && last.message == maskedMsg && tr == null && last.stackTrace == null) {
            last.repeatCount++
            last.timeMillis = System.currentTimeMillis()
            versionState = ++nextId
            return
        }

        val entry = LogEntry(
            id = nextId++,
            level = level,
            tag = tag,
            message = maskedMsg,
            stackTrace = stackTrace
        )

        // Android 原始 logcat 镜像
        when (level) {
            LogLevel.SYS -> Log.i(TAG, "[$tag] $maskedMsg")
            LogLevel.INFO -> Log.i(TAG, "[$tag] $maskedMsg")
            LogLevel.DEBUG -> Log.d(TAG, "[$tag] $maskedMsg")
            LogLevel.WARN -> Log.w(TAG, "[$tag] $maskedMsg")
            LogLevel.ERROR -> Log.e(TAG, "[$tag] $maskedMsg", tr)
            LogLevel.NET -> Log.i(TAG, "[$tag] $maskedMsg")
        }

        // 环形内存缓冲
        logList.addLast(entry)
        while (logList.size > MAX_MEMORY_LOGS) {
            logList.removeFirst()
        }

        // 文件持久化追加
        writeExecutor.execute {
            try { appendToFile(entry) } catch (_: Exception) {}
        }

        // 驱动 Compose UI 刷新
        versionState = nextId
    }

    @Synchronized
    fun getLogs(): List<LogEntry> = ArrayList(logList)

    @Synchronized
    fun getErrorCount(): Int = logList.count { it.level == LogLevel.ERROR }

    @Synchronized
    fun clear() {
        logList.clear()
        try {
            logFile?.writeText("")
        } catch (_: Exception) {}
        versionState = ++nextId
    }

    private fun appendToFile(entry: LogEntry) {
        val file = logFile ?: return
        try {
            if (file.exists() && file.length() > MAX_FILE_SIZE) {
                // 滚动清理：截取后半段
                val content = file.readText()
                val half = content.drop(content.length / 2)
                file.writeText(half)
            }
            FileWriter(file, true).use { fw ->
                fw.write("[${entry.fullDateFormatted}] [${entry.level.label}] [${entry.tag}] ${entry.message}\n")
                if (entry.stackTrace != null) {
                    fw.write("${entry.stackTrace}\n")
                }
            }
        } catch (_: Exception) {}
    }

    private fun getLogFile(context: android.content.Context) = logFile ?: File(context.filesDir, "mudlife_debug.log")

    fun exportToDownload(context: android.content.Context): Boolean {
        val src = getLogFile(context) ?: return false
        if (!src.exists() || src.length() == 0L) return false
        val timeStr = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.CHINA).format(java.util.Date())
        val fileName = "泥浆生活日志_$timeStr.txt"
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(android.provider.MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(
                    android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    src.inputStream().use { it.copyTo(out) }
                }
                true
            } else {
                val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                if (!dir.exists()) dir.mkdirs()
                src.copyTo(java.io.File(dir, fileName), overwrite = true)
                true
            }
        } catch (e: Exception) {
            e("ExportLog", "导出日志失败", e)
            false
        }
    }

    /** 创建系统分享 Intent，直接投递给微信、QQ 或其他应用 */
    fun createShareIntent(context: Context): Intent {
        val file = logFile ?: File(context.filesDir, "mudlife_debug.log")
        if (!file.exists()) {
            file.writeText("[${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}] 日志为空\n")
        }

        val contentUri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, "泥浆生活_内测诊断日志")
            putExtra(Intent.EXTRA_TEXT, "这是来自泥浆生活 (v${BuildConfig.VERSION_NAME}) 的内测运行诊断日志文件。")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** 一键复制最近报错与调用栈到剪贴板 */
    fun copyRecentErrorsToClipboard(context: Context): String {
        val errors = synchronized(this) {
            val list = logList.filter { it.level == LogLevel.ERROR || it.level == LogLevel.WARN }.takeLast(8)
            if (list.isNotEmpty()) list else logList.takeLast(6)
        }
        val sb = StringBuilder()
        sb.append("【泥浆生活 v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE}) 诊断记录】\n")
        sb.append("设备: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})\n")
        sb.append("时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n")
        sb.append("----------------------------\n")

        if (errors.isEmpty()) {
            sb.append("暂无报错记录。\n")
        } else {
            errors.forEachIndexed { index, err ->
                val rep = if (err.repeatCount > 1) " [x${err.repeatCount}]" else ""
                sb.append("#${index + 1} [${err.timeFormatted}] [${err.level.label}] [${err.tag}]$rep: ${err.message}\n")
                if (!err.stackTrace.isNullOrBlank()) {
                    val snippet = err.stackTrace.lines().take(6).joinToString("\n")
                    sb.append(snippet).append("\n")
                }
                sb.append("\n")
            }
        }

        val text = sb.toString()
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("MudLife Error Log", text)
        cm.setPrimaryClip(clip)
        return text
    }

    private fun maskSensitive(text: String): String {
        var res = text
        res = REGEX_LOGIN_CODE_URL.replace(res) { "loginCode=***" }
        res = REGEX_LOGIN_CODE_JSON.replace(res) { "\"loginCode\":\"***\"" }
        res = REGEX_TOKEN_URL.replace(res) { "token=***" }
        res = REGEX_TOKEN_JSON.replace(res) { "\"token\":\"***\"" }
        res = REGEX_PHONE.replace(res) { "${it.groupValues[1]}****${it.groupValues[2]}" }
        res = REGEX_PASSWORD_URL.replace(res) { "password=******" }
        res = REGEX_PASSWORD_JSON.replace(res) { "\"password\":\"******\"" }
        return res
    }
}
