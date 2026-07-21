package com.rutamercaderistas.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.rutamercaderistas.models.ClienteInfo
import com.rutamercaderistas.models.LocalDelDia
import com.rutamercaderistas.ui.theme.MercaderistasTheme
import org.junit.Rule
import org.junit.Test

class StoreCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testLocal = LocalDelDia(
        codigo = "001",
        local = "Supermercado Central",
        direccion = "Av. Siempre Viva 123",
        comuna = "Santiago",
        region = "RM",
        cadena = "Jumbo",
        formato = "Supermercado",
        clientes = listOf(
            ClienteInfo(nombre = "Coca-Cola", esPrioritaria = true, frecuencia = 7),
            ClienteInfo(nombre = "Nestlé", esPrioritaria = false, frecuencia = 3),
        ),
    )

    @Test
    fun showsStoreName() {
        composeTestRule.setContent {
            MercaderistasTheme {
                StoreCard(
                    local = testLocal,
                    marcaResaltada = null,
                    onBrandClick = {},
                    onAddressClick = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Supermercado Central").assertIsDisplayed()
    }

    @Test
    fun showsBrandNames() {
        composeTestRule.setContent {
            MercaderistasTheme {
                StoreCard(
                    local = testLocal,
                    marcaResaltada = null,
                    onBrandClick = {},
                    onAddressClick = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Coca-Cola").assertIsDisplayed()
        composeTestRule.onNodeWithText("Nestlé").assertIsDisplayed()
    }
}
