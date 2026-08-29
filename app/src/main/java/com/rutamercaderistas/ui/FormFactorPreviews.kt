package com.rutamercaderistas.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import com.rutamercaderistas.data.local.PromotionEntity
import com.rutamercaderistas.models.ClienteInfo
import com.rutamercaderistas.models.LocalDelDia
import com.rutamercaderistas.ui.screens.AllLocalesScreen
import com.rutamercaderistas.ui.screens.ManualScreen
import com.rutamercaderistas.ui.screens.PromotionsOverviewScreen
import com.rutamercaderistas.ui.theme.MercaderistasTheme

@Preview(name = "Phone", device = Devices.PHONE, showBackground = true)
@Preview(name = "Foldable", device = Devices.FOLDABLE, showBackground = true)
@Preview(name = "Tablet", device = Devices.TABLET, showBackground = true)
@Preview(name = "Desktop", device = Devices.DESKTOP, showBackground = true)
annotation class FormFactorPreviews

private val samplePromos = mapOf(
    "CUK" to listOf(
        PromotionEntity(brand = "CUK", chain = "Jumbo", productName = "Arroz 1 kg", price = "\$1.990", endDate = "2026-07-31"),
        PromotionEntity(brand = "CUK", chain = "Jumbo", productName = "Fideos 500 g", price = "2x\$1.500", endDate = "2026-07-31"),
    ),
    "OLIMPIA" to listOf(
        PromotionEntity(brand = "OLIMPIA", chain = "Lider", productName = "Té Verde 20 un.", price = "\$1.490"),
    ),
)

private val sampleLocales = listOf(
    LocalDelDia(
        codigo = "123",
        local = "Local A",
        direccion = "Av. Siempre Viva 123",
        clientes = listOf(ClienteInfo("CUK", true, 7)),
    ),
    LocalDelDia(
        codigo = "124",
        local = "Local B",
        direccion = "Calle Falsa 456",
        clientes = listOf(ClienteInfo("OLIMPIA", false, 3)),
    ),
)

@FormFactorPreviews
@Composable
fun ManualScreenPreview() {
    MercaderistasTheme {
        ManualScreen(onClose = {})
    }
}

@FormFactorPreviews
@Composable
fun PromotionsOverviewScreenPreview() {
    MercaderistasTheme {
        PromotionsOverviewScreen(
            promotionsByBrand = samplePromos,
            onClose = {},
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@FormFactorPreviews
@Composable
fun AllLocalesScreenPreview() {
    MercaderistasTheme {
        AllLocalesScreen(
            locales = sampleLocales,
            onClose = {},
            onAddressClick = {},
        )
    }
}
