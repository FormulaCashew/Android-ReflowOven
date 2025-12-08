package com.example.reflowoven

import androidx.compose.ui.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.reflowoven.data.repository.ReflowOvenRepository
import com.example.reflowoven.ui.screen.DashboardScreen
import com.example.reflowoven.ui.viewmodel.MainViewModel
import io.mockk.mockk

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */

class DashboardScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun dashboard_shows_connect_button_when_disconnected() {
        val mockRepo = mockk<ReflowOvenRepository>(relaxed = true)
        val mockViewModel = MainViewModel(mockRepo) // Or use a mockk of ViewModel directly

        composeTestRule.setContent {
            DashboardScreen(viewModel = mockViewModel)
        }

        composeTestRule.onNodeWithText("Connect").assertIsDisplayed()

        composeTestRule.onNodeWithText("START").assertDoesNotExist()
    }

    @Test
    fun clicking_connect_opens_dialog() {
        val mockRepo = mockk<ReflowOvenRepository>(relaxed = true)
        val mockViewModel = MainViewModel(mockRepo)

        composeTestRule.setContent {
            DashboardScreen(viewModel = mockViewModel)
        }

        composeTestRule.onNodeWithText("Connect").performClick()

        composeTestRule.onNodeWithText("Connection Parameters").assertIsDisplayed()

        composeTestRule.onNodeWithText("IP Address").assertIsDisplayed()
    }
}