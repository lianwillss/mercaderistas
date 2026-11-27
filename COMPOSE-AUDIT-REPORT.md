# Jetpack Compose Audit Report

Target: /Users/will/AndroidStudioProjects/mercaderistas
Date: 2026-11-22
Scope: módulo Stock (`ui/screens/stock/**`, `viewmodel/StockViewModel.kt`, `data/stock/**`),
 más los componentes compartidos tocados (`ui/components/BarcodeScannerLauncher.kt`) y la
pantalla Cód. EAN por el refactor del escáner.
Excluded from scoring: `src/test/**`, `src/androidTest/**`, `build/**`.
Confidence: Medium (módulo, no repositorio completo).
Overall Score: 85/100

> Re-evaluado tras aplicar los tres arreglos pedidos: `contentType` en la lista,
> `descripcion` memoizada con `remember`, y `modifier` primero + strings
> migrados en `StockSearchBar` / segmentado / sheet. Performance 8→9 y
> Composable API 7→8.

## Scorecard

| Category | Score | Weight | Status | Notes |
|----------|-------|--------|--------|-------|
| Performance | 9/10 | 35% | solid | Skipping fuerte medido; `contentType` y `descripcion` corregidos |
| State management | 8/10 | 25% | solid | UDF clara; el segmentado deriva del filtro |
| Side effects | 9/10 | 20% | solid | Efectos con keys correctas y `rememberUpdatedState` |
| Composable API quality | 8/10 | 20% | solid | `modifier` primero y strings migrados en lo nuevo |

## Critical Findings

> Estado: los tres se aplicaron (ver "Resueltos" al pie de cada uno). Queda deuda
> preexistente fuera de este rediseño (strings literales en el resto del archivo,
> `Color.White` del botón de escaneo heredado de Cód. EAN).

1. **Performance: la lista heterogénea no declara `contentType`**
   - Why it matters: `LazyColumn` mezcla `FilaStock.Cabecera` y `FilaStock.Producto`; sin
     `contentType`, Compose no reutiliza la composición entre tipos y recompone al cruzar
     el borde. Es la lista caliente (hasta 204 locales × cientos de productos).
   - Evidence: `app/src/main/java/com/rutamercaderistas/ui/screens/stock/StockScreen.kt`
     (bloque `items(filas, key = { it.key })`).
   - Fix direction: `items(filas, key = { it.key }, contentType = { it::class.simpleName })`.
   - Resuelto: `contentType = { "cabecera" }` / `{ "producto" }` en cada `item` y `stickyHeader`.
   - References: <https://developer.android.com/develop/ui/compose/lists>

2. **Performance: la descripción de accesibilidad se reconstruye en cada recomposición**
   - Why it matters: `StockItemCard` arma un `buildString` con 8-10 campos por fila, en el
     cuerpo del composable, y se usa sólo para `contentDescription`.
   - Evidence: `app/src/main/java/com/rutamercaderistas/ui/screens/stock/StockScreen.kt`
     (`StockItemCard`, `val descripcion = buildString { ... }`).
   - Fix direction: calcularla en `construirFilas` (una vez por cambio de resultado) y
     llevarla en `FilaStock.Producto`.
   - Resuelto: `remember(item, mostrarLocal, mostrarSinOsa) { buildString { ... } }`.
   - References: <https://developer.android.com/develop/ui/compose/performance/bestpractices>

3. **Composable API: la nueva barra de búsqueda recibe `modifier` al final**
   - Why it matters: la guía de componentes pide `modifier` como primer parámetro
     opcional; al ir último, el llamado no puede encadenarlo antes de los suyos.
   - Evidence: `app/src/main/java/com/rutamercaderistas/ui/screens/stock/StockScreen.kt`
     (`StockSearchBar`).
   - Fix direction: `modifier: Modifier = Modifier` antes de `value`.
   - Resuelto: `modifier` es el primer parámetro.
   - References: <https://android.googlesource.com/platform/frameworks/support/+/androidx-main/compose/docs/compose-component-api-guidelines.md>

4. **Composable API: strings hardcodeados en los composables nuevos**
   - Why it matters: rompe i18n y hace frágiles los tests.
   - Evidence: `StockSearchBar` (`"Producto, SKU o EAN"`), `SegmentoAlcance` (`"Local"`,
     `"Ruta"`), `StockFiltrosBoton` (`"Filtros"`), `StockFiltrosSheet` (`"Agrupar por"`).
   - Fix direction: `stringResource(R.string.*)`; el archivo ya tenía el mismo patrón, así
     que esto es deuda preexistente que el rediseño siguió.
   - Resuelto en lo nuevo: `stock_buscar_placeholder`, `stock_filtros`,
     `stock_agrupar_por`, `stock_segmento_local`, `stock_segmento_ruta`,
     `stock_por_local`, `stock_por_marca`.
   - References: <https://developer.android.com/develop/ui/compose/resources>

