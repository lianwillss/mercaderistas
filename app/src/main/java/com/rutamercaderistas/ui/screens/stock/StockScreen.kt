package com.rutamercaderistas.ui.screens.stock

import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Badge
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rutamercaderistas.R
import com.rutamercaderistas.data.stock.ConsultaStock
import com.rutamercaderistas.data.stock.StockCatalog
import com.rutamercaderistas.data.stock.StockFilters
import com.rutamercaderistas.data.stock.StockItem
import com.rutamercaderistas.data.stock.StockLocal
import com.rutamercaderistas.data.stock.StockSugerencia
import com.rutamercaderistas.data.stock.StockBusquedas
import com.rutamercaderistas.data.stock.StockSeleccion
import com.rutamercaderistas.data.stock.TipoBusquedaStock
import com.rutamercaderistas.data.stock.usosPorTipo
import com.rutamercaderistas.data.stock.opcionesDeLocales
import com.rutamercaderistas.data.stock.opcionesDeMarcas
import com.rutamercaderistas.data.stock.opcionesDeRutas
import com.rutamercaderistas.data.stock.sugerenciasStock
import com.rutamercaderistas.data.stock.StockMode
import com.rutamercaderistas.data.stock.StockResult
import com.rutamercaderistas.data.stock.StockSort
import com.rutamercaderistas.data.stock.StockSource
import com.rutamercaderistas.data.stock.CoberturaTipo
import com.rutamercaderistas.data.stock.EstadoTipo
import com.rutamercaderistas.data.stock.ValidacionNegativo
import com.rutamercaderistas.data.stock.StockStatus
import com.rutamercaderistas.data.stock.validacionNegativo
import com.rutamercaderistas.data.stock.StockSyncPhase
import com.rutamercaderistas.data.stock.coberturaDe
import com.rutamercaderistas.data.stock.conLocal
import com.rutamercaderistas.data.stock.conMarca
import com.rutamercaderistas.data.stock.conRuta
import com.rutamercaderistas.data.stock.estadoDe
import com.rutamercaderistas.data.stock.limpiar
import com.rutamercaderistas.data.stock.activos
import com.rutamercaderistas.data.stock.marcaFueraDeRuta
import com.rutamercaderistas.data.stock.marcaExcluida
import com.rutamercaderistas.data.stock.marcasPara
import com.rutamercaderistas.data.stock.marcasParaSelector
import com.rutamercaderistas.data.stock.normalizarRuta
import com.rutamercaderistas.data.stock.normalizarStock
import com.rutamercaderistas.ui.components.AppButton
import com.rutamercaderistas.ui.components.AppButtonVariant
import com.rutamercaderistas.ui.components.AppFilterChip
import com.rutamercaderistas.ui.components.BannerAccion
import com.rutamercaderistas.ui.components.BannerAlturas
import com.rutamercaderistas.ui.components.BannerMensaje
import com.rutamercaderistas.ui.components.CollapsingBanner
import com.rutamercaderistas.ui.components.ActualizacionDatos
import com.rutamercaderistas.ui.components.HighlightedText
import com.rutamercaderistas.ui.components.ShimmerBlock
import com.rutamercaderistas.ui.components.rememberCollapsingBannerState
import com.rutamercaderistas.ui.screens.stock.StockItemZoom
import com.rutamercaderistas.util.StockShareImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.rutamercaderistas.ui.theme.AccentGreen
import com.rutamercaderistas.ui.theme.AccentGreenSoft
import com.rutamercaderistas.ui.theme.NegativosBlack
import com.rutamercaderistas.ui.theme.NegativosNeon
import com.rutamercaderistas.ui.theme.QuiebreOrangeEnd
import com.rutamercaderistas.ui.theme.QuiebreOrangeStart
import com.rutamercaderistas.ui.theme.QuiebreYellow
import com.rutamercaderistas.ui.theme.AccentGreenText
import com.rutamercaderistas.ui.theme.UrgencyOrangeSoft
import com.rutamercaderistas.ui.theme.UrgencyOrangeText
import com.rutamercaderistas.ui.theme.DownloadNeonViolet
import com.rutamercaderistas.ui.theme.DownloadNeonGlow
import com.rutamercaderistas.ui.theme.DownloadNeonIconEdge
import com.rutamercaderistas.ui.theme.LocalAppDimens
import com.rutamercaderistas.ui.theme.MercaderistasTheme
import com.rutamercaderistas.ui.theme.metricValueCompact
import com.rutamercaderistas.ui.theme.metricValue
import com.rutamercaderistas.ui.theme.metricLabel
import com.rutamercaderistas.ui.theme.LocalReducedMotionEnabled
import com.rutamercaderistas.ui.theme.pressScale
import com.rutamercaderistas.ui.theme.rememberPressInteractionSource
import com.rutamercaderistas.ui.components.rememberBarcodeScannerLauncher
import com.rutamercaderistas.viewmodel.StockViewModel
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StockScreen(
    onAbrirAjustes: () -> Unit = {},
    vm: StockViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val filters by vm.filters.collectAsStateWithLifecycle()
    val historial by vm.historial.collectAsStateWithLifecycle()
    // Al volver a la pestaña, si el dato tiene más de un minuto, se refresca
    // en segundo plano sin ocultar la lista que ya está en pantalla.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.alVolver() }
    val dimens = LocalAppDimens.current
    val context = LocalContext.current
    val reducirMovimiento = LocalReducedMotionEnabled.current
    val scope = rememberCoroutineScope()
    // Escáner del módulo Cód. EAN: al escanear, Stock filtra a ese producto.
    val lanzarEscaner = rememberBarcodeScannerLauncher { vm.onBarcodeScanned(it) }
    var zoomItem by remember { mutableStateOf<StockItem?>(null) }
    // En tablets la columna no se estira: se centra a un ancho legible.
    val anchoPantalla = LocalConfiguration.current.screenWidthDp
    val margenAncho = (((anchoPantalla - 720).coerceAtLeast(0)) / 2).dp

    val filas = remember(state.result.items, filters.modo, filters.ruta) {
        construirFilas(
            state.result.items,
            filters.modo,
            alcancesMultiples = filters.ruta.isNotBlank() ||
                state.result.uniqueLocales > 1,
        )
    }
    // El local solo aporta cuando la lista mezcla varios y no hay cabecera
    // de local que lo muestre (modo por marca).
    val mostrarLocal = filters.modo == StockMode.POR_MARCA && state.result.uniqueLocales > 1
    val textoProgreso = state.progresoCarga?.let { fraccion ->
        val pct = (fraccion * 100f).roundToInt().coerceIn(0, 100)
        if (state.progresoCompleto || pct >= 100) {
            "Stock actualizado · 100%"
        } else if (pct <= 5) {
            // Fase inicial de conexión a KPI One: aún no hay % real.
            "Conectando con KPI One…"
        } else {
            "Cargando stock… " + pct + "%"
        }
    }
    // La barra de cada tarjeta se mide contra el mayor stock del resultado.
    // Qué eje de filtros está a la vista: estado del stock o cobertura de venta.
    // Son dimensiones distintas y no se muestran a la vez para no mezclar escalas.
    var verCobertura by rememberSaveable { mutableStateOf(false) }
    val maxStock = remember(state.result.items) {
        state.result.items.maxOfOrNull { it.stockUnits } ?: 0.0
    }
    // Marcas que ya aparecen en el listado: con un snapshot viejo pueden no
    // estar en catalog.marcas y aun así ser resultados reales en vivo.
    val marcasVisibles = remember(state.result.items) {
        state.result.items.map { it.brand }
            .filter { it.isNotBlank() && !marcaExcluida(it) }
            .distinct()
            .sorted()
    }
    // Marcas que ofrece el selector con la ruta actual; también alimenta la
    // limpieza de una marca que dejó de existir en la ruta.
    val marcasRuta = marcasParaSelector(state.catalog, filters.ruta, marcasVisibles)
    LaunchedEffect(filters.ruta, marcasRuta) {
        if (state.catalog.marcaFueraDeRuta(filters.ruta, filters.marca)) {
            vm.onFilter { it.copy(marca = "") }
        }
    }

    fun compartir(productos: List<StockItem>, tituloLocal: String? = null) {
        if (productos.isEmpty()) {
            Toast.makeText(
                context,
                "No hay stock para compartir con estos filtros",
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        val contexto = tituloLocal ?: buildList {
            if (filters.ruta.isNotBlank()) add(filters.ruta)
            if (filters.local.isNotBlank()) add(filters.local)
            if (filters.marca.isNotBlank()) add(filters.marca)
        }.joinToString(" · ").ifBlank { "RM" }
        val subtitulo = state.fechaDatos
            ?.let { "Datos de KPI One al " + fechaCorta(it) }
            ?: "Sin fecha de datos"
        val texto = mensajeCompartir(StockResult(items = productos), filters, contexto)
        scope.launch {
            val archivos = runCatching {
                withContext(Dispatchers.IO) {
                    StockShareImage.generate(
                        context = context,
                        items = productos,
                        titulo = contexto,
                        subtitulo = subtitulo,
                        soloNegativos = filters.soloNegativos,
                    )
                }
            }.getOrNull()
            if (archivos.isNullOrEmpty()) {
                Toast.makeText(
                    context,
                    "No se pudo generar la imagen",
                    Toast.LENGTH_SHORT,
                ).show()
                return@launch
            }
            val uris = archivos.map { archivo ->
                FileProvider.getUriForFile(
                    context,
                    context.packageName + ".fileprovider",
                    archivo,
                )
            }
            val intent = if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_STREAM, uris.first())
                }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                    clipData = ClipData.newUri(
                        context.contentResolver,
                        "stock",
                        uris.first(),
                    ).apply {
                        uris.drop(1).forEach { addItem(ClipData.Item(it)) }
                    }
                }
            }
            intent.type = "image/png"
            intent.putExtra(Intent.EXTRA_TEXT, texto)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(intent, "Compartir stock"))
        }
    }

    // ── Banner colapsable ──────────────────────────────────────────
    // Vive fuera de la lista: la lista le pasa el scroll por nestedScroll
    // y el banner se encoge/expande sin empujar el contenido.
    val listState = rememberLazyListState()
    val actualizacion = remember(state.fechaDatos, state.generadoEn) {
        ActualizacionDatos.desdeIso(state.fechaDatos, state.generadoEn)
    }
    val errorBanner = remember(
        state.actualizacionError,
        state.mensajeActualizacion,
        actualizacion,
    ) {
        if (state.actualizacionError) {
            mensajeDeError(state.mensajeActualizacion, actualizacion)
        } else {
            null
        }
    }
    val avisoBanner = remember(
        state.mensajeActualizacion,
        state.actualizacionError,
        state.actualizando,
        state.aviso,
        state.truncado,
    ) {
        when {
            state.truncado -> BannerMensaje(
                texto = "Resultados parciales: acota por local, marca o producto.",
                exito = false,
            )
            state.aviso != null -> BannerMensaje(texto = state.aviso.orEmpty(), exito = false)
            else -> {
                val texto = state.mensajeActualizacion
                if (texto == null || state.actualizacionError || state.actualizando) {
                    null
                } else {
                    BannerMensaje(
                        texto = texto,
                        exito = texto.contains("actualizado", ignoreCase = true),
                    )
                }
            }
        }
    }
    val pideAcceso = state.actualizacionError &&
        (state.mensajeActualizacion?.contains("KPI One", true) == true ||
            state.mensajeActualizacion?.contains("Ajustes", true) == true)
    val puedeForzar = state.actualizacionError &&
        (state.mensajeActualizacion?.contains("incompleto", true) == true ||
            state.mensajeActualizacion?.contains("Fallaron", true) == true)
    // El error necesita más alto: mensaje de dos líneas + acciones.
    val alturaBanner = if (errorBanner != null) {
        BannerAlturas.expandidaError
    } else {
        BannerAlturas.expandida
    }
    val bannerState = rememberCollapsingBannerState(alturaExpandida = alturaBanner)
    val cancelarActualizacion: (() -> Unit)? =
        if (state.actualizando) vm::cancelarActualizacion else null

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(bannerState.nestedScrollConnection),
        ) {
        CollapsingBanner(
            titulo = stringResource(R.string.stock_title),
            subtitulo = when {
                state.fuente == StockSource.LIVE ->
                    "KPI One en vivo · " + state.catalog.locales.size + " locales"
                state.fuente == StockSource.CACHE -> if (state.refrescando) {
                    "Datos guardados · actualizando…"
                } else {
                    "Datos guardados"
                }
                state.isDemo ->
                    "Datos de ejemplo · " + state.catalog.locales.size + " locales"
                else ->
                    "KPI One · " + state.catalog.locales.size + " locales"
            },
            actualizacion = actualizacion,
            cargando = state.actualizando,
            onRefresh = { vm.iniciarActualizacion() },
            estado = bannerState,
            // La barra solo es determinada mientras descarga por lotes.
            progreso = state.progreso
                ?.takeIf { it.phase == StockSyncPhase.DOWNLOADING && it.total > 0 }
                ?.fraction,
            detalleProgreso = state.progreso?.descripcion,
            porcentaje = state.progreso
                ?.takeIf { it.phase == StockSyncPhase.DOWNLOADING && it.total > 0 }
                ?.let { (it.fraction * 100).toInt() },
            error = errorBanner,
            mensaje = avisoBanner,
            etiqueta = if (state.isDemo) "DEMO" else null,
            onCancelar = cancelarActualizacion,
            descripcionRefrescar = "Actualizar stock",
            // El módulo se actualiza solo: no hay ↻ manual en la cabecera.
            mostrarRefrescar = false,
            progresoStock = state.progresoCarga,
            textoProgresoStock = textoProgreso,
            accionesError = {
                if (pideAcceso) {
                    BannerAccion("Revisar acceso", onAbrirAjustes)
                }
                if (puedeForzar) {
                    BannerAccion(
                        texto = "Forzar de todos modos",
                        onClick = { vm.iniciarActualizacion(force = true) },
                    )
                }
            },
        )
        // Barra de búsqueda fija: no se mueve con el scroll de la lista.
        StockSearchBar(
            value = filters.sku,
            onValueChange = { texto -> vm.onFilter { it.copy(sku = texto) } },
            onScan = lanzarEscaner,
            modifier = Modifier.padding(
                start = dimens.spacingMd + margenAncho,
                end = dimens.spacingMd + margenAncho,
                top = dimens.spacingSm,
                bottom = dimens.spacingSm,
            ),
        )
        PullToRefreshBox(
            isRefreshing = state.pullRefresh,
            onRefresh = vm::refrescar,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                start = dimens.spacingMd + margenAncho,
                end = dimens.spacingMd + margenAncho,
                top = dimens.spacingSm,
                bottom = dimens.spacingLg,
            ),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        ) {
            item(key = "filtros") {
                StockScopeRow(
                    catalog = state.catalog,
                    filters = filters,
                    historial = historial,
                    marcasRuta = marcasRuta,
                    onFilter = vm::onFilter,
                    onElegirLocal = vm::elegirLocal,
                    onElegirRuta = vm::elegirRuta,
                )
            }
            if (state.status != StockStatus.Idle && state.status != StockStatus.SinSesion) {
                item(key = "resumen") {
                    ResumenEstados(
                        result = state.result,
                        filtros = filters,
                        enCobertura = verCobertura,
                        osaEnCurso = state.osaEnCurso,
                        onSeccion = { cobertura ->
                            verCobertura = cobertura
                            vm.onFilter {
                                if (cobertura) {
                                    it.copy(
                                        estado = null,
                                        soloNegativos = false,
                                        validacion = null,
                                    )
                                } else {
                                    it.copy(cobertura = null)
                                }
                            }
                        },
                        onSelectEstado = { estado ->
                            vm.onFilter {
                                it.copy(estado = estado, soloNegativos = false, validacion = null)
                            }
                        },
                        onToggleNegativos = {
                            vm.onFilter {
                                val activar = !it.soloNegativos
                                it.copy(
                                    soloNegativos = activar,
                                    estado = null,
                                    cobertura = null,
                                    validacion = if (activar) it.validacion else null,
                                    orden = if (activar && it.orden == StockSort.PRODUCTO_AZ) {
                                        StockSort.GRAVEDAD
                                    } else {
                                        it.orden
                                    },
                                )
                            }
                        },
                        onSelectValidacion = { validacion ->
                            vm.onFilter {
                                it.copy(
                                    validacion = validacion,
                                    soloNegativos = true,
                                )
                            }
                        },
                        onSelectCobertura = { tipo ->
                            vm.onFilter {
                                it.copy(
                                    cobertura = tipo,
                                    estado = null,
                                    soloNegativos = false,
                                    validacion = null,
                                    orden = if (tipo != null && it.orden == StockSort.PRODUCTO_AZ) {
                                        StockSort.MENOR_COBERTURA
                                    } else {
                                        it.orden
                                    },
                                )
                            }
                        },
                    )
                }
                item(key = "detalle") {
                    BarraHerramientas(
                        filtros = filters,
                        onFilter = vm::onFilter,
                        onLimpiar = { vm.onFilter { it.limpiar() } },
                        onCompartir = { compartir(state.result.items) },
                    )
                }
            }
            when (state.status) {
                StockStatus.SinSesion -> item(key = "sin-sesion") {
                    EstadoCard(
                        icono = Icons.Outlined.Lock,
                        titulo = "Configura tu acceso",
                        detalle = "Guarda tu correo y clave en Ajustes; la app entra sola.",
                        accion = "Ir a Ajustes",
                        onAccion = onAbrirAjustes,
                    )
                }
                StockStatus.Idle -> item(key = "inicial") {
                    if (state.catalog.locales.isEmpty()) {
                        EstadoCard(
                            icono = Icons.Outlined.Storefront,
                            titulo = "Sin locales en el rutero",
                            detalle = "No hay locales indexados. Sincroniza el stock o prueba con otro filtro.",
                            accion = "Sincronizar",
                            onAccion = { vm.iniciarActualizacion(force = true) },
                        )
                    } else {
                        EstadoCard(
                            icono = Icons.Outlined.Search,
                            titulo = "Busca para ver el stock",
                            detalle = "Elige un local o una marca, o escribe un producto.",
                        )
                    }
                }
                StockStatus.Loading -> items(3, key = { "skeleton-$it" }) { SkeletonCard() }
                StockStatus.Error -> item(key = "error") {
                    EstadoCard(
                        icono = Icons.Outlined.WarningAmber,
                        titulo = "No se pudo consultar el stock",
                        detalle = "Revisa la conexión e inténtalo de nuevo.",
                        accion = "Reintentar",
                        onAccion = vm::reintentar,
                    )
                }
                StockStatus.Empty -> item(key = "vacio") {
                    val localElegido = state.catalog.locales.firstOrNull { it.code == filters.local }
                    if (state.catalog.locales.isEmpty()) {
                        EstadoCard(
                            icono = Icons.Outlined.Storefront,
                            titulo = "Sin locales en el rutero",
                            detalle = "No hay locales indexados. Sincroniza el stock o prueba con otro filtro.",
                            accion = "Sincronizar",
                            onAccion = { vm.iniciarActualizacion(force = true) },
                        )
                    } else {
                        StockVacio(
                            catalog = state.catalog,
                            filters = filters,
                            localElegido = localElegido,
                            osaEnCurso = state.osaEnCurso,
                            fechaDatos = state.fechaDatos,
                            eanNoEncontrado = state.eanNoEncontrado,
                            onFilter = vm::onFilter,
                            onElegirLocal = vm::elegirLocal,
                            onElegirRuta = vm::elegirRuta,
                            onElegirMarca = vm::elegirMarca,
                            marcasVisibles = marcasVisibles,
                            onActualizar = { vm.iniciarActualizacion() },
                        )
                    }
                }
                StockStatus.Ready -> {
                    filas.forEach { fila ->
                        when (fila) {
                            is FilaStock.Cabecera -> {
                                if (fila.pegajosa) {
                                    // Encabezado pegajoso: no usa animateItem.
                                    stickyHeader(
                                        key = fila.key,
                                        contentType = { "cabecera" },
                                    ) {
                                        CabeceraSeccion(
                                            titulo = fila.titulo,
                                            detalle = fila.detalle,
                                            fecha = fila.fecha,
                                        )
                                    }
                                } else {
                                    item(
                                        key = fila.key,
                                        contentType = { "cabecera" },
                                    ) {
                                        val modificador = if (reducirMovimiento) {
                                            Modifier
                                        } else {
                                            Modifier.animateItem()
                                        }
                                        CabeceraSeccion(
                                            titulo = fila.titulo,
                                            detalle = fila.detalle,
                                            fecha = fila.fecha,
                                            modifier = modificador,
                                        )
                                    }
                                }
                            }
                            is FilaStock.Producto -> item(
                                key = fila.key,
                                contentType = { "producto" },
                            ) {
                                val modificador = if (reducirMovimiento) {
                                    Modifier
                                } else {
                                    Modifier.animateItem()
                                }
                                StockItemCard(
                                    item = fila.item,
                                    mostrarLocal = mostrarLocal,
                                    mostrarSinOsa = state.result.osaConsultada,
                                    fraccion = if (maxStock > 0.0) {
                                        (fila.item.stockUnits / maxStock).toFloat().coerceIn(0f, 1f)
                                    } else {
                                        0f
                                    },
                                    onItemClick = { zoomItem = fila.item },
                                    modifier = modificador,
                                )
                            }
                        }
                    }
                }
            }
        }
        }

        zoomItem?.let { item ->
            StockItemZoom(
                item = item,
                flujoEan = vm::eanParaSku,
                onDismiss = { zoomItem = null },
            )
        }
    }
}
}

