package com.rutamercaderistas.ui.screens

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.WriterException
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.rutamercaderistas.R
import com.rutamercaderistas.data.local.EanProductEntity
import com.rutamercaderistas.services.brandNote
import com.rutamercaderistas.services.normalizeSearch
import com.rutamercaderistas.ui.components.HighlightedText
import com.rutamercaderistas.ui.theme.AccentBlue
import com.rutamercaderistas.ui.theme.AccentBlueSoft
import com.rutamercaderistas.ui.theme.AccentGreen
import com.rutamercaderistas.ui.theme.AccentGreenSoft
import com.rutamercaderistas.ui.theme.AccentGreenText
import com.rutamercaderistas.ui.theme.AccentOrange
import com.rutamercaderistas.ui.theme.AccentOrangeSoft
import com.rutamercaderistas.ui.theme.LocalAppDimens
import com.rutamercaderistas.ui.theme.MercaderistasTheme
import com.rutamercaderistas.ui.theme.StoreColorFuchsia
import com.rutamercaderistas.ui.theme.StoreColorFuchsiaSoft
import com.rutamercaderistas.ui.theme.StoreColorPurple
import com.rutamercaderistas.ui.theme.StoreColorPurpleSoft
import com.rutamercaderistas.ui.theme.StoreColorRed
import com.rutamercaderistas.ui.theme.StoreColorRedSoft
import com.rutamercaderistas.ui.theme.StoreColorYellow
import com.rutamercaderistas.ui.theme.StoreColorYellowSoft
import com.rutamercaderistas.ui.theme.rs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val barcodeCache = LruCache<String, Bitmap>(64)

// Paleta de acentos por marca: color estable derivado del nombre para que cada
// marca sea reconocible por color en la interfaz (punto en el encabezado y chip).
private val BRAND_PALETTE = listOf(
    AccentBlue to AccentBlueSoft,
    AccentGreen to AccentGreenSoft,
    AccentOrange to AccentOrangeSoft,
    StoreColorPurple to StoreColorPurpleSoft,
    StoreColorFuchsia to StoreColorFuchsiaSoft,
    StoreColorRed to StoreColorRedSoft,
    StoreColorYellow to StoreColorYellowSoft,
)

internal fun brandPair(name: String): Pair<Color, Color> {
    val h = normalizeSearch(name).hashCode()
    val idx = if (h < 0) -h else h
    return BRAND_PALETTE[idx % BRAND_PALETTE.size]
}

