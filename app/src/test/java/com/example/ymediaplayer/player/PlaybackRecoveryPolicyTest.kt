package com.example.ymediaplayer.player

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackRecoveryPolicyTest {
    @Test
    fun networkFailuresRetryThreeTimesThenStop() {
        val code = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
        assertEquals(RecoveryAction.RETRY, PlaybackRecoveryPolicy.actionFor(code, 0, false))
        assertEquals(RecoveryAction.RETRY, PlaybackRecoveryPolicy.actionFor(code, 2, false))
        assertEquals(RecoveryAction.FAIL, PlaybackRecoveryPolicy.actionFor(code, 3, false))
    }

    @Test
    fun unsupportedCodecTriesSafeModeThenCompatibilityBackend() {
        val code = PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED
        assertEquals(RecoveryAction.SAFE_MODE_RETRY, PlaybackRecoveryPolicy.actionFor(code, 0, false))
        assertEquals(RecoveryAction.COMPATIBILITY_FALLBACK, PlaybackRecoveryPolicy.actionFor(code, 1, false))
    }

    @Test
    fun audioDecoderFailureCanContinueWithVideoOnly() {
        val code = PlaybackException.ERROR_CODE_DECODING_FAILED
        assertEquals(RecoveryAction.SAFE_MODE_RETRY, PlaybackRecoveryPolicy.actionFor(code, 0, true))
        assertEquals(RecoveryAction.DISABLE_AUDIO_RETRY, PlaybackRecoveryPolicy.actionFor(code, 1, true))
    }

    @Test
    fun permissionFailureNeverLoops() {
        assertEquals(
            RecoveryAction.FAIL,
            PlaybackRecoveryPolicy.actionFor(PlaybackException.ERROR_CODE_IO_NO_PERMISSION, 0, false)
        )
    }

    @Test
    fun retryBackoffIsBounded() {
        assertEquals(500L, PlaybackRecoveryPolicy.retryDelayMs(0))
        assertEquals(1_500L, PlaybackRecoveryPolicy.retryDelayMs(1))
        assertEquals(3_000L, PlaybackRecoveryPolicy.retryDelayMs(20))
    }
}
