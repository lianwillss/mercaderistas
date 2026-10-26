package com.rutamercaderistas.ui.screens

import android.app.Activity
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.zxing.integration.android.IntentIntegrator
import com.rutamercaderistas.R
import com.rutamercaderistas.data.local.BrandCountResult
import com.rutamercaderistas.data.local.CajaCountResult
import com.rutamercaderistas.data.local.EanProductEntity
import com.rutamercaderistas.ui.components.IosModal
import com.rutamercaderistas.ui.theme.LocalAppDimens
import com.rutamercaderistas.ui.theme.LocalReducedMotionEnabled
import com.rutamercaderistas.ui.theme.MotionSprings
import com.rutamercaderistas.ui.theme.pressScale
import com.rutamercaderistas.ui.theme.rememberPressInteractionSource
import com.rutamercaderistas.ui.theme.rs
import com.rutamercaderistas.util.ShareImageGenerator
import com.rutamercaderistas.util.buildEanListShareText
import com.rutamercaderistas.viewmodel.EanSearchUiState
import com.rutamercaderistas.viewmodel.EanSearchViewModel
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay

@Composable
fun EanSearchScreen(
    viewModel: EanSearchViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val catalogMeta by viewModel.catalogMeta.collectAsStateWithLifecycle()
    val catalogBrands by viewModel.catalogBrands.collectAsStateWithLifecycle()
    val dimens = LocalAppDimens.current
    val context = LocalContext.current

    var zoomProduct by remember { mutableStateOf<EanProductEntity?>(null) }
    var showFlejes by remember { mutableStateOf(false) }

    val scannerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val intentResult = IntentIntegrator.parseActivityResult(
            result.resultCode, result.data,
        )
        if (intentResult != null && !intentResult.contents.isNullOrBlank()) {
            viewModel.onBarcodeScanned(intentResult.contents)
        }
    }

    val launchScanner = {
        Toast.makeText(
            context,
            R.string.ean_scanning_cd,
            Toast.LENGTH_SHORT,
        ).show()
        val activity = context as? Activity
        if (activity != null) {
            val integrator = IntentIntegrator(activity)
                .setOrientationLocked(false)
                .setPrompt(activity.getString(R.string.ean_scanning_cd))
                .setBeepEnabled(true)
            scannerLauncher.launch(integrator.createScanIntent())
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                MaterialTheme.colorScheme.background.let { c ->
                    Color(c.red * 0.85f, c.green * 0.85f, c.blue * 0.85f, c.alpha)
                },
            ),
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
            }

            when (val state = uiState) {
                is EanSearchUiState.Loading -> EanLoadingContent(state.progress)
                is EanSearchUiState.Error -> EanErrorContent(state.message, viewModel::retryLoad)
                is EanSearchUiState.Ready -> EanSearchContent(
                    state = state,
                    viewModel = viewModel,
                    catalogMeta = catalogMeta,
                    catalogBrands = catalogBrands,
                    onScan = launchScanner,
                    onShowFlejes = { showFlejes = true },
                    onBarcodeClick = { zoomProduct = it },
                )
            }
        }
    }

    zoomProduct?.let { product ->
        EanBarcodeZoom(
            product = product,
            onDismiss = { zoomProduct = null },
        )
    }

    if (showFlejes) {
        EanFlejesDialog(onDismiss = { showFlejes = false })
    }
}

@Composable
private fun EanLoadingContent(progress: String) {
    val dimens = LocalAppDimens.current
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
            text = progress,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics {
                contentDescription = loadingCd
                liveRegion = LiveRegionMode.Polite
            },
        )
    }
}

@Composable
private fun EanErrorContent(message: String, onRetry: () -> Unit) {
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
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp),
        )
        Spacer(modifier = Modifier.height(dimens.spacingMd))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = dimens.spacingLg),
        )
        Spacer(modifier = Modifier.height(dimens.spacingLg))
        Button(onClick = onRetry) {
            Text(stringResource(R.string.reintentar_carga))
        }
    }
}

/**
 * Contenido de la pantalla en estado Ready. La lista, los chips, el conteo y
 * el "compartir" salen del mismo resultado paginado que ordena SQL: no se
 * reagrupa ni se filtra en memoria (eso rompía el ranking y la paginación).
 */
