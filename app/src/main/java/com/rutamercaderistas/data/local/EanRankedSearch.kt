package com.rutamercaderistas.data.local

import androidx.sqlite.db.SimpleSQLiteQuery
import com.rutamercaderistas.services.compactNorm

/**
 * Búsqueda EAN con ranking en SQL (reemplaza al ranking in-memory de
 * `MultiTokenPagingSource`): un solo query con score CASE, AND de tokens y
 * paginación real de Room (sin tope LIMIT 300 ni re-ordenar por página).
 *
 * Tiers y pesos (el score preserva el orden lexicográfico anterior: cada
 * peso supera la suma máxima de todos los tiers menores):
 * - EAN exacto (ignorando ceros a la izquierda): 300000
 * - EAN prefijo (escáner parcial): 20000
 * - EAN contiene la query (o viceversa): 1000
 * - SKU exacto (Cencosud o proveedor): 200
 * - Marca exacta (normalizada): 20
 * - Golpes de tokens en marca (todos/algunos/ninguno): 2/1/0
 * Desempate: marca ASC, descripción ASC (igual que `thenBy` en Kotlin para
 * texto BMP). Los tokens ya vienen de compactNorm ([a-z0-9]), así que son
 * seguros en LIKE (sin % ni _).
 *
 * Cobertura del WHERE = `containsToken` salvo el fallback Levenshtein<=2
 * (imposible en SQLite; en la práctica esos candidatos ya entraban por LIKE
 * o FTS de otro campo). Pura y testeable en JVM.
 */
data class RankedQuery(val sql: String, val args: List<Any?>) {
    fun toQuery(): SimpleSQLiteQuery = SimpleSQLiteQuery(sql, args.toTypedArray())
}

fun buildRankedSearch(query: String, tokens: List<String>): RankedQuery? {
    val clean = tokens.map { it.trim() }.filter { it.isNotBlank() }
    if (clean.isEmpty()) return null
    val q = query.trim()
    val q0 = q.trimStart('0')
    val qc = compactNorm(query)
    val sql = StringBuilder("SELECT p.* FROM ean_products p WHERE ")
    val args = mutableListOf<Any?>()

    // AND de grupos OR por token.
    clean.forEachIndexed { i, t ->
        if (i > 0) sql.append(" AND ")
        val t0 = t.trimStart('0')
        sql.append(
            "(p.eanPrincipal LIKE ? OR p.codCencosud LIKE ?" +
                " OR p.codProveedor LIKE ? OR p.codigoBarra LIKE ?" +
                " OR p.descripcionProducto LIKE ? OR p.marca LIKE ?" +
                " OR p.descripcion_norm LIKE ? OR p.marca_norm LIKE ?" +
                " OR p.descripcion_norm_nospace LIKE ? OR p.marca_norm_nospace LIKE ?" +
                " OR ltrim(p.eanPrincipal,'0') LIKE ? OR ltrim(p.codCencosud,'0') LIKE ?" +
                " OR ltrim(p.codProveedor,'0') LIKE ? OR ltrim(p.codigoBarra,'0') LIKE ?)"
        )
        val like = "%$t%"
        repeat(10) { args.add(like) }
        // t0 vacío → '%%' (matchea todo: igual que containsToken con tt="").
        val like0 = "%$t0%"
        repeat(4) { args.add(like0) }
    }

    // Golpes de marca por token para el tier 4 (suma 0..N).
    val brandHits = clean.joinToString(" + ") {
        "(CASE WHEN p.marca_norm LIKE ? OR p.marca_norm_nospace LIKE ? THEN 1 ELSE 0 END)"
    }
    clean.forEach { t ->
        val like = "%$t%"
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
    sql.append(") DESC, p.marca ASC, p.descripcionProducto ASC")

    return RankedQuery(sql.toString(), args)
}
