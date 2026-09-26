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

    @Test
    fun `default pendingUpdate is null`() = runTest {
        assertNull(repository.getPendingUpdate())
    }

    @Test
    fun `set and get pendingUpdate`() = runTest {
        repository.setPendingUpdate(PendingUpdate("12.02", 12002, "http://x.apk"))
        assertEquals(
            PendingUpdate("12.02", 12002, "http://x.apk"),
            repository.getPendingUpdate()
        )
    }

    @Test
    fun `clearPendingUpdate removes it`() = runTest {
        repository.setPendingUpdate(PendingUpdate("12.02", 12002, "http://x.apk"))
        repository.clearPendingUpdate()
        assertNull(repository.getPendingUpdate())
    }

    @Test
    fun `lastNotifiedUpdateCode roundtrip`() = runTest {
        assertEquals(0, repository.getLastNotifiedUpdateCode())
        repository.setLastNotifiedUpdateCode(12002)
        assertEquals(12002, repository.getLastNotifiedUpdateCode())
    }

    @Test
    fun `lastNotifiedUpdateTag roundtrip`() = runTest {
        assertEquals("", repository.getLastNotifiedUpdateTag())
        repository.setLastNotifiedUpdateTag("v12.20.1")
        assertEquals("v12.20.1", repository.getLastNotifiedUpdateTag())
    }

    @Test
    fun `pendingUpdate conserva versionTag`() = runTest {
        repository.setPendingUpdate(PendingUpdate("12.20.1", 12020, "http://x.apk", "v12.20.1"))
        assertEquals(
            PendingUpdate("12.20.1", 12020, "http://x.apk", "v12.20.1"),
            repository.getPendingUpdate()
        )
    }

    @Test
    fun `updateNotifAsked roundtrip`() = runTest {
        assertEquals(false, repository.wasUpdateNotifAsked())
        repository.setUpdateNotifAsked()
        assertEquals(true, repository.wasUpdateNotifAsked())
    }

    @Test
    fun `lastSyncCheck defaults to 0 and roundtrips`() = runTest {
        assertEquals(0L, repository.getLastSyncCheck())
        repository.setLastSyncCheck(987654321L)
        assertEquals(987654321L, repository.getLastSyncCheck())
    }

    @Test
    fun `lastPromoRefresh defaults to 0 and roundtrips`() = runTest {
        assertEquals(0L, repository.getLastPromoRefresh())
        repository.setLastPromoRefresh(555L)
        assertEquals(555L, repository.getLastPromoRefresh())
    }
}
