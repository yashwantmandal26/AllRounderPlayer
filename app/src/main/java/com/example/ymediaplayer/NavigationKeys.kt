package com.example.ymediaplayer

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object FolderList : NavKey
@Serializable data class FolderDetail(val folderId: String, val folderName: String) : NavKey
@Serializable data class VideoPlayer(val videoUri: String) : NavKey
