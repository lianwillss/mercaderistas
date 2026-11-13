package com.rutamercaderistas.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rutamercaderistas.BuildConfig
import com.rutamercaderistas.ui.theme.BannerGlass
import com.rutamercaderistas.ui.theme.BannerGlassBorder
import com.rutamercaderistas.ui.theme.BannerPurpleDeep
import com.rutamercaderistas.ui.theme.BannerPurpleLight
import com.rutamercaderistas.ui.theme.BannerPurpleMid
import com.rutamercaderistas.ui.theme.BannerText
import com.rutamercaderistas.ui.theme.BannerTextMuted
import com.rutamercaderistas.ui.theme.FrescuraAtrasado
import com.rutamercaderistas.ui.theme.FrescuraAyer
import com.rutamercaderistas.ui.theme.FrescuraHoy
import com.rutamercaderistas.ui.theme.LocalAppDimens
import com.rutamercaderistas.ui.theme.LocalReducedMotionEnabled
import com.rutamercaderistas.ui.theme.QuiebreYellow
import com.rutamercaderistas.ui.theme.pressScale
import com.rutamercaderistas.ui.theme.rememberPressInteractionSource
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

// ── Alturas ────────────────────────────────────────────────────────

/**
 * Alto del banner. [expandida] son 56 dp de fila + 28 dp de píldora;
 * [colapsada] es la barra mínima (título + refrescar), que sigue siendo
 * el target de 48 dp del botón. Se pasan a [rememberCollapsingBannerState].
 */
object BannerAlturas {
    val colapsada: Dp = 56.dp
    val expandida: Dp = 84.dp

    /** Error visible: mensaje (2 líneas) + fila de acciones, sin apretar. */
    val expandidaError: Dp = 156.dp
}

private val ALTURA_FILA = 56.dp
private val ALTURA_ZONA = 28.dp
private val RADIO_INFERIOR = 28.dp
private val MARGEN_H = 16.dp

// ── Frescura del dato ──────────────────────────────────────────────

/** Antigüedad del snapshot: define el color de la píldora. */
enum class FrescuraDatos {
    /** El snapshot es de hoy. */
    Hoy,

    /** El snapshot es de ayer. */
    Ayer,

    /** Dos días o más. */
    Atrasado,

    /** El archivo no trae fecha. */
    SinFecha,
}

/**
 * "Actualizado hoy 07:21" tal como lo ve el mercaderista.
 *
 * [fecha] es la fecha del snapshot de KPI One (frescura real del dato) y
 * [hora] la hora en que se generó el archivo. La hora solo se muestra si
 * corresponde al mismo día que [fecha]; si no, se prefiere no inventarla.
 */
@Immutable
data class ActualizacionDatos(
    val fecha: LocalDate?,
    val hora: LocalTime? = null,
) {

    fun frescura(hoy: LocalDate = LocalDate.now()): FrescuraDatos {
        val dia = fecha ?: return FrescuraDatos.SinFecha
        // Un reloj adelantado no debe pintar la píldora en rojo.
        val dias = ChronoUnit.DAYS.between(dia, hoy)
        return when {
            dias <= 0L -> FrescuraDatos.Hoy
            dias == 1L -> FrescuraDatos.Ayer
            else -> FrescuraDatos.Atrasado
        }
    }

    fun etiqueta(hoy: LocalDate = LocalDate.now()): String {
        val dia = fecha ?: return "Sin fecha de datos"
        val dias = ChronoUnit.DAYS.between(dia, hoy)
        val horaTexto = hora?.let { " " + FORMATO_HORA.format(it) }.orEmpty()
        return when {
            dias <= 0L -> "Actualizado hoy" + horaTexto
            dias == 1L -> "Actualizado ayer" + horaTexto
            else -> "Actualizado hace " + dias + " días"
        }
    }

    /** "29-10 07:21" (sin hora si no se conoce), para mensajes de error. */
    fun fechaHoraCorta(hoy: LocalDate = LocalDate.now()): String {
        val dia = fecha ?: return "sin fecha"
        val diaTexto = if (dia.year == hoy.year) {
            FORMATO_DIA.format(dia)
        } else {
            FORMATO_DIA_ANIO.format(dia)
        }
        return hora?.let { diaTexto + " " + FORMATO_HORA.format(it) } ?: diaTexto
    }

    companion object {
        /**
         * Construye el modelo desde lo que entrega el repositorio:
         * [fecha] es la fecha ISO del snapshot y [generadoEn] el instante
         * ISO-8601 en que se generó el archivo (puede faltar en el asset).
         */
        fun desdeIso(fecha: String?, generadoEn: String?): ActualizacionDatos? {
            val dia = fecha?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val generado = generadoEn?.let { texto ->
                runCatching {
                    Instant.parse(texto).atZone(ZoneId.systemDefault()).toLocalDateTime()
                }.getOrNull()
            }
            if (dia == null && generado == null) return null
            val hora = generado
                ?.takeIf { dia == null || it.toLocalDate() == dia }
                ?.toLocalTime()
            return ActualizacionDatos(
                fecha = dia ?: generado?.toLocalDate(),
                hora = hora,
            )
        }
    }
}

