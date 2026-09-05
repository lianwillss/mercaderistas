package com.rutamercaderistas.ui.screens

import android.app.Activity
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.util.LruCache
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.systemBarsPadding
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.TextStyle
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
import com.rutamercaderistas.services.brandNote
import com.rutamercaderistas.services.compactNorm
import com.rutamercaderistas.services.normalizeSearch
import com.rutamercaderistas.ui.theme.AccentBlue
import com.rutamercaderistas.ui.theme.AccentBlueSoft
import com.rutamercaderistas.ui.theme.AccentGreen
import com.rutamercaderistas.ui.theme.AccentGreenSoft
import com.rutamercaderistas.ui.theme.AccentOrange
import com.rutamercaderistas.ui.theme.AccentOrangeSoft
import com.rutamercaderistas.ui.theme.LocalAppDimens
import com.rutamercaderistas.ui.theme.StoreColorFuchsia
import com.rutamercaderistas.ui.theme.StoreColorFuchsiaSoft
import com.rutamercaderistas.ui.theme.StoreColorPurple
import com.rutamercaderistas.ui.theme.StoreColorPurpleSoft
import com.rutamercaderistas.ui.theme.StoreColorRed
import com.rutamercaderistas.ui.theme.StoreColorRedSoft
import com.rutamercaderistas.ui.theme.StoreColorYellow
import com.rutamercaderistas.ui.theme.StoreColorYellowSoft
import com.rutamercaderistas.ui.theme.rs
import com.rutamercaderistas.viewmodel.EanSearchUiState
import com.rutamercaderistas.viewmodel.EanSearchViewModel

private val barcodeCache = LruCache<String, Bitmap>(64)

private sealed interface EanResultRow
private data class EanBrandRow(val brand: String) : EanResultRow
private data class EanProductRow(val product: EanProductEntity) : EanResultRow
private const val NO_BRAND_KEY = "\u0000"

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

private fun brandPair(name: String): Pair<Color, Color> {
    val h = normalizeSearch(name).hashCode()
    val idx = if (h < 0) -h else h
    return BRAND_PALETTE[idx % BRAND_PALETTE.size]
}

