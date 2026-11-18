package com.rutamercaderistas.ui.screens.stock

import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rutamercaderistas.R
import com.rutamercaderistas.data.local.EanSkuLookup
import com.rutamercaderistas.data.stock.EstadoTipo
import com.rutamercaderistas.data.stock.StockItem
import com.rutamercaderistas.data.stock.estadoDe
import com.rutamercaderistas.ui.components.AppButton
import com.rutamercaderistas.ui.components.AppButtonVariant
import com.rutamercaderistas.ui.components.IosModal
import com.rutamercaderistas.ui.screens.EanBarcodeView
import com.rutamercaderistas.ui.theme.LocalAppDimens
import com.rutamercaderistas.util.StockShareImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Detalle de un SKU con su código de barras y el stock del local: la versión
 * stock de [com.rutamercaderistas.ui.screens.EanBarcodeZoom].
 *
 * El código de barras sale del catálogo del módulo EAN (por Cód. Cencosud =
 * SKU). Si el producto todavía no está catalogado con código, el área del
 * código queda en blanco y solo se comparte el stock con el SKU.
 */
@Composable
fun StockItemZoom(
    item: StockItem,
    flujoEan: (String) -> Flow<EanSkuLookup>,
    onDismiss: () -> Unit,
) {
    val dimens = LocalAppDimens.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Reactivo: si el catálogo EAN se reimporta con el código nuevo, el detalle
    // abierto lo refleja sin cerrarse (consulta Flow de ean_products).
    val lookup by remember(item.sku) { flujoEan(item.sku) }
        .collectAsStateWithLifecycle(initialValue = EanSkuLookup.Cargando)
    val ean = (lookup as? EanSkuLookup.Resultado)?.ean
    val cargando = lookup is EanSkuLookup.Cargando

    IosModal(
        visible = true,
        onDismiss = onDismiss,
        title = item.product.ifBlank { item.sku },
        subtitle = stringResource(R.string.stock_subtitle_sku, item.sku),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Sin código de barras en el catálogo EAN: el área queda en blanco
            // (solo se comparte el stock con el SKU y sus datos).
            when {
                ean != null -> {
                    EanBarcodeView(
                        ean = ean.orEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 90.dp),
                    )
                    Text(
                        text = stringResource(R.string.stock_codigo_barras),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                cargando -> BoxProgresoBarras()
            }

            Spacer(modifier = Modifier.height(dimens.spacingMd))

            DatosStock(item = item)

            Spacer(modifier = Modifier.height(dimens.spacingLg))

            AppButton(
                text = stringResource(R.string.stock_copiar_sku),
                onClick = {
                    val cm = context.getSystemService(ClipboardManager::class.java)
                    cm?.setPrimaryClip(
                        android.content.ClipData.newPlainText("SKU", item.sku),
                    )
                    Toast.makeText(
                        context,
                        context.resources.getString(R.string.stock_sku_copiado),
                        Toast.LENGTH_SHORT,
                    ).show()
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(dimens.spacingSm))

            AppButton(
                text = stringResource(R.string.stock_compartir_barras),
                onClick = {
                    scope.launch {
                        val archivo = runCatching {
                            withContext(Dispatchers.IO) {
                                StockShareImage.generateForStockItem(
                                    context = context,
                                    item = item,
                                    ean = ean,
                                )
                            }
                        }.getOrNull()
                        if (archivo == null) {
                            Toast.makeText(
                                context,
                                context.resources.getString(R.string.stock_error_compartir),
                                Toast.LENGTH_SHORT,
                            ).show()
                            return@launch
                        }
                        val uri = FileProvider.getUriForFile(
                            context,
                            context.packageName + ".fileprovider",
                            archivo,
                        )
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/png"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(
                            Intent.createChooser(
                                intent,
                                context.getString(R.string.stock_compartir_barras),
                            ),
                        )
                    }
                },
                variant = AppButtonVariant.Tonal,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Espacio reservado mientras se busca el código en el catálogo EAN. */
@Composable
private fun BoxProgresoBarras() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            strokeWidth = 2.dp,
        )
    }
}

/** Fila etiqueta/valor del detalle de stock. */
@Composable
private fun Dato(
    label: String,
    value: String,
    destacado: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(82.dp),
        )
        Text(
            text = value,
            style = if (destacado) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.bodyMedium
            },
            fontWeight = if (destacado) FontWeight.SemiBold else null,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DatosStock(item: StockItem) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Dato(label = stringResource(R.string.stock_label_local), value = item.storeName)
        Dato(label = stringResource(R.string.stock_label_marca), value = item.brand)
        Dato(
            label = stringResource(R.string.stock_label_stock),
            value = item.stockLabel + " " + context.resources.getString(R.string.stock_label_unidades),
            destacado = estadoDe(item) != EstadoTipo.Disponible,
        )
        val transito = item.transitUnits ?: 0.0
        if (transito > 0.0) {
            Dato(
                label = stringResource(R.string.stock_label_transito),
                value = item.transitLabel,
            )
        }
    }
}
