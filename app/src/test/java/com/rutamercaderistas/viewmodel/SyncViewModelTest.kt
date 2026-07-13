package com.rutamercaderistas.viewmodel

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.rutamercaderistas.models.BrandReference
import com.rutamercaderistas.services.PromotionRepository
import com.rutamercaderistas.services.RuteroManager
import com.rutamercaderistas.services.RuteroRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SyncViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var application: Application
    private lateinit var ruteroManager: RuteroManager
    private lateinit var repository: RuteroRepository
    private lateinit var promotionRepository: PromotionRepository
    private lateinit var brandReference: BrandReference
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var clipboardManager: ClipboardManager
    private lateinit var viewModel: SyncViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        connectivityManager = mockk(relaxed = true) {
            every { activeNetwork } returns null
            every { getNetworkCapabilities(null) } returns null
        }
        clipboardManager = mockk(relaxed = true)

        application = mockk<Application>(relaxed = true) {
            every { getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
            every { getSystemService(Context.CLIPBOARD_SERVICE) } returns clipboardManager
            every { packageName } returns "com.rutamercaderistas.test"
        }

        ruteroManager = mockk(relaxed = true)
        repository = mockk(relaxed = true)
        promotionRepository = mockk(relaxed = true) {
            coEvery { refresh() } returns true
        }
        brandReference = mockk(relaxed = true)

        viewModel = SyncViewModel(
            application = application,
            ruteroManager = ruteroManager,
            repository = repository,
            promotionRepository = promotionRepository,
            brandReference = brandReference,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Idle`() {
        val state = viewModel.state.value
        assertEquals(SyncState.Idle, state.state)
        assertFalse(state.isSyncing)
        assertNull(state.syncPhase)
    }

    @Test
    fun `clearSnackbar resets message`() {
        viewModel.clearSnackbar()
        assertNull(viewModel.state.value.snackbarMessage)
    }

    @Test
    fun `downloadPdf calls brandReference once`() {
        viewModel.downloadPdf()
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 1) { brandReference.descargarPdf(application, null, null) }
    }

    @Test
    fun `syncFromDrive sets state to Syncing`() {
        viewModel.syncFromDrive()
        assertEquals(SyncState.Syncing(), viewModel.state.value.state)
        assertTrue(viewModel.state.value.isSyncing)
    }
}
