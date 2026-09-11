package com.example.ymediaplayer.data

import android.content.Context
import android.content.SharedPreferences

class AppPreferences(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences("ymedia_prefs", Context.MODE_PRIVATE))

    // History: Map of Video URI to Last Played Position (in milliseconds)
    fun saveVideoProgress(uri: String, position: Long) {
        prefs.edit().putLong("progress_$uri", position).apply()
        recordPlayedUri(uri)
    }

    fun isCompleted(uri: String): Boolean {
        return prefs.getBoolean("completed_$uri", false)
    }

    fun markCompleted(uri: String, completed: Boolean) {
        prefs.edit().putBoolean("completed_$uri", completed).apply()
    }

    fun recordPlayback(uri: String, position: Long, duration: Long) {
        val isDone = duration > 0L && position >= (duration - 3000L)
        prefs.edit()
            .putLong("progress_$uri", position)
            .putLong("duration_$uri", duration)
            .putLong("timestamp_$uri", System.currentTimeMillis())
            .putBoolean("completed_$uri", isDone)
            .apply()
        recordPlayedUri(uri)
    }

    private fun recordPlayedUri(uri: String) {
        val currentList = getPlayedUris().toMutableList()
        currentList.remove(uri)
        currentList.add(0, uri) // Most recent first
        val trimmed = currentList.take(50)
        prefs.edit().putString("played_uris", trimmed.joinToString(";;;")).apply()
    }

    fun getPlayedUris(): List<String> {
        val raw = prefs.getString("played_uris", "") ?: ""
        if (raw.isEmpty()) return emptyList()
        return raw.split(";;;").filter { it.isNotBlank() }
    }

    fun getVideoProgress(uri: String): Long {
        if (isCompleted(uri)) return 0L
        return prefs.getLong("progress_$uri", 0L)
    }

    fun getVideoDuration(uri: String): Long {
        return prefs.getLong("duration_$uri", 0L)
    }

    fun getVideoLastPlayedTime(uri: String): Long {
        return prefs.getLong("timestamp_$uri", 0L)
    }

    // Favorites: Set of Video URIs
    fun toggleFavorite(uri: String) {
        val favs = getFavorites().toMutableSet()
        if (favs.contains(uri)) {
            favs.remove(uri)
        } else {
            favs.add(uri)
        }
        prefs.edit().putStringSet("favorites", favs).apply()
    }

    fun getFavorites(): Set<String> {
        return prefs.getStringSet("favorites", emptySet()) ?: emptySet()
    }
    
    fun isFavorite(uri: String): Boolean {
        return getFavorites().contains(uri)
    }

    // Sort Order
    fun saveSortOrder(order: String) {
        prefs.edit().putString("sort_order", order).apply()
    }

    fun getSortOrder(): String {
        return prefs.getString("sort_order", "DATE") ?: "DATE"
    }

    // Player Theme & Visual Customizations
    fun getPlayerTheme(): String {
        return prefs.getString("player_theme", "CYBER") ?: "CYBER"
    }

    fun savePlayerTheme(themeName: String) {
        prefs.edit().putString("player_theme", themeName).apply()
    }

    fun getSubtitleDesign(): Int {
        return prefs.getInt("subtitle_design", 0)
    }

    fun saveSubtitleDesign(design: Int) {
        prefs.edit().putInt("subtitle_design", design).apply()
    }

    fun isSignatureViewEnabled(): Boolean {
        return prefs.getBoolean("signature_view_enabled", false)
    }

    fun setSignatureViewEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("signature_view_enabled", enabled).apply()
    }

    fun getSignatureSaturation(): Float {
        return prefs.getFloat("signature_saturation", 1.25f)
    }

    fun setSignatureSaturation(value: Float) {
        prefs.edit().putFloat("signature_saturation", value).apply()
    }

    fun getSignatureSharpness(): Float {
        return prefs.getFloat("signature_sharpness", 0.5f)
    }

    fun setSignatureSharpness(value: Float) {
        prefs.edit().putFloat("signature_sharpness", value).apply()
    }

    fun isAmbientGlowEnabled(): Boolean {
        return prefs.getBoolean("ambient_glow_enabled", true)
    }

    fun setAmbientGlowEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("ambient_glow_enabled", enabled).apply()
    }

    // Music Favorites
    fun toggleMusicFavorite(uri: String) {
        val favs = getMusicFavorites().toMutableSet()
        if (favs.contains(uri)) {
            favs.remove(uri)
        } else {
            favs.add(uri)
        }
        prefs.edit().putStringSet("music_favorites", favs).apply()
    }

    fun getMusicFavorites(): Set<String> {
        return prefs.getStringSet("music_favorites", emptySet()) ?: emptySet()
    }

    fun isMusicFavorite(uri: String): Boolean {
        return getMusicFavorites().contains(uri)
    }

    // Music Artwork Mode: "VINYL", "CARD"
    fun getMusicArtworkStyle(): String {
        return prefs.getString("music_artwork_style", "VINYL") ?: "VINYL"
    }

    fun setMusicArtworkStyle(style: String) {
        prefs.edit().putString("music_artwork_style", style).apply()
    }

    // Security & Privacy Shield (FLAG_SECURE)
    fun isPrivacyLockEnabled(): Boolean {
        return prefs.getBoolean("privacy_lock_enabled", false)
    }

    fun setPrivacyLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("privacy_lock_enabled", enabled).apply()
    }

    // ─── Video Progress Management ──────────────────────────────────────────
    fun clearVideoProgress(uri: String) {
        prefs.edit()
            .remove("progress_$uri")
            .remove("duration_$uri")
            .remove("timestamp_$uri")
            .remove("completed_$uri")
            .apply()
        // Also remove from played history
        val currentList = getPlayedUris().toMutableList()
        currentList.remove(uri)
        prefs.edit().putString("played_uris", currentList.joinToString(";;;")).apply()
    }

    // ─── Pinned Folders ────────────────────────────────────────────────────
    fun getPinnedFolders(): Set<String> {
        return prefs.getStringSet("pinned_folders", emptySet()) ?: emptySet()
    }

    fun pinFolder(folderId: String) {
        val pinned = getPinnedFolders().toMutableSet()
        pinned.add(folderId)
        prefs.edit().putStringSet("pinned_folders", pinned).apply()
    }

    fun unpinFolder(folderId: String) {
        val pinned = getPinnedFolders().toMutableSet()
        pinned.remove(folderId)
        prefs.edit().putStringSet("pinned_folders", pinned).apply()
    }

    fun togglePinFolder(folderId: String) {
        if (isFolderPinned(folderId)) unpinFolder(folderId) else pinFolder(folderId)
    }

    fun isFolderPinned(folderId: String): Boolean {
        return getPinnedFolders().contains(folderId)
    }

    // ─── Media View Type (Detailed List, Compact List, Grid 2, Grid 3, Large Card) ───
    fun getMediaViewType(): MediaViewType {
        val raw = prefs.getString("media_view_type", MediaViewType.DETAILED_LIST.name) ?: MediaViewType.DETAILED_LIST.name
        return runCatching { MediaViewType.valueOf(raw) }.getOrDefault(MediaViewType.DETAILED_LIST)
    }

    fun saveMediaViewType(type: MediaViewType) {
        prefs.edit().putString("media_view_type", type.name).apply()
    }
}

