package com.hualala.linyu

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * 扫码绑定热水器。
 *
 * 趣智校园热水器上的二维码形如：KLCXKJ-Water,M,C47F0EDA85D8
 * 解析最后一个字段（冒号去掉后为 12 位十六进制）作为设备 snCode。
 *
 * 返回：RESULT_OK + extra "sn_code"
 */
class QrScanActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SN_CODE = "sn_code"

        fun parseSnCode(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            val trimmed = raw.trim()
            // 兼容带有 URL 的二维码：提取参数或路径末尾
            val target = if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
                val uri = try { android.net.Uri.parse(trimmed) } catch (_: Exception) { null }
                uri?.getQueryParameter("sn")
                    ?: uri?.getQueryParameter("snCode")
                    ?: uri?.getQueryParameter("device")
                    ?: uri?.getQueryParameter("mac")
                    ?: uri?.lastPathSegment
                    ?: trimmed
            } else trimmed

            val parts = target.split(",")
            val candidate = parts.lastOrNull()?.trim() ?: target.trim()
            val normalized = candidate.replace(":", "").uppercase()
            // 1. 优先匹配 12 位标准十六进制 MAC
            if (normalized.length == 12 && normalized.matches(Regex("[0-9A-F]{12}"))) {
                return normalized
            }
            // 2. 兼容 6~20 位字母/数字设备编号（如直饮水机 8 位数字 62591102 等）
            if (normalized.length in 6..20 && normalized.matches(Regex("[0-9A-Z]+"))) {
                return normalized
            }
            // 3. 兜底提取字符串中连续 6-16 位的十六进制或字母数字
            val match = Regex("(?<![0-9A-Za-z])[0-9A-Za-z]{6,16}(?![0-9A-Za-z])").find(raw)
            return match?.value?.uppercase()
        }
    }

    private lateinit var previewView: PreviewView
    private var scanning = false
    private var camera: androidx.camera.core.Camera? = null
    private var torchOn = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera() else showError("需要相机权限才能扫码")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK

        // 简单布局：标题 + PreviewView + 取消按钮 + 手电筒
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }
        previewView = PreviewView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        val title = TextView(this).apply {
            text = "扫描设备二维码（饮水机 / 热水器）"
            setTextColor(Color.WHITE)
            textSize = 16f
        }
        val cancel = TextView(this).apply {
            text = "取消"
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(0, 16, 0, 16)
            gravity = android.view.Gravity.CENTER
            setOnClickListener { finish() }
        }

        root.addView(previewView)
        root.addView(title, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
            topMargin = 80
        })
        root.addView(cancel, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = android.view.Gravity.BOTTOM
            bottomMargin = 60
        })
        setContentView(root)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun toggleTorch() {
        val cam = camera ?: return
        if (!cam.cameraInfo.hasFlashUnit()) {
            showError("设备不支持闪光灯")
            return
        }
        torchOn = !torchOn
        cam.cameraControl.enableTorch(torchOn)
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val cameraProvider = providerFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            val scanner = BarcodeScanning.getClient()
            analysis.setAnalyzer(ContextCompat.getMainExecutor(this)) { imageProxy ->
                if (scanning) { imageProxy.close(); return@setAnalyzer }
                val mediaImage = imageProxy.image
                if (mediaImage != null) {
                    val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                    scanner.process(inputImage)
                        .addOnSuccessListener { barcodes ->
                            for (barcode in barcodes) {
                                val raw = barcode.rawValue
                                val sn = parseSnCode(raw)
                                if (sn != null && !scanning) {
                                    scanning = true
                                    try { scanner.close() } catch (_: Exception) {}
                                    setResult(RESULT_OK, Intent().putExtra(EXTRA_SN_CODE, sn))
                                    finish()
                                    return@addOnSuccessListener
                                }
                            }
                        }
                        .addOnCompleteListener { imageProxy.close() }
                } else {
                    imageProxy.close()
                }
            }
            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (_: Exception) {}
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        // 退出时关灯
        try { camera?.cameraControl?.enableTorch(false) } catch (_: Exception) {}
    }

    private fun showError(msg: String) {
        runOnUiThread {
            setResult(RESULT_CANCELED, Intent().putExtra("error", msg))
            finish()
        }
    }
}
