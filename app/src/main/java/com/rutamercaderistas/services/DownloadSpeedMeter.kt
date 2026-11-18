package com.rutamercaderistas.services

/**
 * Estima la velocidad de descarga en ventanas cortas y la suaviza para que
 * el número de MB/s no baile con cada callback de progreso.
 *
 * - La primera muestra solo fija la línea base.
 * - Cada [windowMillis] calcula bytes/segundo del tramo y lo mezcla con la
 *   estimación anterior (EMA simple con alpha = 0.3).
 * - Mantiene un historial circular de las últimas N muestras para calcular
 *   un promedio móvil más estable.
 * - Si el progreso retrocede (reinicio de la descarga), no genera velocidad
 *   negativa: solo reinicia la ventana.
 * - No expone ETA hasta tener al menos 3 muestras de velocidad válidas.
 */
class DownloadSpeedMeter(
    private val windowMillis: Long = 1000L,  // Ventana de 1 segundo para más estabilidad
    private val maxSamples: Int = 10,         // Historial de 10 muestras
) {
    private var hasSample = false
    private var windowStart = 0L
    private var windowBytes = 0L
    private var smoothedBytesPerSecond = 0L
    private val samples = mutableListOf<Long>()
    private var sampleCount = 0

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
            // EMA con alpha = 0.3 para suavizar más agresivamente
            smoothedBytesPerSecond = if (smoothedBytesPerSecond == 0L) {
                instant
            } else {
                (smoothedBytesPerSecond * 7 + instant * 3) / 10
            }

            // Agregar al historial circular para promedio móvil
            samples.add(smoothedBytesPerSecond)
            if (samples.size > maxSamples) {
                samples.removeAt(0)
            }
            sampleCount++
        }
        windowStart = nowMillis
        windowBytes = bytes
        return smoothedBytesPerSecond
    }

    /** Devuelve true si hay suficientes muestras para mostrar ETA confiable. */
    fun hasReliableEta(): Boolean = sampleCount >= 3 && samples.size >= 3

    /** Velocidad promedio móvil de las últimas muestras (más estable que EMA sola). */
    fun getAverageSpeed(): Long {
        if (samples.isEmpty()) return smoothedBytesPerSecond
        val sum = samples.sum()
        return sum / samples.size
    }

    fun reset() {
        hasSample = false
        windowStart = 0L
        windowBytes = 0L
        smoothedBytesPerSecond = 0L
        samples.clear()
        sampleCount = 0
    }
}
