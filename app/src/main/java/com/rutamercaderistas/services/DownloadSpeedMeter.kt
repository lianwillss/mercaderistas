package com.rutamercaderistas.services

/**
 * Estima la velocidad de descarga en ventanas cortas y la suaviza para que
 * el número de MB/s no baile con cada callback de progreso.
 *
 * - La primera muestra solo fija la línea base.
 * - Cada [windowMillis] calcula bytes/segundo del tramo y lo mezcla con la
 *   estimación anterior (EMA simple).
 * - Si el progreso retrocede (reinicio de la descarga), no genera velocidad
 *   negativa: solo reinicia la ventana.
 */
class DownloadSpeedMeter(
    private val windowMillis: Long = 750L,
) {
    private var hasSample = false
    private var windowStart = 0L
    private var windowBytes = 0L
    private var smoothedBytesPerSecond = 0L

    fun update(bytes: Long, nowMillis: Long): Long {
        if (!hasSample) {
            hasSample = true
            windowStart = nowMillis
            windowBytes = bytes
            return 0L
        }

        val elapsed = nowMillis - windowStart
        if (elapsed < windowMillis || elapsed <= 0L) return smoothedBytesPerSecond

        val delta = bytes - windowBytes
        if (delta >= 0L) {
            val instant = delta * 1000L / elapsed
            smoothedBytesPerSecond = if (smoothedBytesPerSecond == 0L) {
                instant
            } else {
                (smoothedBytesPerSecond * 3 + instant) / 4
            }
        }
        windowStart = nowMillis
        windowBytes = bytes
        return smoothedBytesPerSecond
    }
}
