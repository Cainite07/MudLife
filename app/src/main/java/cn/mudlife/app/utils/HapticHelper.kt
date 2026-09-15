package cn.mudlife.app.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * 旗舰级 X 轴线性马达高精度触感引擎
 * 支持各类微交互提供物理机械级触控回馈
 */
object HapticHelper {

    private fun getVibrator(context: Context): Vibrator? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 机械咔哒（Click）
     * 适用：右上角明暗光影切换、扫码出水卡片点击、Switch 开关变动
     */
    fun click(context: Context) {
        try {
            val vibrator = getVibrator(context) ?: return
            if (!vibrator.hasVibrator()) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(16L, 180))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(16L)
            }
        } catch (_: Exception) {}
    }

    /**
     * 灵动微滴（Tick）
     * 适用：悬浮胶囊底栏切换、账单分类 Chip 切换、月份下拉选择
     */
    fun tick(context: Context) {
        try {
            val vibrator = getVibrator(context) ?: return
            if (!vibrator.hasVibrator()) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(8L, 100))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(8L)
            }
        } catch (_: Exception) {}
    }

    /**
     * 磁吸双击（Double Click / Confirm）
     * 适用：轻触 8 位使用码整行复制成功的清脆物理确认
     */
    fun doubleClick(context: Context) {
        try {
            val vibrator = getVibrator(context) ?: return
            if (!vibrator.hasVibrator()) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 12, 40, 14), intArrayOf(0, 160, 0, 200), -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 12, 40, 14), -1)
            }
        } catch (_: Exception) {}
    }

    /**
     * 饱满重击（Heavy Click）
     * 适用：启动关停出水、重要二次确认操作
     */
    fun heavyClick(context: Context) {
        try {
            val vibrator = getVibrator(context) ?: return
            if (!vibrator.hasVibrator()) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(24L, 255))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(24L)
            }
        } catch (_: Exception) {}
    }
}
