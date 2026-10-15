package com.rutamercaderistas.data.local

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rutamercaderistas.services.compactNorm
import com.rutamercaderistas.services.normalizeSearch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Ranking SQL (buildRankedSearch + EanProductDao.rankedSearch) contra una
 * base Room real en memoria: el SQL no se puede ejecutar en tests JVM, así
 * que este test instrumentado es el contrato de comportamiento (tiers,
 * AND de tokens, ceros a la izquierda, desempate). Requiere dispositivo.
 */
@RunWith(AndroidJUnit4::class)
class EanRankedSearchDeviceTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: EanProductDao

    private fun product(
        ean: String,
        sku: String,
        descripcion: String,
        marca: String,
    ) = EanProductEntity(
        eanPrincipal = ean,
        codCencosud = sku,
        descripcionProducto = descripcion,
        descripcionNorm = normalizeSearch(descripcion),
        descripcionNormNospace = compactNorm(descripcion),
        marca = marca,
        marcaNorm = normalizeSearch(marca),
        marcaNormNospace = compactNorm(marca),
    )

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.eanProductDao()
        runBlocking {
            dao.insertAll(
                listOf(
                    product("7804690890292", "2087431", "ACEITE OLIVA EXTR VIRG TRUFA BACIO 500ML", "KEYFOOD"),
                    product("7804690890339", "2089254", "ACETO BALSAMIC CREMOSO PESTO BACIO 500ML", "KEYFOOD"),
                    product("0658325654776", "2089654", "GOMITAS MIEL/PROPOLIO ABEJA DORADA 80G", "ABEJA DORADA"),
                    product("7800000000111", "1000001", "NAT ROMERO PISTACHO", "NAT NATURAL"),
                    product("7800000000222", "1000002", "JUGO MANZANA", "OTRA MARCA"),
                )
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun search(query: String): List<EanProductEntity> {
        val tokens = query.split(Regex("\\s+")).map { compactNorm(it) }.filter { it.isNotBlank() }
        val rq = buildRankedSearch(query, tokens) ?: error("query vacía")
        val result = runBlocking {
            dao.rankedSearch(rq.toQuery()).load(
                PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false)
            )
        }
        check(result is PagingSource.LoadResult.Page) { "se esperaba Page, fue $result" }
        return result.data
    }

    @Test
    fun exactEan_ranksFirst() {
        val rows = search("7804690890292")
        assertTrue(rows.isNotEmpty())
        assertEquals("7804690890292", rows.first().eanPrincipal)
    }

    @Test
    fun zeroTrimmedEan_matchesStoredWithLeadingZero() {
        // Guardado "0658325654776", buscado sin el cero.
        val rows = search("658325654776")
        assertTrue(rows.isNotEmpty())
        assertEquals("0658325654776", rows.first().eanPrincipal)
        assertEquals("ABEJA DORADA", rows.first().marca)
    }

    @Test
    fun skuExact_ranksFirst() {
        val rows = search("2089654")
        assertTrue(rows.isNotEmpty())
        assertEquals("2089654", rows.first().codCencosud)
    }

    @Test
    fun brandExact_returnsOnlyThatBrandOrderedByDescription() {
        val rows = search("keyfood")
        assertEquals(2, rows.size)
        assertTrue(rows.all { it.marca == "KEYFOOD" })
        assertEquals("ACEITE OLIVA EXTR VIRG TRUFA BACIO 500ML", rows[0].descripcionProducto)
        assertEquals("ACETO BALSAMIC CREMOSO PESTO BACIO 500ML", rows[1].descripcionProducto)
    }

    @Test
    fun multiToken_andSemantics() {
        val rows = search("bacio 500")
        assertEquals(2, rows.size)
        assertTrue(rows.all { it.marca == "KEYFOOD" })
    }

    @Test
    fun prefixScan_matches() {
        val rows = search("78046908903")
        assertTrue(rows.isNotEmpty())
        assertEquals("7804690890339", rows.first().eanPrincipal)
    }

    @Test
    fun noMatch_returnsEmpty() {
        assertTrue(search("zzzznomatch").isEmpty())
    }
}
