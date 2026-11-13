package com.example.ymediaplayer.ui

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

import com.example.ymediaplayer.data.AppPreferences

enum class HapticType {
    TICK, LIGHT, MEDIUM, HEAVY
}

fun View.performHaptic(type: HapticType) {
    try {
        if (!AppPreferences(context).isHapticsEnabled()) return
        val flag = HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
        val constant = when (type) {
            HapticType.TICK -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    HapticFeedbackConstants.SEGMENT_TICK
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    HapticFeedbackConstants.TEXT_HANDLE_MOVE
                } else {
                    HapticFeedbackConstants.CLOCK_TICK
                }
            }
            HapticType.LIGHT -> HapticFeedbackConstants.KEYBOARD_TAP
            HapticType.MEDIUM -> HapticFeedbackConstants.VIRTUAL_KEY
            HapticType.HEAVY -> HapticFeedbackConstants.LONG_PRESS
        }
        val performed = performHapticFeedback(constant, flag)
        if (!performed) {
            context.performHaptic(type)
        }
    } catch (_: Exception) {
        context.performHaptic(type)
    }
}

fun Context.performHaptic(type: HapticType) {
    try {
        if (!AppPreferences(this).isHapticsEnabled()) return
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val (duration, amplitude) = when (type) {
                    HapticType.TICK -> 8L to 65
                    HapticType.LIGHT -> 14L to 90
                    HapticType.MEDIUM -> 26L to 180
                    HapticType.HEAVY -> 50L to 255
                }
                vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude))
            } else {
                @Suppress("DEPRECATION")
                val duration = when (type) {
                    HapticType.TICK -> 8L
                    HapticType.LIGHT -> 15L
                    HapticType.MEDIUM -> 30L
                    HapticType.HEAVY -> 55L
                }
                vibrator.vibrate(duration)
            }
        }
    } catch (_: Exception) {}
}

fun View.performLevelHaptic(level: Float) {
    context.performLevelHaptic(level)
}

fun Context.performLevelHaptic(level: Float) {
    try {
        if (!AppPreferences(this).isHapticsEnabled()) return
        val clampedLevel = level.coerceIn(0f, 1f)
        val vibrator = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }) ?: return

        if (!vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val amplitude = if (vibrator.hasAmplitudeControl()) {
                // Dynamically scale amplitude from 25 (delicate tactile tick) to 255 (maximum kick)
                (25 + (clampedLevel * 230f)).toInt().coerceIn(1, 255)
            } else {
                VibrationEffect.DEFAULT_AMPLITUDE
            }
            // 12ms to 24ms ensures physical motor overcomes inertia while staying crisp and clicky
            val duration = (12L + (clampedLevel * 12f).toLong()).coerceIn(10L, 26L)
            val effect = VibrationEffect.createOneShot(duration, amplitude)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val attrs = VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_TOUCH)
                    .build()
                vibrator.vibrate(effect, attrs)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val attrs = android.media.AudioAttributes.Builder()
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .build()
                vibrator.vibrate(effect, attrs)
            } else {
                vibrator.vibrate(effect)
            }
        } else {
            @Suppress("DEPRECATION")
            val duration = (12L + (clampedLevel * 16f).toLong()).coerceIn(10L, 28L)
            vibrator.vibrate(duration)
        }
    } catch (_: Exception) {}
}

