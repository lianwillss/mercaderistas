package com.rutamercaderistas.ui.components

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.google.zxing.integration.android.IntentIntegrator
import com.rutamercaderistas.R

/**
 * Lanzador del escáner de código de barras del módulo Cód. EAN (ZXing).
 *
 * Lo comparten Cód. EAN y Stock: el mismo escáner, el mismo aviso y el mismo
 * parseo de resultado. Devuelve la acción para dispararlo desde un botón.
 */
@Composable
fun rememberBarcodeScannerLauncher(
    onScanned: (String) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val aviso = stringResource(R.string.ean_scanning_cd)
    val onScannedActual by rememberUpdatedState(onScanned)

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val intentResult = IntentIntegrator.parseActivityResult(
            result.resultCode,
            result.data,
        )
        if (intentResult != null && !intentResult.contents.isNullOrBlank()) {
            onScannedActual(intentResult.contents)
        }
    }

    return {
        Toast.makeText(context, aviso, Toast.LENGTH_SHORT).show()
        val activity = context as? Activity
        if (activity != null) {
            val integrator = IntentIntegrator(activity)
                .setOrientationLocked(false)
                .setPrompt(aviso)
                .setBeepEnabled(true)
            launcher.launch(integrator.createScanIntent())
        }
    }
}
