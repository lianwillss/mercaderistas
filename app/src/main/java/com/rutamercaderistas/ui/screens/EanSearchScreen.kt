package com.rutamercaderistas.ui.screens

import android.app.Activity
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.util.LruCache
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.WriterException
import com.journeyapps.barcodescanner.BarcodeEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.zxing.integration.android.IntentIntegrator
import com.rutamercaderistas.BuildConfig
import com.rutamercaderistas.R
import com.rutamercaderistas.data.local.EanProductEntity
import com.rutamercaderistas.ui.components.IosModal
import com.rutamercaderistas.ui.theme.LocalAppDimens
import com.rutamercaderistas.ui.theme.rs
import com.rutamercaderistas.viewmodel.EanSearchUiState
import com.rutamercaderistas.viewmodel.EanSearchViewModel

private val barcodeCache = LruCache<String, Bitmap>(64)

@Composable
fun EanSearchScreen(
    viewModel: EanSearchViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val dimens = LocalAppDimens.current
    val context = LocalContext.current

    var zoomProduct by remember { mutableStateOf<EanProductEntity?>(null) }

    val scannerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val intentResult = IntentIntegrator.parseActivityResult(
            result.resultCode, result.data
        )
        if (intentResult != null && !intentResult.contents.isNullOrBlank()) {
            viewModel.onBarcodeScanned(intentResult.contents)
        }
    }

    val tfColors = TextFieldDefaults.colors(
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        unfocusedIndicatorColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        cursorColor = MaterialTheme.colorScheme.primary,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                MaterialTheme.colorScheme.background.let { c ->
                    Color(c.red * 0.85f, c.green * 0.85f, c.blue * 0.85f, c.alpha)
                },
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.spacingMd, vertical = dimens.spacingSm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.volver_cd),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(modifier = Modifier.width(dimens.spacingSm))
                Text(
                    text = stringResource(R.string.ean_search_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                IconButton(onClick = { viewModel.forceReload() }) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.ean_refresh_cd),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            when (val state = uiState) {
                is EanSearchUiState.Loading -> {
                    val loadingCd = stringResource(R.string.ean_loading_cd)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(dimens.spacingXl),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(dimens.spacingMd))
                        Text(
                            text = state.progress,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.semantics { contentDescription = loadingCd },
                        )
                    }
                }

                is EanSearchUiState.Error -> {
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
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(modifier = Modifier.height(dimens.spacingMd))
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = dimens.spacingLg),
                        )
                        Spacer(modifier = Modifier.height(dimens.spacingLg))
                        Button(onClick = { viewModel.retryLoad() }) {
                            Text(stringResource(R.string.reintentar_carga))
                        }
                    }
                }

                is EanSearchUiState.Ready -> {
                    val value = state
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = dimens.spacingMd, vertical = dimens.spacingSm),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextField(
                                value = value.query,
                                onValueChange = { viewModel.onQueryChange(it) },
                                label = {
                                    Text(
                                        text = stringResource(R.string.ean_search_label),
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                },
                                placeholder = {
                                    Text(
                                        text = stringResource(R.string.ean_search_placeholder),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Search,
                                        contentDescription = stringResource(R.string.buscar_cd),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                trailingIcon = {
                                    if (value.query.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.clearQuery() }) {
                                            Icon(
                                                imageVector = Icons.Filled.Close,
                                                contentDescription = stringResource(R.string.limpiar_cd),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    } else {
                                        IconButton(
                                            onClick = {
                                                Toast.makeText(
                                                    context,
                                                    R.string.ean_scanning_cd,
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                                val activity = context as? Activity
                                                if (activity != null) {
                                                    scannerLauncher.launch(
                                                        IntentIntegrator(activity).createScanIntent()
                                                    )
                                                }
                                            },
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_barcode),
                                                contentDescription = stringResource(R.string.escanear_codigo_barras),
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                },
                                singleLine = true,
                                colors = tfColors,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = dimens.touchMin),
                            )
                        }
                    }

                    if (value.query.isNotEmpty() || value.results.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.ean_results_count, value.results.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(
                                    horizontal = dimens.spacingMd,
                                    vertical = dimens.spacingXs,
                                )
                                .semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }

                    if (value.query.isNotBlank() && value.results.isEmpty()) {
                        EanEmptyState(query = value.query)
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                horizontal = dimens.spacingMd,
                                vertical = dimens.spacingSm,
                            ),
                            verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
                        ) {
                            items(value.results, key = { it.id }) { product ->
                                EanProductCard(
                                    product = product,
                                    onBarcodeClick = { zoomProduct = product },
                                )
                            }
                        }
                    }
                }

                is EanSearchUiState.BarcodeResult -> {
                    // Estado transitorio: se resuelve en Ready con el código como query
                }
            }
        }
    }

    zoomProduct?.let { product ->
        IosModal(
            visible = true,
            onDismiss = { zoomProduct = null },
            title = product.descripcionProducto.ifBlank { product.eanPrincipal },
            subtitle = product.eanPrincipal,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BarcodeImage(
                    ean = product.eanPrincipal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp),
                )
                Spacer(modifier = Modifier.height(dimens.spacingSm))
                Text(
                    text = stringResource(R.string.ean_barcode_zoom_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(dimens.spacingMd))
                Button(
                    onClick = {
                        val cm = context.getSystemService(ClipboardManager::class.java)
                        cm?.setPrimaryClip(
                            android.content.ClipData.newPlainText(
                                "EAN",
                                product.eanPrincipal,
                            )
                        )
                        Toast.makeText(context, R.string.ean_copied, Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.heightIn(min = dimens.touchMin),
                ) {
                    Text(stringResource(R.string.ean_copy_cd))
                }
            }
        }
    }
}