@Composable
private fun EanSearchContent(
    state: EanSearchUiState.Ready,
    viewModel: EanSearchViewModel,
    catalogMeta: Pair<Int, Int>?,
    catalogBrands: List<BrandCountResult>,
    onScan: () -> Unit,
    onShowFlejes: () -> Unit,
    onBarcodeClick: (EanProductEntity) -> Unit,
) {
    val dimens = LocalAppDimens.current
    val context = LocalContext.current
    val lazyItems = state.pagingFlow.collectAsLazyPagingItems()
    val itemCount = lazyItems.itemCount
    val total = state.totalCount
    val refreshing = lazyItems.loadState.refresh is LoadState.Loading
    val browsing = state.query.isBlank()
    val hasFilters = state.brandFilter != null || state.cajaFilter != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = dimens.spacingMd, vertical = dimens.spacingSm),
    ) {
        EanSearchBar(
            query = state.query,
            onQueryChange = viewModel::onQueryChange,
            onClear = viewModel::clearQuery,
            onScan = onScan,
            onShowFlejes = onShowFlejes,
        )

        when {
            browsing && !hasFilters && total == 0 && itemCount == 0 ->
                Box(modifier = Modifier.weight(1f)) {
                    EanCatalogEmptyContent(onReload = viewModel::forceReload)
                }
            browsing && !hasFilters && itemCount == 0 && total == null && !refreshing ->
                Box(modifier = Modifier.weight(1f)) {
                    EanCatalogEmptyContent(onReload = viewModel::forceReload)
                }
            // Sin texto pero con filtro activo: el catálogo no está vacío, el
            // filtro no tiene coincidencias (p. ej. tras un reimport).
            browsing && hasFilters && total == 0 -> Box(modifier = Modifier.weight(1f)) {
                EanFiltersEmptyContent(onClear = viewModel::clearFilters)
            }
            !browsing && total == 0 -> Box(modifier = Modifier.weight(1f)) {
                EanEmptyState(query = state.query, onClear = viewModel::clearQuery)
            }
            else -> {
                if (state.brandFacets.size > 1 || state.cajaFacets.isNotEmpty()) {
                    EanFilterChips(
                        brandFacets = state.brandFacets,
                        cajaFacets = state.cajaFacets,
                        brandFilter = state.brandFilter,
                        cajaFilter = state.cajaFilter,
                        onBrandFilter = viewModel::onBrandFilter,
                        onCajaFilter = viewModel::onCajaFilter,
                    )
                }

                val snapshot = lazyItems.itemSnapshotList
                // Las filas se derivan del snapshot (orden SQL intacto). La clave
                // del remember es el snapshot completo: con solo lazyItems no se
                // recalculaba al llegar páginas nuevas y la lista se quedaba corta.
                val rows = remember(snapshot, browsing) {
                    buildEanRows(snapshot.items, showBrandHeaders = browsing)
                }
                val shareCd = stringResource(R.string.ean_share_list_cd)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.ean_results_count, total ?: itemCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = dimens.spacingXs)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    IconButton(
                        onClick = {
                            val products = rows.filterIsInstance<EanResultRow.Product>()
                                .map { it.product }
                            val q = state.query.trim()
                            val title = if (q.isBlank()) {
                                context.getString(R.string.ean_share_catalog_title, products.size)
                            } else {
                                context.getString(R.string.ean_share_list_title, q, products.size)
                            }
                            val text = buildEanListShareText(
                                title = title,
                                products = products,
                            ) { n ->
                                context.getString(R.string.ean_share_more, n)
                            }
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(
                                Intent.createChooser(intent, context.getString(R.string.compartir)),
                            )
                        },
                        modifier = Modifier.size(dimens.touchMin),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = shareCd,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                EanResultsList(
                    modifier = Modifier.weight(1f),
                    rows = rows,
                    query = state.query,
                    onBarcodeClick = onBarcodeClick,
                )
            }
        }

        EanCatalogFooter(
            catalogMeta = catalogMeta,
            catalogBrands = catalogBrands,
            onBrandClick = viewModel::onBrandFilter,
        )
    }
}

@Composable
private fun EanCatalogEmptyContent(onReload: () -> Unit) {
    val dimens = LocalAppDimens.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(dimens.spacingXl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.ean_empty_catalog),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(modifier = Modifier.height(dimens.spacingMd))
        Button(onClick = onReload, modifier = Modifier.heightIn(min = dimens.touchMin)) {
            Text(stringResource(R.string.ean_reload_catalog))
        }
    }
}

