package com.thermalsentinel.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Launch-level smoke tests. They cover the two contracts that must not break
 * during the engine phase: the app launches into the Dashboard, and the
 * navigation drawer can reach a deep screen (Alert Rules).
 *
 * The splash screen holds the first Compose frame until DataStore emits its
 * first value, and DataStore reads on an IO dispatcher that waitForIdle does
 * not cover. Both tests wait explicitly for the target text to appear.
 */
@RunWith(AndroidJUnit4::class)
class NavigationSmokeTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun dashboardRendersOnLaunch() {
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("Device overview").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Device overview").assertIsDisplayed()
    }

    @Test
    fun drawerNavigatesToAlertRules() {
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("Device overview").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithContentDescription("Open navigation").performClick()
        rule.onNodeWithText("Alert Rules").performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("Warning threshold").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Warning threshold").assertIsDisplayed()
    }
}
