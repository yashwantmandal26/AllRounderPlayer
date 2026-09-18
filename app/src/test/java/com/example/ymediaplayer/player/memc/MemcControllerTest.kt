package com.example.ymediaplayer.player.memc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemcControllerTest {
    @Test
    fun `availability requires both an active and off profile`() {
        assertFalse(MemcAvailability(profileIds = mapOf(MemcMode.HIGH to "high")).isSupported)
        assertFalse(MemcAvailability(profileIds = mapOf(MemcMode.OFF to "off")).isSupported)
        assertTrue(
            MemcAvailability(
                profileIds = mapOf(MemcMode.OFF to "off", MemcMode.HIGH to "high")
            ).isSupported
        )
    }

    @Test
    fun `unsupported strengths remain disabled while off is always safe`() {
        val availability = MemcAvailability(
            profileIds = mapOf(MemcMode.OFF to "off", MemcMode.LOW to "low")
        )

        assertTrue(availability.supports(MemcMode.OFF))
        assertTrue(availability.supports(MemcMode.LOW))
        assertFalse(availability.supports(MemcMode.MEDIUM))
        assertFalse(availability.supports(MemcMode.HIGH))
    }

    @Test
    fun `software backend supplies every strength without OEM profiles`() {
        val availability = MemcAvailability(softwareSupported = true)

        assertTrue(availability.isSupported)
        assertTrue(availability.usesSoftware(MemcMode.LOW))
        assertTrue(availability.supports(MemcMode.MEDIUM))
        assertTrue(availability.supports(MemcMode.HIGH))
        assertFalse(availability.usesSoftware(MemcMode.OFF))
    }

    @Test
    fun `reported profile maps back to its actual mode`() {
        val availability = MemcAvailability(
            profileIds = mapOf(MemcMode.OFF to "profile-0", MemcMode.MEDIUM to "profile-2")
        )

        assertEquals(MemcMode.MEDIUM, availability.modeForProfileId("profile-2"))
        assertNull(availability.modeForProfileId("unknown"))
        assertNull(availability.modeForProfileId(null))
    }

    @Test
    fun `invalid persisted mode falls back to off`() {
        assertEquals(MemcMode.OFF, MemcMode.fromStoredValue("turbo"))
        assertEquals(MemcMode.OFF, MemcMode.fromStoredValue(null))
    }
}
