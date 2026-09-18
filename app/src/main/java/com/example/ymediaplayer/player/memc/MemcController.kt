@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.ymediaplayer.player.memc

import android.content.Context
import android.media.MediaFormat
import android.os.Build
import androidx.media3.exoplayer.CodecParameters
import androidx.media3.exoplayer.ExoPlayer

enum class MemcMode(val storedValue: String, val displayName: String) {
    OFF("off", "Off"),
    LOW("low", "Low"),
    MEDIUM("medium", "Medium"),
    HIGH("high", "High");

    companion object {
        fun fromStoredValue(value: String?): MemcMode =
            entries.firstOrNull { it.storedValue == value } ?: OFF
    }
}

data class MemcAvailability(
    val profileIds: Map<MemcMode, String> = emptyMap(),
    val profileNames: Map<MemcMode, String> = emptyMap(),
    val softwareSupported: Boolean = false,
    val unavailableReason: String? = null
) {
    val hardwareSupported: Boolean
        get() = profileIds.containsKey(MemcMode.OFF) &&
            profileIds.keys.any { it != MemcMode.OFF }

    val isSupported: Boolean
        get() = hardwareSupported || softwareSupported

    fun supports(mode: MemcMode): Boolean =
        mode == MemcMode.OFF || softwareSupported || (hardwareSupported && profileIds.containsKey(mode))

    fun usesSoftware(mode: MemcMode): Boolean =
        mode != MemcMode.OFF && softwareSupported && !profileIds.containsKey(mode)

    fun modeForProfileId(profileId: String?): MemcMode? =
        profileId?.let { id -> profileIds.entries.firstOrNull { it.value == id }?.key }
}

data class MemcApplyResult(
    val applied: Boolean,
    val message: String
)

/**
 * Applies the platform/OEM motion-compensation picture profile to MediaCodec.
 *
 * Android exposes this path on API 37+ TV devices. Phones and TVs without a
 * MEMC-capable Media Quality HAL intentionally report unsupported rather than
 * simulating interpolation by repeating or blending frames.
 */
object MemcController {
    private const val MIN_MEMC_API = 37

    fun discover(context: Context): MemcAvailability {
        val softwareSupported = supportsSoftwareMemc(context)
        if (Build.VERSION.SDK_INT < MIN_MEMC_API) {
            return MemcAvailability(
                softwareSupported = softwareSupported,
                unavailableReason = if (softwareSupported) null else
                    "Software MEMC requires Android 10+ with OpenGL ES 3.0"
            )
        }
        val hardware = Api37.discover(context)
        return hardware.copy(
            softwareSupported = softwareSupported,
            unavailableReason = if (hardware.hardwareSupported || softwareSupported) null else
                hardware.unavailableReason
        )
    }

    private fun supportsSoftwareMemc(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            ?: return false
        return activityManager.deviceConfigurationInfo.reqGlEsVersion >= 0x00030000
    }

    fun apply(
        player: ExoPlayer,
        mode: MemcMode,
        availability: MemcAvailability
    ): MemcApplyResult {
        if (Build.VERSION.SDK_INT < MIN_MEMC_API) {
            return MemcApplyResult(false, availability.unavailableReason ?: "MEMC is unavailable")
        }

        val profileId = availability.profileIds[mode]
        if (profileId == null) {
            if (availability.usesSoftware(mode)) {
                return MemcApplyResult(true, "Starting clean-room software MEMC (${mode.displayName.lowercase()})")
            }
            return if (mode == MemcMode.OFF) {
                // No profile has been applied in this session, so ordinary playback is already active.
                MemcApplyResult(true, "Motion smoothing off")
            } else {
                MemcApplyResult(false, "${mode.displayName} MEMC is not provided by this device")
            }
        }

        return runCatching {
            player.setVideoCodecParameters(
                CodecParameters.Builder()
                    .setString(MediaFormat.KEY_PICTURE_PROFILE_ID, profileId)
                    .build()
            )
            val profileName = availability.profileNames[mode]
            val detail = if (profileName.isNullOrBlank()) "" else " ($profileName)"
            MemcApplyResult(true, "Requested ${mode.displayName.lowercase()} motion smoothing$detail")
        }.getOrElse { error ->
            MemcApplyResult(false, "Could not apply MEMC: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    @androidx.annotation.RequiresApi(MIN_MEMC_API)
    private object Api37 {
        fun discover(context: Context): MemcAvailability {
            return runCatching {
                val manager = context.getSystemService(android.media.quality.MediaQualityManager::class.java)
                    ?: return MemcAvailability(unavailableReason = "This device has no Media Quality service")
                val query = android.media.quality.MediaQualityManager.ProfileQueryParams.Builder()
                    .setParametersIncluded(true)
                    .build()
                val profiles = manager.getAvailablePictureProfiles(query).orEmpty()
                val ids = linkedMapOf<MemcMode, String>()
                val names = linkedMapOf<MemcMode, String>()

                profiles.forEach { profile ->
                    val id = profile.profileId ?: return@forEach
                    val name = profile.name.orEmpty()

                    val value = profile.parameters.getString(
                        android.media.quality.MediaQualityContract.PictureQuality.PARAMETER_MEMC_EFFECT
                    ) ?: return@forEach
                    val mode = when (value) {
                        android.media.quality.MediaQualityContract.LEVEL_OFF -> MemcMode.OFF
                        android.media.quality.MediaQualityContract.LEVEL_LOW -> MemcMode.LOW
                        android.media.quality.MediaQualityContract.LEVEL_MEDIUM -> MemcMode.MEDIUM
                        android.media.quality.MediaQualityContract.LEVEL_HIGH -> MemcMode.HIGH
                        else -> null
                    } ?: return@forEach
                    ids.putIfAbsent(mode, id)
                    names.putIfAbsent(mode, name)
                }

                if (ids.keys.none { it != MemcMode.OFF }) {
                    MemcAvailability(
                        profileIds = ids,
                        profileNames = names,
                        unavailableReason = "The OEM exposes no MEMC picture profiles"
                    )
                } else if (!ids.containsKey(MemcMode.OFF)) {
                    MemcAvailability(
                        profileIds = ids,
                        profileNames = names,
                        unavailableReason = "The OEM exposes MEMC profiles but no safe profile to turn MEMC off"
                    )
                } else {
                    MemcAvailability(profileIds = ids, profileNames = names)
                }
            }.getOrElse { error ->
                MemcAvailability(
                    unavailableReason = "MEMC capability check failed: ${error.message ?: error.javaClass.simpleName}"
                )
            }
        }
    }
}
