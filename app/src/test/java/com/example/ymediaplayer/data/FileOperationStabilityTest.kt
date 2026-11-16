package com.example.ymediaplayer.data

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*

class FileOperationStabilityTest {
    @get:Rule val temporary = TemporaryFolder()
    private val resolver = mock(ContentResolver::class.java)
    private val context = mock(Context::class.java).also {
        `when`(it.contentResolver).thenReturn(resolver)
    }
    private val manager = FileManager(context)

    private fun uri(scheme: String, path: String) = mock(Uri::class.java).also {
        `when`(it.scheme).thenReturn(scheme)
        `when`(it.path).thenReturn(path)
    }

    @Test fun renameKeepsTheOriginalContentUriInsteadOfRedirectingAnAudioIdToVideo() = runBlocking<Unit> {
        val audio = uri("content", "/external/audio/media/42")
        `when`(resolver.update(same(audio), any(ContentValues::class.java), isNull(), isNull())).thenReturn(1)
        assertEquals(FileOperationResult.Success, manager.renameVideo(audio, "renamed.mp3"))
        verify(resolver).update(same(audio), any(ContentValues::class.java), isNull(), isNull())
    }

    @Test fun opaqueDocumentUriDoesNotRequireANumericMediaId() = runBlocking<Unit> {
        val document = uri("content", "/document/primary:Movies/trailer.mp4")
        assertNull(manager.getVideoInfo(document))
        verify(resolver).query(same(document), any(Array<String>::class.java), isNull(), isNull(), isNull())
    }

    @Test fun duplicateFileSelectionIsDeletedOnlyOnce() = runBlocking {
        val file = temporary.newFile("disposable.mp4")
        val target = uri("file", file.absolutePath)
        assertEquals(FileOperationResult.Success, manager.deleteVideos(listOf(target, target)))
        assertFalse(file.exists())
    }

    @Test fun renameDoesNotOverwriteAnExistingFile() = runBlocking {
        val original = temporary.newFile("original.mp4").apply { writeText("original") }
        val existing = temporary.newFile("existing.mp4").apply { writeText("keep") }
        assertTrue(manager.renameVideo(uri("file", original.absolutePath), existing.name) is FileOperationResult.Failure)
        assertEquals("original", original.readText())
        assertEquals("keep", existing.readText())
    }

    @Test fun renamingAMissingFileToItselfReportsFailure() = runBlocking {
        val missing = java.io.File(temporary.root, "missing.mp4")
        assertTrue(manager.renameVideo(uri("file", missing.absolutePath), missing.name) is FileOperationResult.Failure)
    }

    @Test fun unsupportedLocationDoesNotThrowOrTouchTheResolver() = runBlocking {
        val target = uri("https", "/movie.mp4")
        assertTrue(manager.renameVideo(target, "new.mp4") is FileOperationResult.Failure)
        assertNull(manager.getVideoInfo(target))
        verifyNoInteractions(resolver)
    }
}
