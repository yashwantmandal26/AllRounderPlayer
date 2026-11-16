package com.example.ymediaplayer.player

import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioAttributes
import android.os.Build

private val hapticMediaAttributes by lazy { AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build() }

internal fun currentHapticRoute(manager: AudioManager?): String {
    if (manager == null) return "speakers"
    val routed = if (Build.VERSION.SDK_INT >= 33) try {
        manager?.getAudioDevicesForAttributes(hapticMediaAttributes).orEmpty()
    } catch (_: RuntimeException) { emptyList() } else emptyList()
    val devices = if (routed.isNotEmpty()) routed else try {
        manager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.toList().orEmpty()
    } catch (_: RuntimeException) { emptyList() }
    val types = devices.map { it.type }
    if (AudioDeviceInfo.TYPE_BLUETOOTH_A2DP in types || AudioDeviceInfo.TYPE_BLUETOOTH_SCO in types ||
        (Build.VERSION.SDK_INT >= 31 && AudioDeviceInfo.TYPE_BLE_HEADSET in types)) return "bluetooth"
    if (AudioDeviceInfo.TYPE_WIRED_HEADSET in types || AudioDeviceInfo.TYPE_WIRED_HEADPHONES in types ||
        (Build.VERSION.SDK_INT >= 26 && AudioDeviceInfo.TYPE_USB_HEADSET in types)) return "headphones"
    return "speakers"
}
