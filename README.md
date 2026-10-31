# Mercaderistas

App Android para mercaderistas: descarga el rutero desde Google Drive, lo organiza por día, muestra los locales con sus marcas y promociones, permite buscar códigos EAN de productos y consultar el manual de marcas en PDF. Funciona sin conexión después de la primera sincronización.

> Este README es la vista general del proyecto. Las reglas operativas, de verificación y de release están en [AGENTS.md](AGENTS.md), que es la fuente de verdad para cambios.

---

## 1. Estado del proyecto

| Dato | Valor |
|---|---|
| Versión en `app/build.gradle` | `12.38` (versionCode `12038`) |
| `minSdk` / `targetSdk` / `compileSdk` | 26 / 35 / 37 |
| Idioma de la interfaz | Español (`resConfigs "es"`) |
| Distribución | Releases de GitHub + actualizador interno |
| Tests JVM | 292 (JUnit4 + MockK + coroutines-test) |
| Repositorio | [github.com/lianwillss/mercaderistas](https://github.com/lianwillss/mercaderistas) |

El árbol de trabajo puede contener cambios posteriores a la última versión publicada; la versión oficial siempre es la de `app/build.gradle`.

---

## 2. Stack

| Capa | Tecnología |
|---|---|
| UI | Jetpack Compose + Material 3 (Material 3 Adaptive Navigation Suite) |
| Arquitectura | MVVM + `StateFlow` + Hilt |
| Persistencia | Room (KSP) + DataStore Preferences |
| Trabajo en segundo plano | WorkManager + Hilt Work |
| Excel | Apache POI 5.5.1 |
| Red | OkHttp 5.4.0 / `HttpURLConnection` |
| PDF | visor PDF (oothp) + ML Kit Text Recognition 16.0.1 (OCR de marcas) |
| Códigos de barras | ZXing core + embedded + `journeyapps` |
| Navegación | Navigation Compose 2.8.5 con rutas type-safe (kotlinx.serialization) |
| Imágenes | Compose + generación de PNG para compartir rutero |
| Logging / crash | Timber + Sentry (opcional por DSN) |
| Build | Gradle 9.6, AGP 9.4.0, Kotlin 2.4.20, KSP 2.3.9, Compose BOM 2026.09.00, Room 2.8.5, Paging 3.5.1, Hilt 2.60.1, Java 17 |

---

## 3. Arquitectura

Proyecto de **un solo módulo** (`:app`), con separación por paquetes y MVVM:

- **UI Compose** (`ui/screens`, `ui/components`, `ui/theme`): pantallas sin lógica de negocio; recolectan estado con `collectAsStateWithLifecycle()`.
- **ViewModels** (`viewmodel`): estado de pantalla (`StateFlow`), orquestación y reglas de presentación.
- **Dominio** (`domain`): modelos y casos de uso puros (agrupación de promociones, cadenas, marcas).
- **Datos** (`data`): Room, DataStore, red, exportación y resultados.
- **Servicios** (`services`): sincronización del rutero, promociones, parseo EAN/Excel, actualizaciones.
- **Workers** (`worker`): sync periódico, refresco de promos, notificación diaria y chequeo de actualización.

Flujo de datos principal:

```
Google Drive (XLSX) ──► RuteroManager ──► validación + archivo temporal + reemplazo atómico
                                         └──► Room (route_entries) ──► RouteViewModel ──► Compose

Google Drive (CSV)  ──► PromotionRepository ──► Room (promotions) ──► pantallas/notificaciones

assets ean*.xlsx   ──► EanExcelParser ──► Room (ean_products) ──► EanSearchViewModel ──► Paging + SQL

GitHub Releases    ──► UpdateChecker / UpdateViewModel ──► APK + notificación + instalación
```

Decisiones clave:

- **Offline-first**: Room es la fuente de la UI; la red solo actualiza.
- **Sin migraciones destructivas**: `AppDatabase` no usa `fallbackToDestructiveMigration`; cada cambio de esquema requiere `Migration` explícita.
- **Sync atómico**: el Excel se escribe en temporal, se valida y recién entonces se reemplaza; el índice de Room se actualiza en una transacción.
- **Rutas type-safe** con `@Serializable`; navegación principal con `NavigationSuiteScaffold`.
- **El código manda sobre la documentación** si esta difiere; el material histórico está en [`docs/archive/`](docs/archive/).

---

## 4. Estructura del proyecto

```
app/src/main/java/com/rutamercaderistas/
├── MainActivity.kt            # Entry point, tema, sync/actualizaciones, permisos
├── MercaderistasApp.kt        # Application: Hilt, Timber, Sentry, canales, limpieza
├── PdfViewerActivity.kt       # Visor PDF (actividad aparte)
├── Constants.kt               # URLs de Drive, timeouts, intervalos
├── models/                    # LocalDelDia, ClienteInfo, DiaSemana, PDF/Brand
├── domain/
│   ├── model/                 # ChainConfig, normalización de cadenas
│   ├── usecase/               # Promociones, marcas, cadenas
│   └── validation/            # Validaciones de planilla
├── data/
│   ├── local/                 # Room: entidades, DAOs, migraciones, búsqueda EAN SQL
│   ├── network/               # Descargas HTTP y parser CSV
│   ├── preferences/           # DataStore: rutas, historial, fuente, PDF, updates
│   ├── export/                # Rutero a PNG para compartir
│   └── result/                # SyncResult / UpdateResult
├── services/                  # RuteroManager, PromotionRepository, EanExcelParser,
│                              # UpdateChecker, ApkDownloader, ColumnMapper
├── worker/                    # Sync, promos, notificación diaria, updates
├── viewmodel/                 # Route, Sync, Settings, Update, EanSearch
├── ui/screens/                # Rutero, Locales, Promos, EAN, Cód. Prov, Manual, Ajustes
├── ui/components/             # Header, tarjetas, sheets, chips, toasts, modales
├── ui/theme/                  # Colores, tipografía Inter, dimens, motion, contrast
├── ui/navigation/             # Rutas @Serializable
└── utils/ + util/             # Fuzzy search, EAN share text, Maps, imágenes
```

Assets relevantes:

- `app/src/main/assets/ean*.xlsx`: catálogo EAN combinado automáticamente.
- `app/src/main/res/raw/manual_marcas.pdf`: manual de marcas (107 páginas, v4.0).
- `app/src/main/res/drawable-nodpi/img_flejes_barcode.png`: código de flejes generado.

---

## 5. Pantallas y funcionalidades

| Pantalla | Archivo | Qué hace |
|---|---|---|
| Rutero | [MainRouteContent.kt](app/src/main/java/com/rutamercaderistas/ui/screens/MainRouteContent.kt) | Ruta del día, pager/botones de día, búsqueda de ruta, tarjetas de local, banners de sync/updates, pull-to-refresh |
| Locales | [AllLocalesScreen.kt](app/src/main/java/com/rutamercaderistas/ui/screens/AllLocalesScreen.kt) | Todos los locales, búsqueda difusa, secciones por marca, filtro por día, detalle en hoja (teléfono) o panel (tablet) |
| Promociones | [PromotionsOverviewScreen.kt](app/src/main/java/com/rutamercaderistas/ui/screens/PromotionsOverviewScreen.kt) / [PromotionsListDetailScreen.kt](app/src/main/java/com/rutamercaderistas/ui/screens/PromotionsListDetailScreen.kt) | Promos por marca y cadena, list-detail adaptativo, por vencer |
| Cód. Prov | [CodProvScreen.kt](app/src/main/java/com/rutamercaderistas/ui/screens/CodProvScreen.kt) | Códigos de proveedor por marca, búsqueda y copiado |
| Cód. EAN | [EanSearchScreen.kt](app/src/main/java/com/rutamercaderistas/ui/screens/EanSearchScreen.kt) | Búsqueda de productos por EAN/SKU/nombre, escáner, filtros, código de barras, compartir |
| Búsqueda global | [GlobalSearchScreen.kt](app/src/main/java/com/rutamercaderistas/ui/screens/GlobalSearchScreen.kt) | Locales + promociones en un solo buscador, con ranking y filtros de cadena |
| Manual de marcas | [ManualScreen.kt](app/src/main/java/com/rutamercaderistas/ui/screens/ManualScreen.kt), [PdfViewerScreen.kt](app/src/main/java/com/rutamercaderistas/ui/screens/PdfViewerScreen.kt) | Grilla de marcas, páginas del PDF y buscador con OCR |
| Ajustes | [SettingsScreen.kt](app/src/main/java/com/rutamercaderistas/ui/screens/SettingsScreen.kt) | Tamaño de texto, modo de transporte (Maps), caché EAN, historial, historial de sync, versión |
| Onboarding | [OnboardingOverlay.kt](app/src/main/java/com/rutamercaderistas/ui/components/OnboardingOverlay.kt) | Guía de primer arranque |

### Rutero

- Descarga la planilla XLSX de Drive (URL en [Constants.kt](app/src/main/java/com/rutamercaderistas/Constants.kt)).
- Agrupa por día y muestra los locales del día con marcas, días de visita, dirección y promociones.
- Acciones por local: abrir Maps (tap), copiar dirección (long-press), compartir local con selector de contenido.
- Auto-sync al abrir (throttle 15 min), worker cada 1 h, pull-to-refresh y botón de sync en el header.
- Banner de cambios (agregados/eliminados/movidos/direcciones/marcas) calculado contra el estado anterior.

### Locales

- Lista completa de locales únicos (código + nombre), con búsqueda difusa (acentos, typos, tokens).
- Agrupa en secciones por marca cuando la búsqueda coincide con marcas; filtro por día dentro de esas secciones.
- En teléfono, la tarjeta abre una hoja con el detalle y **todas** las marcas; en tablet, panel lateral.
- Muestra "+N marcas" cuando la tarjeta trunca a 2 coincidencias.

### Cód. EAN

- Catálogo propio desde assets (ver sección EAN).
- Búsqueda con ranking en SQL y paginación real (Paging 3): EAN exacto > prefijo > SKU > marca > tokens.
- Filtros de marca/caja con facetas SQL y total real de resultados.
- Escáner ZXing integrado; normaliza UPC-A a EAN-13 y limpia filtros.
- Genera el código de barras (CODE_128 cuando el EAN empieza con 0) y permite copiar/compartir como imagen o texto.

---

## 6. Datos y persistencia

Room (`AppDatabase`, versión **6**, `mercaderistas.db`):

| Entidad | Tabla | Contenido |
|---|---|---|
| `RouteEntryEntity` | `route_entries` | Filas del rutero (local, marca, día, dirección, cadena…) |
| `PromotionEntity` | `promotions` | Promociones por marca/cadena, vigencia y precio |
| `EanProductEntity` | `ean_products` | Catálogo EAN con campos normalizados para búsqueda |

Migraciones definidas: `1→3`, `3→4`, `4→5` (rebuild con copia), `5→6` (columnas compactas). **No hay migración destructiva.**

DataStore (`PreferencesRepository`) guarda, entre otras cosas: rutas recientes, historial de búsquedas, escala de fuente, modo de transporte, onboarding, hash/versión del catálogo EAN, metadatos de sync y estado de actualizaciones.

Archivos y cachés: PDF del manual, miniaturas, exportaciones PNG y APK de actualización en `cache/apk`. `MercaderistasApp` limpia caché al detectar un cambio de versión y conserva el APK pendiente si sigue siendo más nuevo.

---

## 7. Sincronización y trabajos en segundo plano

Reglas y decisiones del sync (lock, atomicidad, hash, ETag, ventanas de frescura):
[AGENTS.md](AGENTS.md) §Sync del rutero.

| Worker | Frecuencia | Restricción |
|---|---|---|
| `SyncWorker` | 1 hora | Red conectada |
| `PromotionRefreshWorker` | 12 horas | Red conectada |
| `DailyPromotionNotificationWorker` | 1 día (07:00) | Sin red requerida |
| `UpdateCheckWorker` | 6 horas | Red conectada |

---

## 8. Catálogo EAN

Reglas del parser, alias, dedupe, ranking y facetas: [AGENTS.md](AGENTS.md) §Catálogo EAN.
Resumen: los `app/src/main/assets/ean*.xlsx` se combinan al importar, `EAN_DATA_VERSION` fuerza
la reimportación y la búsqueda se resuelve entera en SQL
([EanRankedSearch.kt](app/src/main/java/com/rutamercaderistas/data/local/EanRankedSearch.kt)),
sin FTS5.

Para agregar una marca: copiar el Excel como `app/src/main/assets/ean_<marca>.xlsx`, mapear la
marca si hace falta (`EAN_FILE_BRANDS`), subir `EAN_DATA_VERSION` y agregar un test de parseo
(ver [EanCodigosParserTest.kt](app/src/test/java/com/rutamercaderistas/services/EanCodigosParserTest.kt)).

---

## 9. Actualizaciones

Reglas del actualizador (dedupe por tag completo, permisos, caché del APK):
[AGENTS.md](AGENTS.md) §Actualizador de versiones.
Fuente: `releases/latest` de GitHub; exige el asset `app-universal-release.apk`;
`versionCode = major * 1000 + minor`.

Publicar una versión:

```bash
./gradlew :app:bumpVersion          # sube minor y valida versionCode
git add -A && git commit -m "chore: bump version to X.Y"
git tag vX.Y && git push origin main vX.Y
```

El workflow [.github/workflows/release.yml](.github/workflows/release.yml) compila y publica el APK universal; solo se dispara con tags `v*`.

---

## 10. UI, diseño y accesibilidad

Reglas de Compose, tema, tokens, motion y densidad: [AGENTS.md](AGENTS.md) §Compose y diseño.

- Solo **tema claro**; tipografía Inter empaquetada; color dinámico en Android 12+.
- Accesibilidad: `heading()` en títulos, `liveRegion` para sync/errores, `contentDescription`
  en acciones, targets de 48 dp y estados de selección anunciados.
- La barra inferior se oculta en Cód. EAN (pantalla completa con su propio botón volver).
- Límite conocido: la escala de fuente se topea en **1.3×** por decisión de producto; quien usa
  zoom alto pierde el 200 %.

---

## 11. Build y tests

Comandos, toolchain y reglas de tests (carreras de `Dispatchers.Main`, `forkEvery = 1`,
mocks de Paging): [AGENTS.md](AGENTS.md) §Comandos y §Tests.

- JDK 21 para Gradle (el proyecto compila Java 17); en este equipo `JAVA_HOME` sale de `~/.gradle/jdks/...`.
- APKs de release: `app/build/outputs/apk/release/app-universal-release.apk` (+ `arm64-v8a`, `armeabi-v7a`).
- Firma: `keystore.properties` local (gitignored) o variables `KEYSTORE_*` en CI.

---

## 12. Convenciones y reglas críticas

Todas viven en [AGENTS.md](AGENTS.md) (comandos, tests, EAN, diseño, release). Las tres que más
se olvidan: **no hay migraciones destructivas**, todo cambio de assets/alias EAN sube
`EAN_DATA_VERSION`, y **no se hace commit, tag ni push sin pedido explícito**.

---

## 13. Documentación relacionada

| Archivo | Contenido | Estado |
|---|---|---|
| [AGENTS.md](AGENTS.md) | Reglas operativas, comandos, convenciones y decisiones por módulo | Vigente (fuente de verdad) |
| [docs/MANUAL.md](docs/MANUAL.md) | Manual de usuario final (sincronizar, rutas, promociones, EAN, códigos) | Vigente |
| [STOCK.md](STOCK.md) | Módulo de stock (contexto completo) | Vigente |
| [docs/archive/ARQUITECTURA.md](docs/archive/ARQUITECTURA.md) | Arquitectura anterior (targetSdk 34, AGP 9.2.1) | Histórico |
| [docs/archive/SPEC.md](docs/archive/SPEC.md) | Spec de tipografía iOS 26 usada en una iteración de diseño | Histórico |
| [docs/archive/GUIA_ANDROID_STUDIO.md](docs/archive/GUIA_ANDROID_STUDIO.md) | Guía inicial para abrir el proyecto | Histórico |
| [docs/gen_manual.py](docs/gen_manual.py) | Generación del manual de usuario | Utilidad |

---

## 14. Deuda técnica y próximos pasos

- **Locales (fase 1 de búsqueda)**: el filtrado sigue siendo síncrono en composición y sin ranking; conviene un índice precalculado, debounce, `Dispatchers.Default` y reutilizar `rankLocales`. El filtro por día hoy solo aparece cuando la búsqueda coincide con una marca.
- **Accesibilidad (fase 3)**: quedan roles/estados (chips de día como grupo de radio, badges de promoción expandibles, sheet de compartir), etiquetas duplicadas y contrastes del tema estático (Android 8–11).
- **Escala de fuente**: decidir si se respeta el zoom del sistema hasta 2.0 o se mantiene el tope de 1.3 y se arregla el control `A+` que deja de responder.
- **Documentación (hecho)**: arquitectura consolidada en este README; `ARQUITECTURA.md`, `SPEC.md`
  y la guía de Android Studio pasaron a `docs/archive/`.
- **Archivos grandes**: `AllLocalesScreen` y `MainRouteContent` superan las 900 líneas; extraer listas/tarjetas facilita el mantenimiento.

---

## 15. Instalación para probar

1. Descargar el APK universal desde [Releases](https://github.com/lianwillss/mercaderistas/releases) (o compilar con `assembleRelease`).
2. Instalarlo permitiendo "instalar apps desconocidas" o con `adb install -r app-universal-release.apk`.
3. Abrir la app y esperar la primera sincronización (requiere internet). Después funciona offline.
4. Para el catálogo EAN, la primera apertura de **Cód. EAN** importa los assets; puede tardar unos segundos.
