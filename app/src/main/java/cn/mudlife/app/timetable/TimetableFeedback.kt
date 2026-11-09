package cn.mudlife.app.timetable

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import cn.mudlife.app.utils.PrefsHelper

internal enum class TimetableFeedback {
    Toggle, Saved;

    fun play(context: Context) {
        if (!PrefsHelper.touchFeedback || Settings.System.getInt(context.contentResolver,
                Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 0) return
        @Suppress("DEPRECATION")
        val vibrator = context.getSystemService(Vibrator::class.java) ?: return
        if (!vibrator.hasVibrator()) return

        val toggle = this == Toggle
        val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val primitive = if (toggle) VibrationEffect.Composition.PRIMITIVE_TICK else VibrationEffect.Composition.PRIMITIVE_CLICK
            if (vibrator.areAllPrimitivesSupported(primitive)) {
                VibrationEffect.startComposition().addPrimitive(primitive, if (toggle) .16f else .45f).compose()
            } else null
        } else null
        // A motor without intensity control cannot produce a soft toggle. Keep that action quiet.
        val pulse = effect ?: when {
            vibrator.hasAmplitudeControl() -> VibrationEffect.createOneShot(if (toggle) 6L else 12L, if (toggle) 38 else 92)
            toggle -> return
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
            else -> VibrationEffect.createOneShot(10L, VibrationEffect.DEFAULT_AMPLITUDE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(pulse, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pulse, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build())
        }
    }
}
