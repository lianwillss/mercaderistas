package com.rutamercaderistas.services

import android.content.Context
import com.rutamercaderistas.data.local.PromotionDao
import com.rutamercaderistas.data.preferences.PreferencesRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.io.path.createTempDirectory

class PromotionRepositoryTest {

    private lateinit var repository: PromotionRepository
    private lateinit var prefs: PreferencesRepository

    @Before
    fun setUp() {
        val tempDir = createTempDirectory().toFile()
        val ctx = mockk<Context>(relaxed = true) {
            every { applicationContext } returns this
            every { filesDir } returns tempDir
        }
        prefs = PreferencesRepository(ctx)
        repository = PromotionRepository(
            promotionDao = mockk(relaxed = true),
            preferencesRepository = prefs,
            context = ctx,
        )
    }

    @Test
    fun `shouldRefreshPromos is true without previous refresh`() {
        assertTrue(repository.shouldRefreshPromos(lastRefresh = 0L, now = 1_000_000L))
    }

    @Test
    fun `shouldRefreshPromos is false within interval`() {
        val now = 1_000_000L
        assertFalse(
            repository.shouldRefreshPromos(
                lastRefresh = now - 60_000L,
                now = now,
            )
        )
    }

    @Test
    fun `shouldRefreshPromos is true after interval`() {
        val now = 100_000_000L
        assertTrue(repository.shouldRefreshPromos(lastRefresh = 1_000L, now = now))
    }

    @Test
    fun `refreshIfStale skips download when recent`() = runTest {
        prefs.setLastPromoRefresh(System.currentTimeMillis())
        // No debe tocar red ni DAO: con refresh reciente devuelve true directo.
        assertTrue(repository.refreshIfStale())
    }
}