private val FORMATO_HORA: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("es-CL"))
private val FORMATO_DIA: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd-MM", Locale.forLanguageTag("es-CL"))
private val FORMATO_DIA_ANIO: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.forLanguageTag("es-CL"))

/** Confirmación o aviso transitorio que ocupa el lugar de la píldora. */
@Immutable
data class BannerMensaje(
    val texto: String,
    val exito: Boolean = false,
)

// ── Estado de colapso ──────────────────────────────────────────────

/**
 * Alto del banner guiado por el scroll de la lista.
 *
 * No usa animateDpAsState: el gesto manda y el alto sigue al dedo. Para
 * que el contenido no salte, el delta que agranda o encoge el banner se
 * consume del scroll (pre-scroll al bajar, post-scroll al volver arriba).
 * Enganchar con Modifier.nestedScroll(estado.nestedScrollConnection) en
 * el contenedor que envuelve al banner y a la lista.
 */
@Stable
class CollapsingBannerState internal constructor(
    alturaExpandidaPx: Float,
    alturaColapsadaPx: Float,
) {
    private var maxPx: Float = alturaExpandidaPx
    private var minPx: Float = alturaColapsadaPx

    /** Alto actual del contenido, en píxeles. */
    var alturaPx by mutableFloatStateOf(alturaExpandidaPx)
        private set

    /** 1 = expandido, 0 = colapsado. */
    val visibilidadContenido: Float
        get() = if (maxPx <= minPx) {
            1f
        } else {
            ((alturaPx - minPx) / (maxPx - minPx)).coerceIn(0f, 1f)
        }

    val colapsado: Boolean get() = alturaPx <= minPx + 0.5f

    /**
     * Si cambia el alto expandido (por ejemplo, al aparecer un error), se
     * mantiene la posición: expandido sigue expandido, colapsado sigue
     * colapsado y a mitad de gesto se recorta al rango nuevo.
     */
    fun reconfigurar(alturaExpandidaPx: Float, alturaColapsadaPx: Float) {
        val estabaExpandido = alturaPx >= maxPx - 0.5f
        val estabaColapsado = alturaPx <= minPx + 0.5f
        minPx = alturaColapsadaPx.coerceAtMost(alturaExpandidaPx)
        maxPx = alturaExpandidaPx.coerceAtLeast(minPx)
        alturaPx = when {
            estabaExpandido -> maxPx
            estabaColapsado -> minPx
            else -> alturaPx.coerceIn(minPx, maxPx)
        }
    }

    fun expandir() {
        alturaPx = maxPx
    }

    fun colapsar() {
        alturaPx = minPx
    }

    val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        // Bajar en la lista (dedo hacia arriba: y < 0) encoge el banner y
        // ese alto se descuenta del scroll: el contenido no salta.
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y >= 0f) return Offset.Zero
            return desplazar(available.y)
        }

        // Subir: el sobrante que la lista no pudo consumir (ya está arriba)
        // devuelve el alto al banner.
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            if (available.y <= 0f) return Offset.Zero
            return desplazar(available.y)
        }
    }

    private fun desplazar(delta: Float): Offset {
        val objetivo = (alturaPx + delta).coerceIn(minPx, maxPx)
        val consumido = objetivo - alturaPx
        if (consumido != 0f) alturaPx = objetivo
        return Offset(0f, consumido)
    }
}

