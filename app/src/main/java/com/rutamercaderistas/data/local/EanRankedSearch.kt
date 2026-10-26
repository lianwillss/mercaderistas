package com.rutamercaderistas.data.local

import androidx.sqlite.db.SimpleSQLiteQuery
import com.rutamercaderistas.services.compactNorm

/**
 * Búsqueda EAN resuelta **entera en SQLite**: ranking, filtros de marca/caja,
 * facetas de filtros y conteo total comparten el mismo WHERE, de modo que lo
 * que se ve, lo que se cuenta y lo que se filtra no pueden divergir.
 *
 * Reglas del query (ver [buildRankedSearch]):
 * - WHERE: un grupo OR por token (todos los campos de código + normalizados),
 *   unidos por AND; opcionalmente marca exacta y caja exacta.
 * - ORDER BY: score DESC y luego marca/descripcion/id como desempate estable.
 *   El desempate por id es obligatorio: la paginación de Room es LIMIT/OFFSET,
 *   y sin un orden total dos páginas pueden repetir u omitir filas.
 * - Tiers y pesos (cada peso supera la suma máxima de los tiers menores):
 *   EAN exacto (ignorando ceros) 300000, EAN prefijo 20000, EAN contiene 1000,
 *   SKU exacto 200, marca exacta 20, golpes de tokens en marca 2/1/0.
 *
 * Los tokens ya vienen de [compactNorm] ([a-z0-9]), así que son seguros en
 * LIKE (sin comodines). Puro y testeable en JVM.
 */
data class RankedQuery(val sql: String, val args: List<Any?>) {
    fun toQuery(): SimpleSQLiteQuery = SimpleSQLiteQuery(sql, args.toTypedArray())
}

// Columnas por las que se puede matchear un token. Debe coincidir con el
// orden de los argumentos que agrega [whereClause] (10 LIKE + 4 ltrim).
private const val TOKEN_MATCHES = "p.eanPrincipal LIKE ? OR p.codCencosud LIKE ?" +
    " OR p.codProveedor LIKE ? OR p.codigoBarra LIKE ?" +
    " OR p.descripcionProducto LIKE ? OR p.marca LIKE ?" +
    " OR p.descripcion_norm LIKE ? OR p.marca_norm LIKE ?" +
    " OR p.descripcion_norm_nospace LIKE ? OR p.marca_norm_nospace LIKE ?" +
    " OR ltrim(p.eanPrincipal,'0') LIKE ? OR ltrim(p.codCencosud,'0') LIKE ?" +
    " OR ltrim(p.codProveedor,'0') LIKE ? OR ltrim(p.codigoBarra,'0') LIKE ?"

private const val TOKEN_ARG_COUNT = 14

internal fun eanTokens(query: String): List<String> =
    query.split(Regex("\\s+"))
        .map { compactNorm(it) }
        .filter { it.isNotBlank() }

/**
 * WHERE compartido por resultados, facetas y conteo. Devuelve el fragmento
 * (vacío si no hay condiciones) y sus argumentos en el orden en que aparecen.
 * [brand] y [caja] son valores exactos de las columnas marca y conversion;
 * brand = "" filtra "Sin marca", por eso null significa sin filtro.
 */
private fun whereClause(
    tokens: List<String>,
    brand: String?,
    caja: String?,
    onlyWithCaja: Boolean = false,
): Pair<String, List<Any?>> {
    val parts = mutableListOf<String>()
    val args = mutableListOf<Any?>()
    tokens.forEach { token ->
        parts.add("($TOKEN_MATCHES)")
        val like = "%$token%"
        repeat(TOKEN_ARG_COUNT - 4) { args.add(like) }
        // Token sin ceros: "0658…" y "658…" deben encontrarse igual.
        val likeTrimmed = "%${token.trimStart('0')}%"
        repeat(4) { args.add(likeTrimmed) }
    }
    if (brand != null) {
        parts.add("p.marca = ?")
        args.add(brand)
    }
    if (caja != null) {
        parts.add("p.conversion = ?")
        args.add(caja)
    }
    if (onlyWithCaja) {
        parts.add("p.conversion IS NOT NULL AND p.conversion != ''")
    }
    val where = if (parts.isEmpty()) "" else " WHERE " + parts.joinToString(" AND ") { it }
    return where to args
}

/**
 * Resultados con ranking. Null cuando no hay tokens (el llamador usa
 * [buildBrowseSearch]); los filtros de marca/caja ya vienen aplicados.
 */
