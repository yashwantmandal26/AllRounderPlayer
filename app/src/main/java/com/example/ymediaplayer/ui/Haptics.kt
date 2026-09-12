package com.example.ymediaplayer.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

enum class HapticType {
    LIGHT, MEDIUM, HEAVY
}

fun View.performHaptic(type: HapticType) {
    try {
        val flag = HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
        val constant = when (type) {
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
                    HapticType.LIGHT -> 14L to 90
                    HapticType.MEDIUM -> 26L to 180
                    HapticType.HEAVY -> 50L to 255
                }
                vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude))
            } else {
                @Suppress("DEPRECATION")
                val duration = when (type) {
                    HapticType.LIGHT -> 15L
                    HapticType.MEDIUM -> 30L
                    HapticType.HEAVY -> 55L
                }
                vibrator.vibrate(duration)
            }
        }
    } catch (_: Exception) {}
}

