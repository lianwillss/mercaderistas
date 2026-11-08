# Módulo de Stock — contexto completo

Documento de referencia de todo lo construido alrededor del módulo **Stock** de la app
Ruta Mercaderistas: qué hace, cómo se alimenta, qué decisiones se tomaron y qué queda
pendiente.

---

## 1. Qué es

Una pestaña **Stock** dentro de la app que muestra el inventario de los locales de la
Región Metropolitana, sacado de **KPI One** (`https://kpione-app-production.up.railway.app`),
la web interna de la empresa.

El objetivo es que el mercaderista vea el stock de su local sin entrar a la web, y que un
supervisor pueda revisar una ruta completa y detectar quiebres y negativos.

Hoy el módulo funciona con un **snapshot local** ("stock.json"). Se puede regenerar
**desde la propia app** contra KPI One (botón ↻ de la cabecera, con barra de progreso y
cancelación), sin Mac ni subida manual a Drive. El asset de la APK queda como respaldo
para la primera instalación. El código de descarga por URL/Drive se conserva para una
próxima fase, pero todavía no tiene botón en la interfaz.

---

## 2. Datos

### Formato `stock.json`

```json
{
  "locales": [{ "c": "J503", "n": "J503-JUMBO MAIPU", "r": ["EMU-3"] }],
  "marcas": ["CASO Y CIA", "CUK", "SUK"],
  "items": [{
    "s": "J503", "n": "J503-JUMBO MAIPU", "b": "CASO Y CIA",
    "p": "NECTAR JUMEX 335 CC, DURAZNO", "k": "192829",
    "u": 94, "t": 0, "d": "2026-10-28", "q": 0, "i": 10
  }]
}
```

| Clave | Significado |
|---|---|
| `s` / `n` | código y nombre del local |
| `r` | rutas del rutero a las que pertenece (puede haber varias) |
| `b` / `p` / `k` | marca, descripción del producto, SKU |
| `u` / `t` | unidades en stock / en tránsito (pueden ser negativas o nulas) |
| `d` | `snapshot_date` de KPI One para esa fila |
| `q` / `i` | flag de quiebre / días de inventario |

### Cifras actuales (RM)

- **296 locales** con stock, de 315 códigos del rutero en RM.
- **42 rutas**: EMU-1…7, DMU-1/5/6/8, FMU, GMU, HMU, BR, PITUTO.
- **52 marcas**, ~**13.100 filas**, archivo de ~1,95 MB.
- **172 filas con stock negativo** en 89 locales.
- 71 locales pertenecen a más de una ruta.

### Endpoints de KPI One (descubiertos)

| Uso | Endpoint |
|---|---|
| Login | `POST /api/auth/login` con `{"email","password"}` → cookie de sesión |
| Sesión | `GET /api/auth/me` |
| Catálogo | `GET /api/b2b-reports/terreno/inventory-filter-options` → `storeCodes`, `brands`, `skus` |
| Stock | `GET /api/b2b-reports/terreno/inventory-browse?storeCodes=..&brands=..&q=..&limit=500&offset=0` |
| Maestro | `GET /api/stores?limit=500&offset=0` → código y nombre del local |

Detalles: la sesión es por **cookie**; `pagination.total` permite paginar; los
`storeCodes` aceptan varios separados por coma; `q` filtra por texto y se combina con
`storeCodes`; cada fila trae su propio `snapshot_date` (hay datos de meses distintos).

---

## 3. Arquitectura en la app

```
StockScreen (Compose)
   │  filters / state (StateFlow)
StockViewModel ── StockSyncScheduler (WorkManager)
   │                    └── StockSyncWorker (foreground + notificación)
   │                          └── StockBulkSync
   │                                ├── KpiOneClient (login/catálogo/browse)
   │                                └── RouteEntryDao (códigos y rutas del rutero)
   ├── DemoStockRepository (archivo local: asset o snapshot)
   └── StockUpdater (respaldo: stock.json por URL/Drive)
```

| Archivo | Rol |
|---|---|
| `data/stock/StockModels.kt` | modelos, `EstadoTipo`, `estadoDe`, normalización, helpers de filtros |
| `data/stock/StockRepository.kt` | interfaz + repositorio del snapshot local |
| `data/stock/StockDemoData.kt` | parser del JSON (delega en el codec) |
| `data/stock/StockFileCodec.kt` | formato compacto + `stock-meta.json` |
| `data/stock/StockStorage.kt` | escritura atómica, fsync y `.bak` |
| `data/stock/StockBulkSync.kt` | motor: rutas desde Room, lotes, reintentos, QC |
| `data/stock/StockSyncScheduler.kt` | trabajo único `stock-sync` en WorkManager |
| `data/stock/StockSyncNotifier.kt` | notificación de progreso (foreground) |
| `data/stock/StockUpdater.kt` | respaldo: descarga un `stock.json` por URL |
| `data/stock/remote/KpiOneClient.kt` | cliente único contra KPI One |
| `data/stock/remote/KpiSessionStore.kt` | contrato de sesión (Keystore / fake de tests) |
| `worker/StockSyncWorker.kt` | worker Hilt con foreground y `WorkInfo.progress` |