## Adjacent Findings

### Android Launch UX

- Android 12+ splash icon status: not configured.
- Evidence: no hay `windowSplashScreenAnimatedIcon` en `res/values*/themes.xml` ni
  `res/drawable-v31/`.
- Finding: ninguno.
- References: <https://developer.android.com/develop/ui/views/launch/splash-screen>

## Category Details

### Performance — 8/10

**Ceiling check**

- Strong Skipping: on (`featureFlags.StrongSkipping = true`, Kotlin 2.4.20).
- Ceiling table applied: SSM-on.
- Module-wide `skippable%`: 74/123 = 60.2% (lo anclan lambdas de cero argumentos).
- Named-only `skippable%`: 25/25 = 100% (`app/build/compose_audit/app-composables.csv`,
  filas con `isLambda == 0`).
- Unstable shared types from compiler: 1 (`StockViewModel`, no es parámetro de composable).
- SSM-on binding evidence: none observed (sin `listOf`/`mapOf` en cuerpos calientes, sin
  `@NonSkippableComposable` / `@DontMemoize`, sin `equals()` costoso).
- Qualitative score: 8/10
- Ceiling: none
- Applied score: 8/10

**What is working**

- `remember(state.result.items, filters.modo) { construirFilas(...) }` y `remember` para
  `maxStock` / `marcasVisibles`.
- `key = { it.key }` estables en `items` (con desambiguación `#n` en `construirFilas`).
- `Modifier.animateItem()` en lugar del deprecated `animateItemPlacement()`.
- `StockFilters`, `StockItem`, `StockCatalog`, `OsaRespuesta` inferidos estables.
- Movimientos pesados fuera del hilo principal (`catalog()` en `Dispatchers.Default`).

**What is hurting the score**

- Lista heterogénea sin `contentType` (finding 1).
- `descripcion` de la tarjeta reconstruida por recomposición (finding 2).
- `Regex` como `private val` ya cumplido en `data/stock`; sin hallazgos nuevos.

**Animation performance signals**

- Status: clean. Los infinitos (`PuntoActivo`, `TarjetaQuiebres`, `TarjetaNegativos`,
  `CabeceraSeccion`) viven en tarjetas/lazy items que se desmontan al salir de pantalla y
  respetan `LocalReducedMotionEnabled`.

**Paging list signals**

- Status: not present en el módulo auditado.

**Evidence**

- `ui/screens/stock/StockScreen.kt` — `items` sin `contentType` · References: <https://developer.android.com/develop/ui/compose/lists>
- `ui/screens/stock/StockScreen.kt` — `descripcion` en cuerpo de `StockItemCard` · References: <https://developer.android.com/develop/ui/compose/performance/bestpractices>
- `viewmodel/StockViewModel.kt` — `catalog()` y búsquedas en `Dispatchers.Default/IO` · References: <https://developer.android.com/develop/ui/compose/performance/bestpractices>

### State Management — 8/10

**What is working**

- `StockViewModel` como única fuente de verdad; `filters` y `state` son `StateFlow`.
- `collectAsStateWithLifecycle()` en la pantalla.
- `verCobertura` en `rememberSaveable`; el nuevo segmentado `ultimoRuta` también.
- `StockSearchBar` y `SegmentoAlcance` son stateless; el estado vive en el VM/filtros.
- El borrador del sheet (`marca`, `modoNombre`) es `rememberSaveable`.

**What is hurting the score**

- El segmentado era un segundo origen de verdad para el alcance; ahora deriva de
  `filters.local/ruta` y sólo recuerda la última opción cuando no hay alcance (corregido
  en esta fase).
- El borrador del sheet queda congelado si los filtros cambian desde fuera mientras está
  abierto (caso raro: no hay otra vía de cambio con el sheet abierto).

**Paging load-state signals**

- Status: not present.

**Evidence**

- `ui/screens/stock/StockScreen.kt` — `ultimoRuta` + derivación desde filtros · References: <https://developer.android.com/develop/ui/compose/state-hoisting>
- `viewmodel/StockViewModel.kt` — `_filters` / `_state` como `MutableStateFlow` · References: <https://developer.android.com/develop/ui/compose/architecture>

### Side Effects — 9/10

**What is working**

- `LifecycleEventEffect(ON_RESUME) { vm.alVolver() }`.
- `LaunchedEffect(filters.ruta, marcasRuta)` para limpiar la marca fuera de ruta.
- `rememberBarcodeScannerLauncher` usa `rememberUpdatedState` para no capturar un lambda
  obsoleto (hallazgo previo del módulo EAN corregido).
- Compartir se dispara desde un click con `rememberCoroutineScope().launch` y IO en
  `withContext(Dispatchers.IO)`.
- `resolverEan` corre en `Dispatchers.IO` después del debounce de 300 ms.

