package com.rutamercaderistas

object Constants {
    // Prefs
    const val PREFS_NAME = "mercaderistas_prefs"
    const val KEY_RUTERO = "selected_rutero"
    const val KEY_LAST_SYNC = "last_sync_time"
    const val KEY_UPDATE_SUPPRESSED_UNTIL = "update_suppressed_until"
    const val KEY_DRIVE_URL = "drive_url"
    const val KEY_TRANSPORT_MODE = "transport_mode"

    // Drive
    const val DRIVE_FILE_ID = "18PejQq99XzNyTDf0fFku0hHwMs2sG1X5"
    const val DRIVE_EXPORT_URL = "https://docs.google.com/spreadsheets/d/$DRIVE_FILE_ID/export?format=xlsx"
    const val DRIVE_EXPORT_FORMAT = "format=xlsx"

    // PDF
    const val PDF_FILE_ID = "1AeWTubwXIRyQYng6dK0xa53L1vMJAudj"
    const val PDF_FILE_NAME = "manual_marcas.pdf"

    // Network
    const val CONNECT_TIMEOUT_MS = 30_000
    const val READ_TIMEOUT_MS = 60_000
    const val DOWNLOAD_TIMEOUT_MS = 180_000
    const val MAX_RETRIES = 3
    const val RETRY_BACKOFF_MS = 1_000L

    // UI
    const val UPDATE_SUPPRESS_DAYS_MS = 86_400_000L // 24h
    const val MIN_APK_SIZE_BYTES = 1_024L
    const val APK_DOWNLOAD_BUFFER = 65_536 // 64KB
    const val PDF_CACHE_SIZE_MB = 10
    const val RECENT_ROUTES_MAX = 10

    // GitHub
    const val GITHUB_REPO = "lianwillss/mercaderistas"
    const val UPDATE_API_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
    const val APK_ASSET_NAME = "app-universal-release.apk"

    // Colors (red for selected route)
    val SELECTED_ROUTE_COLOR = androidx.compose.ui.graphics.Color(0xFFC62828)
    val ONLINE_DOT_COLOR = androidx.compose.ui.graphics.Color(0xFF34C759)
    val OFFLINE_DOT_COLOR = androidx.compose.ui.graphics.Color(0xFFFF3B30)
}