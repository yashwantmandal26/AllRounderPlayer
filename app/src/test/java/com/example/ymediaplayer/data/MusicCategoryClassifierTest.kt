package com.example.ymediaplayer.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MusicCategoryClassifierTest {
    @Test
    fun normalSongStartingWithCallStaysInTracks() {
        assertEquals(
            AudioCategory.SONG,
            classifyAudioCategory(
                title = "Call Me Maybe",
                path = "/storage/emulated/0/Music/Call Me Maybe.mp3",
                relativePath = "Music/",
                duration = 199_000L
            )
        )
    }

    @Test
    fun shortAudioInGenericMediaFolderStaysInTracks() {
        assertEquals(
            AudioCategory.SONG,
            classifyAudioCategory(
                title = "Interlude",
                path = "/storage/emulated/0/Android/media/Interlude.mp3",
                relativePath = "Android/media/",
                duration = 18_000L
            )
        )
    }

    @Test
    fun knownWhatsappVoiceNoteIsARecording() {
        assertEquals(
            AudioCategory.RECORDING,
            classifyAudioCategory(
                title = "AUD-20261027-WA0123",
                path = "/storage/emulated/0/WhatsApp/Media/WhatsApp Voice Notes/AUD-20261027-WA0123.opus",
                relativePath = "WhatsApp/Media/WhatsApp Voice Notes/",
                duration = 12_000L
            )
        )
    }

    @Test
    fun ringtoneFolderIsClassifiedAsRingtone() {
        assertEquals(
            AudioCategory.RINGTONE,
            classifyAudioCategory(
                title = "My tone",
                path = "/storage/emulated/0/Ringtones/My tone.mp3",
                relativePath = "Ringtones/",
                duration = 20_000L
            )
        )
    }
}
