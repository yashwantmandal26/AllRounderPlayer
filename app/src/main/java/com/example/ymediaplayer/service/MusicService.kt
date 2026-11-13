@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.ymediaplayer.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp3.Mp3Extractor
import androidx.media3.extractor.ts.AdtsExtractor
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer as VlcMediaPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import com.example.ymediaplayer.util.MediaArtworkHelper
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import com.example.ymediaplayer.ui.MusicEqPreset
import com.example.ymediaplayer.ui.MusicSoundMode
import com.example.ymediaplayer.MainActivity
import com.example.ymediaplayer.R
import com.example.ymediaplayer.data.AppPreferences
import com.example.ymediaplayer.data.MusicItem
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class MusicService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler by lazy { Handler(android.os.Looper.getMainLooper()) }
    private var audioRecoveryAttempt = 0
    private var audioRecoveryJob: Job? = null
    private var lastReadyAtMs = 0L

    private var vlcLib: LibVLC? = null
    private var vlcAudioPlayer: VlcMediaPlayer? = null
    private var vlcMedia: Media? = null
    private var vlcPfd: ParcelFileDescriptor? = null

    companion object {
        const val EXTRA_OPEN_MUSIC_PLAYER = "EXTRA_OPEN_MUSIC_PLAYER"
        const val MUSIC_NOTIFICATION_CHANNEL_ID = "music_playback_channel"
        const val MUSIC_NOTIFICATION_ID = 1001

        const val CUSTOM_ACTION_FAVORITE = "com.example.ymediaplayer.ACTION_FAVORITE"
        const val CUSTOM_ACTION_REWIND = "com.example.ymediaplayer.ACTION_REWIND_10"
        const val CUSTOM_ACTION_FORWARD = "com.example.ymediaplayer.ACTION_FORWARD_10"
        const val CUSTOM_ACTION_STOP = "com.example.ymediaplayer.ACTION_STOP_PLAYBACK"

        const val ACTION_PLAY_PAUSE = "com.example.ymediaplayer.ACTION_PLAY_PAUSE"
        const val ACTION_PREV = "com.example.ymediaplayer.ACTION_PREV"
        const val ACTION_NEXT = "com.example.ymediaplayer.ACTION_NEXT"
        const val ACTION_STOP = "com.example.ymediaplayer.ACTION_STOP"

        var currentAudioSessionId: androidx.compose.runtime.MutableState<Int> =
            androidx.compose.runtime.mutableIntStateOf(0)

        var currentSoundMode: androidx.compose.runtime.MutableState<MusicSoundMode> =
            androidx.compose.runtime.mutableStateOf(MusicSoundMode.BALANCED)

        var isEqEnabled: androidx.compose.runtime.MutableState<Boolean> =
            androidx.compose.runtime.mutableStateOf(true)

        var currentEqBands: androidx.compose.runtime.MutableState<List<Float>> =
            androidx.compose.runtime.mutableStateOf(listOf(0f, 0f, 0f, 0f, 0f))

        var currentBassBoost: androidx.compose.runtime.MutableFloatState =
            androidx.compose.runtime.mutableFloatStateOf(0f)

        var currentVirtualizer: androidx.compose.runtime.MutableFloatState =
            androidx.compose.runtime.mutableFloatStateOf(0f)

        var currentPresetName: androidx.compose.runtime.MutableState<String> =
            androidx.compose.runtime.mutableStateOf("Flat")

        fun initPreferencesState(context: Context) {
            val prefs = AppPreferences(context)
            isEqEnabled.value = prefs.isEqEnabled()
            currentSoundMode.value = MusicSoundMode.fromId(prefs.getMusicSoundMode())
            currentEqBands.value = prefs.getEqBands()
            currentBassBoost.floatValue = prefs.getEqBassBoost()
            currentVirtualizer.floatValue = prefs.getEqVirtualizer()
            currentPresetName.value = prefs.getEqPreset()
            serviceInstance?.applyCurrentEffects()
        }

        fun applySoundMode(context: Context, mode: MusicSoundMode) {
            val prefs = AppPreferences(context)
            currentSoundMode.value = mode
            prefs.setMusicSoundMode(mode.id)
            isEqEnabled.value = true
            currentEqBands.value = mode.bands
            currentBassBoost.floatValue = mode.bassBoost
            currentVirtualizer.floatValue = mode.virtualizer
            currentPresetName.value = mode.displayName
            prefs.saveEqSettings(
                preset = mode.displayName,
                bands = mode.bands,
                bassBoost = mode.bassBoost,
                virt = mode.virtualizer,
                enabled = true
            )
            serviceInstance?.applyCurrentEffects()
        }

        fun applyPreset(context: Context, preset: MusicEqPreset) {
            val prefs = AppPreferences(context)
            currentPresetName.value = preset.name
            currentEqBands.value = preset.bands
            prefs.saveEqSettings(
                preset = preset.name,
                bands = preset.bands,
                bassBoost = currentBassBoost.floatValue,
                virt = currentVirtualizer.floatValue,
                enabled = isEqEnabled.value
            )
            serviceInstance?.applyCurrentEffects()
        }

        fun applyBandLevel(context: Context, bandIndex: Int, gainDb: Float) {
            val prefs = AppPreferences(context)
            val newBands = currentEqBands.value.toMutableList()
            if (bandIndex in newBands.indices) {
                newBands[bandIndex] = gainDb
            }
            currentEqBands.value = newBands
            currentPresetName.value = "Custom"
            prefs.saveEqSettings(
                preset = "Custom",
                bands = newBands,
                bassBoost = currentBassBoost.floatValue,
                virt = currentVirtualizer.floatValue,
                enabled = isEqEnabled.value
            )
            serviceInstance?.applyCurrentEffects()
        }

        fun applyBassBoost(context: Context, value: Float) {
            val prefs = AppPreferences(context)
            currentBassBoost.floatValue = value
            prefs.saveEqSettings(
                preset = currentPresetName.value,
                bands = currentEqBands.value,
                bassBoost = value,
                virt = currentVirtualizer.floatValue,
                enabled = isEqEnabled.value
            )
            serviceInstance?.applyCurrentEffects()
        }

        fun applyVirtualizer(context: Context, value: Float) {
            val prefs = AppPreferences(context)
            currentVirtualizer.floatValue = value
            prefs.saveEqSettings(
                preset = currentPresetName.value,
                bands = currentEqBands.value,
                bassBoost = currentBassBoost.floatValue,
                virt = value,
                enabled = isEqEnabled.value
            )
            serviceInstance?.applyCurrentEffects()
        }

        fun setEqEnabled(context: Context, enabled: Boolean) {
            val prefs = AppPreferences(context)
            isEqEnabled.value = enabled
            prefs.saveEqSettings(
                preset = currentPresetName.value,
                bands = currentEqBands.value,
                bassBoost = currentBassBoost.floatValue,
                virt = currentVirtualizer.floatValue,
                enabled = enabled
            )
            serviceInstance?.applyCurrentEffects()
        }

        // Direct player reference when service is running
        var playerInstance: Player? = null
            private set

        var serviceInstance: MusicService? = null
            private set

        // Observable "Now Playing" state — readable from any Composable
        var isMiniPlayerDismissed: androidx.compose.runtime.MutableState<Boolean> =
            androidx.compose.runtime.mutableStateOf(true)
        var isMusicPlaying: androidx.compose.runtime.MutableState<Boolean> =
            androidx.compose.runtime.mutableStateOf(false)
        var nowPlayingTitle: androidx.compose.runtime.MutableState<String> =
            androidx.compose.runtime.mutableStateOf("")
        var nowPlayingArtist: androidx.compose.runtime.MutableState<String> =
            androidx.compose.runtime.mutableStateOf("")
        var nowPlayingArtUri: androidx.compose.runtime.MutableState<Uri?> =
            androidx.compose.runtime.mutableStateOf(null)

        var isAutoPlayEnabled: androidx.compose.runtime.MutableState<Boolean> =
            androidx.compose.runtime.mutableStateOf(true)

        var totalTracksCount: androidx.compose.runtime.MutableIntState =
            androidx.compose.runtime.mutableIntStateOf(0)
        var totalTracksSize: androidx.compose.runtime.MutableLongState =
            androidx.compose.runtime.mutableLongStateOf(0L)

        // Compatibility fallback telemetry & bridge (activated only when ExoPlayer+FFmpeg cannot parse a heavily damaged track)
        var isUsingFallbackEngine: androidx.compose.runtime.MutableState<Boolean> =
            androidx.compose.runtime.mutableStateOf(false)
        var fallbackDurationMs: androidx.compose.runtime.MutableLongState =
            androidx.compose.runtime.mutableLongStateOf(0L)
        var fallbackPositionMs: androidx.compose.runtime.MutableLongState =
            androidx.compose.runtime.mutableLongStateOf(0L)
        var playbackStatusMessage: androidx.compose.runtime.MutableState<String?> =
            androidx.compose.runtime.mutableStateOf(null)

        fun getPlaybackPositionMs(): Long = if (isUsingFallbackEngine.value) {
            fallbackPositionMs.longValue.coerceAtLeast(0L)
        } else {
            playerInstance?.currentPosition?.coerceAtLeast(0L) ?: 0L
        }

        fun getPlaybackDurationMs(): Long = if (isUsingFallbackEngine.value) {
            fallbackDurationMs.longValue.coerceAtLeast(0L)
        } else {
            playerInstance?.duration?.coerceAtLeast(0L) ?: 0L
        }

        fun seekToPosition(positionMs: Long) {
            val target = positionMs.coerceAtLeast(0L)
            if (isUsingFallbackEngine.value) {
                serviceInstance?.seekVlcFallback(target)
                return
            }
            playerInstance?.seekTo(target)
        }

        fun setPlaybackParameters(speed: Float, pitch: Float = 1f) {
            val safeSpeed = speed.coerceIn(0.25f, 4.0f)
            if (isUsingFallbackEngine.value) {
                serviceInstance?.setVlcFallbackRate(safeSpeed)
                return
            }
            playerInstance?.playbackParameters = PlaybackParameters(safeSpeed, pitch.coerceIn(0.5f, 2.0f))
        }

        var currentMusicItem: androidx.compose.runtime.MutableState<MusicItem?> =
            androidx.compose.runtime.mutableStateOf(null)
        val activeQueue = androidx.compose.runtime.mutableStateOf<List<MusicItem>>(emptyList())
        var currentPlaylist: List<MusicItem>
            get() = activeQueue.value
            set(value) { activeQueue.value = value.distinctBy { it.uri } }

        fun reorderQueue(fromIndex: Int, toIndex: Int) {
            val list = currentPlaylist.toMutableList()
            if (fromIndex in list.indices && toIndex in list.indices && fromIndex != toIndex) {
                val item = list.removeAt(fromIndex)
                list.add(toIndex, item)
                currentPlaylist = list
                playerInstance?.let { player ->
                    if (player.mediaItemCount == list.size) {
                        player.moveMediaItem(fromIndex, toIndex)
                        currentSongIndex = player.currentMediaItemIndex
                    }
                }
            }
        }
        val repeatMode = androidx.compose.runtime.mutableIntStateOf(Player.REPEAT_MODE_OFF)
        val shuffleEnabled = androidx.compose.runtime.mutableStateOf(false)
        val sleepTimerSecondsRemaining = androidx.compose.runtime.mutableIntStateOf(0)
        private var sleepTimerJob: Job? = null
        private var sleepDeadlineMs = 0L
        private var sleepFadePlayer: Player? = null
        private var sleepFadeOriginalVolume = 1f
        private var sleepFadeAppliedVolume = 1f

        private fun restoreSleepFadeVolume() {
            sleepFadePlayer?.let { player ->
                // Preserve a volume change made by the user during the fade.
                if (kotlin.math.abs(player.volume - sleepFadeAppliedVolume) < 0.001f) {
                    player.volume = sleepFadeOriginalVolume
                }
            }
            sleepFadePlayer = null
        }

        fun setRepeatMode(mode: Int) {
            repeatMode.intValue = mode
            playerInstance?.repeatMode = mode
            syncAutoAdvance()
        }

        fun setShuffleEnabled(enabled: Boolean) {
            shuffleEnabled.value = enabled
            playerInstance?.shuffleModeEnabled = enabled
            syncAutoAdvance()
        }

        fun setAutoPlayEnabled(enabled: Boolean) {
            isAutoPlayEnabled.value = enabled
            syncAutoAdvance()
        }

        private fun syncAutoAdvance() {
            (playerInstance as? ExoPlayer)?.setPauseAtEndOfMediaItems(
                !isAutoPlayEnabled.value && repeatMode.intValue == Player.REPEAT_MODE_OFF && !shuffleEnabled.value)
        }

        private fun queueMediaItem(item: MusicItem): MediaItem = MediaItem.Builder()
            .setUri(item.uri).setMediaId(item.id.toString())
            .setMediaMetadata(MediaMetadata.Builder().setTitle(item.title).setArtist(item.artist)
                .setAlbumTitle(item.album).setArtworkUri(item.albumArtUri).build()).build()

        fun enqueueNext(song: MusicItem): Boolean {
            val player = playerInstance ?: return false
            val current = currentMusicItem.value ?: return false
            // This queue contains each song once; do not duplicate the current row.
            if (song.uri == current.uri) return false
            val queue = currentPlaylist.filter { it.uri != song.uri }.toMutableList()
            val index = queue.indexOfFirst { it.uri == current.uri }
            if (index < 0) return false
            queue.add(index + 1, song)
            val position = player.currentPosition
            val playing = player.playWhenReady
            currentPlaylist = queue
            player.setMediaItems(queue.map(::queueMediaItem), index, position)
            player.prepare()
            player.playWhenReady = playing
            return true
        }

        fun setSleepTimer(minutes: Int) = startSleepTimer(minutes.coerceAtLeast(0).toLong() * 60_000L)

        internal fun startSleepTimer(durationMs: Long) {
            sleepTimerJob?.cancel()
            sleepTimerJob = null
            restoreSleepFadeVolume()
            sleepDeadlineMs = if (durationMs > 0) android.os.SystemClock.elapsedRealtime() + durationMs else 0L
            sleepTimerSecondsRemaining.intValue = ((durationMs.coerceAtLeast(0L) + 999) / 1000).toInt()
            if (durationMs <= 0) return
            sleepTimerJob = musicScope.launch {
                val appPrefs = AppPreferences(serviceInstance ?: return@launch)
                val fadeOut = appPrefs.isSleepTimerFadeOutEnabled()
                while (true) {
                    val remaining = (sleepDeadlineMs - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                    sleepTimerSecondsRemaining.intValue = ((remaining + 999) / 1000).toInt()
                    if (fadeOut && remaining <= 20_000L && remaining > 0L) {
                        val factor = (remaining / 20_000f).coerceIn(0.05f, 1f)
                        if (isUsingFallbackEngine.value) {
                            serviceInstance?.setVlcFallbackVolumeFactor(factor)
                        } else {
                            playerInstance?.let { player ->
                                if (sleepFadePlayer !== player ||
                                    kotlin.math.abs(player.volume - sleepFadeAppliedVolume) >= 0.001f) {
                                    sleepFadePlayer = player
                                    sleepFadeOriginalVolume = player.volume
                                }
                                sleepFadeAppliedVolume = sleepFadeOriginalVolume * factor
                                player.volume = sleepFadeAppliedVolume
                            }
                        }
                    }
                    if (remaining == 0L) {
                        if (isUsingFallbackEngine.value) {
                            serviceInstance?.pauseVlcFallback()
                            serviceInstance?.setVlcFallbackVolumeFactor(1f)
                        } else {
                            playerInstance?.pause()
                            restoreSleepFadeVolume()
                        }
                        sleepDeadlineMs = 0L
                        break
                    }
                    delay(minOf(remaining, 250L))
                }
            }
        }
        var currentSongIndex: Int = -1

        var onPlayNextAction: (() -> Unit)? = null
        var onPlayPrevAction: (() -> Unit)? = null
        var onTogglePlayPauseAction: (() -> Unit)? = null
        var onPauseAction: (() -> Unit)? = null
        var onStopAction: (() -> Unit)? = null

        fun resolveArtworkBytes(context: Context, songUri: Uri?, albumArtUri: Uri? = null): ByteArray? {
            return MediaArtworkHelper.getAudioArtworkBytes(context, songUri, albumArtUri)
        }

        fun resolveArtworkBytes(context: Context, uri: Uri?): ByteArray? {
            return MediaArtworkHelper.getAudioArtworkBytes(context, uri, uri)
        }

        private var fadeJob: Job? = null
        private val musicScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

        fun fadeIn(player: Player, durationMs: Long = 150L, onComplete: (() -> Unit)? = null) {
            fadeJob?.cancel()
            player.volume = 1f
            if (!player.isPlaying) {
                player.play()
            }
            onComplete?.invoke()
        }

        fun fadeOutAndPause(player: Player, durationMs: Long = 150L, onComplete: (() -> Unit)? = null) {
            fadeJob?.cancel()
            player.pause()
            player.volume = 1f
            onComplete?.invoke()
        }

        fun pauseMusic() {
            if (isUsingFallbackEngine.value) {
                serviceInstance?.pauseVlcFallback()
                onPauseAction?.invoke()
                isMusicPlaying.value = false
                return
            }
            playerInstance?.let {
                fadeOutAndPause(it) {
                    onPauseAction?.invoke()
                    isMusicPlaying.value = false
                }
            } ?: run {
                onPauseAction?.invoke()
                isMusicPlaying.value = false
            }
        }

        fun stopMusic() {
            fadeJob?.cancel()
            serviceInstance?.stopVlcFallback()
            playerInstance?.let {
                it.stop()
                it.volume = 1f
            }
            onStopAction?.invoke()
            isMusicPlaying.value = false
        }

        fun stopAndDismiss() {
            isMiniPlayerDismissed.value = true
            fadeJob?.cancel()
            serviceInstance?.stopVlcFallback()
            playerInstance?.let {
                it.playWhenReady = false
                it.stop()
                it.clearMediaItems()
                it.volume = 1f
            }
            currentPlaylist = emptyList()
            currentSongIndex = -1
            onStopAction?.invoke()
            isMusicPlaying.value = false
            nowPlayingTitle.value = ""
            nowPlayingArtist.value = ""
            nowPlayingArtUri.value = null
            currentMusicItem.value = null
            try {
                serviceInstance?.stopForeground(STOP_FOREGROUND_REMOVE)
                val nm = serviceInstance?.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.cancel(MUSIC_NOTIFICATION_ID)
                serviceInstance?.stopSelf()
            } catch (_: Exception) {}
        }

        fun playSong(item: MusicItem) {
            isMiniPlayerDismissed.value = false
            serviceInstance?.stopVlcFallback()
            serviceInstance?.resetRecoveryState()
            com.example.ymediaplayer.player.VideoPlaybackManager.player?.let { vPlayer ->
                if (vPlayer.isPlaying || vPlayer.playWhenReady) {
                    com.example.ymediaplayer.player.VideoPlaybackManager.manualPause()
                }
            }
            val context = serviceInstance?.applicationContext ?: com.example.ymediaplayer.YMediaApplication.instance
            try {
                com.example.ymediaplayer.service.VideoPlaybackService.stopService(context)
            } catch (_: Exception) {}

            try {
                val sIntent = Intent(context, MusicService::class.java)
                try {
                    context.startService(sIntent)
                } catch (_: Exception) {
                    ContextCompat.startForegroundService(context, sIntent)
                }
            } catch (_: Exception) {}

            currentMusicItem.value = item
            nowPlayingTitle.value = item.title
            nowPlayingArtist.value = item.artist
            nowPlayingArtUri.value = item.albumArtUri

            val player = playerInstance ?: return

            val initialMetadata = MediaMetadata.Builder()
                .setTitle(item.title)
                .setArtist(item.artist)
                .setAlbumTitle(item.album)
                .setArtworkUri(item.albumArtUri)
                .build()

            val mediaItem = MediaItem.Builder()
                .setUri(item.uri)
                .setMediaId(item.id.toString())
                .setMediaMetadata(initialMetadata)
                .build()

            if (currentPlaylist.none { it.uri == item.uri }) currentPlaylist = listOf(item)
            currentSongIndex = currentPlaylist.indexOfFirst { it.uri == item.uri }.coerceAtLeast(0)
            player.setMediaItems(currentPlaylist.map { queued ->
                if (queued.uri == item.uri) mediaItem else queueMediaItem(queued)
            }, currentSongIndex, 0L)
            player.prepare()
            player.volume = 1f
            player.play()
            isMusicPlaying.value = true

            serviceInstance?.updateSessionCustomLayout()

            // Resolve artwork bytes asynchronously on IO thread to prevent UI thread freeze
            CoroutineScope(Dispatchers.IO).launch {
                val artBytes = resolveArtworkBytes(context, item.uri, item.albumArtUri)
                if (artBytes != null && currentMusicItem.value?.id == item.id) {
                    val enrichedMeta = initialMetadata.buildUpon()
                        .setArtworkData(artBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                        .build()
                    withContext(Dispatchers.Main) {
                        if (currentMusicItem.value?.id == item.id) {
                            val updatedItem = mediaItem.buildUpon().setMediaMetadata(enrichedMeta).build()
                            val curIdx = player.currentMediaItemIndex
                            if (curIdx >= 0 && curIdx < player.mediaItemCount) {
                                player.replaceMediaItem(curIdx, updatedItem)
                            }
                            serviceInstance?.updateSessionCustomLayout()
                        }
                    }
                }
            }
        }

        fun togglePlayPause() {
            if (isUsingFallbackEngine.value) {
                val svc = serviceInstance
                if (svc != null) {
                    if (isMusicPlaying.value) {
                        svc.pauseVlcFallback()
                    } else {
                        com.example.ymediaplayer.player.VideoPlaybackManager.manualPause()
                        svc.playVlcFallback()
                    }
                    return
                }
            }
            val player = playerInstance
            if (player != null) {
                if (player.isPlaying) {
                    player.pause()
                    player.volume = 1f
                    isMusicPlaying.value = false
                } else {
                    com.example.ymediaplayer.player.VideoPlaybackManager.manualPause()
                    if (player.playbackState == Player.STATE_ENDED) {
                        player.seekTo(0L)
                    }
                    player.volume = 1f
                    player.play()
                    isMusicPlaying.value = true
                }
                return
            }
            onTogglePlayPauseAction?.invoke()
        }

        fun playNext() {
            isMiniPlayerDismissed.value = false
            if (isUsingFallbackEngine.value) {
                serviceInstance?.stopVlcFallback()
                serviceInstance?.resetRecoveryState()
            }
            val player = playerInstance ?: return
            if (player.hasNextMediaItem()) player.seekToNextMediaItem()
            else if (player.mediaItemCount > 0) player.seekTo(0, 0L)
            player.play()
        }

        fun playPrevious() {
            isMiniPlayerDismissed.value = false
            val pos = getPlaybackPositionMs()
            if (isUsingFallbackEngine.value && pos <= 3000L) {
                serviceInstance?.stopVlcFallback()
                serviceInstance?.resetRecoveryState()
            } else if (isUsingFallbackEngine.value && pos > 3000L) {
                seekToPosition(0L)
                return
            }
            val player = playerInstance ?: return
            if (player.currentPosition > 3000L) player.seekTo(0L)
            else if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem()
            else if (player.mediaItemCount > 0) player.seekTo(player.mediaItemCount - 1, 0L)
            player.play()
        }

    }

    override fun onCreate() {
        super.onCreate()
        serviceInstance = this

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                MUSIC_NOTIFICATION_CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active music playback controls and artwork"
                setShowBadge(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }

        ensureForegroundNotification()

        val audioVisualizerProcessor = AudioVisualizerProcessor()
        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): androidx.media3.exoplayer.audio.AudioSink {
                return androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf(audioVisualizerProcessor))
                    .build()
            }

            override fun buildAudioRenderers(
                context: Context,
                extensionRendererMode: Int,
                mediaCodecSelector: androidx.media3.exoplayer.mediacodec.MediaCodecSelector,
                enableDecoderFallback: Boolean,
                audioSink: androidx.media3.exoplayer.audio.AudioSink,
                eventHandler: Handler,
                eventListener: androidx.media3.exoplayer.audio.AudioRendererEventListener,
                out: java.util.ArrayList<androidx.media3.exoplayer.Renderer>
            ) {
                // Inject FfmpegAudioRenderer for FLAC, ALAC, OPUS, AC3, E-AC3, DTS, WMA, APE, Musepack
                try {
                    val ffmpegRenderer = androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer(
                        eventHandler, eventListener, audioSink
                    )
                    out.add(ffmpegRenderer)
                } catch (t: Throwable) {
                    android.util.Log.w("MusicService", "Could not inject FfmpegAudioRenderer: ${t.message}")
                }
                super.buildAudioRenderers(
                    context,
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF,
                    mediaCodecSelector,
                    enableDecoderFallback,
                    audioSink,
                    eventHandler,
                    eventListener,
                    out
                )
            }
        }.apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            setEnableDecoderFallback(true)
            setEnableAudioTrackPlaybackParams(true)
        }

        val appPreferences = AppPreferences(this)
        isAutoPlayEnabled.value = appPreferences.isAutoPlayNextEnabled()
        val handleAudioFocus = !appPreferences.isPlayDuringCallsEnabled()

        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 20_000,
                /* maxBufferMs = */ 60_000,
                /* bufferForPlaybackMs = */ 150,
                /* bufferForPlaybackAfterRebufferMs = */ 500
            )
            .setBackBuffer(/* backBufferDurationMs = */ 15_000, /* retainBackBufferFromKeyframe = */ true)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setConstantBitrateSeekingAlwaysEnabled(true)
            .setMp3ExtractorFlags(Mp3Extractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING)
            .setAdtsExtractorFlags(AdtsExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING)
            .setTsExtractorTimestampSearchBytes(8 * 1024 * 1024)
        val mediaSourceFactory = DefaultMediaSourceFactory(
            DefaultDataSource.Factory(this),
            extractorsFactory
        ).setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(5))

        val player = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                handleAudioFocus
            )
            .setHandleAudioBecomingNoisy(appPreferences.isPauseOnHeadsetDisconnect())
            .build()

        playerInstance = player
        player.repeatMode = repeatMode.intValue
        player.shuffleModeEnabled = shuffleEnabled.value
        syncAutoAdvance()
        var lastRecordedUri: String? = null
        fun recordPlayedItem(item: MediaItem?, repeat: Boolean = false) {
            val uri = item?.localConfiguration?.uri?.toString() ?: return
            if (repeat || uri != lastRecordedUri) {
                appPreferences.recordMusicPlayed(uri)
                appPreferences.incrementPlayCount(uri)
                lastRecordedUri = uri
            }
        }
        val initialSessionId = player.audioSessionId
        currentAudioSessionId.value = initialSessionId
        initPreferencesState(this)
        if (initialSessionId > 0) {
            attachAudioEffects(initialSessionId)
        }

        player.addListener(object : Player.Listener {
            override fun onRepeatModeChanged(mode: Int) { repeatMode.intValue = mode; syncAutoAdvance() }
            override fun onShuffleModeEnabledChanged(enabled: Boolean) { shuffleEnabled.value = enabled; syncAutoAdvance() }
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                currentAudioSessionId.value = audioSessionId
                if (audioSessionId > 0) {
                    attachAudioEffects(audioSessionId)
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isUsingFallbackEngine.value) {
                    isMusicPlaying.value = isPlaying
                }
                if (isPlaying) recordPlayedItem(player.currentMediaItem)
                if (!isPlaying && !isUsingFallbackEngine.value) {
                    AudioReactor.reset()
                }
                updateForegroundNotification()
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    lastReadyAtMs = SystemClock.elapsedRealtime()
                    playbackStatusMessage.value = null
                }
                if ((playbackState == Player.STATE_ENDED ||
                        playbackState == Player.STATE_IDLE) && !isUsingFallbackEngine.value) {
                    AudioReactor.reset()
                }
                // Media3 advances/repeats the timeline itself. STATE_ENDED with
                // Repeat Off is the end of the queue, not an implicit Next tap.
                updateForegroundNotification()
            }
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                AudioReactor.reset()
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                stopVlcFallback()
                resetRecoveryState()
                AudioReactor.reset()
                if (mediaItem != null) {
                    val mediaId = mediaItem.mediaId
                    val uriStr = mediaItem.localConfiguration?.uri?.toString()
                    if (player.isPlaying) recordPlayedItem(mediaItem, reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)
                    val found = currentPlaylist.find {
                        (mediaId.isNotBlank() && it.id.toString() == mediaId) || it.uri.toString() == uriStr
                    }
                    if (found != null) {
                        currentMusicItem.value = found
                        currentSongIndex = player.currentMediaItemIndex
                        nowPlayingTitle.value = found.title
                        nowPlayingArtist.value = found.artist
                        nowPlayingArtUri.value = found.albumArtUri
                    } else {
                        mediaItem.mediaMetadata.let { meta ->
                            if (!meta.title.isNullOrBlank()) nowPlayingTitle.value = meta.title.toString()
                            if (!meta.artist.isNullOrBlank()) nowPlayingArtist.value = meta.artist.toString()
                            if (meta.artworkUri != null) nowPlayingArtUri.value = meta.artworkUri
                        }
                    }
                }
                updateSessionCustomLayout()
                updateForegroundNotification()
            }
            override fun onPlayerError(error: PlaybackException) {
                handleAudioPlaybackError(player, error)
            }
        })

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_MUSIC_PLAYER, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val sessionCallback = object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                    .add(SessionCommand(CUSTOM_ACTION_FAVORITE, Bundle.EMPTY))
                    .add(SessionCommand(CUSTOM_ACTION_REWIND, Bundle.EMPTY))
                    .add(SessionCommand(CUSTOM_ACTION_FORWARD, Bundle.EMPTY))
                    .add(SessionCommand(CUSTOM_ACTION_STOP, Bundle.EMPTY))
                    .build()

                val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                    .add(Player.COMMAND_PLAY_PAUSE)
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                    .build()

                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(sessionCommands)
                    .setAvailablePlayerCommands(playerCommands)
                    .setCustomLayout(buildCustomLayout())
                    .build()
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle
            ): ListenableFuture<SessionResult> {
                when (customCommand.customAction) {
                    CUSTOM_ACTION_FAVORITE -> {
                        val currentUri = currentMusicItem.value?.uri?.toString()
                        if (!currentUri.isNullOrBlank()) {
                            AppPreferences(this@MusicService).toggleMusicFavorite(currentUri)
                            updateSessionCustomLayout()
                        }
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                    CUSTOM_ACTION_REWIND -> {
                        player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L))
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                    CUSTOM_ACTION_FORWARD -> {
                        player.seekTo((player.currentPosition + 10_000L).coerceAtMost(player.duration.coerceAtLeast(0L)))
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                    CUSTOM_ACTION_STOP -> {
                        stopAndDismiss()
                        stopSelf()
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                }
                return super.onCustomCommand(session, controller, customCommand, args)
            }

            override fun onPlaybackResumption(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo
            ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                val currentItem = mediaSession.player.currentMediaItem
                if (currentItem != null) {
                    val startPos = mediaSession.player.currentPosition
                    return Futures.immediateFuture(
                        MediaSession.MediaItemsWithStartPosition(listOf(currentItem), 0, startPos)
                    )
                }
                val firstItem = currentPlaylist.firstOrNull()
                if (firstItem != null) {
                    val artBytes = resolveArtworkBytes(this@MusicService, firstItem.albumArtUri)
                    val metadata = MediaMetadata.Builder()
                        .setTitle(firstItem.title)
                        .setArtist(firstItem.artist)
                        .setAlbumTitle(firstItem.album)
                        .setArtworkUri(firstItem.albumArtUri)
                        .apply {
                            if (artBytes != null) {
                                setArtworkData(artBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                            }
                        }
                        .build()
                    val mItem = MediaItem.Builder()
                        .setUri(firstItem.uri)
                        .setMediaId(firstItem.id.toString())
                        .setMediaMetadata(metadata)
                        .build()
                    return Futures.immediateFuture(
                        MediaSession.MediaItemsWithStartPosition(listOf(mItem), 0, 0L)
                    )
                }
                return super.onPlaybackResumption(mediaSession, controller)
            }
        }

        val forwardingPlayer = object : ForwardingPlayer(player) {
            override fun play() {
                if (isUsingFallbackEngine.value) {
                    playVlcFallback()
                    return
                }
                player.volume = 1f
                player.play()
            }

            override fun pause() {
                if (isUsingFallbackEngine.value) {
                    pauseVlcFallback()
                    return
                }
                player.pause()
                player.volume = 1f
            }

            override fun setPlayWhenReady(playWhenReady: Boolean) {
                if (isUsingFallbackEngine.value) {
                    if (playWhenReady) playVlcFallback() else pauseVlcFallback()
                    return
                }
                if (playWhenReady) {
                    player.volume = 1f
                }
                super.setPlayWhenReady(playWhenReady)
            }

            override fun seekTo(positionMs: Long) {
                if (isUsingFallbackEngine.value) {
                    seekVlcFallback(positionMs)
                    return
                }
                super.seekTo(positionMs)
            }

            override fun isCommandAvailable(command: Int): Boolean {
                if (command == Player.COMMAND_SEEK_TO_NEXT || command == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM) {
                    return currentPlaylist.isNotEmpty() || onPlayNextAction != null
                }
                if (command == Player.COMMAND_SEEK_TO_PREVIOUS || command == Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM) {
                    return currentPlaylist.isNotEmpty() || onPlayPrevAction != null
                }
                return super.isCommandAvailable(command)
            }

            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                    .build()
            }

            override fun seekToNext() {
                playNext()
            }

            override fun seekToNextMediaItem() {
                playNext()
            }

            override fun seekToPrevious() {
                playPrevious()
            }

            override fun seekToPreviousMediaItem() {
                playPrevious()
            }
        }

        try {
            mediaSession = MediaSession.Builder(this, forwardingPlayer)
                .setId("AllRounder_Music_Session")
                .setSessionActivity(pendingIntent)
                .setCallback(sessionCallback)
                .build()
        } catch (e: Exception) {
            android.util.Log.e("MusicService", "Error creating music MediaSession", e)
        }

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(MUSIC_NOTIFICATION_CHANNEL_ID)
                .setNotificationId(MUSIC_NOTIFICATION_ID)
                .build()
        )
    }

    private fun buildMediaNotification(): android.app.Notification {
        val title = nowPlayingTitle.value.ifEmpty { "Music Playback" }
        val artist = nowPlayingArtist.value.ifEmpty { "AllRounder Player" }
        val isPlaying = if (isUsingFallbackEngine.value) isMusicPlaying.value else (playerInstance?.isPlaying == true)

        // Activity intent when notification body is tapped
        val activityIntent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_MUSIC_PLAYER, true)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action PendingIntents
        val prevIntent = Intent(this, MusicService::class.java).apply { action = ACTION_PREV }
        val prevPendingIntent = PendingIntent.getService(
            this, 1, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIntent = Intent(this, MusicService::class.java).apply { action = ACTION_PLAY_PAUSE }
        val playPausePendingIntent = PendingIntent.getService(
            this, 2, playPauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = Intent(this, MusicService::class.java).apply { action = ACTION_NEXT }
        val nextPendingIntent = PendingIntent.getService(
            this, 3, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, MusicService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 6, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
            .setShowActionsInCompactView(0, 1, 2)

        try {
            mediaSession?.platformToken?.let { token ->
                mediaStyle.setMediaSession(android.support.v4.media.session.MediaSessionCompat.Token.fromToken(token))
            }
        } catch (_: Exception) {}

        val builder = androidx.core.app.NotificationCompat.Builder(this, MUSIC_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(artist)
            .setContentIntent(contentPendingIntent)
            .setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
            .setOngoing(isPlaying)
            .setShowWhen(false)
            .addAction(android.R.drawable.ic_media_previous, "Previous", prevPendingIntent)
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pause" else "Play",
                playPausePendingIntent
            )
            .addAction(android.R.drawable.ic_media_next, "Next", nextPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setDeleteIntent(stopPendingIntent)
            .setStyle(mediaStyle)

        try {
            val artUri = nowPlayingArtUri.value ?: currentMusicItem.value?.albumArtUri
            val songUri = currentMusicItem.value?.uri
            val bitmap = MediaArtworkHelper.getAudioArtworkBitmap(this, songUri, artUri)
            if (bitmap != null) {
                builder.setLargeIcon(bitmap)
            }
        } catch (_: Throwable) {}

        return builder.build()
    }

    fun updateForegroundNotification() {
        if (isMiniPlayerDismissed.value || currentMusicItem.value == null) {
            try {
                stopForeground(STOP_FOREGROUND_REMOVE)
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.cancel(MUSIC_NOTIFICATION_ID)
            } catch (_: Exception) {}
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val notification = buildMediaNotification()
                val activelyPlaying = if (isUsingFallbackEngine.value) isMusicPlaying.value else (playerInstance?.isPlaying == true)
                if (activelyPlaying) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(
                            MUSIC_NOTIFICATION_ID,
                            notification,
                            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                        )
                    } else {
                        startForeground(MUSIC_NOTIFICATION_ID, notification)
                    }
                } else {
                    // A paused player doesn't need foreground-service priority. Detaching
                    // leaves its controls visible and lets the user dismiss the notification.
                    stopForeground(STOP_FOREGROUND_DETACH)
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    nm?.notify(MUSIC_NOTIFICATION_ID, notification)
                }
            } catch (e: Exception) {
                android.util.Log.e("MusicService", "Error in updateForegroundNotification", e)
            }
        }
    }

    private fun ensureForegroundNotification() {
        updateForegroundNotification()
    }

    private fun buildCustomLayout(): List<CommandButton> {
        val currentUri = currentMusicItem.value?.uri?.toString()
        val isFav = if (!currentUri.isNullOrBlank()) AppPreferences(this).isMusicFavorite(currentUri) else false

        val rewindButton = CommandButton.Builder()
            .setDisplayName("Rewind 10s")
            .setIconResId(R.drawable.ic_notif_rewind_10)
            .setSessionCommand(SessionCommand(CUSTOM_ACTION_REWIND, Bundle.EMPTY))
            .build()

        val favoriteButton = CommandButton.Builder()
            .setDisplayName(if (isFav) "Favorited" else "Favorite")
            .setIconResId(if (isFav) R.drawable.ic_notif_favorite else R.drawable.ic_notif_favorite_border)
            .setSessionCommand(SessionCommand(CUSTOM_ACTION_FAVORITE, Bundle.EMPTY))
            .build()

        val stopButton = CommandButton.Builder()
            .setDisplayName("Stop")
            .setIconResId(android.R.drawable.ic_menu_close_clear_cancel)
            .setSessionCommand(SessionCommand(CUSTOM_ACTION_STOP, Bundle.EMPTY))
            .build()

        return listOf(rewindButton, favoriteButton, stopButton)
    }

    fun updateSessionCustomLayout() {
        val session = mediaSession ?: return
        session.setCustomLayout(buildCustomLayout())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> togglePlayPause()
            ACTION_PREV -> playPrevious()
            ACTION_NEXT -> playNext()
            ACTION_STOP -> {
                stopAndDismiss()
                stopSelfResult(startId)
                return START_NOT_STICKY
            }
            CUSTOM_ACTION_REWIND -> {
                val curPos = getPlaybackPositionMs()
                seekToPosition((curPos - 10_000L).coerceAtLeast(0L))
            }
            CUSTOM_ACTION_FORWARD -> {
                val curPos = getPlaybackPositionMs()
                val curDur = getPlaybackDurationMs()
                val target = if (curDur > 0L) (curPos + 10_000L).coerceAtMost(curDur) else (curPos + 10_000L)
                seekToPosition(target)
            }
        }
        updateForegroundNotification()
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Removing the app from Recents is an explicit stop request here. Use
        // Media3's helper so every session/player managed by this service is paused
        // and the service is stopped, even when a controller is still bound.
        stopAndDismiss()
        pauseAllPlayersAndStopSelf()
    }

    fun attachAudioEffects(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        releaseAudioEffects()
        try {
            val eq = Equalizer(0, audioSessionId)
            val bass = BassBoost(0, audioSessionId)
            val virt = Virtualizer(0, audioSessionId)

            equalizer = eq
            bassBoost = bass
            virtualizer = virt

            applyCurrentEffects()
        } catch (e: Exception) {
            android.util.Log.e("MusicService", "Failed to attach audio effects to session $audioSessionId", e)
        }
    }

    fun releaseAudioEffects() {
        try { equalizer?.release() } catch (_: Exception) {}
        try { bassBoost?.release() } catch (_: Exception) {}
        try { virtualizer?.release() } catch (_: Exception) {}
        equalizer = null
        bassBoost = null
        virtualizer = null
    }

    fun applyCurrentEffects() {
        val eqEnabled = isEqEnabled.value
        val bands = currentEqBands.value
        val bassStrength = currentBassBoost.floatValue
        val virtStrength = currentVirtualizer.floatValue

        equalizer?.let { eq ->
            try {
                eq.enabled = eqEnabled
                val minRange = eq.bandLevelRange[0]
                val maxRange = eq.bandLevelRange[1]
                bands.forEachIndexed { idx, db ->
                    val mB = (db * 100).toInt().coerceIn(minRange.toInt(), maxRange.toInt()).toShort()
                    if (idx < eq.numberOfBands.toInt()) {
                        eq.setBandLevel(idx.toShort(), mB)
                    }
                }
            } catch (_: Exception) {}
        }

        bassBoost?.let { b ->
            try {
                b.enabled = eqEnabled
                b.setStrength((bassStrength * 1000).toInt().coerceIn(0, 1000).toShort())
            } catch (_: Exception) {}
        }

        virtualizer?.let { v ->
            try {
                v.enabled = eqEnabled
                v.setStrength((virtStrength * 1000).toInt().coerceIn(0, 1000).toShort())
            } catch (_: Exception) {}
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        setSleepTimer(0)
        audioRecoveryJob?.cancel()
        audioRecoveryJob = null
        stopVlcFallback()
        serviceInstance = null
        playerInstance = null
        releaseAudioEffects()
        currentAudioSessionId.value = 0
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {}
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    internal fun resetRecoveryState() {
        audioRecoveryJob?.cancel()
        audioRecoveryJob = null
        audioRecoveryAttempt = 0
        lastReadyAtMs = 0L
        playbackStatusMessage.value = null
    }

    private fun handleAudioPlaybackError(player: ExoPlayer, error: PlaybackException) {
        if (player !== playerInstance || isUsingFallbackEngine.value) return
        val now = SystemClock.elapsedRealtime()
        if (lastReadyAtMs > 0L && now - lastReadyAtMs >= 20_000L) {
            audioRecoveryAttempt = 0
        }
        val attempt = audioRecoveryAttempt++
        android.util.Log.w("MusicService", "Audio error (attempt=$attempt, code=${error.errorCodeName}): ${error.message}")

        val currentUri = player.currentMediaItem?.localConfiguration?.uri ?: currentMusicItem.value?.uri
        if (attempt >= 3 ||
            error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
            error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED
        ) {
            if (currentUri != null) {
                val resumeMs = player.currentPosition.coerceAtLeast(0L)
                startVlcAudioFallback(player, currentUri, resumeMs)
            } else {
                playbackStatusMessage.value = "Unable to open this audio track"
                isMusicPlaying.value = false
            }
            return
        }

        val currentPos = player.currentPosition.coerceAtLeast(0L)
        val totalDur = player.duration.coerceAtLeast(0L)
        val targetPos = when {
            attempt == 1 && (totalDur <= 0L || currentPos + 4_000L < totalDur) -> currentPos + 4_000L
            attempt >= 2 -> 0L
            else -> currentPos
        }
        playbackStatusMessage.value = if (attempt == 1 && targetPos > currentPos) {
            "Skipping damaged audio frame..."
        } else {
            "Recovering audio playback..."
        }

        audioRecoveryJob?.cancel()
        audioRecoveryJob = serviceScope.launch {
            delay(when (attempt) { 0 -> 350L; 1 -> 650L; else -> 900L })
            if (player !== playerInstance || isUsingFallbackEngine.value) return@launch
            runCatching {
                player.seekTo(targetPos)
                player.prepare()
                player.volume = 1f
                player.playWhenReady = true
            }.onFailure {
                if (currentUri != null) {
                    startVlcAudioFallback(player, currentUri, targetPos)
                }
            }
        }
    }

    private fun startVlcAudioFallback(exoPlayer: ExoPlayer, uri: Uri, startPosMs: Long) {
        audioRecoveryJob?.cancel()
        audioRecoveryJob = null
        try {
            exoPlayer.playWhenReady = false
            exoPlayer.pause()
            exoPlayer.stop()
        } catch (_: Exception) {}

        stopVlcFallback(clearPlaylistFlag = false)

        try {
            val lib = LibVLC(
                applicationContext,
                arrayListOf(
                    "--no-video",
                    "--file-caching=1500",
                    "--network-caching=2500",
                    "--avcodec-corrupted",
                    "--avcodec-error-resilience=1",
                    "--no-video-title-show"
                )
            )
            vlcLib = lib
            val mp = VlcMediaPlayer(lib)
            vlcAudioPlayer = mp

            val pfd = if (uri.scheme == "content") {
                try { contentResolver.openFileDescriptor(uri, "r") } catch (_: Exception) { null }
            } else null
            vlcPfd = pfd

            val media = (if (pfd != null) Media(lib, pfd.fileDescriptor) else Media(lib, uri)).apply {
                addOption(":no-video")
                addOption(":avcodec-corrupted")
                addOption(":file-caching=1500")
            }
            vlcMedia = media
            mp.media = media
            isUsingFallbackEngine.value = true
            fallbackDurationMs.longValue = currentMusicItem.value?.duration?.coerceAtLeast(0L) ?: 0L
            fallbackPositionMs.longValue = startPosMs.coerceAtLeast(0L)
            playbackStatusMessage.value = "Playing in compatibility engine"

            var appliedStartSeek = startPosMs <= 0L
            mp.setEventListener { event ->
                mainHandler.post {
                    if (vlcAudioPlayer !== mp) return@post
                    when (event.type) {
                        VlcMediaPlayer.Event.Playing -> {
                            isMusicPlaying.value = true
                            playbackStatusMessage.value = null
                            val len = mp.length
                            if (len > 0L) fallbackDurationMs.longValue = len
                            if (!appliedStartSeek && startPosMs > 0L && mp.isSeekable) {
                                appliedStartSeek = true
                                mp.time = startPosMs
                            }
                            updateForegroundNotification()
                        }
                        VlcMediaPlayer.Event.Paused -> {
                            isMusicPlaying.value = false
                            updateForegroundNotification()
                        }
                        VlcMediaPlayer.Event.LengthChanged -> {
                            val len = mp.length
                            if (len > 0L) fallbackDurationMs.longValue = len
                        }
                        VlcMediaPlayer.Event.TimeChanged -> {
                            val t = mp.time
                            if (t >= 0L) fallbackPositionMs.longValue = t
                        }
                        VlcMediaPlayer.Event.EndReached -> {
                            isMusicPlaying.value = false
                            updateForegroundNotification()
                            when {
                                repeatMode.intValue == Player.REPEAT_MODE_ONE -> {
                                    startVlcAudioFallback(exoPlayer, uri, 0L)
                                }
                                currentPlaylist.size > 1 &&
                                    (repeatMode.intValue == Player.REPEAT_MODE_ALL || isAutoPlayEnabled.value || shuffleEnabled.value) -> {
                                    playNext()
                                }
                            }
                        }
                        VlcMediaPlayer.Event.EncounteredError -> {
                            isMusicPlaying.value = false
                            playbackStatusMessage.value = "Cannot decode remaining audio data"
                            updateForegroundNotification()
                        }
                    }
                }
            }
            mp.play()
        } catch (t: Throwable) {
            android.util.Log.e("MusicService", "LibVLC audio fallback failed", t)
            stopVlcFallback()
            playbackStatusMessage.value = "Unable to play this audio file"
            isMusicPlaying.value = false
        }
    }

    internal fun playVlcFallback() {
        val mp = vlcAudioPlayer ?: return
        mp.volume = 100
        mp.play()
        isMusicPlaying.value = true
        updateForegroundNotification()
    }

    internal fun pauseVlcFallback() {
        val mp = vlcAudioPlayer ?: return
        mp.pause()
        isMusicPlaying.value = false
        updateForegroundNotification()
    }

    internal fun seekVlcFallback(positionMs: Long) {
        val mp = vlcAudioPlayer ?: return
        val maxDur = fallbackDurationMs.longValue
        val clamped = if (maxDur > 0L) positionMs.coerceIn(0L, maxDur) else positionMs.coerceAtLeast(0L)
        if (mp.isSeekable) {
            mp.time = clamped
        }
        fallbackPositionMs.longValue = clamped
    }

    internal fun setVlcFallbackRate(speed: Float) {
        vlcAudioPlayer?.rate = speed.coerceIn(0.25f, 4.0f)
    }

    internal fun setVlcFallbackVolumeFactor(factor: Float) {
        vlcAudioPlayer?.volume = (factor * 100f).toInt().coerceIn(0, 100)
    }

    internal fun stopVlcFallback(clearPlaylistFlag: Boolean = true) {
        val mp = vlcAudioPlayer
        vlcAudioPlayer = null
        if (mp != null) {
            try {
                mp.setEventListener(null)
                mp.stop()
                mp.release()
            } catch (_: Exception) {}
        }
        try { vlcMedia?.release() } catch (_: Exception) {}
        vlcMedia = null
        try { vlcPfd?.close() } catch (_: Exception) {}
        vlcPfd = null
        try { vlcLib?.release() } catch (_: Exception) {}
        vlcLib = null
        if (clearPlaylistFlag) {
            isUsingFallbackEngine.value = false
            fallbackDurationMs.longValue = 0L
            fallbackPositionMs.longValue = 0L
        }
    }
}