// ── Filas agrupadas ────────────────────────────────────────────────

internal sealed interface FilaStock {
    val key: String

    data class Cabecera(
        override val key: String,
        val titulo: String,
        val detalle: String,
        val fecha: String?,
        val localCode: String? = null,
        val items: List<StockItem> = emptyList(),
        /** Encabezado pegajoso: se usa cuando el alcance abarca varios locales. */
        val pegajosa: Boolean = false,
    ) : FilaStock

    data class Producto(
        val item: StockItem,
        override val key: String,
    ) : FilaStock
}

/**
 * Arma las filas de la lista. Con [alcancesMultiples] (ruta o varios locales)
 * el encabezado de local es pegajoso y muestra código · nombre y el conteo de
 * negativos y quiebres del local.
 */
internal fun construirFilas(
    items: List<StockItem>,
    modo: StockMode,
    alcancesMultiples: Boolean = false,
): List<FilaStock> {
    // Red de seguridad: si dos filas compartieran clave, LazyColumn crashea.
    val vistos = HashMap<String, Int>()
    fun claveUnica(base: String): String {
        val veces = (vistos[base] ?: 0) + 1
        vistos[base] = veces
        return if (veces == 1) base else base + "#" + veces
    }
    var ultimaFechaGrupo: String? = null
    // La fecha del grupo se repite solo cuando cambia: si los 12 locales
    // comparten el mismo snapshot, se muestra una vez.
    fun fechaGrupo(lista: List<StockItem>): String? {
        val iso = lista.maxOfOrNull { it.snapshotDate } ?: return null
        if (iso == ultimaFechaGrupo) return null
        ultimaFechaGrupo = iso
        return fechaTexto(iso)
    }
    fun conteos(lista: List<StockItem>): Pair<Int, Int> =
        lista.count { it.stockUnits < 0.0 } to
            lista.count { estadoDe(it) == EstadoTipo.Quiebre }
    fun producto(item: StockItem): FilaStock.Producto = FilaStock.Producto(
        item = item,
        key = claveUnica("p:" + item.storeCode + "|" + item.sku),
    )
    return when (modo) {
    StockMode.POR_LOCAL -> items
        .groupBy { it.storeCode to it.storeName }
        .flatMap { (clave, lista) ->
            val pegajosa = alcancesMultiples
            val (negativos, quiebres) = conteos(lista)
            listOf(
                FilaStock.Cabecera(
                    key = "h:" + clave.first,
                    titulo = if (pegajosa) {
                        etiquetaLocal(clave.first, clave.second)
                    } else {
                        clave.second
                    },
                    detalle = if (pegajosa) {
                        plural(negativos, "negativo", "negativos") + " · " +
                            plural(quiebres, "quiebre", "quiebres")
                    } else {
                        lista.size.toString() + " productos"
                    },
                    fecha = fechaGrupo(lista),
                    localCode = clave.first,
                    items = lista,
                    pegajosa = pegajosa,
                ),
            ) + lista.map { producto(it) }
        }
    StockMode.POR_MARCA -> items
        .groupBy { it.brand }
        .flatMap { (marca, lista) ->
            listOf(
                FilaStock.Cabecera(
                    key = "h:" + marca,
                    titulo = marca,
                    detalle = lista.size.toString() + " productos",
                    fecha = fechaGrupo(lista),
                ),
            ) + lista.map { producto(it) }
        }
    }
}

