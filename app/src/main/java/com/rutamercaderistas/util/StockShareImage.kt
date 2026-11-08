package com.rutamercaderistas.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.rutamercaderistas.data.stock.EstadoTipo
import com.rutamercaderistas.data.stock.StockItem
import com.rutamercaderistas.data.stock.estadoDe
import java.io.File

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
            plural(totalNegativos, "negativo", "negativos") + " · " +
                plural(totalLocales, "local", "locales")
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
}
