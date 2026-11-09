package cn.mudlife.app.timetable

import android.content.ActivityNotFoundException
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.lang.ref.WeakReference
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.math.roundToInt

class TimetableAppearance(
    private val activity: ComponentActivity,
    owner: String,
    private val openPicker: () -> Unit,
    private val onChange: () -> Unit
) {
    private val ownerId = hash(owner.trim().also { require(it.isNotEmpty()) })
    private val prefs = activity.getSharedPreferences("timetable-colors-" + ownerId, Context.MODE_PRIVATE)
    private val colorKeys = mutableMapOf<String, String>()
    private val wallpaper = File(activity.noBackupFilesDir, "timetable-wallpaper-" + ownerId)
    private var target = WeakReference<ImageView>(null)
    private var loading: Job? = null
    private var saving: Job? = null
    private var closed = false

    fun close() {
        closed = true
        loading?.cancel()
        target.clear()
    }

    val hasWallpaper: Boolean get() = wallpaper.isFile

    private fun fileVersion(): WallpaperVersion? = wallpaper.lastModified()
        .takeIf { it > 0 }?.let { WallpaperVersion(ownerId, it, wallpaper.length()) }

    fun applyTo(image: ImageView) {
        target = WeakReference(image)
        image.scaleType = ImageView.ScaleType.CENTER_CROP
        val version = fileVersion()
        image.visibility = if (version != null) View.VISIBLE else View.GONE
        val cached = recent?.takeIf { it.version == version }?.bitmap?.get()
        image.setImageBitmap(cached)
        if (version == null || cached != null || loading?.isActive == true) return
        loading = activity.lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(wallpaper.path) }
            if (version != fileVersion()) {
                loading = null
                target.get()?.let(::applyTo)
                return@launch
            }
            if (bitmap != null) recent = WallpaperPreview(version, WeakReference(bitmap))
            target.get()?.apply {
                setImageBitmap(bitmap)
                visibility = if (bitmap == null) View.GONE else View.VISIBLE
            }
        }
    }

    fun chooseWallpaper() {
        if (busySaving()) return
        try {
            openPicker()
        } catch (_: ActivityNotFoundException) {
            message("未找到系统文件选择器")
        }
    }

    fun removeWallpaper() {
        if (busySaving() || !hasWallpaper) return
        if (wallpaper.delete() || !hasWallpaper) updated(null)
        else message("壁纸未能移除，请重试")
    }

    private fun busySaving(): Boolean {
        val busy = saving?.isActive == true
        if (busy) message("正在处理壁纸，请稍候")
        return busy
    }

    fun setWallpaper(uri: Uri) {
        if (uri.scheme != "content") {
            message("请选择系统文件选择器中的图片")
            return
        }
        saving?.cancel()
        saving = activity.lifecycleScope.launch {
            var temporary: File? = null
            try {
                // The picker grants temporary access. Store a resized image without the original metadata.
                val bitmap = withContext(Dispatchers.IO) {
                    val image = thumbnail(uri)
                    val file = File.createTempFile("timetable-wallpaper-", ".tmp", activity.cacheDir)
                    temporary = file
                    file.outputStream().use { stream ->
                        val format = if (image.hasAlpha()) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                        if (!image.compress(format, 88, stream)) throw IOException("encode")
                        stream.fd.sync()
                    }
                    image.takeUnless { sameWallpaper(file) }
                } ?: return@launch
                Files.move(checkNotNull(temporary).toPath(), wallpaper.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                updated(bitmap)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                message("无法使用这张图片，请重新选择")
            } finally {
                temporary?.delete()
            }
        }
    }

    private fun sameWallpaper(candidate: File): Boolean {
        val size = candidate.length()
        if (size == 0L || size != wallpaper.length()) return false
        return candidate.inputStream().channel.use { first ->
            wallpaper.inputStream().channel.use { second ->
                first.map(FileChannel.MapMode.READ_ONLY, 0, size) == second.map(FileChannel.MapMode.READ_ONLY, 0, size)
            }
        }
    }

    private fun updated(bitmap: Bitmap?) {
        val version = fileVersion()
        if (bitmap != null && version != null) recent = WallpaperPreview(version, WeakReference(bitmap))
        else if (recent?.version?.owner == ownerId) recent = null
        if (closed) return
        loading?.cancel()
        target.get()?.let { applyTo(it) }
        onChange()
    }

    private fun thumbnail(uri: Uri): Bitmap {
        val resolver = activity.contentResolver
        if (Build.VERSION.SDK_INT >= 28) {
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val size = info.size
                val ratio = MAX_EDGE.toDouble() / maxOf(size.width, size.height)
                if (ratio < 1) decoder.setTargetSize(
                    (size.width * ratio).roundToInt().coerceAtLeast(1),
                    (size.height * ratio).roundToInt().coerceAtLeast(1)
                )
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0)
        val options = BitmapFactory.Options().apply { inSampleSize = 1 }
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest / options.inSampleSize > MAX_EDGE) options.inSampleSize *= 2
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("decode")
        val orientation = try {
            resolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }
        } catch (_: IOException) { null }
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    val colorOptions: List<Pair<String, Int>> get() = palette

    private fun colorKey(name: String): String = colorKeys.getOrPut(name.trim()) { hash(name.trim()) }

    fun courseColor(name: String): Int? {
        val key = colorKey(name)
        return if (prefs.contains(key)) prefs.getInt(key, 0) else null
    }

    fun colors(names: List<String>): Map<String, Int> = buildMap {
        for (name in names.distinct()) courseColor(name)?.let { put(name, it) }
    }

    fun setCourseColor(name: String, color: Int?) {
        if (color == courseColor(name)) return
        val key = colorKey(name)
        prefs.edit().apply { if (color == null) remove(key) else putInt(key, color) }.apply()
        onChange()
    }

    private fun message(text: String) = Toast.makeText(activity, text, Toast.LENGTH_SHORT).show()

    private data class WallpaperVersion(val owner: String, val modified: Long, val bytes: Long)
    private data class WallpaperPreview(val version: WallpaperVersion, val bitmap: WeakReference<Bitmap>)

    private companion object {
        // Reuse a recently drawn photo across tab and widget windows without retaining their Activity or pinning its bitmap.
        var recent: WallpaperPreview? = null
        const val MAX_EDGE = 1920
        fun hash(text: String): String = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        val palette = listOf(
            "松绿" to "#4F7666", "青碧" to "#4E8A89", "湖蓝" to "#5E85B1",
            "靛青" to "#536A95", "藤紫" to "#9281AD", "莓红" to "#AC6B84",
            "珊瑚" to "#BF796E", "琥珀" to "#C48E55", "麦黄" to "#C3AA6E",
            "橄榄" to "#909A67", "灰蓝" to "#829DA8", "陶棕" to "#997B68"
        ).map { (label, value) -> label to Color.parseColor(value) }
    }
}
