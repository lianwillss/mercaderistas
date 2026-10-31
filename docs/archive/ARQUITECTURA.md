# Mercaderistas — Arquitectura del proyecto

App de rutas para mercaderistas. Descarga planilla Excel de Drive con los locales y marcas a visitar, organiza por día, muestra tarjetas con promociones, y permite ver catálogos PDF de marcas con búsqueda por OCR.

---

## Stack técnico

| Capa | Librería |
|---|---|
| UI | Jetpack Compose + Material 3 |
| DI | Hilt 2.60 |
| DB | Room 2.8.4 (KSP) |
| Background | WorkManager 2.9.1 |
| Excel | Apache POI 5.5.1 (SAX streaming) |
| Networking | OkHttp 5.4.0 |
| OCR | ML Kit Text Recognition (Latin) |
| Preferences | DataStore |
| Logging | Timber |

Min SDK 26 · Target SDK 34 · Compile SDK 37 · Kotlin 2.4.0 · AGP 9.2.1

---

## Estructura de paquetes

```
com.rutamercaderistas/
├── MainActivity.kt              # Entry point, hosts Compose UI
├── MercaderistasApp.kt          # Application class, Hilt, Timber
├── Constants.kt                 # URLs, Drive file IDs, timeouts
├── PdfViewerActivity.kt         # Visor PDF vertical con zoom
├── models/                      # Data classes del dominio
├── data/
│   ├── local/                   # Room: entities + DAOs
│   ├── network/                 # downloadBytes() (HttpURLConnection)
│   ├── export/                  # RouteExporter (imagen PNG)
│   └── result/                  # SyncResult, UpdateResult
├── services/                    # Repositorios + lógica de negocio
├── viewmodel/                   # RouteViewModel, SyncViewModel, UpdateViewModel
├── ui/
│   ├── theme/                   # Type, Color, Theme (M3)
│   ├── screens/                 # MainScreen, AllLocalesScreen, ManualScreen
│   └── components/              # StoreCard, StatsCards, HeaderSection, etc.
├── worker/                      # SyncWorker, PromotionRefreshWorker
├── di/                          # AppModule (Hilt providers)
├── fragments/                   # ZoomablePageFragment (PDF)
├── adapters/                    # PagePagerAdapter
├── views/                       # ZoomableImageView
└── utils/ ─ util/               # MapsUtil, Extensions
```

---

## Flujo de datos principal

### 1. Sincronización (Sync → Room → UI)

```
[Drive Excel] ──downloadBytes()──> [RuteroManager.saveMasterExcel()]
                                      │
                                      v
                              [ExcelParser.parseAll()]
                              (SAX streaming, hoja "RUTA RUTERO")
                                      │
                                      v
                              [RouteEntryDao.deleteAllAndInsert()]
                                      │
                                      v
                              [RuteroRepository.entriesFlow] ──> [RouteViewModel] ──> [UI Compose]
```

- `SyncWorker` (WorkManager periódico) o pull-to-refresh gatillan la descarga
- `ExcelParser` usa XSSFReader de POI (streaming, sin cargar todo el Excel en memoria)
- `ColumnMapper` normaliza headers (NFD + uppercase + trim) y mapea alias por columna
- Cada fila del Excel se mapea a `EntradaRuta`, se persiste en Room, y se emite vía StateFlow

### 2. Selección de ruta

```
[RouteViewModel.selectRoute(nombre)]
       │
       v
[RuteroManager.loadRoute(nombre)]
  (Room cache, o Excel lazy parse si no existe en Room)
       │
       v
[RuteroRepository.setEntries(entries, nombre)]
       │
       v
[entriesFlow emite] ──> [RouteViewModel actualiza stats/días/locales/promos] ──> UI
```

### 3. Promociones

```
[Drive CSV] ──downloadBytes()──> [PromotionRepository.refresh()]
                                      │
                                      v
                              [Parseo CSV (;), filtro por fecha]
                                      │
                                      v
                              [PromotionDao.insertAll()]
                                      │
                                      v
                              [RouteViewModel.getAllPromotions()]
                                      │
                                      v
                              [groupBy { brand.lowercase() }]
                                      │
                                      v
                              [StoreCard: badge 🔥 + lista expandida]
```

- CSV descargado al iniciar app + cada 12h vía `PromotionRefreshWorker` + en cada sync manual
- Cruce por Marca + Cadena + Fecha actual
- Marcas sin promociones = mismo layout que antes (sin badge ni espacio extra)
- Badge usa emoji 🔥 + "X promos" con `animateContentSize(250ms)`

### 4. Catálogo PDF + OCR

```
[Tap marca en StoreCard]
       │
       v
[BrandReference.openPdfForBrand(marca)]
       │
       ├─ ¿PDF existe? ─NO──> [PdfDownloader.downloadPdf()] ──> guarda en filesDir
       │
       v
  ¿Marca en detectedPages (SP)?
       │
       ├─ SÍ ──> [PdfViewerActivity con page range]
       │
       └─ NO ──> [PdfBrandScanner.findBrand()]
                    (ML Kit OCR en páginas escaladas)
                    │
                    └─> guarda en SP, abre visor
```

---

## Modelos de dominio

