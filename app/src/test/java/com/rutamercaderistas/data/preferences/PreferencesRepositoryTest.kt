package com.rutamercaderistas.data.preferences

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import kotlin.io.path.createTempDirectory
import java.io.File

class PreferencesRepositoryTest {

    private lateinit var repository: PreferencesRepository

    @Before
    fun setUp() {
        val tempDir = createTempDirectory().toFile()
        val ctx = mockk<Context>(relaxed = true) {
            every { applicationContext } returns this
            every { filesDir } returns tempDir
        }
        repository = PreferencesRepository(ctx)
    }

    @Test
    fun `default selectedRoute is null`() = runTest {
        assertNull(repository.getSelectedRoute())
    }

    @Test
    fun `set and get selectedRoute`() = runTest {
        repository.setSelectedRoute("RUTA-1")
        assertEquals("RUTA-1", repository.getSelectedRoute())
    }

    @Test
    fun `set selectedRoute to null clears it`() = runTest {
        repository.setSelectedRoute("RUTA-1")
        repository.setSelectedRoute(null)
        assertNull(repository.getSelectedRoute())
    }

    @Test
    fun `default lastSyncTime is 0`() = runTest {
        assertEquals(0L, repository.getLastSyncTime())
    }

    @Test
    fun `set and get lastSyncTime`() = runTest {
        repository.setLastSyncTime(12345L)
        assertEquals(12345L, repository.getLastSyncTime())
    }

    @Test
    fun `set and get transportMode`() = runTest {
        repository.setTransportMode("driving")
        assertEquals("driving", repository.getTransportMode())
    }

    @Test
    fun `set transportMode to null clears it`() = runTest {
        repository.setTransportMode("driving")
        repository.setTransportMode(null)
        assertNull(repository.getTransportMode())
    }
}
