package com.example.ymediaplayer.ui

import android.content.Context
import android.os.Build
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
    if (!AppPreferences(context).isHapticsEnabled()) return
    context.performLevelHaptic(level)
}

fun Context.performLevelHaptic(level: Float) {
    try {
        if (!AppPreferences(this).isHapticsEnabled()) return
        val clampedLevel = level.coerceIn(0f, 1f)
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Dynamically scale amplitude from 35 (subtle) to 255 (maximum) with level
                val amplitude = (35 + (clampedLevel * 220f)).toInt().coerceIn(1, 255)
                val duration = (7L + (clampedLevel * 10f).toLong()).coerceIn(6L, 18L)
                vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude))
            } else {
                @Suppress("DEPRECATION")
                val duration = (8L + (clampedLevel * 16f).toLong()).coerceIn(6L, 24L)
                vibrator.vibrate(duration)
            }
        }
    } catch (_: Exception) {}
}

