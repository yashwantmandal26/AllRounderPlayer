package com.example.ymediaplayer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.CompositionLocalProvider
import com.example.ymediaplayer.player.VideoPlaybackManager
import com.example.ymediaplayer.theme.LocalThemeController
import com.example.ymediaplayer.theme.ThemeMode
import com.example.ymediaplayer.theme.YMediaPlayerTheme
import com.example.ymediaplayer.theme.rememberThemeController
import com.example.ymediaplayer.ui.FolderDetailScreen
import com.example.ymediaplayer.ui.FolderListScreen
import com.example.ymediaplayer.ui.InAppMiniPlayer
import com.example.ymediaplayer.ui.VideoPlayerScreen
import com.example.ymediaplayer.ui.SettingsScreen
import java.net.URLDecoder
import java.net.URLEncoder

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PermMedia
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ymediaplayer.theme.LocalAppColors

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN_VIDEO_URL = "EXTRA_OPEN_VIDEO_URL"
        val openMusicTrigger = mutableStateOf(false)
        val openSettingsTrigger = mutableStateOf(false)
        val openVideoUrl = mutableStateOf<String?>(null)
        var onUserLeaveHintListener: (() -> Unit)? = null
    }

    private var videoPermissionGranted by mutableStateOf(false)
    private var audioPermissionGranted by mutableStateOf(false)
    private var hasPromptedPermissions by mutableStateOf(false)

    private val requiredPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    private val permissionResultLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        updatePermissionState()
    }

    private fun updatePermissionState() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            videoPermissionGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_MEDIA_VIDEO
            ) == PackageManager.PERMISSION_GRANTED
            audioPermissionGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_MEDIA_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            val storageGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            videoPermissionGranted = storageGranted
            audioPermissionGranted = storageGranted
        }
    }

    private fun requestPermissions() {
        hasPromptedPermissions = true
        permissionResultLauncher.launch(requiredPermissions)
    }

    private fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        )
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        updatePermissionState()
        // Ensure app screens follow the phone's native brightness
        val lp = window.attributes
        if (lp.screenBrightness != WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) {
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            window.attributes = lp
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        onUserLeaveHintListener?.invoke()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(com.example.ymediaplayer.service.MusicService.EXTRA_OPEN_MUSIC_PLAYER, false)) {
            openMusicTrigger.value = true
        }
        if (intent.getBooleanExtra("OPEN_SETTINGS", false)) {
            openSettingsTrigger.value = true
        }
        val videoUrlExtra = intent.getStringExtra(EXTRA_OPEN_VIDEO_URL)
        if (!videoUrlExtra.isNullOrEmpty()) {
            openVideoUrl.value = videoUrlExtra
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (intent?.getBooleanExtra(com.example.ymediaplayer.service.MusicService.EXTRA_OPEN_MUSIC_PLAYER, false) == true) {
            openMusicTrigger.value = true
        }
        if (intent?.getBooleanExtra("OPEN_SETTINGS", false) == true) {
            openSettingsTrigger.value = true
        }
        val videoUrlExtra = intent?.getStringExtra(EXTRA_OPEN_VIDEO_URL)
        if (!videoUrlExtra.isNullOrEmpty()) {
            openVideoUrl.value = videoUrlExtra
        }

        // Playback screens manage KEEP_SCREEN_ON only while it is useful.
        val initialLp = window.attributes
        initialLp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = initialLp
        enableEdgeToEdge()

        updatePermissionState()

        setContent {
            AppRoot {
                if (videoPermissionGranted || audioPermissionGranted) {
                    MainApp(
                        hasVideoPermission = videoPermissionGranted,
                        hasAudioPermission = audioPermissionGranted,
                        onRequestPermissions = { requestPermissions() }
                    )
                } else {
                    if (!hasPromptedPermissions) {
                        LaunchedEffect(Unit) {
                            requestPermissions()
                        }
                    }
                    PermissionFallbackScreen(
                        onRequestPermissions = { requestPermissions() },
                        onOpenSettings = { openAppSettings() }
                    )
                }
            }
        }
    }
}