/** Comprehensive sort options for media files (videos, folders, music). */
enum class SortOrder(val displayName: String) {
    DATE("Date Added (Newest)"),
    DATE_ASC("Date Added (Oldest)"),
    NAME("Name (A to Z)"),
    NAME_DESC("Name (Z to A)"),
    SIZE("Size (Largest)"),
    SIZE_ASC("Size (Smallest)"),
    DURATION("Duration (Longest)"),
    DURATION_ASC("Duration (Shortest)");

    companion object {
        fun fromString(name: String?): SortOrder {
            return runCatching { valueOf(name ?: "") }.getOrDefault(DATE)
        }
    }
}

fun List<VideoItem>.sortVideosWithOrder(order: SortOrder): List<VideoItem> {
    return when (order) {
        SortOrder.DATE -> sortedByDescending { it.dateAdded.takeIf { d -> d > 0 } ?: it.id }
        SortOrder.DATE_ASC -> sortedBy { it.dateAdded.takeIf { d -> d > 0 } ?: it.id }
        SortOrder.NAME -> sortedBy { it.title.lowercase() }
        SortOrder.NAME_DESC -> sortedByDescending { it.title.lowercase() }
        SortOrder.SIZE -> sortedByDescending { it.size }
        SortOrder.SIZE_ASC -> sortedBy { it.size }
        SortOrder.DURATION -> sortedByDescending { it.duration }
        SortOrder.DURATION_ASC -> sortedBy { it.duration }
    }
}

fun List<VideoFolder>.sortFoldersWithOrder(order: SortOrder): List<VideoFolder> {
    return when (order) {
        SortOrder.DATE -> sortedByDescending { folder -> folder.videos.maxOfOrNull { it.dateAdded.takeIf { d -> d > 0 } ?: it.id } ?: 0L }
        SortOrder.DATE_ASC -> sortedBy { folder -> folder.videos.minOfOrNull { it.dateAdded.takeIf { d -> d > 0 } ?: it.id } ?: Long.MAX_VALUE }
        SortOrder.NAME -> sortedBy { it.name.lowercase() }
        SortOrder.NAME_DESC -> sortedByDescending { it.name.lowercase() }
        SortOrder.SIZE -> sortedByDescending { folder -> folder.videos.sumOf { it.size } }
        SortOrder.SIZE_ASC -> sortedBy { folder -> folder.videos.sumOf { it.size } }
        SortOrder.DURATION -> sortedByDescending { folder -> folder.videos.sumOf { it.duration } }
        SortOrder.DURATION_ASC -> sortedBy { folder -> folder.videos.sumOf { it.duration } }
    }
}

fun List<MusicItem>.sortMusicWithOrder(order: SortOrder): List<MusicItem> {
    return when (order) {
        SortOrder.DATE -> sortedByDescending { it.id }
        SortOrder.DATE_ASC -> sortedBy { it.id }
        SortOrder.NAME -> sortedBy { it.title.lowercase() }
        SortOrder.NAME_DESC -> sortedByDescending { it.title.lowercase() }
        SortOrder.SIZE -> sortedByDescending { it.size }
        SortOrder.SIZE_ASC -> sortedBy { it.size }
        SortOrder.DURATION -> sortedByDescending { it.duration }
        SortOrder.DURATION_ASC -> sortedBy { it.duration }
    }
}

/** View modes for media files. */
enum class MediaViewType(val displayName: String) {
    DETAILED_LIST("Detailed List"),
    COMPACT_LIST("Compact List"),
    GRID_2("Grid (2 Columns)"),
    GRID_3("Grid (3 Columns)"),
    LARGE_CARD("Large Card / Feed")
}