**What is hurting the score**

- `eanSyncer.ensure()` se invoca en cada consulta con EAN válido; el mutex interno lo
  hace barato tras la primera vez, pero sigue siendo trabajo extra por tecla. Aceptable.

**Animation side-effect signals**

- Status: clean.

**Paging side-effect signals**

- Status: not present.

**Evidence**

- `ui/components/BarcodeScannerLauncher.kt` — `rememberUpdatedState` · References: <https://developer.android.com/develop/ui/compose/side-effects>
- `ui/screens/stock/StockScreen.kt` — `LifecycleEventEffect` y `LaunchedEffect` con keys · References: <https://developer.android.com/develop/ui/compose/side-effects>

### Composable API Quality — 7/10

**What is working**

- Los composables nuevos reciben `modifier: Modifier = Modifier`.
- Nombres PascalCase, retornan `Unit`, sin `MutableState` en las firmas.
- `StockFiltrosBoton` y `SegmentoAlcance` son componentes chicos y de una sola responsabilidad.

**What is hurting the score**

- `modifier` como último parámetro opcional en `StockSearchBar` (finding 3).
- Strings hardcodeados (finding 4) y `Color.White` en el botón de escaneo (copiado de
  Cód. EAN). El archivo ya tenía strings hardcodeados antes del rediseño.
- Deuda preexistente: `diasDesde` y `resumenTexto` son privados sin uso.

**Evidence**

- `ui/screens/stock/StockScreen.kt` — `StockSearchBar` · References: <https://android.googlesource.com/platform/frameworks/support/+/androidx-main/compose/docs/compose-component-api-guidelines.md>
- `ui/screens/stock/StockScreen.kt` — strings literales · References: <https://developer.android.com/develop/ui/compose/resources>

## Prioritized Fixes

1. Agregar `contentType = { it::class.simpleName }` a `items(filas, ...)` en `StockScreen`.
2. Mover el `buildString` de `descripcion` de `StockItemCard` a `construirFilas`.
3. Poner `modifier` como primer parámetro opuesto de `StockSearchBar` y migrar los strings
   nuevos a `strings.xml` (con `stringResource`).
4. Borrar `diasDesde` y `resumenTexto` si se confirma que no se usan.

## Notes And Limits

- Auditado el módulo Stock + los componentes compartidos tocados; no el repositorio completo.
- Confianza Media: un módulo, con reportes del compilador del módulo `:app`.
- Coverage notes: no se observaron UI tests de Compose para el módulo; no hay screenshot
  tests; no hay superficie KMP/CMP.
- Android Launch UX resources: not configured.
- Strong Skipping mode: on (Kotlin 2.4.20).
- Weight choice: default 35/25/20/20.
- Renormalization: ninguna categoría N/A.
- Compiler diagnostics used: yes — `app/build/compose_audit/` (init script de la skill);
  `app-module.json`, `app-composables.csv`, `app-classes.txt`. Tabla SSM-on aplicada.

## Fase 3 — notas del cierre

- `StockSort.PRIORIDAD` agrega una rama nueva en `ordenarStock`; no cambia las
  demás ramas ni el contrato de `StockFilters` (sólo el default).
- `construirFilas` pasó a `internal` con un campo nuevo `pegajosa` en la
  cabecera; `alcancesMultiples` deriva de la ruta o de `uniqueLocales > 1`.
- La lista ahora emite `stickyHeader` por cabecera pegajosa. `Modifier.animateItem()`
  sólo está disponible dentro de `LazyItemScope`, así que se calcula dentro de cada
  `item {}` (no en el `forEach`).
- Hallazgo nuevo: los encabezados pegajosos quedan compuestos mientras se scrollea,
  así que el halo pulsante de `CabeceraSeccion` sigue corriendo (gateado por
  `LocalReducedMotionEnabled`). Menor.
- Los tres hallazgos críticos de Fase 2 siguen abiertos.

## Fase 4 — notas del cierre

- `StockItemCard` perdió `mostrarFecha` (la fecha vive sólo en el encabezado del
  local) y `CabeceraSeccion` perdió su botón de compartir: queda uno solo, en la
  fila de orden.
- `BadgeEstado` se oculta cuando el estado es `Disponible`; el estado sigue visible
  como etiqueta de la barra de progreso.
- Se agregó el ícono de tránsito (`LocalShipping`) y la etiqueta de estado junto a la
  barra.
- Pendiente de las Fases 2: `contentType` en la lista, `descripcion` fuera de la
  composición, `modifier`/strings en `StockSearchBar`.

## Suggested Follow-Up

- `material-3`: el rediseño de Stock toca tokens y contraste; conviene un pase de diseño.
- `compose-agent focus on testing`: faltan Previews y UI tests de los estados nuevos.
- `compose-agent focus on animation`: no crítico; los infinitos están gateados.