fun buildRankedSearch(
    query: String,
    tokens: List<String>,
    brand: String? = null,
    caja: String? = null,
): RankedQuery? {
    val clean = tokens.map { it.trim() }.filter { it.isNotBlank() }
    if (clean.isEmpty()) return null
    val q = query.trim()
    val q0 = q.trimStart('0')
    val qc = compactNorm(query)
    val (where, whereArgs) = whereClause(clean, brand, caja)
    val sql = StringBuilder("SELECT p.* FROM ean_products p")
    sql.append(where)
    val args = whereArgs.toMutableList()

    // Golpes de marca por token para el tier 4 (suma 0..N).
    val brandHits = clean.joinToString(" + ") {
        "(CASE WHEN p.marca_norm LIKE ? OR p.marca_norm_nospace LIKE ? THEN 1 ELSE 0 END)"
    }
    clean.forEach {
        val like = "%$it%"
        args.add(like)
        args.add(like)
    }

    sql.append(" ORDER BY (")
    // Tier 1: EAN exacto (crudo o sin ceros).
    sql.append("CASE WHEN p.eanPrincipal = ? OR (? <> '' AND (ltrim(p.eanPrincipal,'0') = ? OR ltrim(p.codigoBarra,'0') = ?)) THEN 300000 ELSE 0 END")
    args.add(q)
    args.add(q0)
    args.add(q0)
    args.add(q0)
    // Tier 1b: EAN prefijo.
    sql.append(" + CASE WHEN ltrim(p.eanPrincipal,'0') LIKE ? OR ltrim(p.codigoBarra,'0') LIKE ? THEN 20000 ELSE 0 END")
    args.add("$q0%")
    args.add("$q0%")
    // Tier 1c: EAN contiene (o viceversa).
    sql.append(" + CASE WHEN ltrim(p.eanPrincipal,'0') LIKE ? OR ? LIKE '%' || ltrim(p.eanPrincipal,'0') || '%' OR ltrim(p.codigoBarra,'0') LIKE ? THEN 1000 ELSE 0 END")
    args.add("%$q0%")
    args.add(q0)
    args.add("%$q0%")
    // Tier 2: SKU exacto.
    sql.append(" + CASE WHEN p.codCencosud = ? OR p.codProveedor = ? THEN 200 ELSE 0 END")
    args.add(q)
    args.add(q)
    // Tier 3: marca exacta.
    sql.append(" + CASE WHEN p.marca_norm = ? OR p.marca_norm_nospace = ? THEN 20 ELSE 0 END")
    args.add(qc)
    args.add(qc)
    // Tier 4: golpes de marca (todos=2, algunos=1, ninguno=0).
    sql.append(" + CASE WHEN ($brandHits) = ${clean.size} THEN 2 WHEN ($brandHits) > 0 THEN 1 ELSE 0 END")
    sql.append(") DESC, p.marca ASC, p.descripcionProducto ASC, p.id ASC")

    return RankedQuery(sql.toString(), args)
}

/**
 * Catálogo en orden de navegación (marca, descripción) con los mismos filtros.
 * Orden total y estable: Room pagina con LIMIT/OFFSET y el desempate por id
 * evita filas repetidas u omitidas entre páginas. "Sin marca" va al final.
 */
fun buildBrowseSearch(brand: String? = null, caja: String? = null): RankedQuery {
    val (where, args) = whereClause(emptyList(), brand, caja)
    return RankedQuery(
        "SELECT p.* FROM ean_products p$where" +
            " ORDER BY (p.marca = '') ASC, p.marca ASC, p.descripcionProducto ASC, p.id ASC",
        args,
    )
}

/**
 * Facetas de marca para los chips: cuentan el resultado actual **sin** aplicar
 * el filtro de marca (así se puede cambiar de marca), pero sí el de caja.
 */
fun buildBrandFacets(tokens: List<String>, caja: String? = null): RankedQuery {
    val clean = tokens.map { it.trim() }.filter { it.isNotBlank() }
    val (where, args) = whereClause(clean, brand = null, caja = caja)
    return RankedQuery(
        "SELECT p.marca AS marca, COUNT(*) AS count FROM ean_products p$where" +
            " GROUP BY p.marca ORDER BY count DESC, p.marca ASC",
        args,
    )
}

/**
 * Facetas de caja (conversión): mismo criterio que [buildBrandFacets] para el
 * filtro de marca. Las filas sin conversión no generan chip.
 */
fun buildCajaFacets(tokens: List<String>, brand: String? = null): RankedQuery {
    val clean = tokens.map { it.trim() }.filter { it.isNotBlank() }
    val (where, args) = whereClause(clean, brand = brand, caja = null, onlyWithCaja = true)
    return RankedQuery(
        "SELECT p.conversion AS conversion, COUNT(*) AS count FROM ean_products p$where" +
            " GROUP BY p.conversion ORDER BY CAST(p.conversion AS INTEGER) ASC",
        args,
    )
}

/** Total exacto de resultados (no solo los ya paginados): mismo WHERE. */
fun buildCountSearch(
    tokens: List<String>,
    brand: String? = null,
    caja: String? = null,
): RankedQuery {
    val clean = tokens.map { it.trim() }.filter { it.isNotBlank() }
    val (where, args) = whereClause(clean, brand, caja)
    return RankedQuery("SELECT COUNT(*) AS total FROM ean_products p$where", args)
}
