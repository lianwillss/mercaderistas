package com.rutamercaderistas.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.rutamercaderistas.R
import com.rutamercaderistas.data.stock.EstadoTipo
import com.rutamercaderistas.data.stock.StockItem
import com.rutamercaderistas.data.stock.ValidacionNegativo
import com.rutamercaderistas.data.stock.estadoDe
import com.rutamercaderistas.data.stock.validacionNegativo
import java.io.File
import kotlin.math.roundToInt

// Compilado una vez: se usa al partir el título de la ficha en líneas.
private val ESPACIOS = Regex("\\s+")

/**
 * Arma una o más imágenes PNG con los problemas de stock agrupados por local.
 * No recorta SKUs: si hay muchas filas, reparte el contenido en páginas para
 * que se pueda compartir todo sin el "... y N más".
 */
object StockShareImage {

    private const val ANCHO = 1080
    private const val MARGEN = 44f
    private const val ALTO_CABECERA = 200f
    private const val ALTO_LOCAL = 74f
    private const val ALTO_FILA = 94f

    // Alto de una página: suficiente para leer en WhatsApp y no reventar el bitmap.
    private const val ALTO_MAXIMO = 2500f
    private const val ALTO_BLOQUE = 132f
    private const val ALTO_PIE = 80f

    private val fondo = Color.parseColor("#F4F7FA")
    private val tarjeta = Color.WHITE
    private val azul = Color.parseColor("#0D4F8B")
    private val azulClaro = Color.parseColor("#1A7BB5")
    private val texto = Color.parseColor("#17202A")
    private val suave = Color.parseColor("#5C6166")
    private val rojo = Color.parseColor("#B3261E")
    private val ambar = Color.parseColor("#B45309")

    // Ficha individual (SKU + código de barras + stock). El alto no está fijo:
    // se arma con el contenido real para no dejar franjas en blanco.
    private const val FICHA_ANCHO = 1080
    private const val FICHA_MARGEN = 32f
    private const val FICHA_RADIO = 40f
    private const val FICHA_PADDING = 44f
    private const val FICHA_LINEA_TITULO = 48f
    private const val FICHA_ALTO_BARRAS = 160f

    private val fichaFondoInicio = Color.parseColor("#E9F0FA")
    private val fichaFondoFin = Color.parseColor("#F8FAFD")
    private val fichaPanel = Color.parseColor("#F2F6FB")
    private val fichaPanelBorde = Color.parseColor("#E1E8F2")
    private val fichaLinea = Color.parseColor("#E8EDF4")
    private val fichaSubtitulo = Color.parseColor("#E7F1FA")
    private val verde = Color.parseColor("#1B5E20")
    private val verdeSuave = Color.parseColor("#E6F4EA")
    private val rojoSuave = Color.parseColor("#FBE9EA")
    private val ambarSuave = Color.parseColor("#FDF2E0")
    private val azulSuave = Color.parseColor("#E8F1FA")

    private data class BloqueLocal(
        val codigo: String,
        val nombre: String,
        val items: List<StockItem>,
        val continuacion: Boolean,
        val totalItems: Int,
        val totalQuiebres: Int,
        val totalNegativos: Int,
    )

    private class Pagina {
        val bloques = mutableListOf<BloqueLocal>()
        var alto = ALTO_CABECERA + 80f
    }

    fun generate(
        context: Context,
        items: List<StockItem>,
        titulo: String,
        subtitulo: String,
        /** Con el filtro de Negativos la cabecera resume solo negativos. */
        soloNegativos: Boolean = false,
    ): List<File> {
        val porLocal = items
            .groupBy { it.storeCode to it.storeName }
            .entries
            .sortedWith(
                compareByDescending<Map.Entry<Pair<String, String>, List<StockItem>>> { entrada ->
                    entrada.value.count { it.stockUnits < 0.0 }
                }.thenByDescending { it.value.size },
            )

        val paginas = paginar(porLocal)
        val dir = File(context.cacheDir, "share_images").also { it.mkdirs() }
        val sello = System.currentTimeMillis()
        val totalNegativos = items.count { it.stockUnits < 0.0 }
        val totalQuiebres = items.count { estadoDe(it) == EstadoTipo.Quiebre }
        // Negativos que la OSA marca como "sí está": son los que hay que
        // verificar antes de reportarlos (stock posiblemente desactualizado).
        val totalPorVerificar = items.count {
            validacionNegativo(it) == ValidacionNegativo.CONTRADICE
        }

        return paginas.mapIndexed { indice, pagina ->
            dibujar(
                context = context,
                pagina = pagina,
                titulo = titulo,
                subtitulo = subtitulo,
                totalItems = items.size,
                totalLocales = porLocal.size,
                totalQuiebres = totalQuiebres,
                totalNegativos = totalNegativos,
                totalPorVerificar = totalPorVerificar,
                soloNegativos = soloNegativos,
                paginaActual = indice + 1,
                totalPaginas = paginas.size,
                dir = dir,
                sello = sello,
            )
        }
    }

