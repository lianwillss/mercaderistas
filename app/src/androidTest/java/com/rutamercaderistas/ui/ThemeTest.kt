package com.rutamercaderistas.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rutamercaderistas.ui.theme.MercaderistasTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun mercaderistasTheme_rendersText() {
        composeTestRule.setContent {
            MercaderistasTheme {
                Text("Hola")
            }
        }
        composeTestRule.onNodeWithText("Hola").assertExists()
    }
}