@Composable
fun EanSearchScreen(
    viewModel: EanSearchViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val dimens = LocalAppDimens.current
    val context = LocalContext.current
    val catalogMeta by viewModel.catalogMeta.collectAsStateWithLifecycle()
    val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()

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

    val launchScanner = {
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
    }

    val tfColors = TextFieldDefaults.colors(
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                            .fillMaxSize()
                            .padding(horizontal = dimens.spacingMd, vertical = dimens.spacingSm),
                    ) {
                        var searchFocused by remember { mutableStateOf(false) }
                        val searchPill = RoundedCornerShape(28.dp)
                        val searchBorder by animateColorAsState(
                            targetValue = if (searchFocused) MaterialTheme.colorScheme.primary
                            else Color.Transparent,
                            label = "eanSearchBorder",
                        )
                        val searchElevation by animateDpAsState(
                            targetValue = if (searchFocused) 8.dp else 2.dp,
                            label = "eanSearchElevation",
                        )
                        val scanCd = stringResource(R.string.escanear_codigo_barras)
                        var historyExpanded by remember { mutableStateOf(false) }
                        val matchingHistory = remember(searchHistory, value.query) {
                            if (value.query.isBlank()) searchHistory
                            else searchHistory.filter { it.contains(value.query, ignoreCase = true) }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier.weight(1f),
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
                                        tint = if (searchFocused) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
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
                                    }
                                },
                                singleLine = true,
                                colors = tfColors,
                                shape = searchPill,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = dimens.touchMin)
                                    .onFocusChanged {
                                        searchFocused = it.isFocused
                                        historyExpanded = it.isFocused
                                    }
                                    .shadow(searchElevation, searchPill)
                                    .border(1.5.dp, searchBorder, searchPill),
                            )
                            DropdownMenu(
                                expanded = historyExpanded && matchingHistory.isNotEmpty(),
                                onDismissRequest = { historyExpanded = false },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                matchingHistory.take(5).forEach { h ->
                                    DropdownMenuItem(
                                        text = { Text(h, maxLines = 1) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Outlined.History,
                                                contentDescription = null,
                                            )
                                        },
                                        onClick = {
                                            viewModel.onHistoryClick(h)
                                            historyExpanded = false
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stringResource(R.string.ean_clear_history),
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    },
                                    onClick = {
                                        viewModel.clearSearchHistory()
                                        historyExpanded = false
                                    },
                                )
                            }
                            }
                            Spacer(modifier = Modifier.width(dimens.spacingSm))
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
                                            )
                                        )
                                    )
                                    .clickable(
                                        onClick = launchScanner,
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

                        if (value.query.isNotEmpty() || value.results.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.ean_results_count, value.results.size),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(vertical = dimens.spacingXs)
                                    .semantics { liveRegion = LiveRegionMode.Polite },
                            )
                        }

                    if (value.query.isNotBlank() && value.results.isEmpty()) {
                        Box(modifier = Modifier.weight(1f)) {
                            EanEmptyState(query = value.query, onClear = { viewModel.clearQuery() })
                        }
                    } else {
                        val brandsInResults = remember(value.results) {
                            value.results.map { it.marca.ifBlank { NO_BRAND_KEY } }.distinct()
                        }
                        var brandFilter by remember(value.query) { mutableStateOf<String?>(null) }
                        if (brandsInResults.size > 1) {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(vertical = dimens.spacingXs),
                                horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
                            ) {
                                item(key = "ean_brand_all") {
                                    FilterChip(
                                        selected = brandFilter == null,
                                        onClick = { brandFilter = null },
                                        label = { Text(stringResource(R.string.todas)) },
                                    )
                                }
                                items(brandsInResults, key = { "ean_brand_$it" }) { brandKey ->
                                    FilterChip(
                                        selected = brandFilter == brandKey,
                                        onClick = { brandFilter = if (brandFilter == brandKey) null else brandKey },
                                        label = {
                                            Text(
                                                if (brandKey == NO_BRAND_KEY) stringResource(R.string.ean_sin_marca)
                                                else brandKey
                                            )
                                        },
                                    )
                                }
                            }
                        }
                        val rows = remember(value.results, brandFilter) {
                            buildList<EanResultRow> {
                                value.results
                                    .groupBy { it.marca.ifBlank { "\u0000" } }
                                    .forEach { (brandKey, products) ->
                                        if (brandFilter != null && brandKey != brandFilter) return@forEach
                                        add(EanBrandRow(brandKey))
                                        products.forEach { add(EanProductRow(it)) }
                                    }
                            }
                        }
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(
                                horizontal = 0.dp,
                                vertical = dimens.spacingSm,
                            ),
                            verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
                        ) {
                            itemsIndexed(rows, key = { _, it ->
                                when (it) {
                                    is EanBrandRow -> "brand_${it.brand}"
                                    is EanProductRow -> it.product.id
                                }
                            }) { index, row ->
                                var visible by remember { mutableStateOf(false) }
                                val animAlpha by animateFloatAsState(
                                    targetValue = if (visible) 1f else 0f,
                                    animationSpec = tween(250, delayMillis = minOf(index, 8) * 40),
                                    label = "eanRowAlpha",
                                )
                                val animOffsetY by animateDpAsState(
                                    targetValue = if (visible) 0.dp else 12.dp,
                                    animationSpec = tween(250, delayMillis = minOf(index, 8) * 40),
                                    label = "eanRowOffset",
                                )
                                LaunchedEffect(Unit) { visible = true }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .graphicsLayer(alpha = animAlpha)
                                        .offset(y = animOffsetY),
                                ) {
                                when (row) {
                                    is EanBrandRow -> EanBrandHeader(
                                        title = if (row.brand == "\u0000")
                                            stringResource(R.string.ean_sin_marca)
                                        else row.brand,
                                    )
                                    is EanProductRow -> EanProductCard(
                                        product = row.product,
                                        query = value.query,
                                        onBarcodeClick = { zoomProduct = row.product },
                                    )
                                }
                                }
                            }
                    }

                    }

                    catalogMeta?.let { (version, count) ->
                        Text(
                            text = stringResource(R.string.ean_catalog_meta, version, count),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = dimens.spacingXs),
                        )
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
        // Entrada tipo container-transform (el Dialog es otra ventana:
        // sharedElement real no es posible, se emula con spring + fade)
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
            onDismiss = { zoomProduct = null },
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
    query: String = "",
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
            HighlightedText(
                text = product.descripcionProducto.ifBlank { product.eanPrincipal },
                query = query,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
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
                            role = Role.Button,
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
                            query = query,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (product.codCencosud.isNotBlank()) {
                        EanCodeChip(
                            label = "SKU",
                            value = product.codCencosud,
                            query = query,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    if (product.conversion.isNotBlank()) {
                        EanCodeChip(
                            label = "CAJA",
                            value = product.conversion,
                            query = query,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
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
private fun EanBrandHeader(title: String) {
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
private fun EanEmptyState(query: String, onClear: () -> Unit = {}) {
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

@Composable
private fun HighlightedText(
    text: String,
    query: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val tokens = remember(query) {
        compactNorm(query).split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }
    }
    if (tokens.isEmpty() || text.isBlank()) {
        Text(text = text, style = style, color = color, modifier = modifier, maxLines = maxLines, overflow = overflow)
        return
    }
    val ranges = remember(text, query) { computeHighlightRanges(text, tokens) }
    val annotated = buildAnnotatedString {
        if (ranges.isEmpty()) {
            append(text)
        } else {
            var cursor = 0
            for (r in ranges) {
                if (r.first > cursor) append(text.substring(cursor, r.first))
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
                append(text.substring(r.first, r.last + 1))
                pop()
                cursor = r.last + 1
            }
            if (cursor < text.length) append(text.substring(cursor))
        }
    }
    Text(text = annotated, style = style, color = color, modifier = modifier, maxLines = maxLines, overflow = overflow)
}

private fun computeHighlightRanges(text: String, tokens: List<String>): List<IntRange> {
    val normBuilder = StringBuilder()
    val map = mutableListOf<IntRange>()
    text.forEachIndexed { i, c ->
        val n = compactNorm(c.toString())
        if (n.isNotEmpty()) {
            map.add(i..i)
            normBuilder.append(n)
        }
    }
    val norm = normBuilder.toString()
    val found = mutableListOf<IntRange>()
    for (tok in tokens) {
        if (tok.isBlank()) continue
        var from = 0
        while (from <= norm.length - tok.length) {
            val idx = norm.indexOf(tok, from)
            if (idx < 0) break
            val startOrig = map[idx].first
            val endOrig = map[idx + tok.length - 1].last
            found.add(startOrig..endOrig)
            from = idx + tok.length
        }
    }
    found.sortBy { it.first }
    val merged = mutableListOf<IntRange>()
    for (r in found) {
        val last = merged.lastOrNull()
        if (last != null && r.first <= last.last + 1) {
            merged[merged.lastIndex] = last.first..maxOf(last.last, r.last)
        } else {
            merged.add(r)
        }
    }
    return merged
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