    private fun paginar(
        porLocal: List<Map.Entry<Pair<String, String>, List<StockItem>>>,
    ): List<Pagina> {
        val paginas = mutableListOf<Pagina>()
        var pagina = Pagina()

        porLocal.forEach { (clave, lista) ->
            if (lista.isEmpty()) return@forEach
            val totalQuiebres = lista.count { estadoDe(it) == EstadoTipo.Quiebre }
            val totalNegativos = lista.count { it.stockUnits < 0.0 }
            var indice = 0
            var continuacion = false
            while (indice < lista.size) {
                // Si no cabe ni la cabecera del local + una fila, empezamos otra página.
                if (pagina.alto + ALTO_BLOQUE + ALTO_FILA > ALTO_MAXIMO) {
                    paginas += pagina
                    pagina = Pagina()
                }
                val capacidad = (
                    (ALTO_MAXIMO - pagina.alto - ALTO_BLOQUE) / ALTO_FILA
                    ).toInt().coerceAtLeast(1)
                val fin = minOf(indice + capacidad, lista.size)
                val bloque = lista.subList(indice, fin)
                pagina.bloques += BloqueLocal(
                    codigo = clave.first,
                    nombre = clave.second,
                    items = bloque,
                    continuacion = continuacion,
                    totalItems = lista.size,
                    totalQuiebres = totalQuiebres,
                    totalNegativos = totalNegativos,
                )
                pagina.alto += ALTO_BLOQUE + bloque.size * ALTO_FILA
                indice = fin
                continuacion = true
                if (indice < lista.size) {
                    paginas += pagina
                    pagina = Pagina()
                }
            }
        }

        if (pagina.bloques.isNotEmpty() || paginas.isEmpty()) paginas += pagina
        return paginas
    }

