# Mercaderistas — Guía del repo

App Android de rutas para mercaderistas: Kotlin, Compose, Hilt, Room y WorkManager. El código manda sobre `ARQUITECTURA.md`/`SPEC.md` si difieren.

## Comandos

- Java no está en `PATH`; anteponer siempre:
  `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew <task>`
- Verificación estándar, en dos comandos para aislar carreras de tests:
  `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew --no-parallel :app:testDebugUnitTest`
  y luego `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleRelease`.
- Test enfocado: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest --tests "com.rutamercaderistas.viewmodel.RouteViewModelTest"`.
- Tests de dispositivo Compose: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:connectedDebugAndroidTest`; requieren un dispositivo/emulador conectado.
- Toolchain verificada: Gradle 9.6, AGP 9.2.1, Kotlin 2.4.0, Compose BOM 2025.06.00, Java 17.
- Este entorno no tiene `rg`; usar `grep`/Glob para búsquedas.

## Arquitectura

- Único módulo `:app`; la entrada es `MainActivity`, la aplicación Hilt es `MercaderistasApp`.
- Estado de pantalla: ViewModels + `StateFlow`; Compose recolecta con `collectAsStateWithLifecycle()`.
- Datos persistentes: Room (`AppDatabase` v6); preferencias: DataStore (`PreferencesRepository`).
- `AppDatabase` NO tiene `fallbackToDestructiveMigration`: toda entidad o cambio de esquema exige `Migration` explícita (ver `MIGRATION_4_5` como plantilla de rebuild con copia de datos).
- Sincronización en segundo plano: WorkManager + `SyncWorker`/`PromotionRefreshWorker`.
- La navegación principal usa `NavigationSuiteScaffold`; `AppWindowWidth` centraliza Compact `<600dp`, Medium `<840dp` y Expanded.

## Tests

- Tests locales son JUnit4 + MockK + `kotlinx-coroutines-test`, sin Robolectric; no asumir `R.string` real en `src/test` y mockear `Context.getString()` cuando corresponda.
- `RouteViewModel` calcula parte del estado en `Dispatchers.Default`; `advanceUntilIdle()` no espera esos hilos reales. Usar el helper `awaitOnMain { ... }` de `RouteViewModelTest` después de `selectRoute`, `loadInitialData` y `setCurrentDay`.
- La suite puede producir `UncaughtExceptionsBeforeTest` por carreras globales de `Dispatchers.Main`; ejecutar con `--no-parallel` y repetir un test aislado antes de atribuirlo al cambio.
- Las pruebas instrumentadas no se ejecutan sin dispositivo; agregar cobertura de scroll ahí, no en tests JVM.
- La búsqueda EAN usa Paging 3 (`pagingSourceAll()` + `MultiTokenPagingSource`, `Ready.pagingFlow: Flow<PagingData<...>>` con `cachedIn(viewModelScope)`). En tests JVM: mockear `android.util.Log` con `mockkStatic` (Paging loguea vía Log), usar `paging-testing` (`asSnapshot()`) y cancelar `vm.viewModelScope` al final para evitar `UncompletedCoroutinesError`. El `Pager` debe crear un `PagingSource` nuevo en cada llamada al factory.
- `UpdateViewModelTest` usa `mockkObject(UpdateChecker)` + `track(vm)` con cancel en `tearDown` y `awaitOnMain { ... }`: sin eso, coroutines filtradas envenenan el test siguiente con `UncaughtExceptionsBeforeTest`.

## Sync del rutero

- URLs fuente están en `Constants.kt`: `DRIVE_EXPORT_URL` para el XLSX del rutero y `PROMOTIONS_CSV_URL` para promociones.
- `RuteroManager` serializa sync con `withSyncLock`, escribe a temporal, valida estructura, reemplaza atómicamente y luego `createIndex()` actualiza Room dentro de una transacción.
- `SyncViewModel` aplica automáticamente un Excel válido; no existe confirmación Aplicar/Cancelar. El hash SHA-256 y la hora de último sync se guardan solo después de indexar correctamente. No hay gate por ETag/HEAD (Google no lo rota de forma confiable y causaba falsos "sin cambios"); `headForETag` se eliminó, no reintroducirlo.
- Auto-sync al abrir: `MainActivity` llama `autoSyncIfStale(ruta)` una vez con la ruta ya seleccionada (throttle `AUTO_SYNC_MIN_INTERVAL_MS = 15min`, solo con internet y con un sync previo; silencioso si no hay cambios). `SyncWorker` (1h) persiste el mismo hash, salta reindex si no hay cambios y falla rápido ante Excel inválido (retry solo en red).
- El arranque frío usa `refreshIfStale()` para promos (throttle 12h); el pull-to-refresh y el post-sync fuerzan `refresh()`. La "Última revisión del Excel" se muestra en Ajustes (`KEY_LAST_SYNC_CHECK`, se escribe en manual/auto/worker).
- La validación estructural rechaza archivo vacío/sin rutas/campos esenciales; no convertir validaciones de filas en banners bloqueantes sin una petición explícita.
- Al cambiar el sync, preservar la recarga de la ruta activa y el cálculo del diff (agregados, eliminados, movidos, direcciones y marcas).

