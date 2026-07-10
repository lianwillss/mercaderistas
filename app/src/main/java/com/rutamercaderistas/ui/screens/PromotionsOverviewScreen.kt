package com.rutamercaderistas.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rutamercaderistas.data.local.PromotionEntity
import com.rutamercaderistas.ui.components.PromotionBadge
import com.rutamercaderistas.ui.components.PromotionList
import com.rutamercaderistas.ui.theme.storeColor
import com.rutamercaderistas.ui.theme.storeSoftColor

@Composable
fun PromotionsOverviewScreen(
    promotionsByBrand: Map<String, List<PromotionEntity>>,
    onClose: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }

    val sortedEntries = remember(promotionsByBrand) {
        promotionsByBrand.entries.sortedBy { it.key }
    }

    val filteredEntries by remember(sortedEntries, searchQuery) {
        derivedStateOf {
            if (searchQuery.isBlank()) sortedEntries
            else {
                val q = searchQuery.lowercase().trim()
                sortedEntries.filter { (brand, _) ->
                    brand.lowercase().contains(q)
                }
            }
        }
    }

    val totalPromos = remember(promotionsByBrand) {
        promotionsByBrand.values.sumOf { it.size }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = "Promociones",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Buscar marca\u2026") },
            leadingIcon = {
                Icon(Icons.Outlined.Search, contentDescription = "Buscar", modifier = Modifier.size(18.dp))
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Limpiar", modifier = Modifier.size(18.dp))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
        )

        Text(
            text = "${filteredEntries.size} marcas con promociones",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(
                items = filteredEntries,
                key = { _, entry -> entry.key }
            ) { _, (brand, promos) ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(storeSoftColor(brand)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ShoppingBag,
                                    contentDescription = "Marca",
                                    tint = storeColor(brand),
                                    modifier = Modifier.size(14.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Text(
                                text = brand,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            PromotionBadge(count = promos.size)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        PromotionList(promotions = promos)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PromotionsOverviewScreenPreview() {
    val testData = mapOf(
        "CUK" to listOf(
            PromotionEntity(brand = "CUK", chain = "Jumbo", productName = "Arroz 1 kg", price = "$1.990", endDate = "2026-07-31"),
            PromotionEntity(brand = "CUK", chain = "Jumbo", productName = "Fideos 500 g", price = "2x$1.500", endDate = "2026-07-31"),
        ),
        "OLIMPIA" to listOf(
            PromotionEntity(brand = "OLIMPIA", chain = "Lider", productName = "Té Verde 20 un.", price = "$1.490"),
        ),
    )
    com.rutamercaderistas.ui.theme.MercaderistasTheme {
        PromotionsOverviewScreen(
            promotionsByBrand = testData,
            onClose = {},
        )
    }
}
