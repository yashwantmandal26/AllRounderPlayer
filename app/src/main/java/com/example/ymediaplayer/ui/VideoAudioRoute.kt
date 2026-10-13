package com.example.ymediaplayer.ui

import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.Build
import androidx.compose.runtime.*

@Composable
internal fun rememberVideoAudioRoute(manager: AudioManager): String {
    fun currentRoute(): String {
        val personalTypes = mutableSetOf(AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
        if (Build.VERSION.SDK_INT >= 26) personalTypes.add(AudioDeviceInfo.TYPE_USB_HEADSET)
        if (Build.VERSION.SDK_INT >= 31) personalTypes.add(AudioDeviceInfo.TYPE_BLE_HEADSET)
        return if (manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in personalTypes }) "headphones" else "speakers"
    }
    var route by remember { mutableStateOf(currentRoute()) }
    DisposableEffect(manager) {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) { route = currentRoute() }
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) { route = currentRoute() }
        }
        manager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        onDispose { manager.unregisterAudioDeviceCallback(callback) }
    }
    return route
}
