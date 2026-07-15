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
import androidx.compose.runtime.CompositionLocalProvider
import com.example.ymediaplayer.theme.LocalThemeController
import com.example.ymediaplayer.theme.ThemeMode
import com.example.ymediaplayer.theme.YMediaPlayerTheme
import com.example.ymediaplayer.theme.rememberThemeController
import com.example.ymediaplayer.ui.FolderDetailScreen
import com.example.ymediaplayer.ui.FolderListScreen
import com.example.ymediaplayer.ui.VideoPlayerScreen
import java.net.URLDecoder
import java.net.URLEncoder

class MainActivity : ComponentActivity() {

    private val permissionResultLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            setContent { AppRoot { MainApp() } }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()

        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if (permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            setContent { AppRoot { MainApp() } }
        } else {
            // Show a simple loading or permission prompt, then request it
            setContent {
                AppRoot {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        LaunchedEffect(Unit) {
                            permissionResultLauncher.launch(permissions)
                        }
                    }
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
        YMediaPlayerTheme(themeMode = themeController.mode) {
            content()
        }
    }
}

@Composable
fun MainApp() {
    val backStack = rememberNavBackStack(FolderList)
    val context = androidx.compose.ui.platform.LocalContext.current
    val window = (context as? ComponentActivity)?.window
    val insetsController = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
    val appColors = com.example.ymediaplayer.theme.LocalAppColors.current

    NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            entryProvider = entryProvider {
                entry<FolderList> {
                    LaunchedEffect(Unit) {
                        insetsController?.show(WindowInsetsCompat.Type.systemBars())
                    }
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = appColors.baseBackground
                    ) {
                        FolderListScreen(
                            onFolderClick = { id, name ->
                                backStack.add(FolderDetail(id, name))
                            },
                            onVideoClick = { uri -> 
                                val encodedUri = URLEncoder.encode(uri, "UTF-8")
                                backStack.add(VideoPlayer(encodedUri)) 
                            }
                        )
                    }
                }
                entry<FolderDetail> { navKey ->
                    LaunchedEffect(Unit) {
                        insetsController?.show(WindowInsetsCompat.Type.systemBars())
                    }
                    FolderDetailScreen(
                        folderId = navKey.folderId,
                        folderName = navKey.folderName,
                        onBack = { backStack.removeLastOrNull() },
                        onVideoClick = { uri ->
                            val encodedUri = URLEncoder.encode(uri, "UTF-8")
                            backStack.add(VideoPlayer(encodedUri))
                        }
                    )
                }
                entry<VideoPlayer> { navKey ->
                    LaunchedEffect(Unit) {
                        insetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        insetsController?.hide(WindowInsetsCompat.Type.systemBars())
                    }
                    val decodedUri = URLDecoder.decode(navKey.videoUri, "UTF-8")
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color.Black
                    ) {
                        VideoPlayerScreen(
                            videoUrl = decodedUri,
                            onBack = { backStack.removeLastOrNull() }
                        )
                    }
                }
            }
        )
}
