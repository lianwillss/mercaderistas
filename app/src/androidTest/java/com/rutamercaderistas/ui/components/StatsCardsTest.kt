package com.rutamercaderistas.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.rutamercaderistas.R
import com.rutamercaderistas.services.RuteroRepository
import com.rutamercaderistas.ui.theme.MercaderistasTheme
import org.junit.Rule
import org.junit.Test

class StatsCardsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun appString(resId: Int): String =
        ApplicationProvider.getApplicationContext<Context>().getString(resId)

    // La UI muestra una sola tarjeta (códigos de proveedor); el parámetro
    // stats ya no se usa para el conteo. Estos tests fijan ese contrato.
    @Test
    fun showsCodProvCount() {
        val stats = RuteroRepository.Stats(totalLocales = 5, totalMarcas = 12, visitasTotales = 20)
        composeTestRule.setContent {
            MercaderistasTheme {
                StatsCards(stats = stats)
            }
        }
        composeTestRule.onNodeWithText(CodProvItems.size.toString()).assertIsDisplayed()
    }

    @Test
    fun showsCodProvLabel() {
        val stats = RuteroRepository.Stats(totalLocales = 5, totalMarcas = 12, visitasTotales = 20)
        composeTestRule.setContent {
            MercaderistasTheme {
                StatsCards(stats = stats)
            }
        }
        composeTestRule.onNodeWithText(appString(R.string.stats_cod_prov_label)).assertIsDisplayed()
    }
}
