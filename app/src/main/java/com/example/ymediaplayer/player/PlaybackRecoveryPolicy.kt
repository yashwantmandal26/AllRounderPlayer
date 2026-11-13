@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.ymediaplayer.player

import androidx.media3.common.PlaybackException

internal enum class RecoveryAction {
    RETRY,
    SAFE_MODE_RETRY,
    DISABLE_AUDIO_RETRY,
    COMPATIBILITY_FALLBACK,
    FAIL
}

/** Keeps recovery decisions deterministic and, most importantly, bounded. */
internal object PlaybackRecoveryPolicy {
    const val MAX_NETWORK_RETRIES = 3
    const val MAX_DECODER_RETRIES = 2

    fun actionFor(errorCode: Int, attempt: Int, audioRendererFailed: Boolean): RecoveryAction {
        return when (errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_TIMEOUT,
            PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> {
                if (attempt < MAX_NETWORK_RETRIES) RecoveryAction.RETRY else RecoveryAction.FAIL
            }

            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
            PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_OFFLOAD_INIT_FAILED,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_OFFLOAD_WRITE_FAILED -> when {
                attempt == 0 -> RecoveryAction.SAFE_MODE_RETRY
                audioRendererFailed && attempt == 1 -> RecoveryAction.DISABLE_AUDIO_RETRY
                attempt < MAX_DECODER_RETRIES -> RecoveryAction.RETRY
                else -> RecoveryAction.COMPATIBILITY_FALLBACK
            }

            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> {
                if (attempt == 0) RecoveryAction.SAFE_MODE_RETRY else RecoveryAction.COMPATIBILITY_FALLBACK
            }

            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
            PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSOR_INIT_FAILED,
            PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED -> {
                if (attempt == 0) RecoveryAction.SAFE_MODE_RETRY else RecoveryAction.COMPATIBILITY_FALLBACK
            }

            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
            PlaybackException.ERROR_CODE_PERMISSION_DENIED,
            PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED,
            PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED,
            PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR,
            PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED,
            PlaybackException.ERROR_CODE_DRM_DISALLOWED_OPERATION,
            PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR,
            PlaybackException.ERROR_CODE_DRM_DEVICE_REVOKED,
            PlaybackException.ERROR_CODE_DRM_LICENSE_EXPIRED -> RecoveryAction.FAIL

            else -> if (attempt == 0) RecoveryAction.RETRY else RecoveryAction.COMPATIBILITY_FALLBACK
        }
    }

    fun retryDelayMs(attempt: Int): Long = when (attempt) {
        0 -> 500L
        1 -> 1_500L
        else -> 3_000L
    }
}