// ── Buscador ───────────────────────────────────────────────────────

/**
 * Barra de búsqueda del encabezado: acepta producto, SKU o EAN (escrito o
 * escaneado). Queda fija bajo el banner, fuera del scroll de la lista.
 */
@Composable
private fun StockSearchBar(
    modifier: Modifier = Modifier,
    value: String,
    onValueChange: (String) -> Unit,
    onScan: () -> Unit,
) {
    val dimens = LocalAppDimens.current
    val scanCd = stringResource(R.string.escanear_codigo_barras)
    val buscarCd = stringResource(R.string.buscar_cd)
    val limpiarCd = stringResource(R.string.limpiar_cd)
    val placeholder = stringResource(R.string.stock_buscar_placeholder)

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder) },
            leadingIcon = {
                Icon(Icons.Outlined.Search, contentDescription = buscarCd)
            },
            trailingIcon = {
                if (value.isNotEmpty()) {
                    IconButton(
                        onClick = { onValueChange("") },
                        modifier = Modifier.semantics { contentDescription = limpiarCd },
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = null)
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        // Botón de escaneo: mismo estilo que el de Cód. EAN.
        val press = rememberPressInteractionSource()
        Box(
            modifier = Modifier
                .size(dimens.touchMin)
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
                .pressScale(press)
                .clickable(
                    interactionSource = press,
                    indication = LocalIndication.current,
                    onClick = onScan,
                    role = Role.Button,
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

// ── Filtros ────────────────────────────────────────────────────────

/**
 * Segmento Local | Ruta. Mientras el usuario no tocó el control, manda el
 * alcance activo; si lo tocó, manda su elección. Así se puede saltar de un
 * alcance al otro sin tener que limpiar el filtro anterior.
 */
internal fun segmentoAlcance(filtros: StockFilters, ultimo: Boolean?): Boolean =
    ultimo ?: (filtros.ruta.isNotBlank() && filtros.local.isBlank())

@Composable
private fun StockScopeRow(
    catalog: StockCatalog,
    filters: StockFilters,
    historial: StockBusquedas,
    marcasRuta: List<String>,
    onFilter: ((StockFilters) -> StockFilters) -> Unit,
    onElegirLocal: (String) -> Unit,
    onElegirRuta: (String) -> Unit,
) {
    val dimens = LocalAppDimens.current
    // El segmentado tiene estado propio: si el usuario lo tocó, su elección manda
    // y se puede saltar de Local a Ruta sin limpiar el filtro anterior. Sólo
    // cuando no lo tocó (pantalla recién abierta o pref restaurado) manda el
    // alcance activo.
    var ultimoSegmento by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val enRuta = segmentoAlcance(filters, ultimoSegmento)
    var sheetAbierto by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        ) {
            SegmentoAlcance(
                enRuta = enRuta,
                onElegir = { ruta -> ultimoSegmento = ruta },
                modifier = Modifier.weight(1f),
            )
            StockFiltrosBoton(
                activos = filters.activos,
                onClick = { sheetAbierto = true },
            )
        }

        if (enRuta) {
            val localesPorRuta = remember(catalog.locales) {
                catalog.locales.flatMap { it.rutas }.groupingBy { it }.eachCount()
            }
            StockCombo(
                label = "Ruta",
                texto = filters.ruta,
                placeholder = "EMU-3",
                opciones = catalog.rutas
                    .sortedByDescending { localesPorRuta[it] ?: 0 }
                    .map { ruta ->
                        StockSugerencia(
                            valor = ruta,
                            etiqueta = ruta + " · " + (localesPorRuta[ruta] ?: 0),
                            claves = ruta + " " + normalizarRuta(ruta) + " " +
                                ruta.replace("-", " "),
                        )
                    },
                recientes = historial.rutas,
                usos = usosPorTipo(historial, TipoBusquedaStock.RUTA),
                onTexto = { texto -> onFilter { it.conRuta(texto) } },
                onElegir = { opcion -> onElegirRuta(opcion.valor) },
            )
        } else {
            // El campo muestra lo que realmente se busca (código o texto); el
            // nombre del local va debajo.
            val localElegido = catalog.locales.firstOrNull { it.code == filters.local }
            StockCombo(
                label = "Local",
                texto = filters.local,
                detalle = localElegido?.nombreCorto,
                placeholder = "Código o nombre",
                opciones = opcionesDeLocales(catalog.locales),
                recientes = historial.locales,
                usos = usosPorTipo(historial, TipoBusquedaStock.LOCAL),
                onTexto = { texto -> onFilter { it.conLocal(texto) } },
                onElegir = { opcion -> onElegirLocal(opcion.valor) },
            )
        }
    }

    if (sheetAbierto) {
        StockFiltrosSheet(
            catalog = catalog,
            filters = filters,
            historial = historial,
            marcasRuta = marcasRuta,
            onAplicar = { marca, modo ->
                onFilter { it.copy(marca = marca, modo = modo) }
                sheetAbierto = false
            },
            onDismiss = { sheetAbierto = false },
        )
    }
}

/** Segmentado Local | Ruta: elige el alcance principal de la búsqueda. */
@Composable
private fun SegmentoAlcance(
    enRuta: Boolean,
    onElegir: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val localLabel = stringResource(R.string.stock_segmento_local)
    val rutaLabel = stringResource(R.string.stock_segmento_ruta)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf(false to localLabel, true to rutaLabel).forEach { (esRuta, etiqueta) ->
            val activo = esRuta == enRuta
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (activo) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
                contentColor = if (activo) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                border = BorderStroke(
                    1.dp,
                    if (activo) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                ),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .selectable(
                        selected = activo,
                        role = Role.Tab,
                        onClick = { if (!activo) onElegir(esRuta) },
                    ),
            ) {
                Text(
                    text = etiqueta,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                )
            }
        }
    }
}