    private fun dibujar(
        context: Context,
        pagina: Pagina,
        titulo: String,
        subtitulo: String,
        totalItems: Int,
        totalLocales: Int,
        totalQuiebres: Int,
        totalNegativos: Int,
        totalPorVerificar: Int,
        soloNegativos: Boolean,
        paginaActual: Int,
        totalPaginas: Int,
        dir: File,
        sello: Long,
    ): File {
        val alto = (pagina.alto + ALTO_PIE).toInt()
        val bitmap = Bitmap.createBitmap(ANCHO, alto, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(fondo)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Cabecera
        paint.color = azul
        canvas.drawRect(0f, 0f, ANCHO.toFloat(), ALTO_CABECERA, paint)
        paint.color = azulClaro
        canvas.drawRect(0f, ALTO_CABECERA - 8f, ANCHO.toFloat(), ALTO_CABECERA, paint)

        paint.color = Color.WHITE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 46f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(
            recortar("Stock — " + titulo, paint, ANCHO - MARGEN * 2),
            MARGEN,
            84f,
            paint,
        )

        paint.typeface = Typeface.DEFAULT
        paint.textSize = 26f
        canvas.drawText(
            recortar(subtitulo, paint, ANCHO - MARGEN * 2),
            MARGEN,
            132f,
            paint,
        )

        paint.textSize = 24f
        val resumen = if (soloNegativos) {
            // El foco es el negativo: sin quiebres ni "registros" en la cabecera.
            buildString {
                append(plural(totalNegativos, "negativo", "negativos"))
                append(" · ").append(plural(totalLocales, "local", "locales"))
                if (totalPorVerificar > 0) {
                    append(" · ")
                    append(plural(totalPorVerificar, "por verificar", "por verificar"))
                }
            }
        } else {
            plural(totalItems, "registro", "registros") + " · " +
                plural(totalQuiebres, "quiebre", "quiebres") + " · " +
                plural(totalNegativos, "negativo", "negativos") + " · " +
                plural(totalLocales, "local", "locales")
        }
        canvas.drawText(resumen, MARGEN, 170f, paint)
        if (totalPaginas > 1) {
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(
                "Página " + paginaActual + "/" + totalPaginas,
                ANCHO - MARGEN,
                170f,
                paint,
            )
            paint.textAlign = Paint.Align.LEFT
        }

        var y = ALTO_CABECERA + 80f
        pagina.bloques.forEach { bloque ->
            val codigo = bloque.codigo
            val nombre = bloque.nombre
                .let { if (it.startsWith(codigo, ignoreCase = true)) it.substring(codigo.length) else it }
                .trim(' ', '-', '.', '·')
                .ifBlank { bloque.nombre }
            val altoTarjeta = ALTO_LOCAL + bloque.items.size * ALTO_FILA

            paint.color = tarjeta
            canvas.drawRoundRect(
                RectF(MARGEN - 12f, y - 44f, ANCHO - MARGEN + 12f, y + altoTarjeta),
                20f,
                20f,
                paint,
            )

            paint.color = azul
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 30f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(codigo, MARGEN, y, paint)

            val anchoCodigo = paint.measureText(codigo)
            val detalleLocal = if (soloNegativos) {
                plural(bloque.totalNegativos, "neg.", "neg.")
            } else {
                buildString {
                    append(plural(bloque.totalItems, "producto", "productos"))
                    if (bloque.totalQuiebres > 0) {
                        append(" · ").append(plural(bloque.totalQuiebres, "quiebre", "quiebres"))
                    }
                    if (bloque.totalNegativos > 0) {
                        append(" · ").append(bloque.totalNegativos).append(" neg.")
                    }
                }
            }
            val paintDetalle = Paint(paint).apply {
                typeface = Typeface.DEFAULT
                textSize = 24f
                color = suave
                textAlign = Paint.Align.RIGHT
            }
            val anchoDetalle = paintDetalle.measureText(detalleLocal)

            paint.color = texto
            val etiquetaLocal = if (bloque.continuacion) nombre + " (cont.)" else nombre
            canvas.drawText(
                recortar(
                    etiquetaLocal,
                    paint,
                    ANCHO - MARGEN * 2 - anchoCodigo - anchoDetalle - 22f,
                ),
                MARGEN + anchoCodigo + 14f,
                y,
                paint,
            )
            canvas.drawText(detalleLocal, ANCHO - MARGEN, y, paintDetalle)

            var yFila = y + 22f
            bloque.items.forEach { item ->
                paint.color = texto
                paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                paint.textSize = 24f
                canvas.drawText(item.sku, MARGEN, yFila + 34f, paint)

                paint.typeface = Typeface.DEFAULT
                paint.color = texto
                val anchoSku = 132f
                canvas.drawText(
                    recortar(item.product, paint, ANCHO - MARGEN * 2 - anchoSku - 130f),
                    MARGEN + anchoSku,
                    yFila + 34f,
                    paint,
                )

                paint.color = when (estadoDe(item)) {
                    EstadoTipo.Negativo -> rojo
                    EstadoTipo.Quiebre -> ambar
                    else -> texto
                }
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textSize = 32f
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText(item.stockLabel, ANCHO - MARGEN, yFila + 36f, paint)
                paint.textAlign = Paint.Align.LEFT
                paint.typeface = Typeface.DEFAULT

                yFila += ALTO_FILA
            }

            y += ALTO_BLOQUE + bloque.items.size * ALTO_FILA
        }

        paint.color = suave
        paint.textSize = 22f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(
            "Generado por Ruta Mercaderistas" +
                if (totalPaginas > 1) " · página " + paginaActual + " de " + totalPaginas else "",
            MARGEN,
            y + 20f,
            paint,
        )

        val sufijo = if (totalPaginas > 1) "_p" + paginaActual else ""
        val file = File(dir, "stock_" + sello + sufijo + ".png")
        file.outputStream().use { salida ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, salida)
        }
        bitmap.recycle()
        return file
    }

    private fun plural(n: Int, singular: String, plural: String): String =
        n.toString() + " " + if (n == 1) singular else plural

    private fun recortar(texto: String, paint: Paint, maxAncho: Float): String {
        if (maxAncho <= 0f) return ""
        if (paint.measureText(texto) <= maxAncho) return texto
        var corte = texto.length
        while (corte > 4 && paint.measureText(texto.substring(0, corte) + "…") > maxAncho) {
            corte--
        }
        return texto.substring(0, corte).trimEnd() + "…"
    }