/**
 * Provides the persisted [ThemeController] and applies [YMediaPlayerTheme] with the
 * currently selected mode, so the whole app reacts to the light/dark switch.
 */
@Composable
fun AppRoot(content: @Composable () -> Unit) {
    val themeController = rememberThemeController()
    CompositionLocalProvider(LocalThemeController provides themeController) {
        YMediaPlayerTheme(
            themeMode = themeController.mode,
            colorTheme = themeController.colorTheme
        ) {
            content()
        }
    }
}

@Composable
fun MainApp(
    hasVideoPermission: Boolean,
    hasAudioPermission: Boolean,
    onRequestPermissions: () -> Unit
) {
    val backStack = rememberNavBackStack(FolderList)
    val context = androidx.compose.ui.platform.LocalContext.current
    val window = (context as? ComponentActivity)?.window
    val insetsController = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
    val appColors = com.example.ymediaplayer.theme.LocalAppColors.current
    val appPreferences = remember { com.example.ymediaplayer.data.AppPreferences(context) }

    LaunchedEffect(Unit) {
        com.example.ymediaplayer.update.UpdateManager.checkForUpdates(context, appPreferences, isManual = false)
    }

    LaunchedEffect(MainActivity.openMusicTrigger.value) {
        if (MainActivity.openMusicTrigger.value) {
            while (backStack.size > 1) {
                backStack.removeLastOrNull()
            }
        }
    }

    LaunchedEffect(MainActivity.openVideoUrl.value) {
        val target = MainActivity.openVideoUrl.value
        if (!target.isNullOrEmpty()) {
            com.example.ymediaplayer.data.VideoRepository.getVideoDimensions(target)?.let { (w, h) ->
                VideoPlaybackManager.setInitialDimensions(w, h)
            }
            val intent = Intent(context, com.example.ymediaplayer.ui.VideoPlayerActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(com.example.ymediaplayer.ui.VideoPlayerActivity.EXTRA_VIDEO_URL, target)
            }
            context.startActivity(intent)
            MainActivity.openVideoUrl.value = null
        }
    }

    LaunchedEffect(MainActivity.openSettingsTrigger.value) {
        if (MainActivity.openSettingsTrigger.value) {
            backStack.add(com.example.ymediaplayer.Settings)
            MainActivity.openSettingsTrigger.value = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavDisplay(
            backStack = backStack,
            onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
            transitionSpec = {
                EnterTransition.None.togetherWith(ExitTransition.None)
            },
            popTransitionSpec = {
                EnterTransition.None.togetherWith(ExitTransition.None)
            },
            entryProvider = entryProvider {
                entry<FolderList> {
                    LaunchedEffect(Unit) {
                        insetsController?.show(WindowInsetsCompat.Type.systemBars())
                        val act = context as? ComponentActivity
                        val lp = act?.window?.attributes
                        if (lp != null && lp.screenBrightness != WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) {
                            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                            act.window?.attributes = lp
                        }
                        MainActivity.onUserLeaveHintListener = null
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            try {
                                act?.setPictureInPictureParams(
                                    android.app.PictureInPictureParams.Builder()
                                        .setAutoEnterEnabled(false)
                                        .build()
                                    )
                            } catch (_: Exception) {}
                        }
                    }
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = appColors.baseBackground
                    ) {
                        FolderListScreen(
                            hasVideoPermission = hasVideoPermission,
                            hasAudioPermission = hasAudioPermission,
                            onRequestPermissions = onRequestPermissions,
                            onFolderClick = { id, name ->
                                backStack.add(FolderDetail(id, name))
                            },
                            onVideoClick = { uri -> 
                                com.example.ymediaplayer.data.VideoRepository.getVideoDimensions(uri)?.let { (w, h) ->
                                    VideoPlaybackManager.setInitialDimensions(w, h)
                                }
                                val intent = Intent(context, com.example.ymediaplayer.ui.VideoPlayerActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                                    putExtra(com.example.ymediaplayer.ui.VideoPlayerActivity.EXTRA_VIDEO_URL, uri)
                                }
                                context.startActivity(intent)
                            },
                            onOpenSettings = {
                                backStack.add(com.example.ymediaplayer.Settings)
                            }
                        )
                    }
                }
                entry<FolderDetail> { navKey ->
                    LaunchedEffect(Unit) {
                        insetsController?.show(WindowInsetsCompat.Type.systemBars())
                        val act = context as? ComponentActivity
                        val lp = act?.window?.attributes
                        if (lp != null && lp.screenBrightness != WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) {
                            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                            act.window?.attributes = lp
                        }
                        MainActivity.onUserLeaveHintListener = null
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            try {
                                act?.setPictureInPictureParams(
                                    android.app.PictureInPictureParams.Builder()
                                        .setAutoEnterEnabled(false)
                                        .build()
                                    )
                            } catch (_: Exception) {}
                        }
                    }
                    FolderDetailScreen(
                        folderId = navKey.folderId,
                        folderName = navKey.folderName,
                        onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
                        onVideoClick = { uri ->
                            com.example.ymediaplayer.data.VideoRepository.getVideoDimensions(uri)?.let { (w, h) ->
                                VideoPlaybackManager.setInitialDimensions(w, h)
                            }
                            val intent = Intent(context, com.example.ymediaplayer.ui.VideoPlayerActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                                putExtra(com.example.ymediaplayer.ui.VideoPlayerActivity.EXTRA_VIDEO_URL, uri)
                            }
                            context.startActivity(intent)
                        }
                    )
                }
                entry<VideoPlayer> { navKey ->
                    val decodedUri = try { URLDecoder.decode(navKey.videoUri, "UTF-8") } catch (_: Exception) { navKey.videoUri }
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color.Black
                    ) {
                        VideoPlayerScreen(
                            videoUrl = decodedUri,
                            onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
                            onOpenSettings = { backStack.add(com.example.ymediaplayer.Settings) }
                        )
                    }
                }
                entry<com.example.ymediaplayer.Settings> {
                    LaunchedEffect(Unit) {
                        insetsController?.show(WindowInsetsCompat.Type.systemBars())
                        MainActivity.onUserLeaveHintListener = null
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val act = context as? ComponentActivity
                            try {
                                act?.setPictureInPictureParams(
                                    android.app.PictureInPictureParams.Builder()
                                        .setAutoEnterEnabled(false)
                                        .build()
                                    )
                            } catch (_: Exception) {}
                        }
                    }
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = appColors.baseBackground
                    ) {
                        SettingsScreen(
                            onBack = { if (backStack.size > 1) backStack.removeLastOrNull() }
                        )
                    }
                }
            }
        )

        // ─── In-App Auto-Updater Dialog ──────────────────────────────────────
        val availableUpdate = com.example.ymediaplayer.update.UpdateManager.availableUpdate.value
        val showUpdateDialog = com.example.ymediaplayer.update.UpdateManager.showUpdateDialog.value
        if (availableUpdate != null && showUpdateDialog) {
            com.example.ymediaplayer.ui.UpdateDialog(
                release = availableUpdate,
                onDismiss = { com.example.ymediaplayer.update.UpdateManager.dismissUpdate() }
            )
        }
    }
}

@Composable
fun PermissionFallbackScreen(
    onRequestPermissions: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val appColors = LocalAppColors.current
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = appColors.baseBackground
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(appColors.glassBg)
                    .border(1.dp, appColors.glassBorder, RoundedCornerShape(24.dp))
                    .padding(32.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(appColors.accentBlue.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PermMedia,
                        contentDescription = "Permission Required",
                        tint = appColors.accentBlue,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(Modifier.height(20.dp))

                Text(
                    text = "Permission Required",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = appColors.textPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    text = "YMedia Player needs access to your media files to display and play videos and audio stored on your device.",
                    fontSize = 14.sp,
                    color = appColors.textSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(Modifier.height(28.dp))

                Button(
                    onClick = onRequestPermissions,
                    colors = ButtonDefaults.buttonColors(containerColor = appColors.accentBlue),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text(
                        "Grant Permission",
                        color = appColors.onAccent,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onOpenSettings,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = appColors.accentBlue),
                    border = BorderStroke(1.dp, appColors.accentBlue.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text(
                        "Open Settings",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
