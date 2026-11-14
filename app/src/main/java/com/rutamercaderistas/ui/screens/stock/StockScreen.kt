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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rutamercaderistas.R
import com.rutamercaderistas.data.stock.StockCatalog
import com.rutamercaderistas.data.stock.StockFilters
import com.rutamercaderistas.data.stock.StockItem
import com.rutamercaderistas.data.stock.StockMode
import com.rutamercaderistas.data.stock.StockAlcance
import com.rutamercaderistas.data.stock.StockResult
import com.rutamercaderistas.data.stock.StockScope
import com.rutamercaderistas.data.stock.StockSort
import com.rutamercaderistas.data.stock.StockSource
import com.rutamercaderistas.data.stock.CoberturaTipo
import com.rutamercaderistas.data.stock.EstadoTipo
import com.rutamercaderistas.data.stock.StockStatus
import com.rutamercaderistas.data.stock.StockSyncPhase
import com.rutamercaderistas.data.stock.coberturaDe
import com.rutamercaderistas.data.stock.conLocal
import com.rutamercaderistas.data.stock.conMarca
import com.rutamercaderistas.data.stock.conRuta
import com.rutamercaderistas.data.stock.estadoDe
import com.rutamercaderistas.data.stock.marcaFueraDeRuta
import com.rutamercaderistas.data.stock.marcasPara
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
import com.rutamercaderistas.ui.components.ShimmerBlock
import com.rutamercaderistas.ui.components.rememberCollapsingBannerState
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
import com.rutamercaderistas.ui.theme.LocalAppDimens
import com.rutamercaderistas.ui.theme.metricValueCompact
import com.rutamercaderistas.ui.theme.metricValue
import com.rutamercaderistas.ui.theme.metricLabel
import com.rutamercaderistas.ui.theme.LocalReducedMotionEnabled
import com.rutamercaderistas.viewmodel.StockViewModel
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun StockScreen(
    onAbrirAjustes: () -> Unit = {},
    vm: StockViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val filters by vm.filters.collectAsStateWithLifecycle()
    // Al volver a la pestaña, si el dato tiene más de un minuto, se refresca
    // en segundo plano sin ocultar la lista que ya está en pantalla.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.alVolver() }
    val dimens = LocalAppDimens.current
    val context = LocalContext.current
    val reducirMovimiento = LocalReducedMotionEnabled.current
    val scope = rememberCoroutineScope()
    // En tablets la columna no se estira: se centra a un ancho legible.
    val anchoPantalla = LocalConfiguration.current.screenWidthDp
    val margenAncho = (((anchoPantalla - 720).coerceAtLeast(0)) / 2).dp

    val filas = remember(state.result.items, filters.modo) {
        construirFilas(state.result.items, filters.modo)
    }
    // El local solo aporta cuando la lista mezcla varios y no hay cabecera
    // de local que lo muestre (modo por marca).
    val mostrarLocal = filters.modo == StockMode.POR_MARCA && state.result.uniqueLocales > 1
    val textoProgreso = state.progresoCarga?.let { fraccion ->
        val pct = (fraccion * 100f).roundToInt().coerceIn(0, 100)
        if (state.progresoCompleto || pct >= 100) {
            "Stock actualizado · 100%"
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
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(
                start = dimens.spacingMd + margenAncho,
                end = dimens.spacingMd + margenAncho,
                top = dimens.spacingSm,
                bottom = dimens.spacingLg,
            ),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        ) {
            item(key = "filtros") {
                StockFiltersCard(
                    catalog = state.catalog,
                    filters = filters,
                    alcance = state.alcance,
                    onAlcance = vm::cambiarAlcance,
                    onFilter = vm::onFilter,
                )
            }
            item(key = "modo") {
                ModoSelector(filters.modo) { modo -> vm.onFilter { it.copy(modo = modo) } }
            }
            if (state.status != StockStatus.Idle && state.status != StockStatus.SinSesion) {
                item(key = "resumen") {
                    ResumenEstados(
                        result = state.result,
                        filtros = filters,
                        enCobertura = verCobertura,
                        onSeccion = { cobertura ->
                            verCobertura = cobertura
                            vm.onFilter {
                                if (cobertura) it.copy(estado = null, soloNegativos = false) else it.copy(cobertura = null)
                            }
                        },
                        onSelectEstado = { estado ->
                            vm.onFilter { it.copy(estado = estado, soloNegativos = false) }
                        },
                        onToggleNegativos = {
                            vm.onFilter {
                                val activar = !it.soloNegativos
                                it.copy(
                                    soloNegativos = activar,
                                    estado = null,
                                    cobertura = null,
                                    orden = if (activar && it.orden == StockSort.PRODUCTO_AZ) {
                                        StockSort.GRAVEDAD
                                    } else {
                                        it.orden
                                    },
                                )
                            }
                        },
                        onSelectCobertura = { tipo ->
                            vm.onFilter {
                                it.copy(
                                    cobertura = tipo,
                                    estado = null,
                                    soloNegativos = false,
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
                    if (state.catalog.locales.isEmpty() && state.alcance.limita) {
                        EstadoCard(
                            icono = Icons.Outlined.Storefront,
                            titulo = "Sin locales en tus rutas",
                            detalle = state.alcance.resumen + ". Prueba con la RM completa.",
                            accion = "Ver RM completa",
                            onAccion = { vm.cambiarAlcance(StockScope.RM_COMPLETA) },
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
                    if (state.catalog.locales.isEmpty() && state.alcance.limita) {
                        EstadoCard(
                            icono = Icons.Outlined.Storefront,
                            titulo = "Sin locales en tus rutas",
                            detalle = state.alcance.resumen + ". Prueba con la RM completa.",
                            accion = "Ver RM completa",
                            onAccion = { vm.cambiarAlcance(StockScope.RM_COMPLETA) },
                        )
                    } else {
                    EstadoCard(
                        icono = Icons.Outlined.Inventory2,
                        titulo = when {
                            filters.soloNegativos -> "Sin negativos"
                            filters.cobertura != null -> "Ningún producto en ese tramo"
                            localElegido != null -> "Sin stock para este local"
                            else -> "Sin resultados"
                        },
                        detalle = when {
                            filters.soloNegativos -> "No hay stock negativo con estos filtros."
                            filters.cobertura != null -> "No hay productos en ese tramo de cobertura con los filtros actuales."
                            localElegido != null -> localElegido.label + ": todavía no hay stock cargado."
                            else -> "Prueba con otro local, otra ruta, otra marca u otro código."
                        },
                        accion = if (filters.cobertura != null) "Ver todos" else "Limpiar filtros",
                        onAccion = {
                            if (filters.cobertura != null) {
                                vm.onFilter { it.copy(cobertura = null) }
                            } else {
                                vm.onFilter {
                                    it.copy(
                                        local = "",
                                        ruta = "",
                                        marca = "",
                                        sku = "",
                                        estado = null,
                                        cobertura = null,
                                        soloQuiebre = false,
                                        soloNegativos = false,
                                    )
                                }
                            }
                        },
                    )
                    }
                }
                StockStatus.Ready -> items(filas, key = { it.key }) { fila ->
                    val modificador = if (reducirMovimiento) Modifier else Modifier.animateItem()
                    when (fila) {
                        is FilaStock.Cabecera -> CabeceraSeccion(
                            titulo = fila.titulo,
                            detalle = fila.detalle,
                            fecha = fila.fecha,
                            modifier = modificador,
                            onCompartir = fila.localCode?.let { codigo ->
                                { compartir(fila.items, etiquetaLocal(codigo, fila.titulo)) }
                            },
                        )
                        is FilaStock.Producto -> StockItemCard(
                            item = fila.item,
                            mostrarLocal = mostrarLocal,
                            mostrarFecha = fila.mostrarFecha,
                            fraccion = if (maxStock > 0.0) {
                                (fila.item.stockUnits / maxStock).toFloat().coerceIn(0f, 1f)
                            } else {
                                0f
                            },
                            modifier = modificador,
                        )
                    }
                }
            }
        }
    }
}

// ── Filas agrupadas ────────────────────────────────────────────────

private sealed interface FilaStock {
    val key: String

    data class Cabecera(
        override val key: String,
        val titulo: String,
        val detalle: String,
        val fecha: String?,
        val localCode: String? = null,
        val items: List<StockItem> = emptyList(),
    ) : FilaStock

    data class Producto(
        val item: StockItem,
        override val key: String,
        val mostrarFecha: Boolean,
    ) : FilaStock
}

private fun construirFilas(items: List<StockItem>, modo: StockMode): List<FilaStock> {
    // Red de seguridad: si dos filas compartieran clave, LazyColumn crashea.
    val vistos = HashMap<String, Int>()
    fun claveUnica(base: String): String {
        val veces = (vistos[base] ?: 0) + 1
        vistos[base] = veces
        return if (veces == 1) base else base + "#" + veces
    }
    var ultimaFecha: String? = null
    var ultimaFechaGrupo: String? = null
    // La fecha del grupo se repite solo cuando cambia: si los 12 locales
    // comparten el mismo snapshot, se muestra una vez.
    fun fechaGrupo(lista: List<StockItem>): String? {
        val iso = lista.maxOfOrNull { it.snapshotDate } ?: return null
        if (iso == ultimaFechaGrupo) return null
        ultimaFechaGrupo = iso
        return fechaTexto(iso)
    }
    fun producto(item: StockItem): FilaStock.Producto {
        val mostrar = item.snapshotDate != ultimaFecha
        if (mostrar) ultimaFecha = item.snapshotDate
        return FilaStock.Producto(
            item = item,
            key = claveUnica("p:" + item.storeCode + "|" + item.sku),
            mostrarFecha = mostrar,
        )
    }
    return when (modo) {
    StockMode.POR_LOCAL -> items
        .groupBy { it.storeCode to it.storeName }
        .flatMap { (clave, lista) ->
            listOf(
                FilaStock.Cabecera(
                    key = "h:" + clave.first,
                    titulo = clave.second,
                    detalle = lista.size.toString() + " productos",
                    fecha = fechaGrupo(lista),
                    localCode = clave.first,
                    items = lista,
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

// ── Filtros ────────────────────────────────────────────────────────

@Composable
private fun StockFiltersCard(
    catalog: StockCatalog,
    filters: StockFilters,
    alcance: StockAlcance,
    onAlcance: (StockScope) -> Unit,
    onFilter: ((StockFilters) -> StockFilters) -> Unit,
) {
    val dimens = LocalAppDimens.current
    val hayFiltros = filters.local.isNotBlank() ||
        filters.ruta.isNotBlank() ||
        filters.marca.isNotBlank() ||
        filters.sku.isNotBlank() ||
        filters.cobertura != null ||
        filters.estado != null ||
        filters.soloQuiebre ||
        filters.soloNegativos

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(dimens.spacingMd),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Buscar",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                if (hayFiltros) {
                    AppButton(
                        text = "Limpiar",
                        onClick = {
                            onFilter {
                                it.copy(
                                    local = "",
                                    ruta = "",
                                    marca = "",
                                    sku = "",
                                    estado = null,
                                    cobertura = null,
                                    soloQuiebre = false,
                                    soloNegativos = false,
                                )
                            }
                        },
                        variant = AppButtonVariant.Text,
                    )
                }
            }

            // Alcance: no es un filtro de la lista, decide qué se descarga y
            // qué locales ofrece el catálogo. Por eso va primero y separado.
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppFilterChip(
                        selected = alcance.scope == StockScope.MIS_RUTAS,
                        onClick = { onAlcance(StockScope.MIS_RUTAS) },
                        label = { Text("Mis rutas") },
                    )
                    AppFilterChip(
                        selected = alcance.scope == StockScope.RM_COMPLETA,
                        onClick = { onAlcance(StockScope.RM_COMPLETA) },
                        label = { Text("RM completa") },
                    )
                }
                Text(
                    text = alcance.resumen,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val localMostrado = catalog.locales
                .firstOrNull { it.code == filters.local }
                ?.label
                ?: filters.local

            StockCombo(
                label = "Local",
                texto = localMostrado,
                placeholder = "Código o nombre",
                opciones = catalog.locales.map {
                    ComboOpcion(
                        valor = it.code,
                        etiqueta = it.label,
                        claves = it.code + " " + it.name,
                    )
                },
                onTexto = { texto -> onFilter { it.conLocal(texto) } },
                onElegir = { opcion -> onFilter { it.conLocal(opcion.valor) } },
            )
            val localesPorRuta = remember(catalog.locales) {
                catalog.locales.flatMap { it.rutas }.groupingBy { it }.eachCount()
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(0.45f)) {
                    StockCombo(
                        label = "Ruta",
                        texto = filters.ruta,
                        placeholder = "EMU-3",
                        opciones = catalog.rutas
                            .sortedByDescending { localesPorRuta[it] ?: 0 }
                            .map { ruta ->
                                ComboOpcion(
                                    valor = ruta,
                                    etiqueta = ruta + " · " + (localesPorRuta[ruta] ?: 0),
                                    claves = ruta + " " + normalizarRuta(ruta) + " " +
                                        ruta.replace("-", " "),
                                )
                            },
                        onTexto = { texto -> onFilter { it.conRuta(texto) } },
                        onElegir = { opcion -> onFilter { it.conRuta(opcion.valor) } },
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.weight(0.55f)) {
                    // Las marcas del combo se limitan a las presentes en los
                    // locales de la ruta elegida.
                    val marcasRuta = catalog.marcasPara(filters.ruta)
                    val hayRuta = filters.ruta.isNotBlank()
                    // Si la ruta cambia y la marca guardada ya no existe ahí, se
                    // limpia una sola vez. Nunca mientras el usuario escribe: el
                    // texto a medio escribir es la búsqueda en curso.
                    LaunchedEffect(filters.ruta, marcasRuta) {
                        if (catalog.marcaFueraDeRuta(filters.ruta, filters.marca)) {
                            onFilter { it.copy(marca = "") }
                        }
                    }
                    val localesPorMarca = if (hayRuta) {
                        catalog.localesPorRutaMarca[normalizarRuta(filters.ruta)].orEmpty()
                    } else {
                        emptyMap()
                    }
                    StockCombo(
                        label = "Marca",
                        texto = filters.marca,
                        placeholder = "Marca",
                        opciones = marcasRuta.map { marca ->
                            ComboOpcion(
                                valor = marca,
                                // El conteo de locales sólo tiene sentido con ruta.
                                etiqueta = if (hayRuta) {
                                    marca + " · " + (localesPorMarca[marca] ?: 0)
                                } else {
                                    marca
                                },
                                claves = marca,
                            )
                        },
                        onTexto = { texto -> onFilter { it.conMarca(texto) } },
                        onElegir = { opcion -> onFilter { it.conMarca(opcion.valor) } },
                    )
                }
            }
            OutlinedTextField(
                value = filters.sku,
                onValueChange = { texto -> onFilter { it.copy(sku = texto) } },
                label = { Text("Producto / SKU") },
                placeholder = { Text("Ej. Aceite o 2088850") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ModoSelector(modo: StockMode, onModo: (StockMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppFilterChip(
            selected = modo == StockMode.POR_LOCAL,
            onClick = { onModo(StockMode.POR_LOCAL) },
            label = { Text("Por local") },
            leadingIcon = { Icon(Icons.Outlined.Storefront, contentDescription = null, Modifier.size(18.dp)) },
        )
        AppFilterChip(
            selected = modo == StockMode.POR_MARCA,
            onClick = { onModo(StockMode.POR_MARCA) },
            label = { Text("Por marca") },
            leadingIcon = { Icon(Icons.Outlined.Inventory2, contentDescription = null, Modifier.size(18.dp)) },
        )
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
    onSeccion: (Boolean) -> Unit,
    onSelectEstado: (EstadoTipo?) -> Unit,
    onToggleNegativos: () -> Unit,
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
    mostrarFecha: Boolean,
    fraccion: Float,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalAppDimens.current
    val estado = estadoDe(item)
    val transito = item.transitUnits ?: 0.0
    val descripcion = buildString {
        append(item.product)
        append(", ").append(item.brand)
        append(", SKU ").append(item.sku)
        append(", ").append(item.stockLabel).append(" unidades")
        append(", ").append(estado.texto.lowercase())
        item.coberturaDias?.let { append(", cobertura ").append(it.toInt()).append(" días") }
        if (mostrarLocal) append(", ").append(item.storeName)
        if (transito > 0.0) append(", tránsito ").append(item.transitLabel)
        if (mostrarFecha) append(", ").append(fechaTexto(item.snapshotDate))
    }

    Card(
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
                BadgeEstado(estado)
                if (item.coberturaDias != null) {
                    Spacer(Modifier.width(6.dp))
                    BadgeCobertura(item)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = buildString {
                        append("SKU ").append(item.sku)
                        if (transito > 0.0) append(" · tránsito ").append(item.transitLabel)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (mostrarFecha) {
                    Text(
                        text = fechaTexto(item.snapshotDate),
                        style = MaterialTheme.metricLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
            // Barra pegada al borde inferior de la tarjeta.
            LinearProgressIndicator(
                progress = { fraccion },
                // Decorativa: el porcentaje no le dice nada al usuario y TalkBack
                // lo anunciaría como "3 por ciento" al final de cada producto.
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clearAndSetSemantics { },
                color = colorDe(estado),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            )
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
    onCompartir: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = LocalAppDimens.current.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = titulo,
            style = MaterialTheme.typography.titleSmall,
            // Permite saltar de local en local con TalkBack.
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = if (fecha != null) detalle + " · datos al " + fecha else detalle,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (onCompartir != null) {
            IconButton(onClick = onCompartir) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = "Compartir local " + titulo,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

// ── Estados ────────────────────────────────────────────────────────

@Composable
private fun EstadoCard(
    icono: androidx.compose.ui.graphics.vector.ImageVector,
    titulo: String,
    detalle: String,
    accion: String? = null,
    onAccion: (() -> Unit)? = null,
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
            if (accion != null && onAccion != null) {
                AppButton(
                    text = accion,
                    onClick = onAccion,
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

private data class ComboOpcion(
    val valor: String,
    val etiqueta: String,
    val claves: String,
)

@Composable
private fun StockCombo(
    label: String,
    texto: String,
    placeholder: String,
    opciones: List<ComboOpcion>,
    onTexto: (String) -> Unit,
    onElegir: (ComboOpcion) -> Unit,
) {
    var expandido by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val consulta = normalizarStock(texto.substringBefore("·"))
    val sugerencias = remember(opciones, consulta) {
        val base = if (consulta.isBlank()) {
            opciones
        } else {
            opciones.filter { opcion ->
                normalizarStock(opcion.claves).contains(consulta) ||
                    normalizarRuta(opcion.claves).contains(normalizarRuta(consulta))
            }
        }
        // Sin texto se respeta el orden recibido (las rutas vienen por cantidad).
        if (consulta.isBlank()) {
            base.take(6)
        } else {
            base.sortedWith(
                compareBy(
                    { if (normalizarStock(it.etiqueta).startsWith(consulta)) 0 else 1 },
                    { normalizarStock(it.etiqueta) },
                ),
            ).take(6)
        }
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
        if (expandido && (sugerencias.isNotEmpty() || consulta.isNotBlank())) {
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
                        .heightIn(max = 216.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (sugerencias.isEmpty()) {
                        Text(
                            text = "Sin coincidencias",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                    sugerencias.forEach { opcion ->
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
                            Text(
                                text = opcion.etiqueta,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
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
