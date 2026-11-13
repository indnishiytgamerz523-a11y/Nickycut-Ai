package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.core.MediaStorageUtils
import com.example.core.StudioAppContainer
import com.example.data.model.AspectRatioOption
import com.example.data.model.ResolutionOption
import com.example.data.model.SubtitleSegment
import com.example.media.AudioEngine
import com.example.media.VideoCanvasRenderer
import com.example.ui.theme.NickyCutTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `app name matches NickyCut AI`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("NickyCut AI", context.getString(R.string.app_name))
    }

    @Test
    fun `srt generator formats valid subtitle timestamps`() {
        val srt = VideoCanvasRenderer.generateSrtString(
            listOf(
                SubtitleSegment(startMs = 0L, endMs = 2500L, text = "Hello Creators"),
                SubtitleSegment(startMs = 2600L, endMs = 5000L, text = "NickyCut AI Studio")
            )
        )
        assertTrue(srt.contains("00:00:00,000 --> 00:00:02,500"))
        assertTrue(srt.contains("Hello Creators"))
        assertTrue(srt.contains("00:00:02,600 --> 00:00:05,000"))
    }

    @Test
    fun `wav writer produces valid RIFF header`() {
        val tempFile = File.createTempFile("test_audio", ".wav")
        try {
            AudioEngine.writeWavFile(shortArrayOf(0, 1000, -1000, 500), 44100, 1, tempFile)
            val bytes = tempFile.readBytes()
            assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
            assertEquals("WAVE", String(bytes, 8, 4, Charsets.US_ASCII))
            assertEquals(44 + 8, bytes.size)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `aspect ratio dimension calculation matches 720p and 1080p`() {
        assertEquals(720 to 1280, AspectRatioOption.VERTICAL_9_16.dimensionsFor(ResolutionOption.HD_720P))
        assertEquals(1920 to 1080, AspectRatioOption.LANDSCAPE_16_9.dimensionsFor(ResolutionOption.FHD_1080P))
        assertEquals(1080 to 1080, AspectRatioOption.SQUARE_1_1.dimensionsFor(ResolutionOption.FHD_1080P))
    }

    @Test
    fun `duration formatting renders minutes seconds tenths`() {
        assertEquals("00:05.5", MediaStorageUtils.formatDuration(5500L))
        assertEquals("01:02.0", MediaStorageUtils.formatDuration(62000L))
    }

    @Test
    fun `home dashboard navigates across bottom tabs and search filters tools`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val container = StudioAppContainer(context)

        composeRule.setContent {
            NickyCutTheme {
                NickyCutAppRoot(container = container)
            }
        }

        // Advance past splash screen
        composeRule.onNodeWithTag("splash_screen").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("home_dashboard").assertIsDisplayed()

        // Search filters tools
        composeRule.onNodeWithTag("home_search_input").performTextInput("Kinetic")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("tool_card_kinetic_typography").assertIsDisplayed()

        // Bottom navigation to Tools, Projects, Settings
        composeRule.onNodeWithTag("bottom_nav_tools").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("all_tools_screen").assertIsDisplayed()

        composeRule.onNodeWithTag("bottom_nav_projects").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("projects_screen").assertIsDisplayed()

        composeRule.onNodeWithTag("bottom_nav_settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_screen").assertIsDisplayed()
        composeRule.onNodeWithText("Default Export Resolution").assertIsDisplayed()
    }
}