### `EntradaRuta`
Una fila del Excel: `codigo`, `local`, `direccion`, `formato`, `region`, `comuna`, `cadena`, `cliente`, y 7 booleanos `dia_N` (lun-dom).

### `LocalDelDia`
Agrupación de `ClienteInfo` por local en un día específico: `local`, `direccion`, `cadena`, `clientes: List<ClienteInfo>`.

### `ClienteInfo`
`nombre`, `esPrioritaria` (⭐), `frecuencia` (suma de días asignados), `frecuenciaTexto`.

### `DiaSemana`
Enum Lun-Dom con `nombre`, `nombreCorto`, `orden`, `fecha()`, `esHoy`.

---

## Estados UI (ViewModels)

### `RouteUiState`
```
routes, selectedRoute, entries, activeDays, currentDayLocales, allLocales,
stats, recentRoutes, lastSyncRelative, promotionsByBrand, isSyncing,
isDataLoaded, snackbarMessage, needsInitialLoad
```

### `SyncUiState`
```
isOnline, isSyncing, syncPhase (IDLE → DOWNLOADING → PARSING → SAVING → DONE),
snackbarMessage
```

### `UpdateUiState`
```
showDialog, versionName, downloading, downloadProgress
```

---

## Reglas de negocio clave

| Regla | Implementación |
|---|---|
| Día actual por defecto | `DaySelector` → `DiaSemana.hoy()` (índice del día real) |
| Marcas prioritarias | `⭐` prefix en columna CLIENTE del Excel → `esPrioritaria = true` |
| Frecuencia de visita | Suma de booleanos `dia_N` para cada cliente → `frecuenciaTexto` ("3d/sem") |
| Match promociones | Brand `lowercase()` + cadena (case-insensitive, salta si cadena vacía) + fecha activa `today in [start, end]` |
| Cache de rutas recientes | DataStore, máx 5, ordenado por último uso |
| Detección de marca en PDF | SP cache + ML Kit OCR con fallback; prescan al descargar PDF |
| Actualización app | GitHub Releases → compara versionCode → descarga APK → instala vía FileProvider |
| Sync automático | WorkManager periódico, constraint: NETWORK_CONNECTED, retry 3 veces |

---

## Diseño visual (Specs)

Ver `SPEC.md` para spec completo iOS 26.

Resumen:
- **headlineLarge** (34sp SemiBold) — título principal
- **headlineMedium** (28sp SemiBold) — números stats
- **headlineSmall** (24sp Bold) — título tarjeta
- **titleLarge** (22sp SemiBold) — nombre local
- **titleMedium** (17sp Medium) — subtítulo
- **bodyLarge** (16sp Regular) — texto normal
- **bodyMedium** (14sp Regular gris) — info secundaria
- **labelMedium** (12sp Medium) — etiqueta pequeña

No ALL CAPS. Natural case. Fuente: Inter (bundled .ttf en res/font/). Sistema de colores en `Color.kt` con colores por cadena (Jumbo=verde, Lider=azul, etc.).

---

## Configuración de compilación

| Parámetro | Valor |
|---|---|
| compileSdk | 37 |
| minSdk | 26 |
| targetSdk | 34 |
| versionName | 11.10 |
| versionCode | 11010 |
| ResConfigs | "es" |
| ABI splits | arm64-v8a, armeabi-v7a, universal |
| ProGuard | R8 deshabilitado (POI reflection), keeps manuales para POI/XMLBeans/ML Kit/OkHttp |
| Firma | Untitled.jks (local) o CI env vars |

---

## Promociones (última feature agregada)

### CSV de entrada
Formato real: `;` (semicolons), columnas: `MARCA;CADENA;INICIO;FINAL;SKU;PRECIO - % PROMOCION`

### Pipeline
1. `PromotionRepository.refresh()` descarga CSV desde Drive
2. `parseSemicolonLine()` parsea respetando quotes
3. Filtra por fecha activa (`LocalDate.now()`)
4. Persiste en `promotions` table (Room)
5. `RouteViewModel` carga y agrupa por brand lowercased
6. `StoreCard` cruza por brand + cadena y muestra `PromotionBadge` + `PromotionList`

### URLs
- **Producción:** `https://drive.usercontent.google.com/download?id=FILE_ID&export=download`
- Usar URL directa (sin redirect 303) para evitar problemas con HttpURLConnection

### Edge cases conocidos
- `local.cadena` puede venir vacío antes del primer sync → filter salta el check de cadena
- Marcas en CSV pueden diferir del Excel (ej: "KOMBUCHACHA" vs "KOMBUCHA" en `MARCAS_PRIORITARIAS`)
- `⭐ ` prefix se limpia antes del match

---

## Troubleshooting común

| Síntoma | Causa posible |
|---|---|
| Promos no se ven | 303 redirect no manejado → usar URL directa; `cadena` vacía (re-sync needed); brand mismatch |
| Excel no se descarga | Google Drive offline; cache-busting param `&ts=` no funciona |
| OCR lento | Primera vez hace prescan full PDF; páginas cacheadas en bitmap LRU |
| POI crashes | ProGuard eliminó clases reflection → ver keeps en proguard-rules.pro |
| WorkManager no corre | Hilt worker factory mal configurado; `@HiltWorker` + `@AssistedInject` necesario |
