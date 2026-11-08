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
    fun `rejectedUpdateTag roundtrip`() = runTest {
        assertEquals("", repository.getRejectedUpdateTag())
        repository.setRejectedUpdateTag("v12.51")
        assertEquals("v12.51", repository.getRejectedUpdateTag())
        repository.setRejectedUpdateTag("")
        assertEquals("", repository.getRejectedUpdateTag())
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
    fun `pendingUpdate conserva tamano y sha256`() = runTest {
        val sha = "a".repeat(64)
        repository.setPendingUpdate(
            PendingUpdate(
                versionName = "12.46",
                versionCode = 12046,
                apkUrl = "http://x.apk",
                versionTag = "v12.46",
                apkSize = 50_123_456L,
                apkSha256 = sha,
            ),
        )
        assertEquals(
            PendingUpdate("12.46", 12046, "http://x.apk", "v12.46", 50_123_456L, sha),
            repository.getPendingUpdate(),
        )
        // El DataStore es singleton por proceso y los tests comparten estado.
        repository.clearPendingUpdate()
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