@Composable
fun rememberCollapsingBannerState(
    alturaExpandida: Dp = BannerAlturas.expandida,
    alturaColapsada: Dp = BannerAlturas.colapsada,
): CollapsingBannerState {
    val densidad = LocalDensity.current
    val maxPx = with(densidad) { alturaExpandida.toPx() }
    val minPx = with(densidad) { alturaColapsada.toPx() }
    val estado = remember { CollapsingBannerState(maxPx, minPx) }
    LaunchedEffect(maxPx, minPx) { estado.reconfigurar(maxPx, minPx) }
    return estado
}

// ── Banner ─────────────────────────────────────────────────────────

/**
 * Banner superior reutilizable: degradado morado, esquinas inferiores
 * redondeadas, título + subtítulo en una línea, píldora de frescura y un
 * botón de refrescar de 48 dp con fondo circular translúcido.
 *
 * - [cargando] bloquea el botón y muestra el spinner + la barra fina del
 *   borde inferior (determinada con [progreso], indeterminada si es null).
 * - [error] reemplaza la píldora por el aviso con advertencia y acciones.
 * - [mensaje] es la confirmación breve (check + texto) tras terminar.
 * - [etiqueta] es el chip opcional ("DEMO").
 * - [accionesError] recibe acciones extra ("Revisar acceso", "Forzar").
 *
 * Misma estructura en toda la app: solo cambian [titulo], [subtitulo] y
 * las acciones.
 */
@Composable
fun CollapsingBanner(
    titulo: String,
    subtitulo: String,
    actualizacion: ActualizacionDatos?,
    cargando: Boolean,
    onRefresh: () -> Unit,
    estado: CollapsingBannerState,
    modifier: Modifier = Modifier,
    progreso: Float? = null,
    detalleProgreso: String? = null,
    porcentaje: Int? = null,
    error: String? = null,
    mensaje: BannerMensaje? = null,
    etiqueta: String? = null,
    onCancelar: (() -> Unit)? = null,
    accionesError: @Composable RowScope.() -> Unit = {},
    descripcionRefrescar: String = "Actualizar",
    descripcionCancelar: String = "Cancelar actualización",
    /** false en pantallas que se actualizan solas y no exponen recarga manual. */
    mostrarRefrescar: Boolean = true,
    /** 0f..1f real de la carga; si no es null reemplaza la píldora de frescura. */
    progresoStock: Float? = null,
    textoProgresoStock: String? = null,
) {
    val densidad = LocalDensity.current
    val altura = with(densidad) { estado.alturaPx.toDp() }
    val visibilidad = estado.visibilidadContenido

    // La confirmación es breve: 2,5 s el check en el botón y 4 s el texto,
    // aunque el mensaje del ViewModel siga guardado hasta la próxima vez.
    var mensajeVisible by remember { mutableStateOf<BannerMensaje?>(null) }
    var exitoVisible by remember { mutableStateOf(false) }
    LaunchedEffect(mensaje, cargando) {
        if (cargando || mensaje == null) {
            exitoVisible = false
            mensajeVisible = null
        } else {
            exitoVisible = mensaje.exito
            mensajeVisible = mensaje
            delay(2500)
            exitoVisible = false
            delay(1500)
            mensajeVisible = null
        }
    }

    val forma = RoundedCornerShape(bottomStart = RADIO_INFERIOR, bottomEnd = RADIO_INFERIOR)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(forma)
            .background(
                Brush.linearGradient(
                    listOf(BannerPurpleDeep, BannerPurpleMid, BannerPurpleLight),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(altura),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ALTURA_FILA)
                    .padding(start = MARGEN_H, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = titulo,
                    style = MaterialTheme.typography.titleLarge,
                    color = BannerText,
                    maxLines = 1,
                    modifier = Modifier.semantics { heading() },
                )
                if (!etiqueta.isNullOrBlank()) {
                    Spacer(Modifier.width(6.dp))
                    ChipBanner(etiqueta)
                }
                Spacer(Modifier.width(8.dp))
                // Al colapsar, el subtítulo se desvanece: la barra mínima
                // queda con título + refrescar, como se pidió.
                Text(
                    text = subtitulo,
                    style = MaterialTheme.typography.labelMedium,
                    color = BannerTextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .alpha(visibilidad),
                )
                if (error != null) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Outlined.WarningAmber,
                        contentDescription = "Error al actualizar",
                        tint = QuiebreYellow,
                        modifier = Modifier.size(18.dp),
                    )
                }
                if (mostrarRefrescar) {
                    Spacer(Modifier.width(8.dp))
                    BotonActualizar(
                        cargando = cargando,
                        exito = exitoVisible,
                        onRefresh = onRefresh,
                        descripcion = descripcionRefrescar,
                    )
                }
                if (cargando && onCancelar != null) {
                    Spacer(Modifier.width(4.dp))
                    BotonCancelar(onCancelar, descripcionCancelar)
                }
            }
            ZonaSecundaria(
                actualizacion = actualizacion,
                cargando = cargando,
                detalleProgreso = detalleProgreso,
                porcentaje = porcentaje,
                error = error,
                mensaje = mensajeVisible,
                visibilidad = visibilidad,
                alReintentar = onRefresh,
                accionesError = accionesError,
                progresoStock = progresoStock,
                textoProgresoStock = textoProgresoStock,
            )
        }
        if (cargando) {
            BarraDeProgreso(
                modifier = Modifier.align(Alignment.BottomCenter),
                progreso = progreso,
            )
        }
    }
}