| `data/stock/RmCodeProvider.kt` | lista RM de respaldo (`assets/stock/locales_rm.txt`) |
| `di/StockModule.kt`, `di/StockDataModule.kt`, `di/StockNetworkModule.kt` | binding, origen y storage |
| `viewmodel/StockViewModel.kt` | estado, filtros, barra y cancelación |
| `ui/screens/stock/StockScreen.kt` | pantalla + integración del banner con el scroll |
| `ui/components/CollapsingBanner.kt` | banner colapsable reutilizable (frescura, carga, error) |
| `ui/theme/Metric.kt` | estilos de cifras tabulares |
| `ui/theme/Color.kt` | tokens (banner morado, píldora de frescura, quiebres, neón, etc.) |
| `assets/stock/locales_rm.txt` | 314 códigos RM; respaldo cuando `REGION` viene vacía |

> **Hilt + WorkManager:** además de `ksp("com.google.dagger:hilt-compiler")` hace falta
> `ksp("androidx.hilt:hilt-compiler")`. Sin el segundo, `HiltWorkerFactory` se genera con
> el mapa vacío, WorkManager no puede instanciar el worker y falla antes de `doWork` con
> un error genérico.

> **R8 + WorkManager:** `proguard-rules.pro` debe conservar el constructor de
> `androidx.work.InputMerger` (`OverwritingInputMerger` se crea por reflexión). Sin esa
> regla, el release falla con `Could not create Input Merger`.

> **Arranque manual:** `StockSyncScheduler` usa `setExpedited` y `ExistingWorkPolicy.REPLACE`,
> sin constraint de red. Así el botón ↻ arranca ya y no se queda pegado a un intento viejo
> en `ENQUEUED`.

### Origen de los datos al abrir

1. Si existe `filesDir/stock/stock.json` (generado con ↻ o bajado de Drive), se usa ese.
2. Si no, se usa el asset `assets/stock/stock_demo.json` de la APK.
3. `stock-meta.json` guarda filas, locales, fecha máxima, fallidos y origen; se usa
   para el control de calidad y para el badge "Datos de KPI One al …".

---

## 4. Interfaz

### Cabecera (banner colapsable)
Un solo componente reutilizable, `ui/components/CollapsingBanner.kt`: recibe título,
subtítulo, frescura, estado de carga, `onRefresh` y acciones. Las demás pantallas lo
reutilizan cambiando solo esos parámetros.

