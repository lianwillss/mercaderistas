package com.rutamercaderistas.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.rutamercaderistas.R
import com.rutamercaderistas.ui.theme.MercaderistasTheme
import org.junit.Rule
import org.junit.Test

/**
 * La barra inferior es flotante y tapa la lista: al avanzar por el contenido
 * debe apartarse, y al retroceder (o al cambiar de pantalla) debe volver. El
 * ancho se fija en Compact para que el scaffold use la barra y no el riel.
 */
class LiquidGlassNavigationScaffoldTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val mainLabel: String
        get() = ApplicationProvider.getApplicationContext<Context>()
            .getString(R.string.rutero_title)

    private fun content(itemCount: Int = 60, resetKey: () -> Any? = { null }) {
        composeTestRule.setContent {
            MercaderistasTheme {
                Box(modifier = Modifier.size(360.dp, 640.dp)) {
                    LiquidGlassNavigationScaffold(
                        selectedKey = BottomBarKey.MAIN,
                        onNavigate = {},
                        navigationVisible = true,
                        marcasConPromo = 0,
                        hasPlanillaChanges = false,
                        modifier = Modifier.fillMaxSize(),
                        navigationResetKey = resetKey(),
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("list"),
                        ) {
                            items(itemCount) { index ->
                                Text("Item $index", modifier = Modifier.size(80.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun hidesOnScrollDownAndReturnsOnScrollUp() {
        content()
        composeTestRule.onNodeWithText(mainLabel).assertIsDisplayed()

        composeTestRule.onNodeWithTag("list").performTouchInput { swipeUp() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(mainLabel).assertDoesNotExist()

        composeTestRule.onNodeWithTag("list").performTouchInput { swipeDown() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(mainLabel).assertIsDisplayed()
    }

    @Test
    fun keepsTheBarWhenTheContentDoesNotScroll() {
        content(itemCount = 2)
        composeTestRule.onNodeWithTag("list").performTouchInput { swipeUp() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(mainLabel).assertIsDisplayed()
    }

    @Test
    fun changingTheNavigationEntryShowsTheBarAgain() {
        var resetKey by mutableStateOf<Any?>("main")
        content(resetKey = { resetKey })
        composeTestRule.onNodeWithTag("list").performTouchInput { swipeUp() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(mainLabel).assertDoesNotExist()

        composeTestRule.runOnUiThread { resetKey = "settings" }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(mainLabel).assertIsDisplayed()
    }
}