@Composable
private fun EanProductCard(
    product: EanProductEntity,
    onBarcodeClick: (EanProductEntity) -> Unit = {},
) {
    val dimens = LocalAppDimens.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(dimens.spacingMd)) {
            Text(
                text = product.descripcionProducto.ifBlank { product.eanPrincipal },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )

            if (product.eanPrincipal.isNotBlank()) {
                Spacer(modifier = Modifier.height(dimens.spacingSm))
                BarcodeImage(
                    ean = product.eanPrincipal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 96.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(
                            onClickLabel = stringResource(R.string.ean_zoom_cd),
                            onClick = { onBarcodeClick(product) },
                        ),
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Column(modifier = Modifier.semantics(mergeDescendants = true) {}) {
                if (product.eanPrincipal.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.ean_codigo_label, product.eanPrincipal),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
                ) {
                    if (product.eanPrincipal.isNotBlank()) {
                        EanCodeChip(
                            label = "EAN",
                            value = product.eanPrincipal,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (product.codCencosud.isNotBlank()) {
                        EanCodeChip(
                            label = "SKU",
                            value = product.codCencosud,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    if (product.codProveedor.isNotBlank()) {
                        EanCodeChip(
                            label = "Prov",
                            value = product.codProveedor,
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
                            Text(
                                text = product.marca,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (product.estado.isNotBlank()) {
                            Text(
                                text = product.estado,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
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
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (product.unBase.isNotBlank() || product.unPedido.isNotBlank() || product.conversion.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = buildString {
                            if (product.unBase.isNotBlank()) append("Base: ${product.unBase}")
                            if (product.unPedido.isNotBlank()) append(" · Pedido: ${product.unPedido}")
                            if (product.conversion.isNotBlank()) append(" · Conv: ${product.conversion}")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun EanCodeChip(label: String, value: String, color: Color) {
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
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BarcodeImage(ean: String, modifier: Modifier = Modifier) {
    val digits = remember(ean) { ean.filter { it.isDigit() } }
    var bitmap by remember(digits) { mutableStateOf<Bitmap?>(barcodeCache.get(digits)) }

    LaunchedEffect(digits) {
        val cached = barcodeCache.get(digits)
        if (cached != null) {
            bitmap = cached
            return@LaunchedEffect
        }
        withContext(Dispatchers.Default) {
            val generated = try {
                val encoder = BarcodeEncoder()
                val format = if (digits.length == 13) BarcodeFormat.EAN_13 else BarcodeFormat.CODE_128
                encoder.encodeBitmap(digits, format, 800, 280)
            } catch (e: WriterException) {
                try {
                    BarcodeEncoder().encodeBitmap(digits, BarcodeFormat.CODE_128, 800, 280)
                } catch (e2: WriterException) {
                    null
                }
            } catch (e: IllegalArgumentException) {
                null
            }
            if (generated != null) barcodeCache.put(digits, generated)
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
private fun EanEmptyState(query: String) {
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
    }
}

@Preview(showBackground = true)
@Composable
private fun EanSearchScreenPreview() {
    if (BuildConfig.DEBUG) {
        com.rutamercaderistas.ui.theme.MercaderistasTheme {
            EanSearchScreen(onBack = {})
        }
    }
}