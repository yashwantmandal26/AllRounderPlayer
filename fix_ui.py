import re

with open(r'c:\Users\Yashwant\Desktop\YMedia Player\app\src\main\java\com\example\ymediaplayer\ui\VideoPlayerScreen.kt', 'r', encoding='utf-8') as f:
    content = f.read()

replacement = '''                if (!isLocked) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 24.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Top Section (Top Bar + Secondary Row)
                        Column {
                            // Top Bar
                            PlayerTopBar(
                                title = videoUrl.substringAfterLast("/").substringBeforeLast("."),
                                onBack = onBack,
                                onSettings = { showSettings = true },
                                onAudioSelect = { showAudioTracks = true }
                            )
                            Spacer(Modifier.height(16.dp))
                            // Secondary Row
                            PlayerSecondaryBar(
                                isMuted = isMuted,
                                onMuteToggle = {
                                    isMuted = !isMuted
                                    exoPlayer.volume = if (isMuted) 0f else 1f
                                },
                                isLandscape = isLandscape,
                                onRotate = {
                                    isLandscape = !isLandscape
                                    activity?.requestedOrientation =
                                        if (isLandscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                        else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                },
                                playbackSpeed = playbackSpeed,
                                onSpeedCycle = {
                                    playbackSpeed = when (playbackSpeed) {
                                        0.5f -> 1.0f
                                        1.0f -> 1.5f
                                        1.5f -> 2.0f
                                        else -> 0.5f
                                    }
                                    exoPlayer.playbackParameters = androidx.media3.common.PlaybackParameters(playbackSpeed)
                                }
                            )
                        }

                        // Middle Section (Floating Camera Snapshot on right)
                        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            PlayerGlassButton(
                                size = 48.dp, 
                                iconSize = 24.dp, 
                                onClick = {
                                    takeScreenshot(context, coroutineScope, videoUrl, exoPlayer.currentPosition)
                                },
                                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 16.dp, end = 16.dp)
                            ) {
                                Icon(Icons.Rounded.PhotoCamera, contentDescription = "Snapshot", tint = PlayerWhite)
                            }
                        }

                        // Bottom Section
                        Column {
                            // Continue Watching Banner
                            if (showContinueBanner) {
                                ContinueWatchingBanner(
                                    onDismiss = { showContinueBanner = false },
                                    onStartOver = { 
                                        exoPlayer.seekTo(0)
                                        showContinueBanner = false
                                    }
                                )
                                Spacer(Modifier.height(16.dp))
                            }

                            // Bottom Controls (Seekbar + Main Controls)
                            PlayerBottomBar(
                                currentPosition = currentPosition,
                                duration = duration,
                                isPlaying = isPlaying,
                                videoUrl = videoUrl,
                                onSeek = { exoPlayer.seekTo(it) },
                                onPlayPause = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() },
                                onRewind = { exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0)) },
                                onForward = { exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration)) },
                                onLock = { isLocked = true },
                                onResize = {
                                    resizeMode = when (resizeMode) {
                                        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                                        else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    }
                                }
                            )
                        }
                    }
                }'''

pattern = re.compile(r'                if \(\!isLocked\) \{.*?                \}', re.DOTALL)
# We only want to replace the FIRST match which is the main UI overlay, not the if (!isLocked) inside Settings or elsewhere.
new_content = pattern.sub(replacement, content, count=1)

with open(r'c:\Users\Yashwant\Desktop\YMedia Player\app\src\main\java\com\example\ymediaplayer\ui\VideoPlayerScreen.kt', 'w', encoding='utf-8') as f:
    f.write(new_content)

print("UI Overlay replaced via python.")