- Degradado morado `BannerPurpleDeep → BannerPurpleMid → BannerPurpleLight`
  (#3B1F7A → #5A34AC → #7B4FD6), esquinas inferiores de 28 dp y texto blanco
  (contraste ≥ 5:1 en todo el degradado). Sin sombras.
- **Compacto**: 84 dp expandido (título + subtítulo en una fila y píldora debajo) y
  **56 dp colapsado** (solo título + ↻). El error abre a 168 dp para el mensaje.
- Se colapsa al bajar en la lista y vuelve al llegar arriba, vía `nestedScroll`: el
  delta que encoge el banner se **descuenta del scroll**, así el contenido no salta.
- Botón ↻ de **48 dp** con fondo circular translúcido. Mientras actualiza queda
  **deshabilitado**, gira un spinner y una **barra fina de 3 dp** en el borde inferior
  muestra la fase (`Descargando 84/296 · 3.120 filas`), el porcentaje y **Cancelar**
  como ✕. Al terminar, check + "Stock actualizado…" durante 4 s.
- Píldora de frescura **"Actualizado hoy 07:21"**: **verde hoy, ámbar ayer y rojo a
  partir de 2 días**. La hora sale de `generatedAt` del snapshot (instante ISO en
  `stock.json`); si el archivo no la trae (asset de la APK), se omite.
- Error: advertencia + **"Sin conexión, mostrando datos del dd-MM HH:mm"** y acciones
  **Reintentar / Revisar acceso / Forzar de todos modos** según el caso.
- Con movimiento reducido no hay spinner ni barra indeterminada: queda una barra fija.

La actualización sigue siendo `StockSyncWorker`; la barra se reengancha al `WorkInfo`
del worker, así que sobrevive rotación y pantalla apagada.

### Buscador
- **Local** (código o nombre, con sugerencias en línea).
- **Ruta** y **Marca** en una fila. La ruta muestra cuántos locales tiene.
- **Producto / SKU**.
- Los filtros se recuerdan entre sesiones (DataStore).

**Independencia de filtros:** local y marca son caminos alternativos a la ruta.
Escribir un local o una marca **limpia la ruta**; elegir una ruta **limpia local, marca
y Producto/SKU** (`conLocal`, `conMarca`, `conRuta` en `StockModels.kt`).

Al buscar por **local** o por **ruta** (escribiendo o eligiendo una opción), la tarjeta
**Disponible** queda seleccionada automáticamente y se apagan **Quiebres/Negativos**.

### Tarjetas de estado (filtros)
Una sola activa a la vez; cada una muestra **siempre** su número aunque otra esté
seleccionada (los conteos se calculan sobre la búsqueda sin filtrar por estado).

| Tarjeta | Estilo | Filtro |
|---|---|---|
| Quiebres | naranja que respira + icono amarillo | `EstadoTipo.Quiebre` |
| Negativos | negro con borde neón parpadeante | `stockUnits < 0` |
| Disponible | verde suave | `EstadoTipo.Disponible` |

La seleccionada muestra **check**, borde más grueso y **punto verde parpadeante**.
Con movimiento reducido, todas las animaciones se detienen.

### Lista
- Agrupada por local (o por marca).
- Encabezado: nombre, cantidad de productos y **fecha solo cuando cambia**.
- Tarjeta: producto, número grande con cifras tabulares, badge de estado, SKU, tránsito,
  fecha cuando cambia, y **barra proporcional** al mayor stock del resultado.
- Fondo tintado por estado (rol del tema), barra pegada al borde inferior.
- **Compartir** genera uno o más **PNG** agrupados por local con SKU, producto y
  unidades (`util/StockShareImage.kt`). Si hay muchas filas, pagina en varias imágenes
  (`Página X/N`), no recorta SKUs y comparte todas con el selector de Android.
  El texto de respaldo separa quiebres de negativos.
- Con el filtro **Negativos** activo, el texto de WhatsApp y la cabecera del PNG
  hablan **solo de negativos** ("9 negativos en 1 local", "Locales con más
  negativos"): sin "registros" ni quiebres, para no desviar el foco del mercaderista.
- Cada **cabecera de local** tiene su propio botón de compartir: comparte solo ese
  local, con los filtros activos (por ejemplo, solo sus quiebres).

### Estados de pantalla
`Idle` (en blanco hasta buscar), `Loading` (skeleton con la forma real de la tarjeta),
`Ready`, `Empty`, `Error`, `SinSesion` (solo en modo remoto).

---

## 5. Cómo se alimenta (pipeline)

```
[Teléfono] Stock → ↻
   → StockSyncWorker (WorkManager, foreground, trabajo único)
   → KpiOneClient: login por cookie, catálogo y inventory-browse
   → StockBulkSync: rutas desde Room, lotes de 4 locales, deduplicación
   → control de calidad → stock.json + stock-meta.json (move atómico)
   → barra en la cabecera + notificación
```

La Mac y `tools/stock-sync.mjs` quedan como referencia y como plan B. El script ya no
es el camino principal; `tools/rutas-sync.py` tampoco hace falta porque el rutero
indexado en Room ya tiene `COD KPI ONE` y `RUTERO`.

El rutero real trae la columna `REGION` vacía, por eso `StockBulkSync` usa
`assets/stock/locales_rm.txt` (314 códigos) como respaldo: pide 296 locales en vez de
los 790 del país. `stock-meta.json` guarda `scope: rm|all`; si cambia el alcance, el QC
no compara contra el snapshot anterior para no bloquear la migración de 790 a 296.

### Control de calidad
Antes de escribir, compara contra `stock-meta.json`: si **filas o locales caen más de
10 %**, o si falla más de 10 % de los locales, no reemplaza el snapshot bueno. Para
forzar desde la app: **Forzar de todos modos** en la cabecera. Desde la Mac:
`./actualizar-stock.sh --force`.

### Comandos (plan B)

```bash
# todo el ciclo (rutas + stock) al Escritorio
./actualizar-stock.sh

# solo stock, locales puntuales
KP_EMAIL=... KP_PASSWORD=... node tools/stock-sync.mjs --locales=J503,J502 --out=/tmp/x.json

# todos los locales del catálogo (archivo grande)
node tools/stock-sync.mjs --all
```

`Constants.STOCK_JSON_URL` queda como respaldo de descarga directa desde Drive.

---

## 6. Accesibilidad

- Tarjetas de estado: `mergeDescendants`, `role = Button`, `selected` y
  `stateDescription` ("Filtro activo/inactivo"), `contentDescription`
  ("Quiebres, 30 productos, filtro activo") y acción `onClick` explícita para TalkBack.
- Selección no depende solo del color: check + borde + punto.
- Encabezados con `heading()`; contadores con `liveRegion` cortés.
- Targets ≥ 48 dp; sugerencias del buscador también.
- Contraste: naranja de quiebres oscurecido a `#E8590C → #C2410C` (~4,6:1 con blanco).
- Movimiento reducido detiene pulso, parpadeo y animaciones de lista.

---

## 7. Decisiones tomadas (y por qué)

| Decisión | Motivo |
|---|---|
| Snapshot local, no servidor | Cero costo, funciona sin red, ~2 MB |
| Generación in-app | 5 supervisores, cuenta dedicada; evita Mac y subida manual |
| Drive como respaldo | Si KPI One falla o cambia la API |
| Login automático con Keystore | Credenciales cifradas; cuenta dedicada y rotación |
| Lotes de 4 locales | 314 requests bajan a ~79; menos carga y tiempo |
| Rutas desde Room | Una sola fuente; ya no hace falta `rutas-sync.py` |
| Barra desde WorkInfo | Sobrevive rotación, pantalla apagada y segundo plano |
| QC + `.bak` + archivo atómico | No publicar un snapshot incompleto; rollback inmediato |
| Negativos separados de quiebres | `EstadoTipo.Negativo`; filtros y conteos no se mezclan |
| Local/ruta arrancan en Disponible | Al buscar por local o ruta, la tarjeta Disponible queda activa |
| Compartir paginado | 280 SKUs → 13 imágenes; sin "... y N más" |
| Ruta y local/marca independientes | Con filtros sumados, buscar un local fuera de la ruta daba 0 resultados |
| Conteos sobre la búsqueda base | Evita que las tarjetas se apaguen al filtrar |
| Estado por días de inventario | Un umbral fijo de unidades no refleja la realidad |
| Deduplicar local+SKU | KPI devuelve filas repetidas y rompía la lista (claves duplicadas) |

---

## 8. Pendientes

1. **Probar en teléfonos reales** — Xiaomi/Samsung con ahorro de batería agresivo;
   verificar que el foreground sobreviva y que Cancelar corte a tiempo.
2. **Alcance "Mis rutas"** — bajar solo las rutas del supervisor (20-40 locales) y
   dejar "RM completa" como opción explícita.
3. **Auto-refresh** — worker periódico (cada 6 h) con jitter y badge de datos viejos.
4. **Exportar/importar snapshot** — que un supervisor genere y los demás importen el
   mismo `stock.json` para no ver cifras distintas.
5. **Comparación con el día anterior** — marcar quiebres nuevos y resueltos.
6. **`StatusStyle` / `FilterTile` únicos** — hoy badge, tarjeta, tile y PNG repiten estilos.
7. **Filtro por cadena**, varias rutas y dos columnas en tablets.
8. **Migración a Room** si se suman regiones (hoy 13k filas en memoria).
9. **Retirar los scripts** una vez validada la paridad; dejar solo el plan B documentado.

---

## 9. Seguridad y credenciales

- `keystore.properties` (gitignored) lleva `KP_EMAIL` / `KP_PASSWORD`; se compilan
  en `BuildConfig` y los usa el login automático. La cuenta es dedicada (no personal).
  Si esas claves quedan vacías, el APK no puede entrar y el worker falla; en CI hay
  que definirlas como secrets (`KP_EMAIL`, `KP_PASSWORD`) antes de publicar.
- `SecureCookieStore` cifra cookie y credenciales con Android Keystore (AES/GCM).
  Ajustes permite reconectar o borrar todo el acceso.
- Las credenciales dentro de la APK son extraíbles con ingeniería inversa. Con 5
  supervisores el riesgo es acotado; la mitigación es cuenta dedicada, rotación
  periódica y revocar cambiando la clave.
- `Actualizar stock.command` y `actualizar-stock.sh` tenían credenciales en claro.
  Al retirar el plan B hay que borrar esas líneas y rotar la clave.
- La cookie de sesión es el dato que realmente viaja; no se escriben correo/clave en logs.

---

## 10. Verificación

```bash
export JAVA_HOME="$HOME/.gradle/jdks/eclipse_adoptium-21-aarch64-os_x.2/jdk-21.0.7+6/Contents/Home"
./gradlew --no-parallel :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Tests del módulo: `DemoStockRepositoryTest` (filtros, rutas, duplicados, conteos,
gravedad, independencia ruta/local), `StockDemoDataTest` (parseo del asset real) y
`KpiOneClientTest` + `StockBulkSyncTest` (login, catálogo, paginación, 401/HTML,
lotes, QC del 10 %, fallidos y escritura del snapshot).

```bash
# solo el motor de stock
./gradlew --no-parallel :app:testDebugUnitTest \
  --tests 'com.rutamercaderistas.data.stock.*' \
  --tests 'com.rutamercaderistas.data.stock.remote.*'

# release (R8, Hilt, WorkManager)
./gradlew --no-parallel :app:assembleRelease
```
