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
- Datos persistentes: Room (`AppDatabase`); preferencias: DataStore (`PreferencesRepository`).
- Sincronización en segundo plano: WorkManager + `SyncWorker`/`PromotionRefreshWorker`.
- La navegación principal usa `NavigationSuiteScaffold`; `AppWindowWidth` centraliza Compact `<600dp`, Medium `<840dp` y Expanded.

## Tests

- Tests locales son JUnit4 + MockK + `kotlinx-coroutines-test`, sin Robolectric; no asumir `R.string` real en `src/test` y mockear `Context.getString()` cuando corresponda.
- `RouteViewModel` calcula parte del estado en `Dispatchers.Default`; `advanceUntilIdle()` no espera esos hilos reales. Usar el helper `awaitOnMain { ... }` de `RouteViewModelTest` después de `selectRoute`, `loadInitialData` y `setCurrentDay`.
- La suite puede producir `UncaughtExceptionsBeforeTest` por carreras globales de `Dispatchers.Main`; ejecutar con `--no-parallel` y repetir un test aislado antes de atribuirlo al cambio.
- Las pruebas instrumentadas no se ejecutan sin dispositivo; agregar cobertura de scroll ahí, no en tests JVM.

## Sync del rutero

- URLs fuente están en `Constants.kt`: `DRIVE_EXPORT_URL` para el XLSX del rutero y `PROMOTIONS_CSV_URL` para promociones.
- `RuteroManager` serializa sync con `withSyncLock`, escribe a temporal, valida estructura, reemplaza atómicamente y luego `createIndex()` actualiza Room dentro de una transacción.
- `SyncViewModel` aplica automáticamente un Excel válido; no existe confirmación Aplicar/Cancelar. El hash/ETag y la hora de último sync se guardan solo después de indexar correctamente.
- La validación estructural rechaza archivo vacío/sin rutas/campos esenciales; no convertir validaciones de filas en banners bloqueantes sin una petición explícita.
- Al cambiar el sync, preservar la recarga de la ruta activa y el cálculo del diff (agregados, eliminados, movidos, direcciones y marcas).

## Catálogo EAN

- Todos los assets `app/src/main/assets/ean*.xlsx` se combinan automáticamente; si el nombre no es obvio, agregarlo a `EAN_FILE_BRANDS` en `EanExcelParser.kt`.
- Al agregar/cambiar un asset EAN, incrementar `EAN_DATA_VERSION` para forzar la reimportación en instalaciones existentes y agregar una prueba del archivo/mapeo.
- Deduplicación: clave EAN, fallback SKU Cencosud; conserva la fila más completa y fusiona campos no vacíos. No perder ceros iniciales de códigos.
- Catálogos actuales incluyen ASMODE, DIX y CUK; los Excel originales de la raíz son fuentes locales y no se deben commitear salvo petición explícita.

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

## Release y Git

- La versión oficial es `versionCode`/`versionName` en `app/build.gradle`; cada release debe subir ambos y usar un tag `v*` nuevo.
- `.github/workflows/release.yml` corre solo al hacer push de un tag `v*` y publica `app-universal-release.apk`.
- Acciones CI están fijadas por SHA; no cambiar a tags flotantes. `softprops/action-gh-release` usa `overwrite_files`.
- Firma local: `keystore.properties` (gitignored). CI: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, `KEYSTORE_PATH`.
- No incluir secretos ni los Excel fuente de la raíz en commits. No hacer commit, tag o push salvo petición explícita.