@Composable
private fun ZonaSecundaria(
    actualizacion: ActualizacionDatos?,
    cargando: Boolean,
    detalleProgreso: String?,
    porcentaje: Int?,
    error: String?,
    mensaje: BannerMensaje?,
    visibilidad: Float,
    alReintentar: () -> Unit,
    accionesError: @Composable RowScope.() -> Unit,
    progresoStock: Float?,
    textoProgresoStock: String?,
) {
    val base = Modifier
        .fillMaxWidth()
        .alpha(visibilidad)
    when {
        error != null -> ZonaError(error, alReintentar, accionesError, base)
        progresoStock != null -> ProgresoStockGlass(
            progreso = progresoStock,
            texto = textoProgresoStock,
            modifier = base,
        )
        cargando -> Row(
            modifier = base
                .height(ALTURA_ZONA)
                .padding(horizontal = MARGEN_H),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = detalleProgreso ?: "Actualizando…",
                style = MaterialTheme.typography.labelMedium,
                color = BannerTextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (porcentaje != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = porcentaje.toString() + "%",
                    style = MaterialTheme.typography.labelMedium,
                    color = BannerText,
                )
            }
        }
        mensaje != null -> Row(
            modifier = base
                .height(ALTURA_ZONA)
                .padding(horizontal = MARGEN_H),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (mensaje.exito) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = BannerText,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = mensaje.texto,
                style = MaterialTheme.typography.labelMedium,
                color = BannerText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        else -> Box(
            modifier = base
                .height(ALTURA_ZONA)
                .padding(horizontal = MARGEN_H),
            contentAlignment = Alignment.CenterStart,
        ) {
            PastillaFrescura(actualizacion)
        }
    }
}

/** Barra real de carga integrada en la franja morada, con carril de vidrio. */
@Composable
private fun ProgresoStockGlass(
    progreso: Float,
    texto: String?,
    modifier: Modifier = Modifier,
) {
    val reducir = LocalReducedMotionEnabled.current
    val objetivo = progreso.coerceIn(0f, 1f)
    val animado by animateFloatAsState(
        targetValue = objetivo,
        animationSpec = if (reducir) snap() else tween(320, easing = FastOutSlowInEasing),
        label = "progresoStock",
    )
    val forma = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .height(ALTURA_ZONA)
            .padding(horizontal = MARGEN_H)
            .background(BannerGlass.copy(alpha = 0.22f), forma)
            .border(1.dp, BannerGlassBorder, forma)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = texto ?: ("Cargando stock… " + (objetivo * 100).toInt() + "%"),
            style = MaterialTheme.typography.labelMedium,
            color = BannerText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.2f),
        )
        Spacer(Modifier.width(10.dp))
        NeonDownloadBar(
            // Se lee en fase de dibujo: el brillo anima sin recomponer la fila.
            progress = { if (reducir) objetivo else animado },
            modifier = Modifier.weight(1f),
            barHeight = 8.dp,
        )
    }
}

@Composable
private fun ZonaError(
    error: String,
    alReintentar: () -> Unit,
    accionesError: @Composable RowScope.() -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = MARGEN_H, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Outlined.WarningAmber,
                contentDescription = null,
                tint = QuiebreYellow,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.labelMedium,
                color = BannerText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = LocalAppDimens.current.touchMin)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BannerAccion("Reintentar", alReintentar)
            accionesError()
        }
    }
}