## Catálogo EAN

- Todos los assets `app/src/main/assets/ean*.xlsx` se combinan automáticamente; si el nombre no es obvio, agregarlo a `EAN_FILE_BRANDS` en `EanExcelParser.kt`.
- Al agregar/cambiar un asset EAN, incrementar `EAN_DATA_VERSION` para forzar la reimportación en instalaciones existentes y agregar una prueba del archivo/mapeo.
- Si la columna Marca trae un nombre distinto al visible (ej: `B FRESH`/`B.TAN` → `BWILD`), agregar alias en `BRAND_ALIASES` (se aplica al importar; sin reimport no llega a instalados).
- `img_flejes_barcode.png` es generado (EAN-13 `6969696969692`, JUMBO fuera de las barras): cualquier texto/logo SOBRE las barras lo vuelve ilegible para el lector. No restaurar `er.png`.
- Deduplicación: clave EAN, fallback SKU Cencosud; conserva la fila más completa y fusiona campos no vacíos. No perder ceros iniciales de códigos.
- Búsqueda full-text FTS5 en tabla sidecar `ean_product_fts` (NO es entidad Room: sin bump de versión ni migración). `EanFtsManager.ensureAndRebuild()` se llama tras cada importación; `buildFtsMatch()` arma el MATCH (AND de prefijos, tokens ya `[a-z0-9]`); el DAO usa `@RawQuery` y el PagingSource cae a LIKE si la tabla falta.
- Marcas cubiertas: ver `EAN_FILE_BRANDS` + assets `ean*.xlsx` (fuente de verdad, no esta lista). Los Excel originales de la raíz son fuentes locales y no se deben commitear salvo petición explícita.

## PDF de marcas

- El manual empaquetado está en `app/src/main/res/raw/manual_marcas.pdf` (107 páginas, MANUAL 4.0); `manual.4.0.pdf` en la raíz es fuente local no trackeada.
- `models/BrandReference.kt` contiene `brandPages` y calcula rangos con inicios ordenados/`knownBrandStarts`; nunca usar `page..page+PAGES_PER_BRAND` ni invadir la marca siguiente.
- Si una marca del Excel no coincide tras normalización, agregar alias en `brandPages` (ejemplo: MORETTA → MORETTA WINES).

## Compose y diseño

- Solo tema claro; Inter está empaquetada. Usar `MaterialTheme.colorScheme`, `.typography`, `.shapes` y `AppDimens`; no introducir `fontSize`/`fontWeight` ni colores crudos en nuevos composables.
- `touchMin = 48.dp`; no reducir targets interactivos para compactar la interfaz.
- La densidad visual se limita al equivalente lógico de 480dpi y la escala de fuente a 1.3x para conservar información en teléfonos con zoom alto.
- Scroll: aplicar insets una sola vez; el `Scaffold` reserva barra/sistema, las listas usan `scrollBottomPadding`, keys estables y `contentType`. No añadir `paddingBottom` arbitrario ni cambiar la altura del viewport durante el gesto.
- Si se modifica una lista, probar último elemento, texto largo, zoom alto, navegación gestual y navegación de tres botones.

## Actualizador de versiones

- Fuente: `releases/latest` de GitHub; exige asset `app-universal-release.apk`. Detección al abrir + worker 6h + manual; banner persistente + diálogo + notificación (canal `app_updates`).
- Dedupe de aviso y restauración del pendiente por **tag completo**, no por code (`v12.20.1` colisiona con `v12.20` en `versionCode`): `lastNotifiedTag`, `PendingUpdate.versionTag`, `isPendingNewer`.
- `POST_NOTIFICATIONS` se pide una sola vez al aparecer el banner (`wasUpdateNotifAsked`); si lo niegan, quedan banner + diálogo. `cleanTempApk` conserva `update.apk` si sigue más nuevo que lo instalado.

## Release y Git

- La versión oficial es `versionCode`/`versionName` en `app/build.gradle`; cada release debe subir ambos y usar un tag `v*` nuevo.
- `.github/workflows/release.yml` corre solo al hacer push de un tag `v*` y publica `app-universal-release.apk`.
- Acciones CI están fijadas por SHA; no cambiar a tags flotantes. `softprops/action-gh-release` usa `overwrite_files`.
- Firma local: `keystore.properties` (gitignored). CI: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, `KEYSTORE_PATH`.
- No incluir secretos ni los Excel fuente de la raíz en commits. No hacer commit, tag o push salvo petición explícita.
