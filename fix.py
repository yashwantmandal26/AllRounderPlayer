import re

with open(r'c:\Users\Yashwant\Desktop\YMedia Player\app\src\main\java\com\example\ymediaplayer\ui\VideoPlayerScreen.kt', 'r', encoding='utf-8') as f:
    content = f.read()

replacement = '''// -------------------------------------------------------------------------------
// NEW COMPONENTS
// -------------------------------------------------------------------------------
@Composable
private fun PlayerTopBar(title: String, onBack: () -> Unit, onSettings: () -> Unit, onAudioSelect: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = PlayerWhite, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(text = title.take(40), color = PlayerWhite, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = {  }) { Icon(Icons.Rounded.Cast, contentDescription = "Cast", tint = PlayerWhite) }
            IconButton(onClick = {  }) { Icon(Icons.Rounded.PictureInPictureAlt, contentDescription = "PiP", tint = PlayerWhite) }
            IconButton(onClick = onAudioSelect) { Icon(Icons.Rounded.ClosedCaption, contentDescription = "CC", tint = PlayerWhite) }
            IconButton(onClick = {  }) { Icon(Icons.Rounded.PlaylistPlay, contentDescription = "Playlist", tint = PlayerWhite) }
            IconButton(onClick = onSettings) { Icon(Icons.Rounded.MoreVert, contentDescription = "Settings", tint = PlayerWhite) }
        }
    }
}

@Composable
private fun PlayerSecondaryBar(isMuted: Boolean, onMuteToggle: () -> Unit, isLandscape: Boolean, onRotate: () -> Unit, playbackSpeed: Float, onSpeedCycle: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        PlayerGlassButton(size = 40.dp, iconSize = 20.dp, onClick = onRotate) {
            Icon(Icons.Rounded.ScreenRotation, contentDescription = "Rotate", tint = if (isLandscape) PlayerAccentBlue else PlayerWhite)
        }
        PlayerGlassButton(size = 40.dp, iconSize = 20.dp, onClick = onMuteToggle) {
            Icon(if (isMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = "Mute", tint = PlayerWhite)
        }
        PlayerGlassButton(size = 40.dp, iconSize = 20.dp, onClick = {  }) {
            Icon(Icons.Rounded.Headset, contentDescription = "Audio Mode", tint = PlayerWhite)
        }
        PlayerGlassButton(size = 40.dp, iconSize = 16.dp, onClick = onSpeedCycle) {
            Text(text = "X", color = PlayerWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        PlayerGlassButton(size = 40.dp, iconSize = 20.dp, onClick = {  }) {
            Icon(Icons.Rounded.ChevronRight, contentDescription = "Next", tint = PlayerWhite)
        }
    }
}

@Composable
private fun ContinueWatchingBanner(onDismiss: () -> Unit, onStartOver: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = "Close", tint = PlayerWhite, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text("Continue playing from where you stopped.", color = PlayerWhite, fontSize = 14.sp)
        }
        Text(
            text = "|  Start Over", 
            color = Color(0xFF4CAF50), 
            fontSize = 14.sp, 
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable { onStartOver() }.padding(8.dp)
        )
    }
}

@Composable
private fun PlayerBottomBar(
    currentPosition: Long, duration: Long, isPlaying: Boolean, videoUrl: String,
    onSeek: (Long) -> Unit, onPlayPause: () -> Unit, onRewind: () -> Unit, onForward: () -> Unit,
    onLock: () -> Unit, onResize: () -> Unit, modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(text = formatTime(currentPosition), color = PlayerWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(12.dp))
            PlayerSeekBar(currentPosition = currentPosition, duration = duration, onSeek = onSeek, videoUrl = videoUrl, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Text(text = formatTime(duration), color = PlayerWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onLock) { Icon(Icons.Rounded.LockOpen, contentDescription = "Lock", tint = PlayerWhite) }
            
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onRewind) { Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", tint = PlayerWhite, modifier = Modifier.size(32.dp)) }
                
                Box(
                    modifier = Modifier.size(56.dp).clip(CircleShape).border(1.dp, PlayerWhite, CircleShape).clickable(onClick = onPlayPause),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = "Play/Pause", tint = PlayerWhite, modifier = Modifier.size(32.dp))
                }
                
                IconButton(onClick = onForward) { Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = PlayerWhite, modifier = Modifier.size(32.dp)) }
            }
            
            IconButton(onClick = onResize) { Icon(Icons.Rounded.AspectRatio, contentDescription = "Resize", tint = PlayerWhite) }
        }
    }
}
'''

pattern = re.compile(r'// -------------------------------------------------------------------------------\n// TOP BAR.*?// -------------------------------------------------------------------------------\n// SEEK BAR WITH PREVIEW', re.DOTALL)
new_content = pattern.sub(replacement + '\n// -------------------------------------------------------------------------------\n// SEEK BAR WITH PREVIEW', content)

with open(r'c:\Users\Yashwant\Desktop\YMedia Player\app\src\main\java\com\example\ymediaplayer\ui\VideoPlayerScreen.kt', 'w', encoding='utf-8') as f:
    f.write(new_content)

print("Replacement done via python.")
