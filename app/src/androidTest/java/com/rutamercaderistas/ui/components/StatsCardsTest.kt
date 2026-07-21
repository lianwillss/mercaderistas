package com.rutamercaderistas.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.rutamercaderistas.services.RuteroRepository
import com.rutamercaderistas.ui.theme.MercaderistasTheme
import org.junit.Rule
import org.junit.Test

class StatsCardsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun showsLocaleCount() {
        val stats = RuteroRepository.Stats(totalLocales = 5, totalMarcas = 12, visitasTotales = 20)
        composeTestRule.setContent {
            MercaderistasTheme {
                StatsCards(stats = stats)
            }
        }
        composeTestRule.onNodeWithText("5").assertIsDisplayed()
    }

    @Test
    fun showsBrandCount() {
        val stats = RuteroRepository.Stats(totalLocales = 5, totalMarcas = 12, visitasTotales = 20)
        composeTestRule.setContent {
            MercaderistasTheme {
                StatsCards(stats = stats)
            }
        }
        composeTestRule.onNodeWithText("12").assertIsDisplayed()
    }

    @Test
    fun showsVisitCount() {
        val stats = RuteroRepository.Stats(totalLocales = 5, totalMarcas = 12, visitasTotales = 20)
        composeTestRule.setContent {
            MercaderistasTheme {
                StatsCards(stats = stats)
            }
        }
        composeTestRule.onNodeWithText("20").assertIsDisplayed()
    }
}