    /**
     * Ficha individual para compartir: producto, código de barras y stock del
     * local en una tarjeta compacta. El alto sale del contenido real, así la
     * imagen no arrastra franjas blancas. Sin código catalogado la tarjeta va
     * solo con SKU y stock: nunca se inventa un código a partir del SKU.
     */
    fun generateForStockItem(
        context: Context,
        item: StockItem,
        ean: String?,
    ): File {
        val codigo = ean?.trim()?.takeIf { it.isNotBlank() }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val xTarjeta = FICHA_MARGEN
        val anchoTarjeta = FICHA_ANCHO - FICHA_MARGEN * 2f
        val xContenido = xTarjeta + FICHA_PADDING
        val anchoContenido = anchoTarjeta - FICHA_PADDING * 2f
        val centroX = xTarjeta + anchoTarjeta / 2f

        val paintTitulo = Paint(paint).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 40f
            textAlign = Paint.Align.LEFT
        }
        val lineasTitulo = envolver(
            texto = item.product.ifBlank { item.sku },
            paint = paintTitulo,
            maxAncho = anchoContenido,
            maxLineas = 2,
        )
        val subtitulo = buildString {
            append("SKU ").append(item.sku)
            if (item.brand.isNotBlank()) append("  ·  ").append(item.brand)
        }
        val estado = estadoDe(item)

        // Las barras se rasterizan al tamaño exacto al que se dibujan: sin
        // estirarlas quedan nítidas y el lector las toma sin problema.
        val barra: Pair<String, Bitmap>? = codigo?.let { texto ->
            runCatching {
                dibujarCodigo(
                    ean = texto,
                    ancho = (anchoContenido - 48f).toInt(),
                    alto = FICHA_ALTO_BARRAS.toInt(),
                )
            }.getOrNull()?.let { bitmap -> texto to bitmap }
        }

        val yTarjeta = FICHA_MARGEN
        val yPrimeraLinea = yTarjeta + 72f
        val ySubtitulo = yPrimeraLinea + (lineasTitulo.size - 1) * FICHA_LINEA_TITULO + 36f
        val yCabeceraFin = ySubtitulo + 34f

        val yPanel = yCabeceraFin + 26f
        val yBarras = yPanel + 24f
        val yDigitos = yBarras + FICHA_ALTO_BARRAS + 44f
        val yPanelFin = yBarras + FICHA_ALTO_BARRAS + 64f

        val yMetricas = (if (barra != null) yPanelFin else yCabeceraFin) + 30f
        val yEtiquetas = yMetricas + 24f
        val yValores = yMetricas + 92f
        val ySeparador = yMetricas + 126f
        val yEtiquetaLocal = ySeparador + 40f
        val yValorLocal = yEtiquetaLocal + 40f
        val yPie = yValorLocal + 54f
        val yTarjetaFin = yPie + 32f
        val alto = (yTarjetaFin + FICHA_MARGEN).toInt()

