package com.rutamercaderistas.data.stock

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Última búsqueda mostrada, guardada en disco.
 *
 * Es la red de seguridad para cuando el ViewModel se recrea (muerte de proceso,
 * pestaña descartada por el sistema o instalación nueva): la pantalla puede
 * pintar los datos al instante y después refrescarlos en segundo plano.
 */
@Serializable
data class StockCacheDto(
    val v: Int = 1,
    val local: String = "",
    val ruta: String = "",
    val marca: String = "",
    val sku: String = "",
    val estado: String? = null,
    val cobertura: String? = null,
    val soloQuiebre: Boolean = false,
    val soloNegativos: Boolean = false,
    val orden: String = "PRODUCTO_AZ",
    val modo: String = "POR_LOCAL",
    val fetchedAt: String? = null,
    val total: Int = 0,
    val truncated: Boolean = false,
    val conteoQuiebre: Int = 0,
    val conteoBajo: Int = 0,
    val conteoDisponible: Int = 0,
    val conteoNegativos: Int = 0,
    val conteoCritico: Int = 0,
    val conteoCoberturaBaja: Int = 0,
    val conteoSano: Int = 0,
    val conteoSobrestock: Int = 0,
    val items: List<StockRowDto> = emptyList(),
)

data class CachedStock(
    val local: String,
    val ruta: String,
    val marca: String,
    val sku: String,
    val fetchedAt: String?,
    val total: Int,
    val truncated: Boolean,
    val items: List<StockItem>,
    val conteoQuiebre: Int = 0,
    val conteoBajo: Int = 0,
    val conteoDisponible: Int = 0,
    val conteoNegativos: Int = 0,
    val conteoCritico: Int = 0,
    val conteoCoberturaBaja: Int = 0,
    val conteoSano: Int = 0,
    val conteoSobrestock: Int = 0,
    val estado: EstadoTipo? = null,
    val cobertura: CoberturaTipo? = null,
    val soloQuiebre: Boolean = false,
    val soloNegativos: Boolean = false,
    val orden: StockSort = StockSort.PRODUCTO_AZ,
    val modo: StockMode = StockMode.POR_LOCAL,
) {
    fun coincide(filtros: StockFilters): Boolean =
        local == filtros.local &&
            ruta == filtros.ruta &&
            marca == filtros.marca &&
            sku == filtros.sku
}

@Singleton
class StockCache @Inject constructor(
    private val storage: StockStorage,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private var lastKey: String? = null
    private var lastSavedAt = 0L

    @Synchronized
    fun load(): CachedStock? = runCatching {
        val texto = storage.read(FILE) ?: return null
        val dto = json.decodeFromString<StockCacheDto>(texto)
        val items = dto.items.map { it.toItem() }
        if (items.isEmpty()) return null
        CachedStock(
            local = dto.local,
            ruta = dto.ruta,
            marca = dto.marca,
            sku = dto.sku,
            fetchedAt = dto.fetchedAt,
            total = dto.total,
            truncated = dto.truncated,
            items = items,
            conteoQuiebre = dto.conteoQuiebre,
            conteoBajo = dto.conteoBajo,
            conteoDisponible = dto.conteoDisponible,
            conteoNegativos = dto.conteoNegativos,
            conteoCritico = dto.conteoCritico,
            conteoCoberturaBaja = dto.conteoCoberturaBaja,
            conteoSano = dto.conteoSano,
            conteoSobrestock = dto.conteoSobrestock,
            estado = dto.estado?.let { runCatching { EstadoTipo.valueOf(it) }.getOrNull() },
            cobertura = dto.cobertura?.let { runCatching { CoberturaTipo.valueOf(it) }.getOrNull() },
            soloQuiebre = dto.soloQuiebre,
            soloNegativos = dto.soloNegativos,
            orden = runCatching { StockSort.valueOf(dto.orden) }
                .getOrDefault(StockSort.PRODUCTO_AZ),
            modo = runCatching { StockMode.valueOf(dto.modo) }
                .getOrDefault(StockMode.POR_LOCAL),
        )
    }.getOrNull()

    @Synchronized
    fun save(filtros: StockFilters, resultado: StockResult) {
        if (resultado.items.isEmpty()) return
        val clave = clave(filtros)
        val ahora = System.currentTimeMillis()
        // Evita reescribir el archivo por cambios de orden/estado o por el
        // mismo refresco repetido en pocos segundos.
        if (clave == lastKey && ahora - lastSavedAt < MIN_INTERVALO_MS) return
        val dto = StockCacheDto(
            local = filtros.local,
            ruta = filtros.ruta,
            marca = filtros.marca,
            sku = filtros.sku,
            estado = filtros.estado?.name,
            cobertura = filtros.cobertura?.name,
            soloQuiebre = filtros.soloQuiebre,
            soloNegativos = filtros.soloNegativos,
            orden = filtros.orden.name,
            modo = filtros.modo.name,
            fetchedAt = resultado.fetchedAt ?: Instant.now().toString(),
            total = resultado.total,
            truncated = resultado.truncated,
            conteoQuiebre = resultado.conteoQuiebre,
            conteoBajo = resultado.conteoBajo,
            conteoDisponible = resultado.conteoDisponible,
            conteoNegativos = resultado.conteoNegativos,
            conteoCritico = resultado.conteoCritico,
            conteoCoberturaBaja = resultado.conteoCoberturaBaja,
            conteoSano = resultado.conteoSano,
            conteoSobrestock = resultado.conteoSobrestock,
            items = resultado.items.map { it.toRowDto() },
        )
        if (storage.writeAtomic(FILE, json.encodeToString(dto))) {
            lastKey = clave
            lastSavedAt = ahora
        }
    }

    @Synchronized
    fun clear() {
        storage.delete(FILE)
        lastKey = null
        lastSavedAt = 0L
    }

    private fun clave(filtros: StockFilters): String =
        filtros.local + "|" + filtros.ruta + "|" + filtros.marca + "|" + filtros.sku +
            "|" + filtros.estado + "|" + filtros.cobertura +
            "|" + filtros.soloQuiebre + "|" + filtros.soloNegativos +
            "|" + filtros.orden + "|" + filtros.modo

    companion object {
        const val FILE = "stock-cache.json"
        private const val MIN_INTERVALO_MS = 3_000L
    }
}
