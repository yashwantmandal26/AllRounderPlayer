package com.example.ymediaplayer.data

import android.content.Context
import android.content.SharedPreferences

class AppPreferences(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences("ymedia_prefs", Context.MODE_PRIVATE))

    companion object {
        private val progressCache = java.util.concurrent.ConcurrentHashMap<String, Long>()
        private val completedCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

        fun clearCache() {
            progressCache.clear()
            completedCache.clear()
        }
    }

    // Reactive Compose state: bumps on any progress/completion changes so UI recomposes instantly
    val lastProgressUpdate = androidx.compose.runtime.mutableLongStateOf(0L)

    // History: Map of Video URI to Last Played Position (in milliseconds)
    fun saveVideoProgress(uri: String, position: Long, duration: Long = 0L) {
        val isDone = duration > 0L && position >= (duration - 3000L)
        completedCache[uri] = isDone
        val effectivePosition = if (isDone) 0L else position
        progressCache[uri] = effectivePosition

        val editor = prefs.edit()
            .putLong("progress_$uri", effectivePosition)
            .putLong("timestamp_$uri", System.currentTimeMillis())
            .putBoolean("completed_$uri", isDone)
        if (duration > 0L) {
            editor.putLong("duration_$uri", duration)
        }
        editor.apply()

        if (isDone) {
            val currentList = getPlayedUris().toMutableList()
            currentList.remove(uri)
            prefs.edit().putString("played_uris", currentList.joinToString(";;;")).apply()
        } else {
            recordPlayedUri(uri)
        }
        lastProgressUpdate.longValue = System.currentTimeMillis()
    }

    fun isCompleted(uri: String): Boolean {
        return completedCache.getOrPut(uri) {
            prefs.getBoolean("completed_$uri", false)
        }
    }

    fun markCompleted(uri: String, completed: Boolean) {
        completedCache[uri] = completed
        if (completed) progressCache[uri] = 0L
        prefs.edit()
            .putBoolean("completed_$uri", completed)
            .putLong("progress_$uri", 0L)
            .apply()
        if (completed) {
            val currentList = getPlayedUris().toMutableList()
            currentList.remove(uri)
            prefs.edit().putString("played_uris", currentList.joinToString(";;;")).apply()
        }
        lastProgressUpdate.longValue = System.currentTimeMillis()
    }

    fun recordPlayback(uri: String, position: Long, duration: Long) {
        saveVideoProgress(uri, position, duration)
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

    fun isAmbientModeEnabled(): Boolean = isAmbientGlowEnabled()

    fun setAmbientModeEnabled(enabled: Boolean) = setAmbientGlowEnabled(enabled)

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
        progressCache.remove(uri)
        completedCache.remove(uri)
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
        lastProgressUpdate.longValue = System.currentTimeMillis()
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

    // ─── Playback & Engine Preferences ─────────────────────────────────────────
    fun getResumeMode(): String = prefs.getString("resume_mode", "AUTO") ?: "AUTO"
    fun setResumeMode(mode: String) { prefs.edit().putString("resume_mode", mode).apply() }

    fun isRememberPlaybackSpeed(): Boolean = prefs.getBoolean("remember_playback_speed", true)
    fun setRememberPlaybackSpeed(remember: Boolean) { prefs.edit().putBoolean("remember_playback_speed", remember).apply() }

    fun getLastPlaybackSpeed(): Float = prefs.getFloat("last_playback_speed", 1.0f)
    fun setLastPlaybackSpeed(speed: Float) { prefs.edit().putFloat("last_playback_speed", speed).apply() }

    fun getDoubleTapSeekSeconds(): Int = prefs.getInt("double_tap_seek_seconds", 10)
    fun setDoubleTapSeekSeconds(sec: Int) { prefs.edit().putInt("double_tap_seek_seconds", sec).apply() }

    fun isBackgroundPlayEnabled(): Boolean = prefs.getBoolean("background_play_enabled", false)
    fun setBackgroundPlayEnabled(enabled: Boolean) { prefs.edit().putBoolean("background_play_enabled", enabled).apply() }

    fun isAutoPipEnabled(): Boolean = prefs.getBoolean("auto_pip_enabled", true)
    fun setAutoPipEnabled(enabled: Boolean) { prefs.edit().putBoolean("auto_pip_enabled", enabled).apply() }

    fun isAutoPlayNextEnabled(): Boolean = prefs.getBoolean("auto_play_next_in_playlist", true)
    fun setAutoPlayNextEnabled(enabled: Boolean) { prefs.edit().putBoolean("auto_play_next_in_playlist", enabled).apply() }

    fun isRememberPlaylistQueue(): Boolean = prefs.getBoolean("remember_playlist_queue", true)
    fun setRememberPlaylistQueue(enabled: Boolean) { prefs.edit().putBoolean("remember_playlist_queue", enabled).apply() }

    fun getControlsAutoHideTimeoutMs(): Long = prefs.getLong("controls_auto_hide_timeout_ms", 3500L)
    fun setControlsAutoHideTimeoutMs(ms: Long) { prefs.edit().putLong("controls_auto_hide_timeout_ms", ms).apply() }

    fun getDefaultResizeMode(): Int = prefs.getInt("default_resize_mode", 0) // 0: FIT
    fun setDefaultResizeMode(mode: Int) { prefs.edit().putInt("default_resize_mode", mode).apply() }

    fun getPlayerRepeatMode(): Int = prefs.getInt("player_repeat_mode", 0) // 0: Player.REPEAT_MODE_OFF
    fun setPlayerRepeatMode(mode: Int) { prefs.edit().putInt("player_repeat_mode", mode).apply() }

    fun isNightMode(): Boolean = prefs.getBoolean("player_night_mode", false)
    fun setNightMode(enabled: Boolean) { prefs.edit().putBoolean("player_night_mode", enabled).apply() }

    fun isPlayerMuted(): Boolean = prefs.getBoolean("player_muted", false)
    fun setPlayerMuted(muted: Boolean) { prefs.edit().putBoolean("player_muted", muted).apply() }

    fun getLastPlayerBrightness(): Float = prefs.getFloat("last_player_brightness", -1f)
    fun setLastPlayerBrightness(brightness: Float) { prefs.edit().putFloat("last_player_brightness", brightness).apply() }

    fun isSubtitlesEnabled(): Boolean = prefs.getBoolean("player_subtitles_enabled", true)
    fun setSubtitlesEnabled(enabled: Boolean) { prefs.edit().putBoolean("player_subtitles_enabled", enabled).apply() }

    fun getPreferredSubtitleLanguage(): String = prefs.getString("preferred_subtitle_language", "") ?: ""
    fun setPreferredSubtitleLanguage(lang: String) { prefs.edit().putString("preferred_subtitle_language", lang).apply() }

    fun isHwAccelerationEnabled(): Boolean = prefs.getBoolean("hw_acceleration_enabled", true)
    fun setHwAccelerationEnabled(enabled: Boolean) { prefs.edit().putBoolean("hw_acceleration_enabled", enabled).apply() }

    fun isKeepScreenAwake(): Boolean = prefs.getBoolean("keep_screen_awake", true)
    fun setKeepScreenAwake(awake: Boolean) { prefs.edit().putBoolean("keep_screen_awake", awake).apply() }

    // ─── Gestures & Controls Preferences ───────────────────────────────────────
    fun isBrightnessGestureEnabled(): Boolean = prefs.getBoolean("gesture_brightness_enabled", true)
    fun setBrightnessGestureEnabled(enabled: Boolean) { prefs.edit().putBoolean("gesture_brightness_enabled", enabled).apply() }

    fun isVolumeGestureEnabled(): Boolean = prefs.getBoolean("gesture_volume_enabled", true)
    fun setVolumeGestureEnabled(enabled: Boolean) { prefs.edit().putBoolean("gesture_volume_enabled", enabled).apply() }

    fun isSeekGestureEnabled(): Boolean = prefs.getBoolean("gesture_seek_enabled", false)
    fun setSeekGestureEnabled(enabled: Boolean) { prefs.edit().putBoolean("gesture_seek_enabled", enabled).apply() }

    fun isVideoSwitchGestureEnabled(): Boolean = prefs.getBoolean("gesture_video_switch_enabled", true)
    fun setVideoSwitchGestureEnabled(enabled: Boolean) { prefs.edit().putBoolean("gesture_video_switch_enabled", enabled).apply() }

    fun isDoubleTapCenterPlayPauseEnabled(): Boolean = prefs.getBoolean("double_tap_center_play_pause", true)
    fun setDoubleTapCenterPlayPauseEnabled(enabled: Boolean) { prefs.edit().putBoolean("double_tap_center_play_pause", enabled).apply() }

    fun getPressHoldSpeed(): Float = prefs.getFloat("press_hold_speed", 2.0f)
    fun setPressHoldSpeed(speed: Float) { prefs.edit().putFloat("press_hold_speed", speed).apply() }

    fun isHapticsEnabled(): Boolean = prefs.getBoolean("haptics_enabled", true)
    fun setHapticsEnabled(enabled: Boolean) { prefs.edit().putBoolean("haptics_enabled", enabled).apply() }

    // ─── Subtitles & Audio Preferences ─────────────────────────────────────────
    fun getSubtitleFontSize(): Int = prefs.getInt("sub_font_size", 18)
    fun setSubtitleFontSize(size: Int) { prefs.edit().putInt("sub_font_size", size).apply() }

    fun getSubtitleColor(): Long = prefs.getLong("sub_color", 0xFFFFFFFFL)
    fun setSubtitleColor(color: Long) { prefs.edit().putLong("sub_color", color).apply() }

    fun getSubtitleBackgroundStyle(): Int = prefs.getInt("sub_bg_style", 1) // 0: None, 1: Semi-transparent, 2: Solid black
    fun setSubtitleBackgroundStyle(style: Int) { prefs.edit().putInt("sub_bg_style", style).apply() }

    fun getSubtitleOutlineStyle(): Int = prefs.getInt("sub_outline_style", 2) // 0: None, 1: Shadow, 2: Bold outline
    fun setSubtitleOutlineStyle(style: Int) { prefs.edit().putInt("sub_outline_style", style).apply() }

    fun isPauseOnHeadsetDisconnect(): Boolean = prefs.getBoolean("pause_on_headset_disconnect", true)
    fun setPauseOnHeadsetDisconnect(enabled: Boolean) { prefs.edit().putBoolean("pause_on_headset_disconnect", enabled).apply() }

    fun getPreferredAudioLanguage(): String = prefs.getString("preferred_audio_language", "DEFAULT") ?: "DEFAULT"
    fun setPreferredAudioLanguage(lang: String) { prefs.edit().putString("preferred_audio_language", lang).apply() }

    // ─── Library & Folders Preferences ─────────────────────────────────────────
    fun isShowContinueWatching(): Boolean = prefs.getBoolean("show_continue_watching", true)
    fun setShowContinueWatching(show: Boolean) {
        prefs.edit().putBoolean("show_continue_watching", show).apply()
        lastProgressUpdate.longValue = System.currentTimeMillis()
    }

    fun getContinueWatchingLimit(): Int = prefs.getInt("continue_watching_limit", 20)
    fun setContinueWatchingLimit(limit: Int) {
        prefs.edit().putInt("continue_watching_limit", limit).apply()
        lastProgressUpdate.longValue = System.currentTimeMillis()
    }

    fun isShowRecentlyAdded(): Boolean = prefs.getBoolean("show_recently_added", true)
    fun setShowRecentlyAdded(show: Boolean) {
        prefs.edit().putBoolean("show_recently_added", show).apply()
        lastProgressUpdate.longValue = System.currentTimeMillis()
    }

    fun getExcludeShortClipsSeconds(): Int = prefs.getInt("exclude_short_clips_seconds", 0)
    fun setExcludeShortClipsSeconds(sec: Int) {
        prefs.edit().putInt("exclude_short_clips_seconds", sec).apply()
        lastProgressUpdate.longValue = System.currentTimeMillis()
    }

    fun isExcludeHiddenFolders(): Boolean = prefs.getBoolean("exclude_hidden_folders", true)
    fun setExcludeHiddenFolders(enabled: Boolean) {
        prefs.edit().putBoolean("exclude_hidden_folders", enabled).apply()
        lastProgressUpdate.longValue = System.currentTimeMillis()
    }

    fun isConfirmDeleteEnabled(): Boolean = prefs.getBoolean("confirm_delete_enabled", true)
    fun setConfirmDeleteEnabled(enabled: Boolean) { prefs.edit().putBoolean("confirm_delete_enabled", enabled).apply() }

    // ─── Music Preferences ─────────────────────────────────────────────────────
    fun isGaplessPlaybackEnabled(): Boolean = prefs.getBoolean("gapless_playback_enabled", true)
    fun setGaplessPlaybackEnabled(enabled: Boolean) { prefs.edit().putBoolean("gapless_playback_enabled", enabled).apply() }

    // ─── Maintenance & Clean-up ────────────────────────────────────────────────
    fun clearContinueWatchingHistory() {
        val played = getPlayedUris()
        val editor = prefs.edit()
        for (uri in played) {
            editor.remove("progress_$uri")
            editor.remove("duration_$uri")
            editor.remove("timestamp_$uri")
            editor.remove("completed_$uri")
        }
        editor.remove("played_uris")
        editor.apply()
        clearCache()
        lastProgressUpdate.longValue = System.currentTimeMillis()
    }

    fun clearAllBookmarks() {
        val allKeys = prefs.all.keys.filter { it.startsWith("bookmarks_") }
        val editor = prefs.edit()
        allKeys.forEach { editor.remove(it) }
        editor.apply()
    }

    fun resetAllSettingsToDefaults() {
        prefs.edit()
            .remove("resume_mode")
            .remove("remember_playback_speed")
            .remove("last_playback_speed")
            .remove("double_tap_seek_seconds")
            .remove("background_play_enabled")
            .remove("auto_pip_enabled")
            .remove("auto_play_next_in_playlist")
            .remove("remember_playlist_queue")
            .remove("controls_auto_hide_timeout_ms")
            .remove("default_resize_mode")
            .remove("player_repeat_mode")
            .remove("player_night_mode")
            .remove("player_muted")
            .remove("last_player_brightness")
            .remove("player_subtitles_enabled")
            .remove("preferred_subtitle_language")
            .remove("hw_acceleration_enabled")
            .remove("keep_screen_awake")
            .remove("gesture_brightness_enabled")
            .remove("gesture_volume_enabled")
            .remove("gesture_seek_enabled")
            .remove("gesture_video_switch_enabled")
            .remove("double_tap_center_play_pause")
            .remove("press_hold_speed")
            .remove("haptics_enabled")
            .remove("sub_font_size")
            .remove("sub_color")
            .remove("sub_bg_style")
            .remove("sub_outline_style")
            .remove("pause_on_headset_disconnect")
            .remove("preferred_audio_language")
            .remove("show_continue_watching")
            .remove("continue_watching_limit")
            .remove("show_recently_added")
            .remove("exclude_short_clips_seconds")
            .remove("exclude_hidden_folders")
            .remove("confirm_delete_enabled")
            .remove("gapless_playback_enabled")
            .remove("signature_view_enabled")
            .remove("signature_saturation")
            .remove("signature_sharpness")
            .remove("ambient_glow_enabled")
            .remove("media_view_type")
            .remove("sort_order")
            .apply()
        lastProgressUpdate.longValue = System.currentTimeMillis()
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