        val bitmap = Bitmap.createBitmap(FICHA_ANCHO, alto, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Fondo tenue y sombra suave: la tarjeta se despega del fondo.
        paint.shader = LinearGradient(
            0f,
            0f,
            0f,
            alto.toFloat(),
            fichaFondoInicio,
            fichaFondoFin,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, FICHA_ANCHO.toFloat(), alto.toFloat(), paint)
        paint.shader = null

        paint.color = Color.argb(30, 13, 79, 139)
        paint.maskFilter = BlurMaskFilter(20f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawRoundRect(
            RectF(xTarjeta, yTarjeta + 8f, xTarjeta + anchoTarjeta, yTarjetaFin + 8f),
            FICHA_RADIO,
            FICHA_RADIO,
            paint,
        )
        paint.maskFilter = null

        paint.color = Color.WHITE
        canvas.drawRoundRect(
            RectF(xTarjeta, yTarjeta, xTarjeta + anchoTarjeta, yTarjetaFin),
            FICHA_RADIO,
            FICHA_RADIO,
            paint,
        )

        // Cabecera con degradado azul y solo las esquinas de arriba redondeadas.
        paint.shader = LinearGradient(
            xTarjeta,
            yTarjeta,
            xTarjeta + anchoTarjeta,
            yCabeceraFin,
            azul,
            azulClaro,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(
            RectF(xTarjeta, yTarjeta, xTarjeta + anchoTarjeta, yCabeceraFin),
            FICHA_RADIO,
            FICHA_RADIO,
            paint,
        )
        canvas.drawRect(
            xTarjeta,
            yCabeceraFin - FICHA_RADIO,
            xTarjeta + anchoTarjeta,
            yCabeceraFin,
            paint,
        )
        paint.shader = null

        lineasTitulo.forEachIndexed { indice, linea ->
            canvas.drawText(
                linea,
                xContenido,
                yPrimeraLinea + indice * FICHA_LINEA_TITULO,
                paintTitulo,
            )
        }

        paint.color = fichaSubtitulo
        paint.typeface = Typeface.DEFAULT
        paint.textSize = 24f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(
            recortar(subtitulo, paint, anchoContenido),
            xContenido,
            ySubtitulo,
            paint,
        )

        if (barra != null) {
            val (textoCodigo, bitmapBarras) = barra
            paint.color = fichaPanel
            canvas.drawRoundRect(
                RectF(xContenido, yPanel, xContenido + anchoContenido, yPanelFin),
                26f,
                26f,
                paint,
            )
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = fichaPanelBorde
            canvas.drawRoundRect(
                RectF(
                    xContenido + 1f,
                    yPanel + 1f,
                    xContenido + anchoContenido - 1f,
                    yPanelFin - 1f,
                ),
                26f,
                26f,
                paint,
            )
            paint.style = Paint.Style.FILL

            canvas.drawBitmap(
                bitmapBarras,
                (xContenido + 24f).roundToInt().toFloat(),
                yBarras.roundToInt().toFloat(),
                null,
            )
            bitmapBarras.recycle()

            paint.color = texto
            paint.typeface = Typeface.MONOSPACE
            paint.textSize = 34f
            paint.letterSpacing = 0.06f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(textoCodigo, centroX, yDigitos, paint)
            paint.letterSpacing = 0f
            paint.textAlign = Paint.Align.LEFT
        }

        // Cifras: el stock es el protagonista, el tránsito acompaña.
        paint.typeface = Typeface.DEFAULT
        paint.textSize = 22f
        paint.color = suave
        paint.letterSpacing = 0.12f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(
            context.resources.getString(R.string.stock_label_stock).uppercase(),
            xContenido,
            yEtiquetas,
            paint,
        )
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(
            context.resources.getString(R.string.stock_label_transito).uppercase(),
            xContenido + anchoContenido,
            yEtiquetas,
            paint,
        )
        paint.letterSpacing = 0f
        paint.textAlign = Paint.Align.LEFT

        paint.color = fichaLinea
        paint.strokeWidth = 2f
        val xSeparador = xContenido + anchoContenido * 0.52f
        canvas.drawLine(xSeparador, yMetricas + 4f, xSeparador, yMetricas + 112f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 56f
        paint.color = colorEstado(estado)
        canvas.drawText(item.stockLabel, xContenido, yValores, paint)
        val anchoStock = paint.measureText(item.stockLabel)
        dibujarChip(
            canvas = canvas,
            paint = paint,
            texto = estado.texto.uppercase(),
            x = xContenido + anchoStock + 20f,
            centroY = yValores - 19f,
            fondo = fondoEstado(estado),
            colorTexto = colorEstado(estado),
        )

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 44f
        paint.textAlign = Paint.Align.RIGHT
        paint.color = if ((item.transitUnits ?: 0.0) > 0.0) texto else suave
        canvas.drawText(item.transitLabel, xContenido + anchoContenido, yValores, paint)
        paint.textAlign = Paint.Align.LEFT

        paint.color = fichaLinea
        paint.strokeWidth = 2f
        canvas.drawLine(xContenido, ySeparador, xContenido + anchoContenido, ySeparador, paint)

        paint.typeface = Typeface.DEFAULT
        paint.textSize = 22f
        paint.color = suave
        paint.letterSpacing = 0.12f
        canvas.drawText(
            context.resources.getString(R.string.stock_label_local).uppercase(),
            xContenido,
            yEtiquetaLocal,
            paint,
        )
        paint.letterSpacing = 0f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 30f
        paint.color = texto
        canvas.drawText(
            recortar(item.storeName, paint, anchoContenido),
            xContenido,
            yValorLocal,
            paint,
        )

        paint.typeface = Typeface.DEFAULT
        paint.textSize = 20f
        paint.color = suave
        canvas.drawText(
            "Generado por Ruta Mercaderistas · " + item.snapshotDate,
            xContenido,
            yPie,
            paint,
        )

        val dir = File(context.cacheDir, "share_images").also { it.mkdirs() }
        val file = File(dir, "stock_sku_" + System.currentTimeMillis() + ".png")
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()
        return file
    }

    /** Parte el texto en líneas que entren en [maxAncho], con "…" si sobra texto. */
    private fun envolver(
        texto: String,
        paint: Paint,
        maxAncho: Float,
        maxLineas: Int,
    ): List<String> {
        val palabras = texto.trim().split(ESPACIOS).filter { it.isNotBlank() }
        if (palabras.isEmpty()) return listOf("")
        val lineas = mutableListOf<String>()
        var actual = ""
        for (palabra in palabras) {
            val candidato = if (actual.isBlank()) palabra else actual + " " + palabra
            if (actual.isBlank() || paint.measureText(candidato) <= maxAncho) {
                actual = candidato
            } else {
                lineas += actual
                actual = palabra
            }
        }
        if (actual.isNotBlank()) lineas += actual
        val visibles = lineas.take(maxLineas).toMutableList()
        if (lineas.size > maxLineas) {
            visibles[maxLineas - 1] = visibles[maxLineas - 1].trimEnd() + " …"
        }
        return visibles.map { recortar(it, paint, maxAncho) }
    }

    /** Píldora de estado (Negativo, Quiebre, ...) con color de fondo suave. */
    private fun dibujarChip(
        canvas: Canvas,
        paint: Paint,
        texto: String,
        x: Float,
        centroY: Float,
        fondo: Int,
        colorTexto: Int,
    ) {
        val paintChip = Paint(paint).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 20f
            letterSpacing = 0.08f
            textAlign = Paint.Align.LEFT
            style = Paint.Style.FILL
        }
        val alto = 40f
        val padH = 15f
        val derecha = x + paintChip.measureText(texto) + padH * 2f
        paintChip.color = fondo
        canvas.drawRoundRect(
            RectF(x, centroY - alto / 2f, derecha, centroY + alto / 2f),
            alto / 2f,
            alto / 2f,
            paintChip,
        )
        paintChip.color = colorTexto
        val fm = paintChip.fontMetrics
        val baseline = centroY - (fm.ascent + fm.descent) / 2f
        canvas.drawText(texto, x + padH, baseline, paintChip)
    }

    private fun colorEstado(estado: EstadoTipo): Int = when (estado) {
        EstadoTipo.Negativo -> rojo
        EstadoTipo.Quiebre -> ambar
        EstadoTipo.Bajo -> azul
        EstadoTipo.Disponible -> verde
    }

    private fun fondoEstado(estado: EstadoTipo): Int = when (estado) {
        EstadoTipo.Negativo -> rojoSuave
        EstadoTipo.Quiebre -> ambarSuave
        EstadoTipo.Bajo -> azulSuave
        EstadoTipo.Disponible -> verdeSuave
    }

    /**
     * Mismo criterio de formato que el módulo EAN: EAN-13 cuando el código es
     * numérico de 12/13 dígitos sin cero inicial y CODE_128 en el resto, para
     * no perder ceros iniciales ni códigos alfanuméricos.
     *
     * El fondo sale transparente para que las barras se apoyen sobre el panel
     * claro de la ficha en vez de dejar un rectángulo blanco encima.
     */
    private fun dibujarCodigo(ean: String, ancho: Int, alto: Int): Bitmap {
        val digits = ean.filter(Char::isDigit)
        val alfanumerico = ean.any { !it.isDigit() }
        val formato = when {
            !alfanumerico && digits.length == 13 && digits[0] != '0' -> BarcodeFormat.EAN_13
            !alfanumerico && digits.length == 12 -> BarcodeFormat.EAN_13
            else -> BarcodeFormat.CODE_128
        }
        val dato = if (formato == BarcodeFormat.CODE_128) ean else digits
        val generado = BarcodeEncoder().encodeBitmap(dato, formato, ancho, alto)
        val anchoReal = generado.width
        val altoReal = generado.height
        val pixeles = IntArray(anchoReal * altoReal)
        generado.getPixels(pixeles, 0, anchoReal, 0, 0, anchoReal, altoReal)
        for (i in pixeles.indices) {
            // Cualquier píxel claro pasa a transparente; las barras quedan negras.
            pixeles[i] = if ((pixeles[i] and 0xFF) < 128) Color.BLACK else Color.TRANSPARENT
        }
        val barras = Bitmap.createBitmap(pixeles, anchoReal, altoReal, Bitmap.Config.ARGB_8888)
        generado.recycle()
        return barras
    }
}
