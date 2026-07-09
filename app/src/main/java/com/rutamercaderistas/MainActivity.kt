package com.rutamercaderistas

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import com.rutamercaderistas.models.BrandReference
import com.rutamercaderistas.models.DiaSemana
import com.rutamercaderistas.models.PdfBrandScanner
import com.rutamercaderistas.services.ApkDownloader
import com.rutamercaderistas.services.ExcelParser
import com.rutamercaderistas.services.RecentRoutesStore
import com.rutamercaderistas.services.RuteroManager
import com.rutamercaderistas.services.RuteroRepository
import com.rutamercaderistas.services.UpdateChecker
import com.rutamercaderistas.BuildConfig
import com.rutamercaderistas.ui.screens.MainScreen
import com.rutamercaderistas.ui.theme.MercaderistasTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {

    private lateinit var ruteroManager: RuteroManager
    private lateinit var recentRoutesStore: RecentRoutesStore

    private val DRIVE_FILE_ID = "18PejQq99XzNyTDf0fFku0hHwMs2sG1X5"
    private val DRIVE_EXPORT_URL = "https://docs.google.com/spreadsheets/d/$DRIVE_FILE_ID/export?format=xlsx"

    private val PREFS_NAME = "mercaderistas_prefs"
    private val KEY_RUTERO = "selected_rutero"
    private val KEY_UPDATE_SUPPRESSED_UNTIL = "update_suppressed_until"

    private var currentRutero: String? = null
    private var syncJob: kotlinx.coroutines.Job? = null

    private val selectorArchivo = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { cargarExcelLocal(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        ruteroManager = RuteroManager(this)
        BrandReference.init(this)
        recentRoutesStore = RecentRoutesStore(this)

        setContent {
            val snackbarHostState = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()
            registerSnackbarCallback { mensaje ->
                scope.launch { snackbarHostState.showSnackbar(mensaje) }
            }

            var showUpdateDialog by remember { mutableStateOf(false) }
            var updateVersionName by remember { mutableStateOf("") }
            var updateVersionCode by remember { mutableIntStateOf(0) }
            var updateApkUrl by remember { mutableStateOf("") }
            var downloading by remember { mutableStateOf(false) }
            var downloadProgress by remember { mutableIntStateOf(0) }

            LaunchedEffect(Unit) {
                mostrarSnackbar("Verificando actualizaciones\u2026")
                val suprimidoHasta = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .getLong(KEY_UPDATE_SUPPRESSED_UNTIL, 0L)
                if (System.currentTimeMillis() < suprimidoHasta) {
                    mostrarSnackbar("Verificaci\u00F3n suprimida hasta ma\u00F1ana")
                    return@LaunchedEffect
                }
                try {
                    val info = UpdateChecker.check(BuildConfig.VERSION_CODE)
                    if (info.available) {
                        updateVersionName = info.versionName
                        updateVersionCode = info.versionCode
                        updateApkUrl = info.apkUrl
                        showUpdateDialog = true
                        mostrarSnackbar("Versi\u00F3n ${info.versionName} disponible!")
                    } else {
                        mostrarSnackbar("Sin actualizaciones (v${BuildConfig.VERSION_CODE})")
                    }
                } catch (e: Exception) {
                    mostrarSnackbar("Error: ${e.message}")
                }
            }

            MercaderistasTheme {
                Scaffold(
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
                ) {
                    MainScreen(
                        ruteroManager = ruteroManager,
                        recentRoutesStore = recentRoutesStore,
                        context = this@MainActivity,
                        onSelectRoute = { seleccionarRutero(it) },
                        onRefresh = { cargarDesdeDrive(DRIVE_EXPORT_URL) },
                        onShowSnackbar = { },
                        onShareRoute = { text -> compartirTexto(text) },
                        modifier = Modifier
                    )
                }

                if (showUpdateDialog) {
                    AlertDialog(
                        onDismissRequest = {
                            if (!downloading) {
                                showUpdateDialog = false
                                val manana = System.currentTimeMillis() + 86400000L
                                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                                    .edit().putLong(KEY_UPDATE_SUPPRESSED_UNTIL, manana).apply()
                            }
                        },
                        title = {
                            Text(
                                text = "Actualización disponible",
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                        },
                        text = {
                            if (downloading) {
                                Column {
                                    Text(
                                        text = "Descargando versión $updateVersionName…",
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(
                                        modifier = Modifier.height(12.dp)
                                    )
                                    LinearProgressIndicator(
                                        progress = { downloadProgress / 100f },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp),
                                    )
                                    Spacer(
                                        modifier = Modifier.height(6.dp)
                                    )
                                    Text(
                                        text = "$downloadProgress%",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                Text(
                                    text = "Versión $updateVersionName disponible. ¿Quieres actualizar ahora?",
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        confirmButton = {
                            if (!downloading) {
                                Button(
                                    onClick = {
                                        downloading = true
                                        downloadProgress = 0
                                        lifecycleScope.launch {
                                            val ok = ApkDownloader.downloadAndInstall(
                                                this@MainActivity,
                                                updateApkUrl
                                            ) { pct -> downloadProgress = pct }
                                            if (ok) {
                                                showUpdateDialog = false
                                                downloading = false
                                            } else {
                                                downloading = false
                                                mostrarSnackbar("Error al instalar la actualización. Ve a Ajustes > Apps > Mercaderistas > Instalar apps desconocidas y actívalo.")
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Text("Actualizar", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        },
                        dismissButton = {
                            if (!downloading) {
                                TextButton(onClick = {
                                    showUpdateDialog = false
                                    val manana = System.currentTimeMillis() + 86400000L
                                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                                        .edit().putLong(KEY_UPDATE_SUPPRESSED_UNTIL, manana).apply()
                                }) {
                                    Text("Más tarde")
                                }
                            }
                        }
                    )
                }
            }
        }

        registrarMonitorRed()
        intentarCargarInicial()
    }

    private fun intentarCargarInicial() {
        lifecycleScope.launch {
            val index = withContext(Dispatchers.IO) { ruteroManager.loadIndex() }
            if (index.isNotEmpty()) {
                descargarPdfEnSegundoPlano()
                sincronizarEnSegundoPlano()
            } else {
                cargarDesdeDrive(DRIVE_EXPORT_URL)
            }
        }
    }

    private fun sincronizarEnSegundoPlano() {
        syncJob?.cancel()
        syncJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                val ts = System.currentTimeMillis()
                val cacheBustedUrl = "$DRIVE_EXPORT_URL&ts=$ts"
                val bytes = descargarConReintentos(cacheBustedUrl) ?: return@launch
                val changed = ruteroManager.saveMasterExcel(bytes)
                if (changed) {
                    withContext(Dispatchers.Main) {
                        val prevRutero = currentRutero
                        ruteroManager.invalidateAllCaches()
                        RuteroRepository.clear()
                        currentRutero = null
                        val indexOk = ruteroManager.createIndex()
                        if (indexOk) {
                            guardarSyncTime()
                            if (prevRutero != null) {
                                mostrarSnackbar("Datos actualizados. Recargando ruta…")
                                seleccionarRutero(prevRutero)
                            } else {
                                mostrarSnackbar("Datos actualizados")
                            }
                        }
                    }
                }
            } catch (_: Exception) { }
        }
    }

    private fun seleccionarRutero(rutero: String) {
        lifecycleScope.launch {
            try {
                val entries = ruteroManager.loadRoute(rutero, object : ExcelParser.ProgressListener {
                    override fun onProgress(message: String, percentage: Int) { }
                })
                if (entries.isNotEmpty()) {
                    currentRutero = rutero
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                        .edit().putString(KEY_RUTERO, rutero).apply()
                    RuteroRepository.setEntries(entries, rutero)
                    lifecycleScope.launch { recentRoutesStore.addRoute(rutero) }
                    guardarSyncTime()
                } else {
                    mostrarSnackbar("Ruta vacía o no encontrada")
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error seleccionando ruta", e)
                mostrarSnackbar("Error al cargar ruta: ${e.message}")
            }
        }
    }

    private fun cargarExcelLocal(uri: Uri) {
        lifecycleScope.launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { stream ->
                        ruteroManager.saveMasterExcel(stream.readBytes())
                    } ?: false
                }
                if (success) {
                    invalidarTodo()
                    ruteroManager.createIndex()
                    mostrarSnackbar("Archivo cargado correctamente")
                } else {
                    mostrarSnackbar("No se pudo guardar el archivo")
                }
            } catch (e: Exception) {
                mostrarSnackbar("Error: ${e.message}")
            }
        }
    }

    private fun cargarDesdeDrive(url: String, forzarPdf: Boolean = false) {
        syncJob?.cancel()
        syncJob = lifecycleScope.launch {
            try {
                val ts = System.currentTimeMillis()
                val cacheBustedUrl = if (url.contains("?")) "$url&ts=$ts" else "$url?ts=$ts"
                val bytes = withContext(Dispatchers.IO) { descargarConReintentos(cacheBustedUrl) }
                if (bytes != null) {
                    val success = withContext(Dispatchers.IO) { ruteroManager.saveMasterExcel(bytes) }
                    if (success) {
                        val prevRutero = currentRutero
                        guardarUrlPersistida(url)
                        invalidarTodo()
                        val indexOk = ruteroManager.createIndex()
                        if (indexOk) {
                            if (prevRutero != null) {
                                seleccionarRutero(prevRutero)
                            } else {
                                if (forzarPdf) descargarYMostrarProgresoPdf()
                                else descargarPdfEnSegundoPlano()
                            }
                        } else {
                            mostrarSnackbar("No se pudo leer el Excel. Verifica que tenga una hoja 'RUTA RUTERO' con columna 'RUTERO'.")
                        }
                    }
                } else {
                    mostrarSnackbar("Error de descarga desde Drive")
                }
            } catch (e: Exception) {
                mostrarSnackbar("Error: ${e.message}")
            }
        }
    }

    private fun descargarPdfEnSegundoPlano(forzar: Boolean = false) {
        val pdfFile = java.io.File(filesDir, BrandReference.PDF_FILE_NAME)
        if (!forzar && pdfFile.exists() && pdfFile.length() > 0) return
        BrandReference.descargarPdf(this)
    }

    private fun descargarYMostrarProgresoPdf() {
        BrandReference.descargarPdf(
            this,
            onProgress = { },
            callback = { success ->
                if (success) mostrarSnackbar("Manual actualizado")
            }
        )
    }

    private fun invalidarTodo() {
        ruteroManager.invalidateAllCaches()
        RuteroRepository.clear()
        currentRutero = null
    }

    private fun guardarSyncTime() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit().putLong(KEY_LAST_SYNC, System.currentTimeMillis()).apply()
    }

    // ── Error / Snackbar ─────────────────────────────────

    private var snackbarCallback: ((String) -> Unit)? = null

    fun registerSnackbarCallback(callback: (String) -> Unit) {
        snackbarCallback = callback
    }

    private fun mostrarSnackbar(mensaje: String) {
        snackbarCallback?.invoke(mensaje) ?: run {
            Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
        }
    }

    // ── Menu actions ─────────────────────────────────────

    private val KEY_LAST_SYNC = "last_sync_time"
    private val PREFS_DRIVE_URL = "drive_url"

    fun abrirSelectorArchivo() {
        selectorArchivo.launch("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    }

    fun pegarLink() {
        val clipboard = getSystemService(ClipboardManager::class.java)
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString() ?: ""
            if (text.contains("drive.google.com") || text.contains("docs.google.com")) {
                cargarDesdeDrive(convertirUrlDrive(text))
            } else {
                mostrarSnackbar("Copia primero el link de Google Drive")
            }
        }
    }

    fun compartirRuta() {
        val rutero = currentRutero
        if (rutero != null) {
            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_TEXT, "Mi ruta de hoy: $rutero")
            }
            startActivity(android.content.Intent.createChooser(shareIntent, "Compartir ruta"))
        }
    }

    // ── Network Monitor ──────────────────────────────────

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onStop() {
        super.onStop()
        syncJob?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        syncJob?.cancel()
        networkCallback?.let {
            (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
                .unregisterNetworkCallback(it)
        }
        PdfBrandScanner.release()
    }

    private fun registrarMonitorRed() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { }
            override fun onLost(network: Network) { }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { }
        }
        try {
            cm.registerNetworkCallback(NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build(), networkCallback!!)
        } catch (e: Exception) { Log.w("MainActivity", "No se pudo registrar monitor de red", e) }
    }

    // ── Download helpers ─────────────────────────────────

    private suspend fun descargarABytes(url: String): ByteArray? {
        var currentUrl = url
        var limit = 5
        var redirect = true
        while (redirect && limit > 0) {
            var conn: HttpURLConnection? = null
            try {
                conn = URL(currentUrl).openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 30000
                conn.readTimeout = 60000
                conn.setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate")
                conn.setRequestProperty("Pragma", "no-cache")
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                val status = conn.responseCode
                if (status == HttpURLConnection.HTTP_MOVED_PERM || status == HttpURLConnection.HTTP_MOVED_TEMP || status == 307 || status == 308) {
                    currentUrl = conn.getHeaderField("Location") ?: return null
                    limit--
                    continue
                }
                return conn.inputStream.use { it.readBytes() }
            } catch (e: Exception) {
                Log.w("Download", "Error en intento ${5 - limit + 1}", e)
                return null
            } finally { conn?.disconnect() }
        }
        return null
    }

    private suspend fun descargarConReintentos(url: String, reintentos: Int = 3): ByteArray? {
        var currentAttempt = 0
        while (currentAttempt < reintentos) {
            try {
                return descargarABytes(url)
            } catch (e: Exception) {
                currentAttempt++
                if (currentAttempt >= reintentos) {
                    Log.e("Download", "Agotados $reintentos intentos", e)
                    return null
                }
                val backoff = 1000L * (1 shl currentAttempt)
                delay(backoff)
            }
        }
        return null
    }

    private fun convertirUrlDrive(url: String): String {
        return when {
            url.contains("/file/d/") -> {
                val id = url.substringAfter("/file/d/").substringBefore("/")
                "https://drive.google.com/uc?export=download&id=$id"
            }
            url.contains("/spreadsheets/d/") -> {
                val id = url.substringAfter("/spreadsheets/d/").substringBefore("/")
                "https://docs.google.com/spreadsheets/d/$id/export?format=xlsx"
            }
            else -> url
        }
    }

    private fun guardarUrlPersistida(url: String) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putString(PREFS_DRIVE_URL, url).apply()
    }

    private fun compartirTexto(texto: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, texto)
        }
        startActivity(Intent.createChooser(intent, "Compartir ruta"))
    }
}
