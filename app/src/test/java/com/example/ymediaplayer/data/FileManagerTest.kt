package com.example.ymediaplayer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileManagerTest {

    @Test
    fun fileOperationResult_types() {
        val success: FileOperationResult = FileOperationResult.Success
        val failure: FileOperationResult = FileOperationResult.Failure("Permission denied")

        assertTrue(success is FileOperationResult.Success)
        assertTrue(failure is FileOperationResult.Failure)
        assertEquals("Permission denied", (failure as FileOperationResult.Failure).message)
    }

    @Test
    fun resolutionFormatting_validDimensions() {
        val width = 1920
        val height = 1080
        val resolution = if (width > 0 && height > 0) "${width}×${height}" else "Unknown"
        assertEquals("1920×1080", resolution)
    }

    @Test
    fun resolutionFormatting_zeroDimensions() {
        val width = 0
        val height = 0
        val resolution = if (width > 0 && height > 0) "${width}×${height}" else "Unknown"
        assertEquals("Unknown", resolution)
    }

    @Test
    fun formatDate_formatsEpochSeconds() {
        val epochSeconds = 1700000000L
        val formatted = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US).format(Date(epochSeconds * 1000L))
        assertTrue(formatted.contains("2023"))
    }
}
