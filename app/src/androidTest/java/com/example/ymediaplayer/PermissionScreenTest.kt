package com.example.ymediaplayer

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PermissionScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun permissionFallbackScreen_rendersAndHandlesClicks() {
        var grantClicked = false
        var settingsClicked = false

        composeTestRule.setContent {
            PermissionFallbackScreen(
                onRequestPermissions = { grantClicked = true },
                onOpenSettings = { settingsClicked = true }
            )
        }

        // Verify title & explanation are displayed
        composeTestRule.onNodeWithText("Permission Required").assertExists()
        composeTestRule.onNodeWithText("Grant Permission").assertExists()
        composeTestRule.onNodeWithText("Open Settings").assertExists()

        // Verify click interactions invoke callbacks
        composeTestRule.onNodeWithText("Grant Permission").performClick()
        assertTrue("Expected grant callback to be invoked", grantClicked)

        composeTestRule.onNodeWithText("Open Settings").performClick()
        assertTrue("Expected settings callback to be invoked", settingsClicked)
    }
}
