package com.example.ymediaplayer.ui

import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.test.platform.app.InstrumentationRegistry
import com.example.ymediaplayer.AppRoot
import com.example.ymediaplayer.data.FileManager
import com.example.ymediaplayer.data.FileOperationResult
import com.example.ymediaplayer.data.VideoRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Only creates, modifies and removes disposable QA media owned by this app. */
class LibraryFileOperationsTest {
    @get:Rule val rule = createComposeRule()
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val context = ins.targetContext
    private val resolver = context.contentResolver

    private fun fixture(): Uri {
        assumeTrue(Build.VERSION.SDK_INT >= 30)
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "QA_FileActions_${System.nanoTime()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/YMediaQA")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = requireNotNull(resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values))
        resolver.openOutputStream(uri)!!.use { out ->
            ins.context.assets.open("QA_Hidden_30s.mp4").use { it.copyTo(out) }
        }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
        return uri
    }

    @Test fun invalidatedCacheDoesNotResurrectRenamedOrTrashedMedia() = runBlocking {
        val uri = fixture()
        val repo = VideoRepository(context)
        val id = ContentUris.parseId(uri)
        try {
            assertTrue(repo.getFoldersWithVideos(true).flatMap { it.videos }.any { it.id == id })
            val name = "QA_Renamed_${System.nanoTime()}.mp4"
            assertEquals(FileOperationResult.Success, FileManager(context).renameVideo(id, name))
            VideoRepository.invalidateCache()
            assertEquals(name, repo.getFoldersWithVideos().flatMap { it.videos }.first { it.id == id }.title)
            resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_TRASHED, 1) }, null, null)
            VideoRepository.invalidateCache()
            assertFalse("Trash must disappear immediately, even with a disk snapshot", repo.getFoldersWithVideos().flatMap { it.videos }.any { it.id == id })
        } finally {
            resolver.delete(uri, null, null)
            repo.getFoldersWithVideos(true)
        }
    }

    @Test fun localVideoShareUsesGrantedContentUri() {
        val file = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES), "QA_Share.mp4")
        file.parentFile!!.mkdirs()
        file.writeBytes(byteArrayOf(1, 2, 3))
        try {
            val intent = FileManager(context).buildShareIntent(Uri.fromFile(file))
            val shared = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
            assertEquals("content", shared.scheme)
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertEquals(shared, intent.clipData!!.getItemAt(0).uri)
            resolver.openInputStream(shared)!!.use { assertArrayEquals(file.readBytes(), it.readBytes()) }
        } finally { file.delete() }
    }

    @Test fun localVideoCanBeRenamedInspectedAndDeleted() = runBlocking {
        val directory = requireNotNull(context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES))
        val file = File(directory, "QA_Local_${System.nanoTime()}.mp4")
        val renamed = File(directory, "QA_Renamed_${System.nanoTime()}.mp4")
        file.parentFile!!.mkdirs()
        ins.context.assets.open("QA_Hidden_30s.mp4").use { input -> file.outputStream().use { input.copyTo(it) } }
        val manager = FileManager(context)
        try {
            assertFalse(FileManager.supportsRecycleBin(listOf(Uri.fromFile(file))))
            assertEquals(FileOperationResult.Success, manager.renameVideo(Uri.fromFile(file), renamed.name))
            assertFalse(file.exists())
            assertTrue(renamed.exists())
            val info = requireNotNull(manager.getVideoInfo(Uri.fromFile(renamed)))
            assertEquals(renamed.name, info.filename)
            assertTrue(info.duration > 0)
            assertEquals(FileOperationResult.Success, manager.deleteVideos(listOf(Uri.fromFile(renamed))))
            assertFalse(renamed.exists())
        } finally { file.delete(); renamed.delete() }
    }

    @Test fun trashButtonCompletesRealSystemConsent() {
        val uri = fixture()
        var success = false
        var failure: String? = null
        try {
            rule.setContent { AppRoot {
                val scope = rememberCoroutineScope()
                val handle = rememberFileOperationHandler(onSuccess = { success = true }, onFailure = { failure = it })
                Button(onClick = { scope.launch { handle(FileManager(context).deleteVideos(listOf(uri))) } }) { Text("Trash QA fixture") }
            } }
            rule.onNodeWithText("Trash QA fixture").performClick()
            // Approve only the system request for the disposable media created above.
            val deadline = android.os.SystemClock.elapsedRealtime() + 10_000
            var clicked = false
            fun approve(node: android.view.accessibility.AccessibilityNodeInfo?): Boolean {
                if (node == null) return false
                if (node.packageName?.toString()?.contains("providers.media") == true &&
                    node.isClickable && node.text?.toString()?.lowercase() in listOf("allow", "move to trash", "move to recycle bin", "move to bin")) {
                    return node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                }
                for (i in 0 until node.childCount) if (approve(node.getChild(i))) return true
                return false
            }
            while (!clicked && !success && failure == null && android.os.SystemClock.elapsedRealtime() < deadline) {
                clicked = approve(ins.uiAutomation.rootInActiveWindow)
                Thread.sleep(100)
            }
            rule.waitUntil(10_000) { success || failure != null }
            assertNull(failure)
            assertTrue(success)
            resolver.query(uri, arrayOf(MediaStore.Video.Media.IS_TRASHED), android.os.Bundle().apply {
                putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
            }, null)!!.use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }
        } finally { resolver.delete(uri, null, null); VideoRepository.invalidateCache() }
    }

    @Test fun folderDeleteDialogRemovesVideoAndContinueWatchingEntry() = runBlocking {
        val uri = fixture()
        val repo = VideoRepository(context)
        val video = repo.getFoldersWithVideos(true).flatMap { it.videos }.first { it.id == ContentUris.parseId(uri) }
        val preferences = com.example.ymediaplayer.data.AppPreferences(context)
        val confirmBefore = preferences.isConfirmDeleteEnabled()
        preferences.setConfirmDeleteEnabled(true)
        preferences.saveVideoProgress(video.uri.toString(), 10_000, 30_000)
        try {
            rule.setContent { AppRoot {
                FolderDetailScreen(folderId = video.bucketId, folderName = "QA", onBack = {}, onVideoClick = {})
            } }
            rule.waitUntil(10_000) { rule.onAllNodesWithText(video.title.substringBeforeLast(".")).fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText(video.title.substringBeforeLast(".")).performTouchInput { longClick() }
            rule.onNodeWithText("Move to Recycle Bin").performClick()
            rule.onNodeWithText("Move to Bin").performClick()
            val deadline = android.os.SystemClock.elapsedRealtime() + 10_000
            fun approve(node: android.view.accessibility.AccessibilityNodeInfo?): Boolean {
                if (node == null) return false
                if (node.packageName?.toString()?.contains("providers.media") == true && node.isClickable &&
                    node.text?.toString()?.lowercase() in listOf("allow", "move to trash", "move to recycle bin", "move to bin")) {
                    return node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                }
                for (i in 0 until node.childCount) if (approve(node.getChild(i))) return true
                return false
            }
            while (android.os.SystemClock.elapsedRealtime() < deadline) {
                if (approve(ins.uiAutomation.rootInActiveWindow)) break
                Thread.sleep(100)
            }
            rule.waitUntil(10_000) { preferences.getVideoProgress(video.uri.toString()) == 0L }
            rule.waitUntil(10_000) { rule.onAllNodesWithText(video.title.substringBeforeLast(".")).fetchSemanticsNodes().isEmpty() }
            rule.onNodeWithText(video.title.substringBeforeLast(".")).assertDoesNotExist()
            assertFalse(preferences.getPlayedUris().contains(video.uri.toString()))
        } finally {
            preferences.setConfirmDeleteEnabled(confirmBefore)
            preferences.clearVideoProgress(video.uri.toString())
            resolver.delete(uri, null, null)
            repo.getFoldersWithVideos(true)
        }
    }
}
