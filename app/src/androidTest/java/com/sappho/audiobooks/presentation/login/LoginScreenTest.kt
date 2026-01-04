package com.sappho.audiobooks.presentation.login

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sappho.audiobooks.presentation.theme.SapphoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()
    
    @Test
    fun loginScreen_displaysAllElements() {
        // Given
        composeTestRule.setContent {
            SapphoTheme {
                LoginScreen(
                    onLoginSuccess = {}
                )
            }
        }
        
        // Then - Verify all UI elements are displayed
        composeTestRule.onNodeWithText("Server URL").assertIsDisplayed()
        composeTestRule.onNodeWithText("Username").assertIsDisplayed()
        composeTestRule.onNodeWithText("Password").assertIsDisplayed()
        composeTestRule.onNodeWithText("Login").assertIsDisplayed()
    }
    
    @Test
    fun loginScreen_passwordVisibilityToggleWorks() {
        // Given
        composeTestRule.setContent {
            SapphoTheme {
                LoginScreen(
                    onLoginSuccess = {}
                )
            }
        }
        
        // When - Enter password
        composeTestRule.onNodeWithText("Password").performTextInput("testpass")
        
        // Then - Password should be hidden by default
        composeTestRule.onNode(hasText("testpass")).assertDoesNotExist()
        composeTestRule.onNode(hasText("••••••••")).assertExists()
        
        // When - Click visibility toggle
        composeTestRule.onNodeWithContentDescription("Show password").performClick()
        
        // Then - Password should be visible
        composeTestRule.onNodeWithText("testpass").assertExists()
        composeTestRule.onNodeWithContentDescription("Hide password").assertExists()
    }
    
    @Test
    fun loginScreen_showsLoadingDuringLogin() {
        // Test would require a mock ViewModel to control state
        // This is a placeholder for the test structure
    }
    
    @Test
    fun loginScreen_showsErrorMessage() {
        // Test would require a mock ViewModel to control state
        // This is a placeholder for the test structure
    }
}