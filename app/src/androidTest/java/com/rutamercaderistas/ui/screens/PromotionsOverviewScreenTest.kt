package com.rutamercaderistas.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import com.rutamercaderistas.R
import com.rutamercaderistas.data.local.PromotionEntity
import com.rutamercaderistas.ui.theme.MercaderistasTheme
import org.junit.Rule
import org.junit.Test

class PromotionsOverviewScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun showsEmptyMessage_whenNoPromotions() {
        composeTestRule.setContent {
            MercaderistasTheme {
                PromotionsOverviewScreen(
                    promotionsByBrand = emptyMap(),
                    onClose = {},
                )
            }
        }
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.sin_promociones_activas)
        ).assertIsDisplayed()
    }

    @Test
    fun showsBrandName_whenPromotionsExist() {
        val testData = mapOf(
            "CUK" to listOf(
                PromotionEntity(brand = "CUK", chain = "Jumbo", productName = "Arroz 1 kg", price = "$1.990"),
            ),
        )
        composeTestRule.setContent {
            MercaderistasTheme {
                PromotionsOverviewScreen(
                    promotionsByBrand = testData,
                    onClose = {},
                )
            }
        }
        composeTestRule.onNodeWithText("CUK").assertIsDisplayed()
    }

    @Test
    fun showsPromoCount_whenPromotionsExist() {
        val testData = mapOf(
            "CUK" to listOf(
                PromotionEntity(brand = "CUK", chain = "Jumbo", productName = "Arroz 1 kg", price = "$1.990"),
                PromotionEntity(brand = "CUK", chain = "Jumbo", productName = "Fideos 500 g", price = "$890"),
            ),
        )
        composeTestRule.setContent {
            MercaderistasTheme {
                PromotionsOverviewScreen(
                    promotionsByBrand = testData,
                    onClose = {},
                )
            }
        }
        composeTestRule.onNodeWithText("CUK").assertIsDisplayed()
    }

    @Test
    fun lastBrandCanBeReachedAfterScrolling() {
        val testData = (0 until 20).associate { index ->
            "BRAND_$index" to listOf(
                PromotionEntity(
                    brand = "BRAND_$index",
                    chain = "Jumbo",
                    productName = "Producto $index",
                    price = "$1.000",
                ),
            )
        }
        composeTestRule.setContent {
            MercaderistasTheme {
                PromotionsOverviewScreen(
                    promotionsByBrand = testData,
                    onClose = {},
                )
            }
        }

        composeTestRule.onRoot().performScrollToNode(hasText("BRAND_19"))
        composeTestRule.onNodeWithText("BRAND_19").assertIsDisplayed()
    }
}
