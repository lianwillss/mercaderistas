# Mercaderistas — Guía del repo

App Android (Kotlin + Compose + Hilt + Room) de rutas para mercaderistas: descarga Excel de Drive, organiza locales por día, muestra promociones y abre catálogos PDF por marca (OCR). Detalles de arquitectura en `ARQUITECTURA.md` (algunas secciones están desactualizadas; el código manda).

## Comandos

- No hay Java en el PATH. Usar siempre:
  `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew <task>`
- Build + tests (verificación estándar): `./gradlew :app:testDebugUnitTest :app:assembleRelease`
- Test único: `./gradlew :app:testDebugUnitTest --tests "com.rutamercaderistas.viewmodel.RouteViewModelTest"`
- No existe `rg` → usar `grep`.
- Tests JVM puros (JUnit4 + MockK + coroutines-test), **sin Robolectric**: no se puede resolver `R.string` real en tests.

## Release (CI)

- CI `.github/workflows/release.yml` corre solo al pushear un tag `v*`; crea la release en GitHub con `app/build/outputs/apk/release/app-universal-release.apk`.
- Acciones fijadas por SHA — no cambiar a tags flotantes. `action-gh-release` v3 usa `overwrite_files` (ya no `overwrite`).
- Firma: `keystore.properties` (gitignored, texto plano) para local; CI usa `KEYSTORE_BASE64`/`KEYSTORE_PASSWORD`/`KEY_ALIAS`/`KEY_PASSWORD` como secrets + `KEYSTORE_PATH` env.
- **`version.json` en la raíz está stale (11.27) y no lo usa el código. No editarlo.** La verdad es `versionCode`/`versionName` en `app/build.gradle` (hoy 11055 / "11.55").

## Testing — races de coroutines

- Varios ViewModels usan `withContext(Dispatchers.Default)` (ej. `observeEntries` en `RouteViewModel`). `advanceUntilIdle()` **no espera** a esos threads reales → tests flaky (`UncaughtExceptionsBeforeTest`).
- Patrón a usar: helper `awaitOnMain { condition }` (poll con `advanceUntilIdle()` + `Thread.sleep(10)`, deadline 5s), ya presente en `RouteViewModelTest`. Úsalo tras cada llamada que dispare `observeEntries` (selectRoute, loadInitialData, setCurrentDay).
- ViewModels que emiten strings de UI inyectan `@ApplicationContext Context` (RouteViewModel) o son `AndroidViewModel` (SyncViewModel). En tests, mockear `context.getString(...)` con el valor literal esperado.

## PDF de marcas (delicado)

- Manual empaquetado: `app/src/main/res/raw/manual_marcas.pdf` (107 págs, "MANUAL 4.0"). `manual.4.0.pdf` en la raíz es la fuente sin trackear; al actualizar, reemplazar el raw y reverificar páginas.
- `models/BrandReference.kt`: mapa `brandPages` (marca → página) + `getPageRange()`. Los rangos se calculan desde los inicios ordenados y el fallback recorta al siguiente inicio conocido (`knownBrandStarts`) — **no** `page..page+PAGES_PER_BRAND`. Nunca ampliar un rango para que invada marcas vecinas (bug histórico: COMERCIAL SZ 46 invadía CORRALES DEL SUR/CUK).
- La normalización quita tildes/espacios (`normalizeMarca`). Si una marca del Excel no matchea el mapa, agregar alias en `brandPages` (ej. "MORETTA" → misma página que "MORETTA WINES").
- Para verificar contra el Excel real: `curl -sL "<DRIVE_EXPORT_URL de Constants.kt>"` y leer con `openpyxl` (hoja "RUTA RUTERO", columna CLIENTE).

## Sync de datos

- Excel desde Google Sheets (URL en `Constants.DRIVE_EXPORT_URL`). Parser (Apache POI SAX) busca la hoja por nombre "RUTA RUTERO" o por encabezados (fix v11.53) — el nombre puede variar entre versiones del archivo.
- Solo `es` (resConfigs "es"). Strings siempre en `app/src/main/res/values/strings.xml`, nunca hardcodeados.

## Diseño

Ver `SPEC.md` (spec completo) y `ui/theme/Type.kt`.

- Inter (bundled) como fuente; **evitar Roboto**. Nada de `fontSize`/`fontWeight` hardcodeados en composables — heredar de `MaterialTheme.typography`.
- No ALL CAPS; nombres de locales en natural case.
- Solo light theme (sin dark mode) → no agregar previews `NIGHT_YES`.
- `touchMin = 48.dp` en `AppDimens` (usarlo para targets táctiles, no 44dp fijo).
- Colores por cadena en `Color.kt`. M3 estándar: `Theme.kt` solo define `lightColorScheme`.