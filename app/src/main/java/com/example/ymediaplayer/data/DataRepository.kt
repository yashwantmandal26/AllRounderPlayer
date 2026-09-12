package com.example.ymediaplayer.data

import android.content.Context
import android.content.SharedPreferences

class AppPreferences(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences("ymedia_prefs", Context.MODE_PRIVATE))

    companion object {
        private val progressCache = java.util.concurrent.ConcurrentHashMap<String, Long>()
        private val completedCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    }

    // History: Map of Video URI to Last Played Position (in milliseconds)
    fun saveVideoProgress(uri: String, position: Long) {
        progressCache[uri] = position
        prefs.edit().putLong("progress_$uri", position).apply()
        recordPlayedUri(uri)
    }

    fun isCompleted(uri: String): Boolean {
        return completedCache.getOrPut(uri) {
            prefs.getBoolean("completed_$uri", false)
        }
    }

    fun markCompleted(uri: String, completed: Boolean) {
        completedCache[uri] = completed
        if (completed) progressCache[uri] = 0L
        prefs.edit().putBoolean("completed_$uri", completed).apply()
    }

    fun recordPlayback(uri: String, position: Long, duration: Long) {
        val isDone = duration > 0L && position >= (duration - 3000L)
        completedCache[uri] = isDone
        if (isDone) progressCache[uri] = 0L else progressCache[uri] = position
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
        return progressCache.getOrPut(uri) {
            prefs.getLong("progress_$uri", 0L)
        }
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

    // ─── Equalizer & Audio Effects Persistence ────────────────────────────
    fun saveEqSettings(preset: String, bands: List<Float>, bassBoost: Float, virt: Float, enabled: Boolean = true) {
        prefs.edit()
            .putString("eq_preset", preset)
            .putString("eq_bands", bands.joinToString(","))
            .putFloat("eq_bass_boost", bassBoost)
            .putFloat("eq_virtualizer", virt)
            .putBoolean("eq_enabled", enabled)
            .apply()
    }

    fun getEqPreset(): String = prefs.getString("eq_preset", "Flat") ?: "Flat"

    fun getEqBands(): List<Float> {
        val raw = prefs.getString("eq_bands", null) ?: return listOf(0f, 0f, 0f, 0f, 0f)
        return try {
            raw.split(",").map { it.toFloat() }
        } catch (_: Exception) {
            listOf(0f, 0f, 0f, 0f, 0f)
        }
    }

    fun getEqBassBoost(): Float = prefs.getFloat("eq_bass_boost", 0.4f)

    fun getEqVirtualizer(): Float = prefs.getFloat("eq_virtualizer", 0.25f)

    fun isEqEnabled(): Boolean = prefs.getBoolean("eq_enabled", true)

    // ─── Custom Playlists Persistence ──────────────────────────────────────
    fun getPlaylists(): Map<String, List<String>> {
        val raw = prefs.getString("custom_playlists_json", null) ?: return emptyMap()
        val result = mutableMapOf<String, List<String>>()
        try {
            val json = org.json.JSONObject(raw)
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val array = json.getJSONArray(key)
                val list = mutableListOf<String>()
                for (i in 0 until array.length()) {
                    list.add(array.getString(i))
                }
                result[key] = list
            }
        } catch (_: Exception) {}
        return result
    }

    fun savePlaylists(map: Map<String, List<String>>) {
        try {
            val json = org.json.JSONObject()
            map.forEach { (name, songs) ->
                val array = org.json.JSONArray()
                songs.forEach { array.put(it) }
                json.put(name, array)
            }
            prefs.edit().putString("custom_playlists_json", json.toString()).apply()
        } catch (_: Exception) {}
    }

    fun createPlaylist(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false
        val current = getPlaylists().toMutableMap()
        if (current.containsKey(trimmed)) return false
        current[trimmed] = emptyList()
        savePlaylists(current)
        return true
    }

    fun deletePlaylist(name: String) {
        val current = getPlaylists().toMutableMap()
        current.remove(name)
        savePlaylists(current)
    }

    fun addSongToPlaylist(name: String, songUri: String) {
        val current = getPlaylists().toMutableMap()
        val list = current[name]?.toMutableList() ?: mutableListOf()
        if (!list.contains(songUri)) {
            list.add(songUri)
            current[name] = list
            savePlaylists(current)
        }
    }

    fun removeSongFromPlaylist(name: String, songUri: String) {
        val current = getPlaylists().toMutableMap()
        val list = current[name]?.toMutableList() ?: return
        if (list.remove(songUri)) {
            current[name] = list
            savePlaylists(current)
        }
    }

    // ─── Play Count & Most Played Tracking ────────────────────────────────
    fun incrementPlayCount(songUri: String) {
        val current = prefs.getInt("play_count_$songUri", 0)
        prefs.edit().putInt("play_count_$songUri", current + 1).apply()

        // Track tracked URIs
        val allTracked = prefs.getStringSet("played_songs_uris", emptySet())?.toMutableSet() ?: mutableSetOf()
        allTracked.add(songUri)
        prefs.edit().putStringSet("played_songs_uris", allTracked).apply()
    }

    fun getPlayCount(songUri: String): Int {
        return prefs.getInt("play_count_$songUri", 0)
    }

    fun getTopPlayedUris(limit: Int = 10): List<String> {
        val allTracked = prefs.getStringSet("played_songs_uris", emptySet()) ?: return emptyList()
        return allTracked.map { uri -> uri to getPlayCount(uri) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    // ─── Video Bookmarks Persistence ──────────────────────────────────────
    fun getBookmarks(videoUri: String): List<Pair<Long, String>> {
        val raw = prefs.getString("bookmarks_$videoUri", null) ?: return emptyList()
        val result = mutableListOf<Pair<Long, String>>()
        try {
            val array = org.json.JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                result.add(obj.getLong("pos") to obj.getString("label"))
            }
        } catch (_: Exception) {}
        return result.sortedBy { it.first }
    }

    fun saveBookmark(videoUri: String, positionMs: Long, label: String) {
        val current = getBookmarks(videoUri).toMutableList()
        current.removeAll { kotlin.math.abs(it.first - positionMs) < 1000L } // Remove duplicate within 1 sec
        current.add(positionMs to label.ifBlank { "Bookmark at ${formatBookmarkTime(positionMs)}" })
        try {
            val array = org.json.JSONArray()
            current.forEach {
                val obj = org.json.JSONObject()
                obj.put("pos", it.first)
                obj.put("label", it.second)
                array.put(obj)
            }
            prefs.edit().putString("bookmarks_$videoUri", array.toString()).apply()
        } catch (_: Exception) {}
    }

    fun deleteBookmark(videoUri: String, positionMs: Long) {
        val current = getBookmarks(videoUri).toMutableList()
        current.removeAll { it.first == positionMs }
        try {
            val array = org.json.JSONArray()
            current.forEach {
                val obj = org.json.JSONObject()
                obj.put("pos", it.first)
                obj.put("label", it.second)
                array.put(obj)
            }
            prefs.edit().putString("bookmarks_$videoUri", array.toString()).apply()
        } catch (_: Exception) {}
    }

    private fun formatBookmarkTime(ms: Long): String {
        val s = ms / 1000
        val m = s / 60
        val sec = s % 60
        return String.format(java.util.Locale.getDefault(), "%02d:%02d", m, sec)
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
        SortOrder.DATE -> {
            val dateMap = associateWith { folder -> folder.videos.maxOfOrNull { it.dateAdded.takeIf { d -> d > 0 } ?: it.id } ?: 0L }
            sortedByDescending { dateMap[it] ?: 0L }
        }
        SortOrder.DATE_ASC -> {
            val dateMap = associateWith { folder -> folder.videos.minOfOrNull { it.dateAdded.takeIf { d -> d > 0 } ?: it.id } ?: Long.MAX_VALUE }
            sortedBy { dateMap[it] ?: Long.MAX_VALUE }
        }
        SortOrder.NAME -> sortedBy { it.name.lowercase() }
        SortOrder.NAME_DESC -> sortedByDescending { it.name.lowercase() }
        SortOrder.SIZE -> {
            val sizeMap = associateWith { folder -> folder.videos.sumOf { it.size } }
            sortedByDescending { sizeMap[it] ?: 0L }
        }
        SortOrder.SIZE_ASC -> {
            val sizeMap = associateWith { folder -> folder.videos.sumOf { it.size } }
            sortedBy { sizeMap[it] ?: 0L }
        }
        SortOrder.DURATION -> {
            val durMap = associateWith { folder -> folder.videos.sumOf { it.duration } }
            sortedByDescending { durMap[it] ?: 0L }
        }
        SortOrder.DURATION_ASC -> {
            val durMap = associateWith { folder -> folder.videos.sumOf { it.duration } }
            sortedBy { durMap[it] ?: 0L }
        }
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
