package com.rutamercaderistas.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

// ── Barra inferior que se aparta al leer ─────────────────────────────
// El patrón es el de las barras de navegación flotantes: el contenido manda
// y la barra se corre hacia abajo cuando el usuario avanza en la lista, y
// vuelve en cuanto retrocede. Los umbrales existen para que un temblor del
// dedo (unos pocos píxeles) no la haga aparecer y desaparecer, y para que no
// se vaya con el primer tirón corto: recién se aparta tras un scroll neto
// de [HIDE_AFTER] hacia adelante.

/** Scroll neto hacia adelante antes de apartar la barra. */
private val HIDE_AFTER = 40.dp

/** Scroll neto hacia atrás antes de devolverla: más corto, para que vuelva rápido. */
private val REVEAL_AFTER = 12.dp

/**
 * Estado de la barra inferior frente al scroll.
 *
 * No consume gestos: solo observa el delta ya consumido por las listas y
 * acumula el movimiento neto. El signo es el de nested scroll: negativo es
 * "el contenido avanza" (el dedo sube, se lee hacia abajo) y positivo es
 * "el contenido retrocede".
 *
 * La cuenta es neta a propósito: si alguien baja 30 px, sube 5 px y baja 20,
 * la barra se aparta porque el contenido quedó 45 px más abajo. Así el
 * umbral no se reinicia con cada micro-corrección del gesto.
 */
@Stable
class BottomBarAutoHideState internal constructor(
    private val hideAfterPx: Float,
    private val revealAfterPx: Float,
) {
    var hidden by mutableStateOf(false)
        private set

    private var forwardPx = 0f
    private var backwardPx = 0f

    /** Acumula el delta de scroll; no consume ni altera el gesto. */
    internal fun onScrollDelta(deltaY: Float) {
        if (deltaY < 0f) {
            if (hidden) {
                // Avanzar mientras está oculta cancela la intención de volver.
                backwardPx = (backwardPx + deltaY).coerceAtLeast(0f)
            } else {
                forwardPx += -deltaY
                if (forwardPx >= hideAfterPx) {
                    hidden = true
                    forwardPx = 0f
                    backwardPx = 0f
                }
            }
        } else if (deltaY > 0f) {
            if (hidden) {
                backwardPx += deltaY
                if (backwardPx >= revealAfterPx) {
                    hidden = false
                    forwardPx = 0f
                    backwardPx = 0f
                }
            } else {
                forwardPx = (forwardPx - deltaY).coerceAtLeast(0f)
            }
        }
    }

    /** Devuelve la barra ya (cambio de pantalla, rotación, volver del riel). */
    fun show() {
        hidden = false
        forwardPx = 0f
        backwardPx = 0f
    }

    /**
     * Se engancha al contenido, no a una lista concreta: cualquier scroll
     * vertical que llegue al scaffold (rutero, promos, stock, ajustes) mueve
     * la misma barra sin que cada pantalla tenga que saber que existe.
     */
    val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            onScrollDelta(consumed.y)
            return Offset.Zero
        }
    }
}

@Composable
internal fun rememberBottomBarAutoHideState(): BottomBarAutoHideState {
    val density = LocalDensity.current
    return remember(density) {
        BottomBarAutoHideState(
            hideAfterPx = with(density) { HIDE_AFTER.toPx() },
            revealAfterPx = with(density) { REVEAL_AFTER.toPx() },
        )
    }
}
