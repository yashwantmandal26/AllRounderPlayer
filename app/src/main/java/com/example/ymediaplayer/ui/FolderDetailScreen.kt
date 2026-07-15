package com.example.ymediaplayer.ui

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ymediaplayer.data.VideoFolder
import com.example.ymediaplayer.data.VideoRepository
import com.example.ymediaplayer.theme.LocalAppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderDetailScreen(
    folderId: String,
    folderName: String,
    onBack: () -> Unit,
    onVideoClick: (String) -> Unit
) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    val repository = remember { VideoRepository(context) }
    var folder by remember { mutableStateOf<VideoFolder?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    val infiniteTransition = rememberInfiniteTransition(label = "bg")
    val gradientOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing), RepeatMode.Reverse),
        label = "bg_offset"
    )

    LaunchedEffect(folderId) {
        isLoading = true
        val allFolders = repository.getFoldersWithVideos()
        if (folderId == "recently_added") {
            val recentVideos = allFolders.flatMap { it.videos }.sortedByDescending { it.id }.take(30)
            folder = VideoFolder(id = "recently_added", name = "Recent Added", videos = recentVideos)
        } else {
            folder = allFolders.find { it.id == folderId }
        }
        isLoading = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(c.baseBackground)
        ) {
            Box(modifier = Modifier.fillMaxSize().background(
                Brush.radialGradient(colors = listOf(c.gradientBlob1.copy(alpha = 0.8f), Color.Transparent), center = Offset(gradientOffset, gradientOffset * 1.5f), radius = 1200f)
            ))
            Box(modifier = Modifier.fillMaxSize().background(
                Brush.radialGradient(colors = listOf(c.gradientBlob2.copy(alpha = 0.9f), Color.Transparent), center = Offset(1000f - gradientOffset, 2000f - gradientOffset), radius = 1500f)
            ))
        }

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Column(modifier = Modifier.fillMaxWidth().background(c.topBarScrim).border(0.5.dp, c.glassBorder)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.size(40.dp).clip(CircleShape).background(c.glassBg).border(0.5.dp, c.glassBorder, CircleShape)
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = c.textPrimary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(16.dp))
                        Text(folderName, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = c.textPrimary, modifier = Modifier.weight(1f))
                        IconButton(onClick = { /* Search in folder */ }) {
                            Icon(Icons.Filled.Search, contentDescription = "Search", tint = c.textPrimary)
                        }
                    }
                }
            }
        ) { paddingValues ->
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = c.accentBlue, strokeWidth = 3.dp)
                }
            } else {
                val videos = folder?.videos ?: emptyList()
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    item {
                        Text("${videos.size} VIDEOS", color = c.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp))
                    }
                    itemsIndexed(videos, key = { _, v -> v.id }) { _, video ->
                        VideoListItem(video = video, onClick = { onVideoClick(video.uri.toString()) }, onMoreClick = {
                            Toast.makeText(context, "Options for ${video.title}", Toast.LENGTH_SHORT).show()
                        })
                    }
                }
            }
        }
    }
}