/** Botón Filtros con la cuenta de filtros activos. */
@Composable
private fun StockFiltrosBoton(
    activos: Int,
    onClick: () -> Unit,
) {
    val filtrosLabel = stringResource(R.string.stock_filtros)
    Box {
        AppFilterChip(
            selected = activos > 0,
            onClick = onClick,
            label = { Text(filtrosLabel) },
            leadingIcon = {
                Icon(
                    Icons.Outlined.FilterList,
                    contentDescription = null,
                    Modifier.size(18.dp),
                )
            },
        )
        if (activos > 0) {
            Badge(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                Text(
                    text = activos.toString(),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

/**
 * Bottom sheet de Filtros: Marca y agrupación. Los cambios se aplican sólo
 * con "Aplicar"; el "Limpiar" global vive en la fila de orden.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StockFiltrosSheet(
    catalog: StockCatalog,
    filters: StockFilters,
    historial: StockBusquedas,
    marcasRuta: List<String>,
    onAplicar: (marca: String, modo: StockMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val dimens = LocalAppDimens.current
    var marca by rememberSaveable { mutableStateOf(filters.marca) }
    var modoNombre by rememberSaveable { mutableStateOf(filters.modo.name) }
    val modo = runCatching { StockMode.valueOf(modoNombre) }
        .getOrDefault(StockMode.POR_LOCAL)
    val hayRuta = filters.ruta.isNotBlank()
    val localesPorMarca = if (hayRuta) {
        catalog.localesPorRutaMarca[normalizarRuta(filters.ruta)].orEmpty()
    } else {
        emptyMap()
    }

    val filtrosLabel = stringResource(R.string.stock_filtros)
    val agruparLabel = stringResource(R.string.stock_agrupar_por)
    val porLocalLabel = stringResource(R.string.stock_por_local)
    val porMarcaLabel = stringResource(R.string.stock_por_marca)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(horizontal = dimens.spacingLg)
                .padding(bottom = dimens.spacingXxl),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingMd),
        ) {
            Text(
                text = filtrosLabel,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            StockCombo(
                label = "Marca",
                texto = marca,
                placeholder = "Marca",
                opciones = marcasRuta.map { nombre ->
                    StockSugerencia(
                        valor = nombre,
                        // El conteo de locales sólo tiene sentido con ruta.
                        etiqueta = if (hayRuta) {
                            nombre + " · " + (localesPorMarca[nombre] ?: 0)
                        } else {
                            nombre
                        },
                        claves = nombre,
                    )
                },
                recientes = historial.marcas,
                usos = usosPorTipo(historial, TipoBusquedaStock.MARCA),
                onTexto = { marca = it },
                onElegir = { opcion -> marca = opcion.valor },
            )
            Text(
                text = agruparLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppFilterChip(
                    selected = modo == StockMode.POR_LOCAL,
                    onClick = { modoNombre = StockMode.POR_LOCAL.name },
                    label = { Text(porLocalLabel) },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Storefront,
                            contentDescription = null,
                            Modifier.size(18.dp),
                        )
                    },
                )
                AppFilterChip(
                    selected = modo == StockMode.POR_MARCA,
                    onClick = { modoNombre = StockMode.POR_MARCA.name },
                    label = { Text(porMarcaLabel) },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Inventory2,
                            contentDescription = null,
                            Modifier.size(18.dp),
                        )
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Spacer(Modifier.weight(1f))
                AppButton(
                    text = "Aplicar",
                    onClick = { onAplicar(marca, modo) },
                )
            }
        }
    }
}

/** Punto verde que late: marca que el filtro está activo. */
@Composable
private fun PuntoActivo(modifier: Modifier = Modifier) {
    val reducir = LocalReducedMotionEnabled.current
    val alfa = if (reducir) {
        1f
    } else {
        val transicion = rememberInfiniteTransition(label = "puntoActivo")
        transicion.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "alfaPunto",
        ).value
    }
    Box(modifier = modifier.size(12.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(AccentGreen.copy(alpha = alfa * 0.35f), CircleShape),
        )
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(AccentGreen.copy(alpha = alfa), CircleShape),
        )
    }
}

/** Estado accesible de una tarjeta-filtro: se anuncia seleccionada o no. */
private fun Modifier.filtroSemantica(
    etiqueta: String,
    valor: Int,
    seleccionada: Boolean,
    alClic: () -> Unit,
): Modifier = semantics(mergeDescendants = true) {
    role = Role.Button
    selected = seleccionada
    stateDescription = if (seleccionada) "Filtro activo" else "Filtro inactivo"
    contentDescription = etiqueta + ", " + valor + " productos" +
        if (seleccionada) ", filtro activo" else ""
    // Sin esto, el nodo fusionado anuncia el botón pero TalkBack no lo activa.
    onClick(label = if (seleccionada) "Quitar filtro" else "Filtrar") {
        alClic()
        true
    }
}

@Composable
private fun ResumenEstados(
    result: StockResult,
    filtros: StockFilters,
    enCobertura: Boolean,
    osaEnCurso: Boolean,
    onSeccion: (Boolean) -> Unit,
    onSelectEstado: (EstadoTipo?) -> Unit,
    onToggleNegativos: () -> Unit,
    onSelectValidacion: (ValidacionNegativo?) -> Unit,
    onSelectCobertura: (CoberturaTipo?) -> Unit,
) {
    SegmentoResumen(enCobertura = enCobertura, onSeccion = onSeccion)
    if (!enCobertura) {
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TarjetaQuiebres(
                valor = result.conteoQuiebre,
                seleccionada = filtros.estado == EstadoTipo.Quiebre,
                onClick = {
                    onSelectEstado(
                        if (filtros.estado == EstadoTipo.Quiebre) null else EstadoTipo.Quiebre,
                    )
                },
                modifier = Modifier.weight(1f),
            )
            TarjetaNegativos(
                valor = result.conteoNegativos,
                seleccionada = filtros.soloNegativos,
                onClick = onToggleNegativos,
                modifier = Modifier.weight(1f),
            )
            TarjetaEstado(
                etiqueta = "Disponible",
                valor = result.conteoDisponible,
                fondo = AccentGreenSoft,
                color = AccentGreenText,
                seleccionada = filtros.estado == EstadoTipo.Disponible,
                onClick = {
                    onSelectEstado(
                        if (filtros.estado == EstadoTipo.Disponible) null else EstadoTipo.Disponible,
                    )
                },
                modifier = Modifier.weight(1f),
            )
        }
        if (filtros.soloNegativos) {
            Spacer(Modifier.height(8.dp))
            CruzarOsa(
                result = result,
                filtros = filtros,
                osaEnCurso = osaEnCurso,
                onSelectValidacion = onSelectValidacion,
            )
        }
    } else {
        Row(
            modifier = Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Días de venta",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "<7 · 7-14 · 15-30 · 30+",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
        Row(
            modifier = Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TarjetaCobertura(
                etiqueta = "Se agota",
                valor = result.conteoCritico,
                tipo = CoberturaTipo.Critico,
                seleccionada = filtros.cobertura == CoberturaTipo.Critico,
                onClick = { onSelectCobertura(if (filtros.cobertura == CoberturaTipo.Critico) null else CoberturaTipo.Critico) },
                modifier = Modifier.weight(1f),
            )
            TarjetaCobertura(
                etiqueta = "Justo",
                valor = result.conteoCoberturaBaja,
                tipo = CoberturaTipo.Bajo,
                seleccionada = filtros.cobertura == CoberturaTipo.Bajo,
                onClick = { onSelectCobertura(if (filtros.cobertura == CoberturaTipo.Bajo) null else CoberturaTipo.Bajo) },
                modifier = Modifier.weight(1f),
            )
            TarjetaCobertura(
                etiqueta = "Holgado",
                valor = result.conteoSano,
                tipo = CoberturaTipo.Sano,
                seleccionada = filtros.cobertura == CoberturaTipo.Sano,
                onClick = { onSelectCobertura(if (filtros.cobertura == CoberturaTipo.Sano) null else CoberturaTipo.Sano) },
                modifier = Modifier.weight(1f),
            )
            TarjetaCobertura(
                etiqueta = "Exceso",
                valor = result.conteoSobrestock,
                tipo = CoberturaTipo.Sobrestock,
                seleccionada = filtros.cobertura == CoberturaTipo.Sobrestock,
                onClick = { onSelectCobertura(if (filtros.cobertura == CoberturaTipo.Sobrestock) null else CoberturaTipo.Sobrestock) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Cruce de los negativos con la OSA de la última visita.
 *
 * Los chips sólo aparecen cuando el cruce terminó: antes "Sin OSA" mentiría.
 * Mientras corre se muestra una línea discreta; si no hay red, se avisa que
 * el cruce necesita conexión.
 */
@Composable
private fun CruzarOsa(
    result: StockResult,
    filtros: StockFilters,
    osaEnCurso: Boolean,
    onSelectValidacion: (ValidacionNegativo?) -> Unit,
) {
    when {
        osaEnCurso && !result.osaConsultada -> {
            Text(
                text = "Cruzando negativos con OSA…",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        result.osaConsultada -> {
            // Los chips cuentan filas de negativos (lo que se ve en la lista);
            // "Todas" usa la suma de los tres cruces, no los SKU únicos.
            val totalFilas = result.conteoConfirmados +
                result.conteoContradicen + result.conteoSinOsa
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AppFilterChip(
                    selected = filtros.validacion == null,
                    onClick = { onSelectValidacion(null) },
                    label = { Text("Todas · " + totalFilas) },
                )
                AppFilterChip(
                    selected = filtros.validacion == ValidacionNegativo.CONTRADICE,
                    onClick = {
                        onSelectValidacion(
                            if (filtros.validacion == ValidacionNegativo.CONTRADICE) {
                                null
                            } else {
                                ValidacionNegativo.CONTRADICE
                            },
                        )
                    },
                    label = { Text("Por verificar · " + result.conteoContradicen) },
                )
                AppFilterChip(
                    selected = filtros.validacion == ValidacionNegativo.CONFIRMADO,
                    onClick = {
                        onSelectValidacion(
                            if (filtros.validacion == ValidacionNegativo.CONFIRMADO) {
                                null
                            } else {
                                ValidacionNegativo.CONFIRMADO
                            },
                        )
                    },
                    label = { Text("Confirmados · " + result.conteoConfirmados) },
                )
                AppFilterChip(
                    selected = filtros.validacion == ValidacionNegativo.SIN_DATO,
                    onClick = {
                        onSelectValidacion(
                            if (filtros.validacion == ValidacionNegativo.SIN_DATO) {
                                null
                            } else {
                                ValidacionNegativo.SIN_DATO
                            },
                        )
                    },
                    label = { Text("Sin OSA · " + result.conteoSinOsa) },
                )
            }
            if (result.conteoContradicen > 0) {
                Text(
                    text = plural(
                        result.conteoContradicen,
                        "negativo",
                        "negativos",
                    ) + " con OSA sí: revisa el stock (puede estar desactualizado).",
                    style = MaterialTheme.typography.labelSmall,
                    color = UrgencyOrangeText,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        result.conteoNegativos > 0 -> {
            Text(
                text = if (result.source == StockSource.LIVE) {
                    "No se pudo consultar OSA; el stock del portal sigue igual."
                } else {
                    "OSA no disponible con datos guardados."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Quiebres: naranjo con borde amarillo que late. Con movimiento reducido queda fijo.
 */
@Composable
private fun TarjetaQuiebres(
    valor: Int,
    seleccionada: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducir = LocalReducedMotionEnabled.current
    val pulso = if (reducir) {
        0.5f
    } else {
        val transicion = rememberInfiniteTransition(label = "quiebres")
        transicion.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(700, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulsoQuiebres",
        ).value
    }
    val fondo = lerp(QuiebreOrangeStart, QuiebreOrangeEnd, pulso)
    val brillo = lerp(QuiebreYellow.copy(alpha = 0.45f), QuiebreYellow, pulso)
    Surface(
        onClick = onClick,
        color = fondo,
        contentColor = NegativosBlack,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(if (seleccionada) 2.5.dp else 1.dp, brillo),
        modifier = modifier.filtroSemantica("Quiebres", valor, seleccionada, onClick),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = valor.toString(),
                    style = MaterialTheme.metricValueCompact,
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = QuiebreYellow,
                    modifier = Modifier.size(14.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Quiebres",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                if (seleccionada) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = NegativosBlack,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
    }
}

/**
 * Negativos: negro con borde neón que parpadea. Con movimiento reducido queda fijo.
 */
@Composable
private fun TarjetaNegativos(
    valor: Int,
    seleccionada: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducir = LocalReducedMotionEnabled.current
    val pulso = if (reducir) {
        0.5f
    } else {
        val transicion = rememberInfiniteTransition(label = "negativos")
        transicion.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(700, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulsoNegativos",
        ).value
    }
    val borde = lerp(NegativosNeon.copy(alpha = 0.35f), NegativosNeon, pulso)
    Surface(
        onClick = onClick,
        color = NegativosBlack,
        contentColor = NegativosNeon,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(if (seleccionada) 2.5.dp else 1.dp, borde),
        modifier = modifier.filtroSemantica("Negativos", valor, seleccionada, onClick),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = valor.toString(),
                    style = MaterialTheme.metricValueCompact,
                )
                Spacer(Modifier.weight(1f))
                if (seleccionada) PuntoActivo()
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Negativos",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                if (seleccionada) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = NegativosNeon,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SegmentoResumen(
    enCobertura: Boolean,
    onSeccion: (Boolean) -> Unit,
) {
    Row(
        // Grupo de pestañas: TalkBack anuncia "1 de 2" y cuál está activa.
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf(false to "Estado", true to "Cobertura").forEach { (esCobertura, etiqueta) ->
            val activo = esCobertura == enCobertura
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (activo) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
                contentColor = if (activo) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                border = BorderStroke(
                    1.dp,
                    if (activo) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant,
                ),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .selectable(
                        selected = activo,
                        role = Role.Tab,
                        onClick = { if (!activo) onSeccion(esCobertura) },
                    ),
            ) {
                Text(
                    text = etiqueta,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun TarjetaCobertura(
    etiqueta: String,
    valor: Int,
    tipo: CoberturaTipo,
    seleccionada: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fondo = when (tipo) {
        CoberturaTipo.Critico -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.75f)
        CoberturaTipo.Bajo -> UrgencyOrangeSoft
        CoberturaTipo.Sano -> AccentGreenSoft
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val color = when (tipo) {
        CoberturaTipo.Critico -> MaterialTheme.colorScheme.error
        CoberturaTipo.Bajo -> UrgencyOrangeText
        CoberturaTipo.Sano -> AccentGreenText
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    TarjetaEstado(
        etiqueta = etiqueta,
        valor = valor,
        fondo = fondo,
        color = color,
        seleccionada = seleccionada,
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun TarjetaEstado(
    etiqueta: String,
    valor: Int,
    fondo: Color,
    color: Color,
    seleccionada: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        color = fondo,
        contentColor = color,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(if (seleccionada) 2.5.dp else 1.dp, color.copy(alpha = if (seleccionada) 1f else 0.35f)),
        modifier = modifier.filtroSemantica(etiqueta, valor, seleccionada, onClick),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = valor.toString(),
                    style = MaterialTheme.metricValueCompact,
                )
                Spacer(Modifier.weight(1f))
                if (seleccionada) PuntoActivo()
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = etiqueta,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                if (seleccionada) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BarraHerramientas(
    filtros: StockFilters,
    onFilter: ((StockFilters) -> StockFilters) -> Unit,
    onLimpiar: () -> Unit,
    onCompartir: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StockDropdown(
            label = "Ordenar",
            value = filtros.orden.label,
            vacio = filtros.orden.label,
            opciones = StockSort.entries.map { it.label },
            onSelect = { etiqueta ->
                val orden = StockSort.entries.firstOrNull { it.label == etiqueta }
                    ?: return@StockDropdown
                onFilter { it.copy(orden = orden) }
            },
            compacto = true,
        )
        Spacer(Modifier.weight(1f))
        // Limpiar sólo aparece cuando hay algo que limpiar.
        if (filtros.activos > 0) {
            AppButton(
                text = "Limpiar",
                onClick = onLimpiar,
                variant = AppButtonVariant.Text,
            )
        }
        AppButton(
            text = "Compartir",
            onClick = onCompartir,
            variant = AppButtonVariant.Text,
            icon = Icons.Outlined.Share,
        )
    }
}

// ── Tarjeta de producto ────────────────────────────────────────────

@Composable
private fun StockItemCard(
    item: StockItem,
    mostrarLocal: Boolean,
    mostrarSinOsa: Boolean,
    fraccion: Float,
    onItemClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val dimens = LocalAppDimens.current
    val estado = estadoDe(item)
    val transito = item.transitUnits ?: 0.0
    val validacion = validacionNegativo(item)
    val descripcion = remember(item, mostrarLocal, mostrarSinOsa) {
        buildString {
            append(item.product)
            append(", ").append(item.brand)
            append(", SKU ").append(item.sku)
            append(", ").append(item.stockLabel).append(" unidades")
            append(", ").append(estado.texto.lowercase())
            item.coberturaDias?.let { append(", cobertura ").append(it.toInt()).append(" días") }
            when (validacion) {
                ValidacionNegativo.CONTRADICE ->
                    append(", OSA dice que sí está en góndola: verificar el stock")
                ValidacionNegativo.CONFIRMADO -> append(", OSA confirma que no está en góndola")
                ValidacionNegativo.SIN_DATO -> if (mostrarSinOsa) append(", sin respuesta OSA")
            }
            if (mostrarLocal) append(", ").append(item.storeName)
            if (transito > 0.0) append(", tránsito ").append(item.transitLabel)
        }
    }

    Card(
        onClick = onItemClick,
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = descripcion },
        colors = CardDefaults.cardColors(containerColor = fondoTarjeta(estado)),
    ) {
        Column {
        Column(
            modifier = Modifier.padding(dimens.spacingMd),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = item.product,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = item.stockLabel,
                        style = MaterialTheme.metricValue,
                        fontWeight = FontWeight.SemiBold,
                        color = when (estado) {
                            EstadoTipo.Quiebre -> MaterialTheme.colorScheme.error
                            EstadoTipo.Negativo -> NegativosBlack
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                    )
                    Text(
                        text = "unid.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // El chip de estado solo aparece cuando hay algo que marcar.
                if (estado != EstadoTipo.Disponible) {
                    BadgeEstado(estado)
                    Spacer(Modifier.width(6.dp))
                }
                if (item.coberturaDias != null) {
                    BadgeCobertura(item)
                    Spacer(Modifier.width(6.dp))
                }
                if (item.stockUnits < 0.0 && (item.osa != null || mostrarSinOsa)) {
                    BadgeOsa(item, mostrarSinOsa)
                    Spacer(Modifier.width(6.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = buildString {
                        append("SKU ").append(item.sku)
                        if (transito > 0.0) {
                            append(" · tránsito ").append(item.transitLabel)
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (transito > 0.0) {
                    Icon(
                        imageVector = Icons.Outlined.LocalShipping,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            if (validacion == ValidacionNegativo.CONTRADICE) {
                Text(
                    text = "OSA dice que está exhibido: el stock del portal puede no estar actualizado.",
                    style = MaterialTheme.typography.labelSmall,
                    color = UrgencyOrangeText,
                )
            }
        }
            // Barra pegada al borde inferior, con el estado como etiqueta.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = estado.texto,
                    style = MaterialTheme.metricLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.widthIn(min = 56.dp),
                )
                Spacer(Modifier.width(dimens.spacingXs))
                LinearProgressIndicator(
                    progress = { fraccion },
                    // Decorativa: el porcentaje no le dice nada al usuario y TalkBack
                    // lo anunciaría como "3 por ciento" al final de cada producto.
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clearAndSetSemantics { },
                    color = colorDe(estado),
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                )
            }
        }
    }
}

@Composable
private fun fondoTarjeta(estado: EstadoTipo): Color = when (estado) {
    // Roles del tema, no colores fijos: si cambia el esquema, acompañan.
    EstadoTipo.Negativo -> NegativosBlack.copy(alpha = 0.06f)
    EstadoTipo.Quiebre -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
    EstadoTipo.Bajo -> UrgencyOrangeSoft.copy(alpha = 0.7f)
    EstadoTipo.Disponible -> MaterialTheme.colorScheme.surfaceContainerLowest
}

@Composable
private fun colorDe(estado: EstadoTipo): Color = when (estado) {
    EstadoTipo.Negativo -> NegativosBlack
    EstadoTipo.Quiebre -> MaterialTheme.colorScheme.error
    EstadoTipo.Bajo -> UrgencyOrangeText
    EstadoTipo.Disponible -> AccentGreenText
}

@Composable
private fun fondoDe(estado: EstadoTipo): Color = when (estado) {
    EstadoTipo.Negativo -> NegativosNeon
    EstadoTipo.Quiebre -> MaterialTheme.colorScheme.errorContainer
    EstadoTipo.Bajo -> UrgencyOrangeSoft
    EstadoTipo.Disponible -> AccentGreenSoft
}

@Composable
private fun BadgeEstado(estado: EstadoTipo) {
    Surface(color = fondoDe(estado), contentColor = colorDe(estado), shape = RoundedCornerShape(6.dp)) {
        Text(
            text = estado.texto,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/**
 * OSA sólo se pinta en negativos: es la evidencia de góndola que permite
 * decidir si el negativo del portal es real o un stock desactualizado.
 */
@Composable
private fun BadgeOsa(item: StockItem, mostrarSinDato: Boolean) {
    val osa = item.osa
    val validacion = validacionNegativo(item)
    if (validacion == ValidacionNegativo.SIN_DATO && !mostrarSinDato) return
    val fondo = when (validacion) {
        ValidacionNegativo.CONTRADICE -> UrgencyOrangeSoft
        ValidacionNegativo.CONFIRMADO,
        ValidacionNegativo.SIN_DATO,
        -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val color = when (validacion) {
        ValidacionNegativo.CONTRADICE -> UrgencyOrangeText
        ValidacionNegativo.CONFIRMADO,
        ValidacionNegativo.SIN_DATO,
        -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val texto = when {
        osa == null -> "Sin OSA"
        osa.estaEnGondola -> "OSA: sí está"
        else -> "OSA: no está"
    }
    Surface(color = fondo, contentColor = color, shape = RoundedCornerShape(6.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (validacion == ValidacionNegativo.CONTRADICE) {
                Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(Modifier.width(3.dp))
            }
            Text(text = texto, style = MaterialTheme.typography.labelSmall)
            if (osa != null && osa.fecha.isNotBlank()) {
                Text(
                    text = " · " + fechaOsaCorta(osa.fecha),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun BadgeCobertura(item: StockItem) {
    val tipo = coberturaDe(item)
    val fondo = when (tipo) {
        CoberturaTipo.Critico -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.75f)
        CoberturaTipo.Bajo -> UrgencyOrangeSoft
        CoberturaTipo.Sano -> AccentGreenSoft
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val color = when (tipo) {
        CoberturaTipo.Critico -> MaterialTheme.colorScheme.error
        CoberturaTipo.Bajo -> UrgencyOrangeText
        CoberturaTipo.Sano -> AccentGreenText
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val dias = item.coberturaDias?.toInt() ?: 0
    Surface(color = fondo, contentColor = color, shape = RoundedCornerShape(6.dp)) {
        Text(
            text = dias.toString() + " d/vent",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun CabeceraSeccion(
    titulo: String,
    detalle: String,
    fecha: String?,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalAppDimens.current
    val reducirMovimiento = LocalReducedMotionEnabled.current

    // Animación de pulso para el halo fluorescente (fuera de remember, al nivel del composable)
    val transicion = rememberInfiniteTransition(label = "marcaPulso")
    val pulso by transicion.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alfaPulso",
    )

    // En movimiento reducido, congelar el pulso en 1f
    val pulsoVisible = if (reducirMovimiento) 1f else pulso

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = dimens.spacingSm, bottom = dimens.spacingXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Cápsula morada fluorescente para el nombre de la marca
        Box(
            modifier = Modifier
                .padding(end = dimens.spacingMd)
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(999.dp),
                    clip = false,
                    ambientColor = DownloadNeonGlow.copy(alpha = pulsoVisible * 0.35f),
                    spotColor = DownloadNeonGlow.copy(alpha = pulsoVisible * 0.5f),
                )
                .background(
                    Brush.linearGradient(
                        colors = listOf(DownloadNeonViolet.copy(alpha = 0.9f), DownloadNeonViolet),
                        start = Offset(pulsoVisible * 0.5f, 0f),
                        end = Offset(1f - pulsoVisible * 0.5f, 0f),
                    ),
                    shape = RoundedCornerShape(999.dp),
                )
                .border(1.5.dp, DownloadNeonIconEdge, RoundedCornerShape(999.dp))
                .padding(horizontal = dimens.spacingMd, vertical = dimens.spacingXs),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = titulo,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .semantics { heading() }
                    .padding(horizontal = 4.dp),
            )
        }
        // Detalle y fecha
        Text(
            text = if (fecha != null) detalle + " · datos al " + fecha else detalle,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

// ── Estados ────────────────────────────────────────────────────────

private data class SugerenciaVacia(
    val etiqueta: String,
    val onClick: () -> Unit,
)

/**
 * Estado vacío de Stock: explica el motivo real y, cuando el texto no
 * corresponde a ninguna opción, ofrece la sugerencia más parecida
 * ("¿Quisiste decir…?"). Antes todo terminaba en "Sin resultados / prueba con
 * otro local", aunque el local no existiera o la marca no estuviera en el
 * snapshot.
 */
@Composable
private fun StockVacio(
    catalog: StockCatalog,
    filters: StockFilters,
    localElegido: StockLocal?,
    osaEnCurso: Boolean,
    fechaDatos: String?,
    eanNoEncontrado: String? = null,
    onFilter: ((StockFilters) -> StockFilters) -> Unit,
    onElegirLocal: (String) -> Unit,
    onElegirRuta: (String) -> Unit,
    onElegirMarca: (String) -> Unit,
    marcasVisibles: List<String> = emptyList(),
    onActualizar: () -> Unit,
) {
    val textoLocal = filters.local.trim()
    val textoMarca = filters.marca.trim()
    val textoRuta = filters.ruta.trim()
    val textoProducto = filters.sku.trim()

    val consultaLocal = ConsultaStock(textoLocal)
    val localesCoincidentes = if (textoLocal.length >= 2) {
        catalog.locales.count { consultaLocal.coincide(it.code + " " + it.name) }
    } else {
        0
    }
    // No es "no existe" si el texto coincide con varios locales (búsqueda por
    // cadena/nombre): ahí el vacío se explica por los filtros, no por el texto.
    val localParcial = localesCoincidentes > 0
    val localSinCoincidencia = localElegido == null && textoLocal.length >= 2 && !localParcial
    val marcasPosibles = if (textoMarca.length >= 2) {
        marcasParaSelector(catalog, textoRuta, marcasVisibles)
    } else {
        emptyList()
    }
    val marcaEnCatalogo = marcasPosibles.any {
        normalizarStock(it) == normalizarStock(textoMarca)
    }
    // "caso" es parcial de "CASO Y CIA": no es una marca fuera del catálogo.
    val marcaParcial = marcaEnCatalogo || marcasPosibles.any {
        ConsultaStock(textoMarca).coincide(it)
    }
    val buscadaRuta = normalizarRuta(textoRuta)
    val rutaExiste = textoRuta.length >= 2 && catalog.rutas.any {
        val normalizada = normalizarRuta(it)
        normalizada == buscadaRuta ||
            (buscadaRuta.length >= 3 && normalizada.startsWith(buscadaRuta))
    }
    val sugerencias: List<SugerenciaVacia> = when {
        localSinCoincidencia -> sugerenciasStock(
            textoLocal,
            opcionesDeLocales(catalog.locales),
            3,
        ).items.map { sugerencia ->
            SugerenciaVacia(sugerencia.etiqueta) {
                onElegirLocal(sugerencia.valor)
            }
        }
        textoRuta.length >= 2 && !rutaExiste -> sugerenciasStock(
            textoRuta,
            opcionesDeRutas(catalog.rutas),
            3,
        ).items.map { sugerencia ->
            SugerenciaVacia(sugerencia.etiqueta) {
                onElegirRuta(sugerencia.valor)
            }
        }
        textoMarca.length >= 2 && !marcaEnCatalogo -> sugerenciasStock(
            textoMarca,
            opcionesDeMarcas(catalog.marcasPara(textoRuta)),
            3,
        ).items.map { sugerencia ->
            SugerenciaVacia(sugerencia.etiqueta) {
                onElegirMarca(sugerencia.valor)
            }
        }
        else -> emptyList()
    }

    val snapshot = fechaDatos?.let { fechaCorta(it) }
    val marcaSoloSnapshot = textoMarca.length >= 2 && textoLocal.isEmpty() &&
        textoRuta.isEmpty() && textoProducto.length < 2
    val (titulo, detalle) = when {
        osaEnCurso && filters.validacion != null ->
            "Cruzando negativos con OSA…" to "Consultando la última visita de cada local."
        filters.validacion == ValidacionNegativo.CONTRADICE ->
            "Sin negativos por verificar" to "Ningún negativo coincide con OSA sí en este corte."
        filters.validacion == ValidacionNegativo.CONFIRMADO ->
            "Sin negativos confirmados" to "Ningún negativo está confirmado por OSA en este corte."
        filters.validacion == ValidacionNegativo.SIN_DATO ->
            "Sin negativos sin OSA" to "Todos los negativos de este corte tienen respuesta OSA."
        eanNoEncontrado != null ->
            "EAN no encontrado" to
                "«" + eanNoEncontrado + "» no está en el catálogo EAN. " +
                "Revisa el código o busca por nombre o SKU."
        localSinCoincidencia ->
            "No encontramos ese local" to
                "«" + filters.local + "» no coincide con ningún local del rutero. " +
                "Prueba con el código o con el nombre."
        textoRuta.length >= 2 && !rutaExiste ->
            "No encontramos esa ruta" to
                "«" + filters.ruta + "» no coincide con ninguna ruta del rutero."
        textoMarca.length >= 2 && !marcaEnCatalogo ->
            "Marca no encontrada" to
                "«" + filters.marca + "» no coincide con ninguna marca del stock descargado" +
                (if (snapshot != null) " (snapshot del " + snapshot + ")" else "") +
                ". Si es un error de tipeo, toca una sugerencia; si es una marca " +
                "nueva, actualiza el stock o ábrela con un local o ruta."
        filters.cobertura != null ->
            "Ningún producto en ese tramo" to
                "No hay productos en ese tramo de cobertura con los filtros actuales."
        filters.soloNegativos ->
            "Sin negativos" to "No hay stock negativo con estos filtros."
        filters.soloQuiebre ->
            "Sin quiebres" to "No hay productos en quiebre con estos filtros."
        filters.estado != null ->
            "Sin productos en estado «" + filters.estado.texto + "»" to
                "Ningún producto de esta búsqueda está en ese estado."
        textoProducto.length >= 2 ->
            "Sin resultados para «" + filters.sku + "»" to if (localElegido != null) {
                localElegido.label + ": no hay coincidencias con ese producto."
            } else {
                "Prueba con parte del nombre del producto o con el SKU."
            }
        localParcial && localElegido == null ->
            "Sin stock para «" + filters.local + "»" to
                "«" + filters.local + "» coincide con " +
                plural(localesCoincidentes, "local", "locales") +
                ", pero ninguno tiene filas con estos filtros."
        textoMarca.length >= 2 && marcaParcial ->
            "Sin stock para «" + filters.marca + "»" to when {
                localElegido != null ->
                    localElegido.label + ": no hay productos de esa marca."
                textoRuta.isNotEmpty() ->
                    "La marca no tiene filas en los locales de la ruta «" + filters.ruta + "»."
                snapshot != null ->
                    "No hay filas en el stock descargado (snapshot del " + snapshot + "). " +
                        "Puede estar desactualizado: actualízalo o combínala con un local o ruta."
                else -> "No hay filas con los filtros actuales."
            }
        localElegido != null ->
            "Sin stock para este local" to
                localElegido.label + ": todavía no hay stock cargado."
        else -> "Sin resultados" to
            "Prueba con otro local, otra ruta, otra marca u otro código."
    }

    val quitarFiltroEstado = filters.estado != null || filters.cobertura != null ||
        filters.soloNegativos || filters.soloQuiebre
    EstadoCard(
        icono = Icons.Outlined.Inventory2,
        titulo = titulo,
        detalle = detalle,
        sugerencias = sugerencias,
        accion = if (quitarFiltroEstado) "Quitar filtro de estado" else "Limpiar filtros",
        onAccion = {
            if (quitarFiltroEstado) {
                onFilter {
                    it.copy(
                        estado = null,
                        cobertura = null,
                        validacion = null,
                        soloQuiebre = false,
                        soloNegativos = false,
                    )
                }
            } else {
                onFilter { it.limpiar() }
            }
        },
        accionSecundaria = if (marcaSoloSnapshot || (textoMarca.length >= 2 && !marcaEnCatalogo)) {
            "Actualizar stock"
        } else {
            null
        },
        onAccionSecundaria = onActualizar,
    )
}

@Composable
private fun EstadoCard(
    icono: androidx.compose.ui.graphics.vector.ImageVector,
    titulo: String,
    detalle: String,
    accion: String? = null,
    onAccion: (() -> Unit)? = null,
    sugerencias: List<SugerenciaVacia> = emptyList(),
    accionSecundaria: String? = null,
    onAccionSecundaria: (() -> Unit)? = null,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(LocalAppDimens.current.spacingLg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icono,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
            Text(titulo, style = MaterialTheme.typography.titleSmall)
            Text(
                text = detalle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (sugerencias.isNotEmpty()) {
                Text(
                    text = "¿Quisiste decir…?",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    sugerencias.forEach { sugerencia ->
                        AppFilterChip(
                            selected = false,
                            onClick = sugerencia.onClick,
                            label = {
                                Text(
                                    text = sugerencia.etiqueta,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
            }
            if (accion != null && onAccion != null) {
                AppButton(
                    text = accion,
                    onClick = onAccion,
                    variant = AppButtonVariant.Text,
                )
            }
            if (accionSecundaria != null && onAccionSecundaria != null) {
                AppButton(
                    text = accionSecundaria,
                    onClick = onAccionSecundaria,
                    variant = AppButtonVariant.Text,
                )
            }
        }
    }
}

@Composable
private fun SkeletonCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column {
            Column(
                modifier = Modifier.padding(LocalAppDimens.current.spacingMd),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ShimmerBlock(widthFraction = 0.7f)
                        ShimmerBlock(widthFraction = 0.45f)
                    }
                    Spacer(Modifier.width(12.dp))
                    ShimmerBlock(modifier = Modifier.width(52.dp))
                }
                ShimmerBlock(modifier = Modifier.width(120.dp))
            }
            ShimmerBlock(modifier = Modifier.fillMaxWidth().height(3.dp))
        }
    }
}

// ── Combos ─────────────────────────────────────────────────────────

@Composable
private fun StockCombo(
    label: String,
    texto: String,
    placeholder: String,
    opciones: List<StockSugerencia>,
    onTexto: (String) -> Unit,
    onElegir: (StockSugerencia) -> Unit,
    detalle: String? = null,
    recientes: List<StockSeleccion> = emptyList(),
    usos: Map<String, Int> = emptyMap(),
) {
    var expandido by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    // Misma regla de coincidencia que la búsqueda (normal + compacta) y
    // ranking por relevancia: la opción más probable va primero y se ve
    // cuántas coincidencias hay en total. Con texto, el desempate por uso va
    // DESPUÉS de la relevancia, sin pisar exacto > prefijo > contiene > typo.
    val consultaTexto = texto.substringBefore("·")
    val consulta = ConsultaStock(consultaTexto)
    val sugerencias = remember(opciones, consulta.normal, consulta.compacto, usos) {
        sugerenciasStock(consultaTexto, opciones, usos = usos)
    }
    // Con el campo vacío se muestran primero las recientes y después el resto.
    // Las que ya no existen en el catálogo (ruta o local dado de baja) no se
    // muestran: no deben poder aplicarse.
    val recientesValidas = recientes.filter { reciente ->
        opciones.any { it.valor == reciente.valor }
    }
    val recientesMostradas = if (consulta.vacia) recientesValidas.take(5) else emptyList()
    // "El resto" no repite lo que ya aparece en "Recientes".
    val itemsMostradas = if (consulta.vacia) {
        val recientesValores = recientesMostradas.map { it.valor }.toSet()
        sugerencias.items.filterNot { it.valor in recientesValores }
    } else {
        sugerencias.items
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = texto,
            onValueChange = { nuevo ->
                onTexto(nuevo)
                expandido = true
            },
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            singleLine = true,
            supportingText = detalle?.let { nombre ->
                {
                    Text(
                        text = nombre,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (texto.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                onTexto("")
                                expandido = false
                            },
                            modifier = Modifier.semantics {
                                contentDescription = "Limpiar " + label
                            },
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = null)
                        }
                    }
                    IconButton(onClick = { expandido = !expandido }) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowDropDown,
                            contentDescription = if (expandido) {
                                "Ocultar opciones de " + label
                            } else {
                                "Mostrar opciones de " + label
                            },
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        // Sugerencias en línea: el foco sigue en el campo y el teclado no se cierra.
        if (expandido && (sugerencias.items.isNotEmpty() || recientesMostradas.isNotEmpty() || !consulta.vacia)) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 2.dp,
            ) {
                Column(
                    modifier = Modifier
                        .heightIn(max = 264.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    // Sección "Recientes": sólo con el campo vacío, antes del resto.
                    if (recientesMostradas.isNotEmpty()) {
                        Text(
                            text = "Recientes",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                start = 12.dp,
                                end = 12.dp,
                                top = 10.dp,
                            ),
                        )
                        recientesMostradas.forEach { reciente ->
                            val opcion = opciones.firstOrNull { it.valor == reciente.valor }
                                ?: StockSugerencia(valor = reciente.valor, etiqueta = reciente.valor)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clickable(role = Role.Button) {
                                        onElegir(opcion)
                                        expandido = false
                                        focusManager.clearFocus()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .semantics(mergeDescendants = true) {},
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.History,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = opcion.etiqueta,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                    if (!consulta.vacia && sugerencias.total > 0) {
                        Text(
                            text = plural(sugerencias.total, "coincidencia", "coincidencias"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                start = 12.dp,
                                end = 12.dp,
                                top = 10.dp,
                            ),
                        )
                    }
                    if (sugerencias.items.isEmpty()) {
                        Text(
                            text = "Sin coincidencias",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                    itemsMostradas.forEach { opcion ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable(role = Role.Button) {
                                    onElegir(opcion)
                                    expandido = false
                                    focusManager.clearFocus()
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .semantics(mergeDescendants = true) {},
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(8.dp))
                            HighlightedText(
                                text = opcion.etiqueta,
                                query = consultaTexto,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StockDropdown(
    label: String,
    value: String,
    vacio: String,
    opciones: List<String>,
    onSelect: (String) -> Unit,
    compacto: Boolean = false,
) {
    var expandido by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { expandido = true },
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (compacto) {
                    Icon(
                        Icons.Outlined.SwapVert,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = if (compacto) value else label + ": " + value.ifBlank { vacio },
                    style = MaterialTheme.typography.labelMedium,
                )
                Icon(
                    Icons.Outlined.ArrowDropDown,
                    contentDescription = "Cambiar " + label,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        DropdownMenu(
            expanded = expandido,
            onDismissRequest = { expandido = false },
            modifier = Modifier.heightIn(max = 320.dp),
        ) {
            opciones.forEach { opcion ->
                DropdownMenuItem(
                    text = { Text(opcion, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        onSelect(opcion)
                        expandido = false
                    },
                )
            }
        }
    }
}

// ── Utilidades ─────────────────────────────────────────────────────

/**
 * Los fallos de red llegan como texto del socket ("Failed to connect…",
 * "Unable to resolve host…"). Se traducen al aviso con los datos en caché;
 * el resto de errores (permisos, KPI One, snapshot incompleto) se muestran
 * tal cual los entrega el ViewModel.
 */
private fun mensajeDeError(mensaje: String?, datos: ActualizacionDatos?): String {
    val texto = mensaje.orEmpty()
    val fecha = datos?.fechaHoraCorta()
    val esRed = texto.isBlank() || listOf(
        "conexión",
        "conect",
        "host",
        "network",
        "internet",
        "timeout",
        "tiempo de espera",
        "socket",
        "unreachable",
        "dns",
    ).any { texto.contains(it, ignoreCase = true) }
    return when {
        esRed && fecha != null -> "Sin conexión, mostrando datos del " + fecha
        esRed -> "Sin conexión. Revisa tu red e inténtalo de nuevo."
        else -> texto
    }
}

private val FORMATO_FECHA: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")

private fun fechaCorta(iso: String): String =
    runCatching { LocalDate.parse(iso).format(FORMATO_FECHA) }.getOrDefault(iso)

/** Días entre la fecha del dato y hoy; null si la fecha viene mal. */
private fun diasDesde(iso: String): Long? = runCatching {
    ChronoUnit.DAYS.between(LocalDate.parse(iso), LocalDate.now())
}.getOrNull()

/** Fecha del snapshot de KPI One, literal. */
private fun fechaTexto(iso: String): String = fechaCorta(iso)

/** Fecha compacta para el badge de OSA: "14/11" en vez de "14-11-2026". */
private fun fechaOsaCorta(iso: String): String =
    runCatching {
        LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("dd/MM"))
    }.getOrDefault(iso.take(10))

private data class LocalGrave(
    val codigo: String,
    val nombre: String,
    val negativos: Int,
    val quiebres: Int,
)

/** "J514 · JUMBO..." salvo que el nombre ya traiga el código. */
private fun etiquetaLocal(codigo: String, nombre: String): String =
    if (nombre.startsWith(codigo, ignoreCase = true)) nombre else codigo + " · " + nombre

/**
 * Resumen listo para pegar en WhatsApp: los 10 locales más complicados.
 *
 * Con el filtro de Negativos activo el mensaje habla solo de negativos
 * (sin quiebres ni "registros"): en ese momento ese es el foco que se
 * quiere mostrar a los mercaderistas.
 */
internal fun mensajeCompartir(
    result: StockResult,
    filtros: StockFilters,
    titulo: String? = null,
): String {
    val items = result.items
    val negativos = items.count { it.stockUnits < 0.0 }
    val quiebres = items.count { estadoDe(it) == EstadoTipo.Quiebre }
    val porVerificar = items.count {
        validacionNegativo(it) == ValidacionNegativo.CONTRADICE
    }
    val peores = items.groupBy { it.storeCode to it.storeName }
        .map { (clave, lista) ->
            LocalGrave(
                codigo = clave.first,
                nombre = clave.second,
                negativos = lista.count { it.stockUnits < 0.0 },
                quiebres = lista.count { estadoDe(it) == EstadoTipo.Quiebre },
            )
        }
        .sortedWith(
            compareByDescending<LocalGrave> { it.negativos }.thenByDescending { it.quiebres },
        )
        .take(10)

    val contexto = titulo ?: listOfNotNull(
        filtros.ruta.takeIf { it.isNotBlank() }?.let { "ruta " + it },
        filtros.local.takeIf { it.isNotBlank() },
        filtros.marca.takeIf { it.isNotBlank() },
    ).joinToString(" · ")

    val localesConNegativos = items
        .filter { it.stockUnits < 0.0 }
        .map { it.storeCode }
        .distinct()
        .size

    return buildString {
        appendLine("Stock " + contexto.ifBlank { "RM" })
        result.maxSnapshot?.let { appendLine("Datos de KPI One al " + fechaCorta(it)) }
        if (filtros.soloNegativos) {
            appendLine(
                plural(negativos, "negativo", "negativos") + " en " +
                    plural(localesConNegativos, "local", "locales"),
            )
            if (porVerificar > 0) {
                appendLine(
                    plural(porVerificar, "negativo", "negativos") +
                        " con OSA sí: revisar el stock (puede estar desactualizado).",
                )
            }
            if (peores.any { it.negativos > 0 }) {
                appendLine()
                appendLine("Locales con más negativos:")
                peores.filter { it.negativos > 0 }.forEach { local ->
                    append("• ").append(etiquetaLocal(local.codigo, local.nombre))
                    append(": ").append(plural(local.negativos, "negativo", "negativos"))
                    appendLine()
                }
            }
        } else {
            appendLine(
                plural(items.size, "registro", "registros") + " · " +
                    plural(quiebres, "quiebre", "quiebres") + " · " +
                    plural(negativos, "negativo", "negativos"),
            )
            if (peores.isNotEmpty()) {
                appendLine()
                appendLine("Locales con más problemas:")
                peores.forEach { local ->
                    append("• ").append(etiquetaLocal(local.codigo, local.nombre))
                    append(": ").append(plural(local.quiebres, "quiebre", "quiebres"))
                    if (local.negativos > 0) {
                        append(", ").append(plural(local.negativos, "negativo", "negativos"))
                    }
                    appendLine()
                }
            }
        }
    }.trim()
}

private fun plural(cantidad: Int, singular: String, plural: String): String =
    cantidad.toString() + " " + if (cantidad == 1) singular else plural

private fun resumenTexto(result: StockResult): String = buildString {
    appendLine("Resumen de stock")
    appendLine("Registros: " + result.items.size)
    appendLine("Productos: " + result.uniqueProducts)
    appendLine("Locales: " + result.uniqueLocales)
    appendLine("Quiebres: " + result.quiebres)
    result.maxSnapshot?.let { appendLine("Datos al: " + fechaCorta(it)) }
}

// ── Previews ───────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun StockSearchBarPreview() {
    MercaderistasTheme {
        StockSearchBar(value = "Aceite", onValueChange = {}, onScan = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun StockItemCardPreview() {
    MercaderistasTheme {
        StockItemCard(
            item = StockItem(
                storeCode = "AL3083",
                storeName = "JUMBO NUNOA",
                brand = "CASO Y CIA",
                product = "N\u00e9ctar Jumex 335 cc Durazno",
                sku = "192829",
                stockUnits = 94.0,
                transitUnits = 0.0,
                snapshotDate = "2026-11-12",
                quiebre = false,
            ),
            mostrarLocal = false,
            mostrarSinOsa = false,
            fraccion = 0.6f,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun StockItemCardNegativoPreview() {
    MercaderistasTheme {
        StockItemCard(
            item = StockItem(
                storeCode = "HI45",
                storeName = "LIDER MAIPU",
                brand = "SUK",
                product = "Falafel",
                sku = "555001",
                stockUnits = -2.0,
                transitUnits = 4.0,
                snapshotDate = "2026-11-12",
                quiebre = false,
            ),
            mostrarLocal = true,
            mostrarSinOsa = true,
            fraccion = 0f,
        )
    }
}