@Composable
internal fun EanProductCard(
    product: EanProductEntity,
    query: String = "",
    onBarcodeClick: (EanProductEntity) -> Unit = {},
) {
    val dimens = LocalAppDimens.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(dimens.spacingMd)) {
            HighlightedText(
                text = product.descripcionProducto.ifBlank { product.eanPrincipal },
                query = query,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )

            if (product.eanPrincipal.isNotBlank()) {
                Spacer(modifier = Modifier.height(dimens.spacingSm))
                EanBarcodeView(
                    ean = product.eanPrincipal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp)
                        .clip(MaterialTheme.shapes.small)
                        .clickable(
                            role = Role.Button,
                            onClickLabel = stringResource(R.string.ean_zoom_cd),
                            onClick = { onBarcodeClick(product) },
                        ),
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
                ) {
                    if (product.eanPrincipal.isNotBlank()) {
                        EanCodeChip(
                            label = "EAN",
                            value = product.eanPrincipal,
                            query = query,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (product.codCencosud.isNotBlank()) {
                        EanCodeChip(
                            label = "SKU",
                            value = product.codCencosud,
                            query = query,
                            color = AccentGreenText,
                        )
                    }
                    if (product.conversion.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.tertiaryContainer)
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "CAJA",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "×${product.conversion}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                            }
                        }
                    }
                    if (product.codProveedor.isNotBlank() && product.codProveedor.trim() != "19") {
                        EanCodeChip(
                            label = "Prov",
                            value = product.codProveedor,
                            query = query,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }

                if (product.marca.isNotBlank() || product.estado.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
                    ) {
                        if (product.marca.isNotBlank()) {
                            BrandChip(name = product.marca, query = query)
                        }
                        if (product.estado.isNotBlank()) {
                            Text(
                                text = product.estado,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                val categorias = listOfNotNull(
                    product.catN1Cencosud.takeIf { it.isNotBlank() },
                    product.catN2Cencosud.takeIf { it.isNotBlank() },
                )
                if (categorias.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = categorias.joinToString("  ›  "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
internal fun EanBrandHeader(title: String) {
    val dimens = LocalAppDimens.current
    val s = rs()
    val (accent, soft) = brandPair(title)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.spacingMd, vertical = 4.dp * s)
            .clip(RoundedCornerShape(12.dp))
            .background(soft),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp * s, vertical = 8.dp * s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp * s)
                    .clip(CircleShape)
                    .background(accent),
            )
            Spacer(modifier = Modifier.width(8.dp * s))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                val note = brandNote(title)
                if (note != null) {
                    Text(
                        text = note,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun BrandChip(name: String, query: String = "") {
    val s = rs()
    val (accent, soft) = brandPair(name)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(soft)
            .padding(horizontal = 8.dp * s, vertical = 3.dp * s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp * s)
                .clip(CircleShape)
                .background(accent),
        )
        Spacer(modifier = Modifier.width(5.dp * s))
        HighlightedText(
            text = name,
            query = query,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun EanCodeChip(label: String, value: String, color: Color, query: String = "") {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
            )
            HighlightedText(
                text = value,
                query = query,
                style = MaterialTheme.typography.labelMedium,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun EanBarcodeView(ean: String, modifier: Modifier = Modifier) {
    val isAlphanumeric = remember(ean) { ean.any { !it.isDigit() } }
    val digits = remember(ean) { ean.filter { it.isDigit() } }
    var bitmap by remember(ean) { mutableStateOf(barcodeCache.get(ean)) }

    LaunchedEffect(ean) {
        val cached = barcodeCache.get(ean)
        if (cached != null) {
            bitmap = cached
            return@LaunchedEffect
        }
        withContext(Dispatchers.Default) {
            val generated = try {
                val encoder = BarcodeEncoder()
                // EAN-13 con 0 inicial pierde el 0 al escanearse como UPC-A en algunos lectores.
                // Generamos CODE_128 para preservar el 0 inicial exacto o para códigos alfanuméricos como FLEJES.
                val format = if (!isAlphanumeric && digits.length == 13 && digits[0] != '0') BarcodeFormat.EAN_13 else if (!isAlphanumeric && digits.length == 12) BarcodeFormat.EAN_13 else BarcodeFormat.CODE_128
                val data = if (format == BarcodeFormat.CODE_128) ean else digits
                encoder.encodeBitmap(data, format, 800, 280)
            } catch (e: WriterException) {
                try {
                    BarcodeEncoder().encodeBitmap(if (isAlphanumeric) ean else digits, BarcodeFormat.CODE_128, 800, 280)
                } catch (e2: WriterException) {
                    null
                }
            } catch (e: IllegalArgumentException) {
                null
            }
            if (generated != null) barcodeCache.put(ean, generated)
            bitmap = generated
        }
    }

    Box(
        modifier = modifier
            .background(Color.White)
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        val current = bitmap
        if (current != null) {
            Image(
                bitmap = current.asImageBitmap(),
                contentDescription = stringResource(R.string.ean_barcode_cd, ean),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = Color.Gray,
                strokeWidth = 2.dp,
            )
        }
    }
}

@Composable
internal fun EanEmptyState(query: String, onClear: () -> Unit = {}) {
    val dimens = LocalAppDimens.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(dimens.spacingXl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(48.dp),
        )
        Spacer(modifier = Modifier.height(dimens.spacingMd))
        Text(
            text = stringResource(R.string.ean_empty_results, query),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(dimens.spacingXs))
        Text(
            text = stringResource(R.string.ean_no_results_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(dimens.spacingMd))
        Button(
            onClick = onClear,
            modifier = Modifier.heightIn(min = dimens.touchMin),
        ) {
            Text(stringResource(R.string.ean_clear_search))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EanProductCardPreview() {
    MercaderistasTheme {
        EanProductCard(
            product = EanProductEntity(
                eanPrincipal = "7804690890292",
                codCencosud = "2087431",
                descripcionProducto = "ACEITE OLIVA EXTR VIRG TRUFA BACIO 500ML",
                marca = "KEYFOOD",
                conversion = "12",
                catN1Cencosud = "ALIMENTOS",
                catN2Cencosud = "ACEITES",
            ),
        )
    }
}
