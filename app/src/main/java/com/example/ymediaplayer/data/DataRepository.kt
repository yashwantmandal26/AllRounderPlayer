package com.example.ymediaplayer.data

import android.content.Context
import android.content.SharedPreferences

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("ymedia_prefs", Context.MODE_PRIVATE)

    // History: Map of Video URI to Last Played Position (in milliseconds)
    fun saveVideoProgress(uri: String, position: Long) {
        prefs.edit().putLong("progress_$uri", position).apply()
    }

    fun getVideoProgress(uri: String): Long {
        return prefs.getLong("progress_$uri", 0L)
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
}

/** Sort options for video/folder lists. */
enum class SortOrder { NAME, DATE, SIZE }
