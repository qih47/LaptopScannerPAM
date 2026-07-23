package com.dev.scanlaptop.utils

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object FeedbackHelper {
    private var toneGenerator: ToneGenerator? = null

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playSuccessFeedback(context: Context) {
        vibrate(context, pattern = longArrayOf(0, 100, 50, 100))
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playErrorFeedback(context: Context) {
        vibrate(context, pattern = longArrayOf(0, 200, 100, 200, 100, 400))
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun vibrate(context: Context, pattern: LongArray) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