@Composable
private fun EanFiltersEmptyContent(onClear: () -> Unit) {
    val dimens = LocalAppDimens.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(dimens.spacingXl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.sin_resultados),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(modifier = Modifier.height(dimens.spacingMd))
        Button(onClick = onClear, modifier = Modifier.heightIn(min = dimens.touchMin)) {
            Text(stringResource(R.string.ean_clear_filters))
        }
    }
}

@Composable
private fun EanSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onScan: () -> Unit,
    onShowFlejes: () -> Unit,
) {
    val dimens = LocalAppDimens.current
    var searchFocused by remember { mutableStateOf(false) }
    val searchPill = RoundedCornerShape(28.dp)
    val searchBorder by animateColorAsState(
        targetValue = if (searchFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "eanSearchBorder",
    )
    val searchElevation by animateDpAsState(
        targetValue = if (searchFocused) 8.dp else 2.dp,
        label = "eanSearchElevation",
    )
    val scanCd = stringResource(R.string.escanear_codigo_barras)
    val flejesCd = stringResource(R.string.flejes_button_cd)
    val tfColors = TextFieldDefaults.colors(
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        unfocusedIndicatorColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        cursorColor = MaterialTheme.colorScheme.primary,
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            TextField(
                value = query,
                onValueChange = onQueryChange,
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
                        tint = if (searchFocused) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = onClear) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.limpiar_cd),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                singleLine = true,
                colors = tfColors,
                shape = searchPill,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = dimens.touchMin)
                    .onFocusChanged { searchFocused = it.isFocused }
                    .shadow(searchElevation, searchPill)
                    .border(1.5.dp, searchBorder, searchPill),
            )
        }
        Spacer(modifier = Modifier.width(dimens.spacingSm))
        // Botón Flejes: código estático para imprimir/etiquetar. Brillo pulsante
        // estático si hay movimiento reducido.
        val flejesGlow: Float = if (LocalReducedMotionEnabled.current) {
            0.7f
        } else {
            val flejesInfinite = rememberInfiniteTransition(label = "flejesGlow")
            flejesInfinite.animateFloat(
                initialValue = 0.45f, targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
                label = "flejesGlow",
            ).value
        }
        val flejesPress = rememberPressInteractionSource()
        Box(
            modifier = Modifier
                .size(48.dp)
                .shadow(10.dp * flejesGlow, CircleShape, clip = false)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f + 0.25f * flejesGlow),
                    CircleShape,
                )
                .pressScale(flejesPress)
                .clickable(
                    interactionSource = flejesPress,
                    indication = LocalIndication.current,
                    onClick = onShowFlejes,
                    role = Role.Button,
                    onClickLabel = flejesCd,
                )
                .semantics { contentDescription = flejesCd },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Print,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer {
                        scaleX = 1f + 0.06f * flejesGlow
                        scaleY = 1f + 0.06f * flejesGlow
                    },
            )
        }
        Spacer(modifier = Modifier.width(dimens.spacingSm))
        val scanPress = rememberPressInteractionSource()
        Box(
            modifier = Modifier
                .size(52.dp)
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary,
                        ),
                    ),
                )
                .pressScale(scanPress)
                .clickable(
                    interactionSource = scanPress,
                    indication = LocalIndication.current,
                    onClick = onScan,
                    role = Role.Button,
                    onClickLabel = scanCd,
                )
                .semantics { contentDescription = scanCd },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_barcode),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/**
 * Chips de filtro alimentados por las facetas SQL: los conteos y las opciones
 * corresponden al resultado real, y al tocar se re-consulta en SQL (no se
 * filtra la página ya cargada).
 */
