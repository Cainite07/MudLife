package cn.mudlife.app.utils

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resumeWithException

enum class UpdateDownloadPhase { CONNECTING, DOWNLOADING, VERIFYING }

data class UpdateDownloadProgress(
    val phase: UpdateDownloadPhase = UpdateDownloadPhase.CONNECTING,
    val bytes: Long = 0,
    val totalBytes: Long = -1,
    val bytesPerSecond: Long = 0,
    val source: String = ""
) {
    val fraction: Float? get() = if (totalBytes > 0) (bytes.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f) else null
    val description: String get() = when (phase) {
        UpdateDownloadPhase.CONNECTING -> "正在连接下载源…"
        UpdateDownloadPhase.VERIFYING -> "正在验证安装包…"
        UpdateDownloadPhase.DOWNLOADING -> listOfNotNull(source.takeIf { it.isNotBlank() },
            if (totalBytes > 0) "${size(bytes)} / ${size(totalBytes)}" else size(bytes),
            bytesPerSecond.takeIf { it > 0 }?.let { "${size(it)}/s" }).joinToString(" · ")
    }
    companion object {
        private fun size(bytes: Long): String = if (bytes >= 1024 * 1024)
            String.format(Locale.ROOT, "%.1f MB", bytes / 1048576.0)
        else String.format(Locale.ROOT, "%.0f KB", bytes / 1024.0)
    }
}

/** Keep the first useful connection; its sample bytes become part of the actual APK. */
internal class UpdateDownloader(private val client: OkHttpClient) {
    private class Transfer(val url: String, val call: Call, val response: Response, val prefix: ByteArray) : Closeable {
        override fun close() { response.close() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun openFastest(urls: List<String>): Transfer = suspendCancellableCoroutine { continuation ->
        val calls = urls.map { url -> client.newCall(Request.Builder().url(url)
            .header("User-Agent", "MudLife-App").build()) }
        val claimed = AtomicBoolean(false)
        val pending = AtomicInteger(calls.size)
        continuation.invokeOnCancellation { calls.forEach(Call::cancel) }
        fun failed() {
            if (pending.decrementAndGet() == 0 && claimed.compareAndSet(false, true))
                continuation.resumeWithException(IOException("下载源暂时无法连接，请稍后重试"))
        }
        calls.forEachIndexed { index, call ->
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) = failed()
                override fun onResponse(call: Call, response: Response) {
                    try {
                        if (!response.isSuccessful) throw IOException("下载源未返回安装包")
                        val body = response.body ?: throw IOException("安装包内容为空")
                        val input = body.byteStream()
                        val buffer = ByteArray(256 * 1024)
                        var count = 0
                        while (count < buffer.size) {
                            val read = input.read(buffer, count, buffer.size - count)
                            if (read < 0) break
                            count += read
                            if (count >= 4 && !(buffer[0] == 0x50.toByte() && buffer[1] == 0x4b.toByte()
                                        && buffer[2] == 3.toByte() && buffer[3] == 4.toByte()))
                                throw IOException("下载源返回的内容不是安装包")
                        }
                        if (count < 4) throw IOException("安装包内容不完整")
                        val transfer = Transfer(urls[index], call, response, buffer.copyOf(count))
                        if (claimed.compareAndSet(false, true)) {
                            continuation.resume(transfer) { transfer.close() }
                            calls.filter { it !== call }.forEach(Call::cancel)
                        } else response.close()
                    } catch (_: Exception) {
                        response.close()
                        failed()
                    }
                }
            })
        }
    }

    suspend fun download(urls: List<String>, target: File, onProgress: suspend (UpdateDownloadProgress) -> Unit,
                         validate: (File) -> Unit) {
        require(urls.isNotEmpty())
        val remaining = urls.distinct().toMutableList()
        val staging = File(target.path + ".part")
        var lastError: Exception? = null
        try {
            while (remaining.isNotEmpty()) {
                currentCoroutineContext().ensureActive()
                onProgress(UpdateDownloadProgress())
                // Bound duplicate traffic to at most one 256KB prefix and cancel the losing call.
                val batch = remaining.take(2)
                val transfer = try { openFastest(batch) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (e: Exception) { lastError = e; remaining.removeAll(batch); continue }
                remaining.remove(transfer.url)
                try {
                    transfer.use {
                        val total = it.response.body!!.contentLength()
                        val source = sourceName(it.url)
                        var downloaded = it.prefix.size.toLong()
                        var windowBytes = downloaded
                        var windowAt = System.nanoTime()
                        var speed = 0L
                        fun state() = UpdateDownloadProgress(UpdateDownloadPhase.DOWNLOADING, downloaded, total, speed, source)
                        staging.outputStream().use { output ->
                            output.write(it.prefix)
                            onProgress(state())
                            val input = it.response.body!!.byteStream()
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                                downloaded += count
                                val now = System.nanoTime()
                                if (now - windowAt >= 200_000_000L) {
                                    speed = ((downloaded - windowBytes) * 1_000_000_000.0 / (now - windowAt)).toLong()
                                    onProgress(state())
                                    windowBytes = downloaded; windowAt = now
                                }
                            }
                        }
                        if (total >= 0 && downloaded != total) throw IOException("安装包下载不完整")
                        onProgress(state().copy(phase = UpdateDownloadPhase.VERIFYING))
                        validate(staging)
                        if (!staging.renameTo(target)) throw IOException("无法保存安装包，请检查剩余空间")
                        return
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (e: Exception) { lastError = e }
            }
            throw lastError ?: IOException("安装包下载失败，请稍后重试")
        } finally { staging.delete() }
    }

    private fun sourceName(url: String): String = when (Request.Builder().url(url).build().url.host) {
        "gitee.com", "foruda.gitee.com" -> "Gitee"
        "github.com", "release-assets.githubusercontent.com" -> "GitHub"
        else -> "备用下载源"
    }
}
