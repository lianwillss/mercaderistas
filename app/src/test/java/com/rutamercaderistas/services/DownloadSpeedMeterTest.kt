package com.rutamercaderistas.services

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadSpeedMeterTest {

    @Test
    fun `la primera muestra no inventa velocidad`() {
        val meter = DownloadSpeedMeter()
        assertEquals(0L, meter.update(bytes = 0L, nowMillis = 1_000L))
    }

    @Test
    fun `no calcula antes de cerrar la ventana`() {
        val meter = DownloadSpeedMeter(windowMillis = 750L)
        meter.update(bytes = 0L, nowMillis = 1_000L)
        assertEquals(0L, meter.update(bytes = 500_000L, nowMillis = 1_400L))
    }

    @Test
    fun `calcula bytes por segundo al cerrar la ventana`() {
        val meter = DownloadSpeedMeter(windowMillis = 750L)
        meter.update(bytes = 0L, nowMillis = 1_000L)
        assertEquals(1_000_000L, meter.update(bytes = 1_000_000L, nowMillis = 2_000L))
    }

    @Test
    fun `suaviza la velocidad entre ventanas`() {
        val meter = DownloadSpeedMeter(windowMillis = 750L)
        meter.update(bytes = 0L, nowMillis = 1_000L)
        assertEquals(1_000_000L, meter.update(bytes = 1_000_000L, nowMillis = 2_000L))
        // 2 MB en el siguiente segundo: instantáneo 2 MB/s, EMA = 1,25 MB/s.
        assertEquals(1_250_000L, meter.update(bytes = 3_000_000L, nowMillis = 3_000L))
    }

    @Test
    fun `un retroceso no produce velocidad negativa`() {
        val meter = DownloadSpeedMeter(windowMillis = 750L)
        meter.update(bytes = 2_000_000L, nowMillis = 1_000L)
        assertEquals(0L, meter.update(bytes = 1_000_000L, nowMillis = 2_000L))
        // Tras el reinicio, la ventana nueva vuelve a medir con bytes positivos.
        assertEquals(500_000L, meter.update(bytes = 1_500_000L, nowMillis = 3_000L))
    }
}