@Composable
private fun EanFilterChips(
    brandFacets: List<BrandCountResult>,
    cajaFacets: List<CajaCountResult>,
    brandFilter: String?,
    cajaFilter: String?,
    onBrandFilter: (String?) -> Unit,
    onCajaFilter: (String?) -> Unit,
) {
    val dimens = LocalAppDimens.current
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = dimens.spacingXs),
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
    ) {
        if (brandFacets.size > 1) {
            item(key = "ean_brand_all") {
                Box(modifier = Modifier.heightIn(min = dimens.touchMin)) {
                    FilterChip(
                        selected = brandFilter == null,
                        onClick = { onBrandFilter(null) },
                        label = { Text(stringResource(R.string.todas)) },
                    )
                }
            }
            items(brandFacets, key = { "ean_brand_${it.marca}" }) { facet ->
                Box(modifier = Modifier.heightIn(min = dimens.touchMin)) {
                    FilterChip(
                        selected = brandFilter == facet.marca,
                        onClick = { onBrandFilter(facet.marca) },
                        label = {
                            Text(
                                if (facet.marca.isBlank()) stringResource(R.string.ean_sin_marca)
                                else "${facet.marca} · ${facet.count}",
                            )
                        },
                    )
                }
            }
        }
        if (cajaFacets.isNotEmpty()) {
            item(key = "ean_caja_all") {
                Box(modifier = Modifier.heightIn(min = dimens.touchMin)) {
                    FilterChip(
                        selected = cajaFilter == null,
                        onClick = { onCajaFilter(null) },
                        label = { Text(stringResource(R.string.todas_cajas)) },
                    )
                }
            }
            items(cajaFacets, key = { "ean_caja_${it.conversion}" }) { facet ->
                Box(modifier = Modifier.heightIn(min = dimens.touchMin)) {
                    FilterChip(
                        selected = cajaFilter == facet.conversion,
                        onClick = { onCajaFilter(facet.conversion) },
                        label = { Text("CAJA ×${facet.conversion} · ${facet.count}") },
                    )
                }
            }
        }
    }
}

@Composable
private fun EanResultsList(
    modifier: Modifier = Modifier,
    rows: List<EanResultRow>,
    query: String,
    onBarcodeClick: (EanProductEntity) -> Unit,
) {
    val dimens = LocalAppDimens.current
    val density = LocalDensity.current
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = 0.dp,
            end = 0.dp,
            top = dimens.spacingSm,
            bottom = dimens.scrollBottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(6.dp * rs()),
    ) {
        itemsIndexed(
            items = rows,
            key = { _, row ->
                when (row) {
                    is EanResultRow.BrandHeader -> "brand_${row.brand}"
                    is EanResultRow.Product -> row.product.id
                }
            },
            contentType = { _, row ->
                when (row) {
                    is EanResultRow.BrandHeader -> "brand_header"
                    is EanResultRow.Product -> "product"
                }
            },
        ) { index, row ->
            var visible by remember { mutableStateOf(false) }
            // Springs (parten del valor en pantalla, interrumpibles); el stagger
            // vive en el disparo, no en la curva. Reducido: solo cross-fade.
            val reducedMotion = LocalReducedMotionEnabled.current
            val animAlpha by animateFloatAsState(
                targetValue = if (visible) 1f else 0f,
                animationSpec = if (reducedMotion) tween(150) else MotionSprings.default(),
                label = "eanRowAlpha",
            )
            val animOffsetY by animateDpAsState(
                targetValue = if (visible) 0.dp else 12.dp,
                animationSpec = if (reducedMotion) tween(150) else MotionSprings.default(),
                label = "eanRowOffset",
            )
            LaunchedEffect(Unit) {
                if (!reducedMotion) delay(minOf(index, 8) * 40L)
                visible = true
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = animAlpha
                        translationY = animOffsetY.value * density.density
                    },
            ) {
                when (row) {
                    is EanResultRow.BrandHeader -> EanBrandHeader(
                        title = if (row.brand.isBlank()) stringResource(R.string.ean_sin_marca)
                        else row.brand,
                    )
                    is EanResultRow.Product -> EanProductCard(
                        product = row.product,
                        query = query,
                        onBarcodeClick = onBarcodeClick,
                    )
                }
            }
        }
    }
}

/**
 * Pie con versión del catálogo y atajos de marca. Tocar una marca aplica el
 * filtro exacto (no escribe su nombre en el buscador).
 */
