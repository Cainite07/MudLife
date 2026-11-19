package cn.mudlife.app.utils

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.webkit.WebView
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

internal object WebResourceCache {
    private const val LIMIT_BYTES = 16L * 1024 * 1024
    private const val CHECK_INTERVAL_MS = 5L * 60 * 1000
    private val nextCheck = AtomicLong(0)
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()

    fun trimIfOversized(view: WebView) {
        val now = SystemClock.elapsedRealtime()
        val scheduled = nextCheck.get()
        if (now < scheduled || !nextCheck.compareAndSet(scheduled, now + CHECK_INTERVAL_MS)) return
        val cache = File(view.context.cacheDir, "WebView")
        worker.execute {
            var bytes = 0L
            for (file in cache.walkTopDown().filter { it.isFile }) {
                bytes += file.length()
                if (bytes >= LIMIT_BYTES) {
                    main.post {
                        if (view.isAttachedToWindow) view.clearCache(true)
                        else nextCheck.compareAndSet(now + CHECK_INTERVAL_MS, 0)
                    }
                    break
                }
            }
        }
    }
}
