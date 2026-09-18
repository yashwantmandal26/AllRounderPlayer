package com.example.ymediaplayer.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.ymediaplayer.AppRoot
import com.example.ymediaplayer.MainActivity

class VideoPlayerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_VIDEO_URL = "EXTRA_VIDEO_URL"
        const val EXTRA_VIDEO_TITLE = "EXTRA_VIDEO_TITLE"
        var onUserLeaveHintListener: (() -> Unit)? = null
    }

    private var currentUrl by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val initialLp = window.attributes
        initialLp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = initialLp

        currentUrl = intent.getStringExtra(EXTRA_VIDEO_URL) ?: ""

        setContent {
            AppRoot {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    if (currentUrl.isNotEmpty()) {
                        VideoPlayerScreen(
                            videoUrl = currentUrl,
                            onBack = { finish() },
                            onOpenSettings = {
                                val intent = Intent(this@VideoPlayerActivity, MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                    putExtra("OPEN_SETTINGS", true)
                                }
                                startActivity(intent)
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val newUrl = intent.getStringExtra(EXTRA_VIDEO_URL)
        if (!newUrl.isNullOrEmpty() && newUrl != currentUrl) {
            currentUrl = newUrl
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        onUserLeaveHintListener?.invoke()
    }

    override fun onDestroy() {
        super.onDestroy()
        onUserLeaveHintListener = null
    }
}