@Composable
private fun EanCatalogFooter(
    catalogMeta: Pair<Int, Int>?,
    catalogBrands: List<BrandCountResult>,
    onBrandClick: (String) -> Unit,
) {
    val dimens = LocalAppDimens.current
    val meta = catalogMeta ?: return
    val (version, count) = meta
    val shortcuts = remember(catalogBrands) {
        catalogBrands
            .filter { it.marca.isNotBlank() }
            .sortedByDescending { it.count }
            .take(8)
    }
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = dimens.spacingXs),
        contentPadding = PaddingValues(horizontal = dimens.spacingMd),
        horizontalArrangement = Arrangement.spacedBy(
            dimens.spacingSm,
            Alignment.CenterHorizontally,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item(key = "ean_meta") {
            Text(
                text = stringResource(R.string.ean_catalog_meta, version, count),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        if (shortcuts.size > 1) {
            items(shortcuts, key = { "ean_shortcut_${it.marca}" }) { facet ->
                AssistChip(
                    onClick = { onBrandClick(facet.marca) },
                    label = { Text("${facet.marca} · ${facet.count}") },
                )
            }
        }
    }
}

@Composable
private fun EanBarcodeZoom(
    product: EanProductEntity,
    onDismiss: () -> Unit,
) {
    val dimens = LocalAppDimens.current
    val context = LocalContext.current
    // Entrada tipo container-transform (el Dialog es otra ventana: sharedElement
    // real no es posible, se emula con spring + fade).
    var zoomVisible by remember(product.eanPrincipal) { mutableStateOf(false) }
    val zoomScale by animateFloatAsState(
        targetValue = if (zoomVisible) 1f else 0.88f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 350f),
        label = "eanZoomScale",
    )
    val zoomAlpha by animateFloatAsState(
        targetValue = if (zoomVisible) 1f else 0f,
        animationSpec = tween(200),
        label = "eanZoomAlpha",
    )
    LaunchedEffect(product.eanPrincipal) { zoomVisible = true }
    IosModal(
        visible = true,
        onDismiss = onDismiss,
        title = product.descripcionProducto.ifBlank { product.eanPrincipal },
        subtitle = product.eanPrincipal,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = zoomScale
                    scaleY = zoomScale
                    alpha = zoomAlpha
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EanBarcodeView(
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
                        android.content.ClipData.newPlainText("EAN", product.eanPrincipal),
                    )
                    Toast.makeText(context, R.string.ean_copied, Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.heightIn(min = dimens.touchMin),
            ) {
                Text(stringResource(R.string.ean_copy_cd))
            }
            Spacer(modifier = Modifier.height(dimens.spacingSm))
            Button(
                onClick = {
                    try {
                        val file = ShareImageGenerator.generateForEanProduct(
                            context = context,
                            productName = product.descripcionProducto,
                            ean = product.eanPrincipal,
                        )
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file,
                        )
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/png"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(
                            Intent.createChooser(
                                shareIntent,
                                context.getString(R.string.ean_share_image),
                            ),
                        )
                    } catch (_: Exception) {
                        Toast.makeText(context, R.string.ean_share_error, Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.heightIn(min = dimens.touchMin),
            ) {
                Text(stringResource(R.string.ean_share_image))
            }
        }
    }
}

@Composable
private fun EanFlejesDialog(onDismiss: () -> Unit) {
    val dimens = LocalAppDimens.current
    val context = LocalContext.current
    // Brillo al máximo mientras se muestra el código: los lectores láser leen la
    // pantalla mucho mejor con brillo alto. Se restaura al cerrar.
    LaunchedEffect(Unit) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        val previous = window.attributes.screenBrightness
        window.attributes = window.attributes.apply { screenBrightness = 1f }
        try {
            awaitCancellation()
        } finally {
            window.attributes = window.attributes.apply { screenBrightness = previous }
        }
    }
    val glow: Float = if (LocalReducedMotionEnabled.current) {
        0.65f
    } else {
        rememberInfiniteTransition(label = "flejesBarcodeGlow").animateFloat(
            initialValue = 0.35f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
            label = "flejesBarcodeGlow",
        ).value
    }
    IosModal(
        visible = true,
        onDismiss = onDismiss,
        title = stringResource(R.string.flejes_barcode_title),
        subtitle = null,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(18.dp * glow, RoundedCornerShape(16.dp), clip = false)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .padding(12.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.img_flejes_barcode),
                    contentDescription = stringResource(R.string.flejes_barcode_cd),
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                )
            }
            Spacer(modifier = Modifier.height(dimens.spacingSm))
            Text(
                text = stringResource(R.string.flejes_barcode_title),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
