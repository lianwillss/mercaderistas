package com.rutamercaderistas.data.stock.remote

import com.rutamercaderistas.BuildConfig
import com.rutamercaderistas.data.stock.StockItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Catálogo de KPI One: códigos de local, marcas y nombres. */
data class KpiCatalog(
    val codes: List<String>,
    val brands: List<String>,
    val names: Map<String, String>,
)

/** Una página de inventario. */
data class InventoryPage(
    val items: List<StockItem>,
    val total: Int,
)

class KpiHttpException(val statusCode: Int, message: String) : IOException(message)

class SessionExpiredException : Exception("La sesión de KPI One expiró")

/**
 * Cliente único contra KPI One. Lo usan la búsqueda en vivo
 * ([RemoteStockRepository]) y la descarga masiva ([StockBulkSync]).
 *
 * Sin servidor intermedio: login por cookie, catálogo y
 * `inventory-browse` paginado. Los reintentos y el re-login viven acá
 * para que ambos caminos se comporten igual.
 */
class KpiOneClient(
    private val session: KpiSessionStore,
    baseUrl: String = BASE_URL,
    private val http: OkHttpClient = defaultHttpClient(session),
    private val buildCredentials: () -> Pair<String, String>? = {
        val email = BuildConfig.KP_EMAIL
        val password = BuildConfig.KP_PASSWORD
        if (email.isBlank() || password.isBlank()) null else email to password
    },
) {
    private val base = baseUrl.trimEnd('/')
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var catalogCache: KpiCatalog? = null

    @Volatile
    private var catalogLoadedAt: Long = 0L

    fun hasSession(): Boolean = !session.cookie().isNullOrBlank()

    /** ¿Hay cookie, credenciales guardadas o credenciales de build para intentar? */
    fun puedeEntrar(): Boolean =
        hasSession() ||
            session.hayCredenciales() ||
            (session.autoLoginPermitido() && buildCredentials() != null)

    suspend fun ensureSession(): Boolean = withContext(Dispatchers.IO) {
        if (hasSession()) return@withContext true
        loginAutomatico()
    }

    suspend fun login(email: String, password: String): Boolean =
        withContext(Dispatchers.IO) { loginBlocking(email.trim(), password) }

    fun logout() = session.borrarSesion()

    fun clearCatalogCache() {
        catalogCache = null
        catalogLoadedAt = 0L
    }

    suspend fun catalog(force: Boolean = false): KpiCatalog = withContext(Dispatchers.IO) {
        val cache = catalogCache
        val edad = System.currentTimeMillis() - catalogLoadedAt
        if (!force && cache != null && edad < CATALOG_TTL_MS) return@withContext cache

        val opciones = getJson("/api/b2b-reports/terreno/inventory-filter-options").jsonObject
        val codigos = opciones["storeCodes"]?.jsonArray.orEmpty()
            .mapNotNull { it.texto() }
            .distinct()
        val marcas = opciones["brands"]?.jsonArray.orEmpty()
            .mapNotNull { it.texto() }
            .distinct()
            .sorted()
        // El maestro de nombres es cosmético: si falla, seguimos con el código.
        val nombres = runCatching { nombresDeLocales() }.getOrDefault(emptyMap())
        val resultado = KpiCatalog(codigos, marcas, nombres)
        catalogCache = resultado
        catalogLoadedAt = System.currentTimeMillis()
        resultado
    }

    suspend fun fetchInventory(
        storeCodes: List<String>,
        brand: String? = null,
        query: String = "",
        offset: Int = 0,
        limit: Int = LIMIT,
    ): InventoryPage = withContext(Dispatchers.IO) {
        val url = buildString {
            append("/api/b2b-reports/terreno/inventory-browse?")
            if (storeCodes.isNotEmpty()) {
                append("storeCodes=").append(encode(storeCodes.joinToString(","))).append("&")
            }
            if (!brand.isNullOrBlank()) {
                append("brands=").append(encode(brand)).append("&")
            }
            if (query.isNotBlank()) {
                append("q=").append(encode(query)).append("&")
            }
            append("limit=").append(limit).append("&offset=").append(offset)
        }
        val root = getJson(url).jsonObject
        val items = root["items"]?.jsonArray.orEmpty().mapNotNull { it.aItem() }
        val total = root["pagination"]?.jsonObject?.get("total")?.numero()?.toInt() ?: items.size
        InventoryPage(items, total)
    }

    // ── Internos ───────────────────────────────────────────────────

    private fun loginAutomatico(): Boolean {
        val credenciales = session.credenciales()
            ?: (if (session.autoLoginPermitido()) buildCredentials() else null)
            ?: return false
        return loginBlocking(credenciales.first, credenciales.second)
    }

    private fun loginBlocking(email: String, password: String): Boolean {
        if (email.isBlank() || password.isBlank()) return false
        // Un login fallido no debe dejar viva una cookie vieja.
        session.borrarSesion()
        val cuerpo = buildJsonObject {
            put("email", email)
            put("password", password)
        }.toString()
        val request = Request.Builder()
            .url(base + "/api/auth/login")
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .post(cuerpo.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return runCatching {
            http.newCall(request).execute().use { respuesta ->
                val cookie = respuesta.headers("Set-Cookie")
                    .mapNotNull { header ->
                        header.substringBefore(";").takeIf { it.contains("=") }
                    }
                    .joinToString("; ")
                respuesta.body?.close()
                if (!respuesta.isSuccessful || cookie.isBlank()) return@use false
                // Si el Keystore no puede cifrar, no damos la sesión por buena.
                session.guardarCookie(cookie) && !session.cookie().isNullOrBlank()
            }
        }.getOrDefault(false)
    }

    /**
     * GET con cookie. Si KPI One devuelve 401/403 o HTML (sesión vencida),
     * borra la cookie, hace un login automático y reintenta una sola vez.
     */
    private fun getJson(path: String): JsonElement {
        var reingreso = false
        while (true) {
            val request = Request.Builder()
                .url(if (path.startsWith("http")) path else base + path)
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .build()
            var reintentar = false
            executeWithRetry(request).use { respuesta ->
                val cuerpo = respuesta.body?.string().orEmpty()
                val esHtml = cuerpo.trimStart().startsWith("<")
                val sinSesion = respuesta.code == 401 || respuesta.code == 403 ||
                    (respuesta.isSuccessful && esHtml)
                if (sinSesion) {
                    session.borrarSesion()
                    if (!reingreso && loginAutomatico()) {
                        reingreso = true
                        reintentar = true
                    } else {
                        throw SessionExpiredException()
                    }
                } else {
                    if (!respuesta.isSuccessful) {
                        throw KpiHttpException(respuesta.code, cuerpo.take(200))
                    }
                    return json.parseToJsonElement(cuerpo)
                }
            }
            if (!reintentar) throw IllegalStateException("No se pudo completar la solicitud")
        }
    }

    private fun executeWithRetry(request: Request): Response {
        var intento = 0
        while (true) {
            try {
                val respuesta = http.newCall(request).execute()
                if (respuesta.code == 429 || respuesta.code >= 500) {
                    if (intento >= MAX_REINTENTOS) return respuesta
                    val espera = respuesta.header("Retry-After")
                        ?.toLongOrNull()
                        ?.times(1_000)
                        ?: BACKOFF_MS[intento.coerceAtMost(BACKOFF_MS.lastIndex)]
                    respuesta.close()
                    Thread.sleep(espera)
                    intento++
                    continue
                }
                return respuesta
            } catch (e: IOException) {
                if (intento >= MAX_REINTENTOS) throw e
                Thread.sleep(BACKOFF_MS[intento.coerceAtMost(BACKOFF_MS.lastIndex)])
                intento++
            }
        }
    }

    private fun nombresDeLocales(): Map<String, String> {
        val salida = linkedMapOf<String, String>()
        var offset = 0
        var paginas = 0
        while (paginas < MAX_PAGINAS_STORES) {
            val lista = getJson("/api/stores?limit=500&offset=$offset").jsonArray
            if (lista.isEmpty()) break
            lista.forEach { elemento ->
                val obj = elemento.jsonObject
                val codigo = obj["storeCode"]?.texto() ?: return@forEach
                val nombre = obj["name"]?.texto() ?: return@forEach
                salida[codigo] = nombre
            }
            if (lista.size < 500) break
            offset += 500
            paginas++
        }
        return salida
    }

    private fun JsonElement?.texto(): String? =
        (this as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonElement?.numero(): Double? =
        (this as? JsonPrimitive)?.content?.toDoubleOrNull()

    private fun JsonElement?.entero(): Int? =
        (this as? JsonPrimitive)?.content?.toDoubleOrNull()?.toInt()

    private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()

    private fun JsonElement.aItem(): StockItem? {
        val obj = this as? JsonObject ?: return null
        val codigo = obj["store_code"]?.texto() ?: return null
        return StockItem(
            storeCode = codigo,
            storeName = obj["store_name"]?.texto().orEmpty(),
            brand = obj["brand_raw"]?.texto().orEmpty(),
            product = obj["product_desc_raw"]?.texto().orEmpty(),
            sku = obj["sku_b2b_raw"]?.texto().orEmpty(),
            stockUnits = obj["stock_units"]?.numero() ?: 0.0,
            transitUnits = obj["transit_units"]?.numero(),
            snapshotDate = obj["snapshot_date"]?.texto().orEmpty(),
            quiebre = obj["quiebre"]?.entero() == 1,
            daysOfInventory = obj["days_of_inventory"]?.entero(),
            // La venta viene en la misma respuesta que el stock: sirve para la
            // cobertura desde la primera sincronización, sin pedir nada extra.
            saleLastWeek = obj["sale_last_week"]?.entero(),
            saleLast4w = obj["sale_last_4w"]?.entero(),
        )
    }

    private fun encode(valor: String): String = URLEncoder.encode(valor, "UTF-8")

    companion object {
        const val BASE_URL = "https://kpione-app-production.up.railway.app"
        const val LIMIT = 500
        private const val CATALOG_TTL_MS = 60 * 60 * 1000L
        private const val MAX_REINTENTOS = 3
        private const val MAX_PAGINAS_STORES = 10
        private val BACKOFF_MS = longArrayOf(800L, 2_000L, 4_000L)
        private const val USER_AGENT = "RutaMercaderistas/" + BuildConfig.VERSION_NAME + " (Android)"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun defaultHttpClient(session: KpiSessionStore): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val cookie = session.cookie()
                    val request = if (cookie.isNullOrBlank()) {
                        chain.request()
                    } else {
                        chain.request().newBuilder().header("Cookie", cookie).build()
                    }
                    chain.proceed(request)
                }
                .build()
    }
}