/** Acción de texto sobre el degradado (contraste blanco, target de 48 dp). */
@Composable
fun BannerAccion(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppButton(
        text = texto,
        onClick = onClick,
        modifier = modifier,
        variant = AppButtonVariant.Text,
        contentColor = BannerText,
    )
}

@Composable
private fun ChipBanner(texto: String) {
    Surface(
        color = BannerGlass,
        contentColor = BannerText,
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text = texto,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
        )
    }
}

@Composable
private fun PastillaFrescura(actualizacion: ActualizacionDatos?) {
    if (actualizacion == null) return
    val hoy = remember { LocalDate.now() }
    val frescura = actualizacion.frescura(hoy)
    val fondo = when (frescura) {
        FrescuraDatos.Hoy -> FrescuraHoy
        FrescuraDatos.Ayer -> FrescuraAyer
        FrescuraDatos.Atrasado -> FrescuraAtrasado
        FrescuraDatos.SinFecha -> BannerGlass
    }
    Surface(
        color = fondo,
        contentColor = BannerText,
        shape = RoundedCornerShape(50),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Event,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = actualizacion.etiqueta(hoy),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun BotonActualizar(
    cargando: Boolean,
    exito: Boolean,
    onRefresh: () -> Unit,
    descripcion: String,
) {
    val interaccion = rememberPressInteractionSource()
    val reducir = LocalReducedMotionEnabled.current
    Box(
        modifier = Modifier
            .size(LocalAppDimens.current.touchMin)
            .pressScale(interaccion)
            .clip(CircleShape)
            .background(if (exito) FrescuraHoy else BannerGlass)
            .clickable(
                interactionSource = interaccion,
                indication = LocalIndication.current,
                enabled = !cargando,
                role = Role.Button,
                onClickLabel = descripcion,
                onClick = onRefresh,
            )
            .semantics {
                contentDescription = if (cargando) "Actualizando stock" else descripcion
                if (cargando) stateDescription = "Actualizando"
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            // Sin movimiento la rueda sería un loop: queda el ícono quieto.
            cargando && !reducir -> CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = BannerText,
                strokeWidth = 2.dp,
            )
            cargando -> Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = null,
                tint = BannerTextMuted,
                modifier = Modifier.size(20.dp),
            )
            exito -> Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = BannerText,
                modifier = Modifier.size(22.dp),
            )
            else -> Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = null,
                tint = BannerText,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun BotonCancelar(onCancelar: () -> Unit, descripcion: String) {
    val interaccion = rememberPressInteractionSource()
    Box(
        modifier = Modifier
            .size(LocalAppDimens.current.touchMin)
            .pressScale(interaccion)
            .clip(CircleShape)
            .background(BannerGlass)
            .clickable(
                interactionSource = interaccion,
                indication = LocalIndication.current,
                role = Role.Button,
                onClickLabel = descripcion,
                onClick = onCancelar,
            )
            .semantics { contentDescription = descripcion },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Close,
            contentDescription = null,
            tint = BannerText,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Barra fina de 3 dp pegada al borde inferior del banner. */
@Composable
private fun BarraDeProgreso(modifier: Modifier, progreso: Float?) {
    val alto = 3.dp
    when {
        progreso != null -> Box(
            modifier = modifier
                .fillMaxWidth()
                .height(alto)
                .background(BannerGlass),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progreso.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(BannerText),
            )
        }
        // Determinada falsa pero quieta: sin loop cuando hay movimiento reducido.
        LocalReducedMotionEnabled.current -> Box(
            modifier = modifier
                .fillMaxWidth()
                .height(alto)
                .background(BannerGlass),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.35f)
                    .fillMaxHeight()
                    .background(BannerText),
            )
        }
        else -> LinearProgressIndicator(
            modifier = modifier
                .fillMaxWidth()
                .height(alto),
            color = BannerText,
            trackColor = BannerGlass,
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 220)
@Composable
private fun CollapsingBannerPreview() {
    if (BuildConfig.DEBUG) {
        com.rutamercaderistas.ui.theme.MercaderistasTheme {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
                CollapsingBanner(
                    titulo = "Stock",
                    subtitulo = "KPI One · 296 locales",
                    actualizacion = ActualizacionDatos(
                        fecha = LocalDate.now(),
                        hora = LocalTime.of(7, 21),
                    ),
                    cargando = false,
                    onRefresh = {},
                    estado = rememberCollapsingBannerState(),
                    etiqueta = "DEMO",
                )
            }
        }
    }
}
