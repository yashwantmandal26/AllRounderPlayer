package com.example.ymediaplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.ymediaplayer.theme.LocalAppColors
import com.example.ymediaplayer.theme.SlateGreyAppColors
import com.example.ymediaplayer.AppRoot
import com.example.ymediaplayer.update.ReleaseInfo
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class UpdateUiTest {
    @get:Rule val rule = createComposeRule()

    private val release = ReleaseInfo("v1.7.1", "1.7.1", "Update screen improvements",
        "*A clearer, more comfortable update experience.*\n\n" +
            "- **Better layout:** readable features and clear version numbers.\n" +
            "- **Rich formatting:** bold, italic, and clean lists.\n" +
            "- **Responsive header:** your library title stays readable.\n" +
            "- **Download progress:** see the status before installation.\n\n" +
            "### Also included\n\n" +
            "1. Larger touch targets.\n2. Scrollable details on smaller screens.\n3. Improved light and dark theme support.",
        "https://example.invalid/app-release.apk", "app-release.apk", 214_239_593, "2026-10-14")

    @Test fun nativeDialogKeepsDownloadInsideItsWindow() {
        rule.setContent {
            AppRoot {
                CompositionLocalProvider(LocalAppColors provides SlateGreyAppColors) {
                    UpdateDialog(release.copy(tagName = "v1.7.2", versionName = "1.7.2"), onDismiss = {})
                }
            }
        }
        rule.onNodeWithTag("update-installed-version").assertTextEquals("v1.7.1")
        rule.onNodeWithTag("update-release-version").assertTextEquals("v1.7.2")
        rule.onNodeWithTag("update-download").assertIsDisplayed()
        val surface = rule.onNodeWithTag("update-dialog").fetchSemanticsNode().boundsInRoot
        val download = rule.onNodeWithTag("update-download").fetchSemanticsNode().boundsInRoot
        assertTrue(download.bottom <= surface.bottom)
        saveScreenshot("update-dialog-native.png")
    }

    @Test fun dialogShowsVersionsFormattedFeaturesAndWorkingActions() {
        var downloads = 0
        rule.setContent {
            CompositionLocalProvider(LocalAppColors provides SlateGreyAppColors) {
                MaterialTheme {
                    Box(Modifier.width(360.dp).height(640.dp).background(SlateGreyAppColors.baseBackground)) {
                        UpdateDialogContent(release, "1.7.0", onDownload = { downloads++ }, onLater = {},
                            onSkip = {}, onClose = {}, modifier = Modifier.padding(12.dp))
                    }
                }
            }
        }
        rule.onNodeWithTag("update-installed-version").assertTextEquals("v1.7.0")
        rule.onNodeWithTag("update-release-version").assertTextEquals("v1.7.1")
        rule.onNodeWithText("Better layout:", substring = true).assertIsDisplayed()
        rule.onNodeWithTag("update-download").assertIsDisplayed()
        saveScreenshot("update-dialog-phone.png")
        rule.onNodeWithTag("update-download").performSemanticsAction(SemanticsActions.OnClick) { it() }
        rule.runOnIdle { assertEquals(1, downloads) }
    }

    @Test fun shortWindowAndLargeTextCanScrollToActions() {
        var dismissed = false
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalAppColors provides SlateGreyAppColors,
                LocalDensity provides Density(density.density, 1.5f)) {
                MaterialTheme {
                    Box(Modifier.width(360.dp).height(300.dp)) {
                        UpdateDialogContent(release, "1.7.0", onDownload = {}, onLater = { dismissed = true },
                            onSkip = {}, onClose = {})
                    }
                }
            }
        }
        rule.onNodeWithTag("update-download").assertIsDisplayed()
        rule.onNodeWithTag("update-release-notes").performTouchInput { swipeUp() }
        rule.onNodeWithTag("update-download").assertIsDisplayed()
        rule.onNodeWithText("Not now").performClick()
        assertTrue(dismissed)
    }

    @Test fun downloadingShowsProgressAndPreventsDuplicateDownload() {
        rule.setContent {
            CompositionLocalProvider(LocalAppColors provides SlateGreyAppColors) {
                MaterialTheme {
                    Box(Modifier.width(360.dp).height(640.dp)) {
                        UpdateDialogContent(release, "1.7.0", isDownloading = true, progress = 0.42f,
                            statusText = "86 MB of 204 MB", onDownload = {}, onLater = {}, onSkip = {},
                            onClose = {})
                    }
                }
            }
        }
        rule.onNodeWithText("42%").assertIsDisplayed()
        rule.onNodeWithTag("update-download").assertDoesNotExist()
        rule.onNodeWithContentDescription("Close update").assertIsNotEnabled()
    }

    @Test fun failedDownloadCanBeRetriedAndSkipped() {
        var retries = 0
        var skips = 0
        rule.setContent {
            CompositionLocalProvider(LocalAppColors provides SlateGreyAppColors) {
                MaterialTheme {
                    Box(Modifier.width(360.dp).height(640.dp)) {
                        UpdateDialogContent(release, "1.7.0", errorMessage = "Download was interrupted. Please try again.",
                            onDownload = { retries++ }, onLater = {}, onSkip = { skips++ }, onClose = {})
                    }
                }
            }
        }
        rule.onNodeWithText("Try again").performClick()
        rule.onNodeWithText("Skip version").performClick()
        rule.runOnIdle { assertEquals(1, retries); assertEquals(1, skips) }
    }

    @Test fun downloadedUpdateOffersInstallInsteadOfAnotherDownload() {
        var installs = 0
        rule.setContent {
            CompositionLocalProvider(LocalAppColors provides SlateGreyAppColors) {
                MaterialTheme {
                    Box(Modifier.width(360.dp).height(640.dp)) {
                        UpdateDialogContent(release, "1.7.0", readyToInstall = true,
                            statusText = "Allow installation, then return to continue",
                            onDownload = { installs++ }, onLater = {}, onSkip = {}, onClose = {})
                    }
                }
            }
        }
        rule.onNodeWithText("Allow installation, then return to continue").assertIsDisplayed()
        rule.onNodeWithText("Install update").performClick()
        rule.runOnIdle { assertEquals(1, installs) }
    }

    @Test fun unknownDownloadSizeShowsIndeterminateProgress() {
        rule.setContent {
            CompositionLocalProvider(LocalAppColors provides SlateGreyAppColors) {
                MaterialTheme {
                    Box(Modifier.width(360.dp).height(640.dp)) {
                        UpdateDialogContent(release, "1.7.0", isDownloading = true, progress = -1f,
                            statusText = "12 MB downloaded", onDownload = {}, onLater = {}, onSkip = {}, onClose = {})
                    }
                }
            }
        }
        rule.onNodeWithTag("update-progress").assertIsDisplayed()
        rule.onNodeWithText("0%").assertDoesNotExist()
        rule.onNodeWithText("12 MB downloaded").assertIsDisplayed()
    }

    @Test fun historyCanCollapseExpandAndOpenAnUpdateInLightTheme() {
        val manager = com.example.ymediaplayer.update.UpdateManager
        val next = release.copy(tagName = "v1.7.2", versionName = "1.7.2")
        var selected: ReleaseInfo? = null
        var closed = false
        manager.releaseHistory.value = listOf(next, release.copy(tagName = "v1.6.0", versionName = "1.6.0"))
        try {
            rule.setContent {
                CompositionLocalProvider(LocalAppColors provides com.example.ymediaplayer.theme.CleanLightAppColors) {
                    MaterialTheme {
                        UpdateHistoryDialog(onDismiss = { closed = true }, onInstallRelease = { selected = it })
                    }
                }
            }
            rule.onNodeWithContentDescription("Hide 1.7.2 details").performClick()
            rule.onNodeWithText("View update").assertDoesNotExist()
            rule.onNodeWithContentDescription("Show 1.7.2 details").performClick()
            rule.onNodeWithText("View update").performScrollTo().performClick()
            rule.runOnIdle { assertTrue(closed); assertEquals(next, selected) }
        } finally {
            rule.runOnIdle { manager.releaseHistory.value = emptyList() }
        }
    }

    @Test fun updateBannerCannotCollapseLibraryTitleWithLargeText() {
        var updates = 0
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalAppColors provides SlateGreyAppColors,
                LocalDensity provides Density(density.density, 1.5f)) {
                MaterialTheme {
                    Box(Modifier.width(360.dp).height(360.dp).background(SlateGreyAppColors.baseBackground)) {
                        LibraryHeader("Video", "290 videos · 15.2 GB", 4, "1.7.1", { updates++ },
                            modifier = Modifier.padding(16.dp)) {
                            listOf(Icons.Rounded.Palette, Icons.Rounded.LightMode, Icons.Rounded.Search, Icons.Rounded.Settings).forEach {
                                IconButton(onClick = {}, modifier = Modifier.size(48.dp)) { Icon(it, null, tint = SlateGreyAppColors.textPrimary) }
                            }
                        }
                    }
                }
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithTag("library_header_title").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1, layouts.single().lineCount)
        val title = rule.onNodeWithTag("library_header_title").fetchSemanticsNode().boundsInRoot
        val actions = rule.onNodeWithTag("library_header_actions").fetchSemanticsNode().boundsInRoot
        val update = rule.onNodeWithTag("library_update_notice").fetchSemanticsNode().boundsInRoot
        assertTrue(title.width > 100f)
        assertTrue(actions.top >= title.bottom)
        assertTrue(update.top >= actions.bottom)
        saveScreenshot("update-header-large-text.png")
        rule.onNodeWithTag("library_update_notice").performClick()
        assertEquals(1, updates)
    }

    private fun saveScreenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.getExternalFilesDir(null), name)
        file.outputStream().use { out ->
            val image = if (name.startsWith("update-dialog")) rule.onNodeWithTag("update-dialog").captureToImage() else rule.onRoot().captureToImage()
            image.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        }
    }
}
