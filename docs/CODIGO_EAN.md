# Módulo Cód. EAN (mercaderistas)

Cómo funciona la pantalla **Buscar EAN / Código de Barras**: de los Excel de marcas en
`assets/` a Room, de Room a la lista paginada, y el cruce con el detalle de Stock.
Todo lo acá escrito está tomado del código (`AGENTS.md` § Catálogo EAN manda como regla).

Rutas base: `app/src/main/java/com/rutamercaderistas/`

## 1. Entrada y navegación

- Ruta `EanSearchRoute` (`ui/navigation/Routes.kt:21`); se entra desde la barra/riel con
  `BottomBarKey.EAN` (`ui/screens/MainScreen.kt:90`) y se monta en `MainScreen.kt:326`.
- En esa ruta la barra/riel flotante se oculta con `AnimatedVisibility` (`MainScreen.kt:152`);
  **no** condicionar el `NavHost` a la visibilidad de la barra.
- Pantalla: `ui/screens/EanSearchScreen.kt:119` (`EanSearchScreen`), con escaneo por ZXing
  `IntentIntegrator` (launcher `:133`, `launchScanner` `:144`, resultado → `onBarcodeScanned`).
- Título: `Buscar EAN / Código de Barras` (`res/values/strings.xml:506`).

## 2. Datos: assets → Room

- Fuente: todos los `ean*.xlsx` de `app/src/main/assets/` (hoy **38** archivos). El import
  los combina automáticamente; el nombre del archivo define la marca por defecto.
- Versión de datos: `EAN_DATA_VERSION = 54` (`services/EanExcelParser.kt:20`). Subirla para
  forzar reimportación en instalaciones existentes.
- Parser: `services/EanExcelParser.kt`
  - `loadFromAssets()` `:494` (lista assets ordenados, parsea cada uno, deduplica y guarda);
    corre en `Dispatchers.IO` dentro de un `Mutex` (`withImportLock` `:149`) porque POI/XML
    es CPU puro y en el hilo principal congelaba la app.
  - `selectSheet()` `:312`: elige la primera hoja cuyo encabezado declare EAN o Código de Barra
    (algunos catálogos traen una hoja resumen).
  - `buildColumnMap()` `:322`: mapeo **por nombre de encabezado**, no por posición. Salta
    columnas de imagen (`imagen`/`image`/`foto`/`logo`): si se mapearan taparían el EAN vecino.
  - `parseRow()` `:390`: `marca` sale de la columna o de `brandFromFilename()` `:109`; se aplica
    `BRAND_ALIASES`; se descarta la fila si la marca normalizada está en `EXCLUDED_BRANDS` `:90`;
    el EAN se toma de `Código de Barra` si es numérico, si no de la columna EAN `:428-429`;
    UPC-A de 12 dígitos → EAN-13 con `0` inicial `:433`; EAN que no sea 8–14 dígitos se limpia
    en silencio `:435`. Los campos `_norm`/`_nospace` se generan acá.
  - Dedupe `dedupeEanProducts()` `:201`: clave `eanPrincipal`, fallback `codCencosud`; gana la
    fila más completa (`eanCompleteness` `:254`) y fusiona campos no vacíos (`mergeProducts` `:222`).
    Un EAN repetido en **otro SKU** fusiona filas: es el caso que hay que auditar antes de agregar
    un asset (`scripts/scan_ean_collisions.py`).
  - Normalización: `normalizeSearch()` `:127` (NFD + quita diacríticos + lower) y
    `compactNorm()` `:135` (además quita espacios/signos). Los `Regex` son `private val`:
    nada de compilar patrones por fila o por tecla.
- Marca visible cuando el Excel no trae columna Marca: `EAN_FILE_BRANDS` `:32` (slug → nombre
  exacto; sin entrada queda solo capitalizada). Alias de submarcas/proveedor: `BRAND_ALIASES` `:74`
  (JUMEX/SUPERZINGS → CASO Y CIA, B FRESH/B.TAN → BWILD, etc.).
- Tabla `ean_products` (`data/local/EanProductEntity.kt:22`) con índices en `eanPrincipal`,
  `codCencosud`, `codProveedor`, `codigoBarra`, `marca`, `descripcion_norm`, `marca_norm` y las
  variantes `_nospace`.

## 3. Importación y sincronización

Dos caminos que aplican la misma decisión (mismo criterio, mismo resultado):

- Al abrir la pantalla: `EanSearchViewModel.loadDatabase()` `:81` — si hay caché, muestra la lista
  al instante y la importación corre en segundo plano.
- Sin abrir la pantalla: `EanCatalogSyncer.ensure()` `services/EanCatalogSyncer.kt:34`, usado por el
  detalle de Stock.

Criterio de reimportación (idéntico en ambos): `count() == 0` O `storedVersion < EAN_DATA_VERSION`
O hash de assets distinto O hay filas sin normalizar (`hasUnnormalized()`). Tras importar se guardan
versión y hash en `SharedPreferences("ean_catalog_prefs")` (`EanExcelParser.kt:550-562`).

- Hash de assets: `computeAssetsHash()` `:564` (SHA-256 de nombres + tamaño/1er KB).
- Forzar reimportación manual: `forceReload()` `:277`.

## 4. Consulta: todo en SQLite

`data/local/EanRankedSearch.kt` construye el SQL; el ViewModel solo lo ejecuta. Resultados,
facetas y total comparten el **mismo WHERE**, así que lo visto, lo filtrado y lo contado no
divergen.

- Tokens: `eanTokens()` `:42` → `compactNorm` por token (sin comodines, seguro en LIKE).
- WHERE: `whereClause()` `:53` — un grupo OR por token sobre 10 columnas + 4 variantes `ltrim(...,'0')`
  (para que `0658…` y `658…` coincidan); `AND` entre tokens; filtros exactos de `marca`/`conversion`.
- Ranking `buildRankedSearch()` `:92` — `ORDER BY score DESC, marca, descripcionProducto, id ASC`.
  Tiers: EAN exacto 300000 · prefijo 20000 · contiene 1000 · SKU exacto 200 · marca exacta 20 ·
  golpes de marca 2/1/0. El desempate por `id` es obligatorio (Room pagina con LIMIT/OFFSET).
- Sin texto: `buildBrowseSearch()` `:159` (marca/descripción; “Sin marca” al final).
- Facetas `buildBrandFacets()` `:172` / `buildCajaFacets()` `:186`: cuentan el resultado actual
  **sin** aplicar su propio filtro (pero sí el otro).
- Total `buildCountSearch()` `:197` (total real, no lo ya paginado).

DAO (`data/local/EanProductDao.kt`): `rankedSearch`/`browseSearch` devuelven `PagingSource`
(`@RawQuery(observedEntities = [EanProductEntity::class])`, se invalidan solos ante cambios),
`brandFacets`, `cajaFacets`, `countForQuery`, y `eanPorSkuFlow(sku)` `:58` para Stock
(prefiere `eanPrincipal`, cae a `codigoBarra`; coincide por SKU exacto o ignorando ceros).

Paging en el ViewModel `refreshResults()` `:155`: `Pager(pageSize = 50, maxSize = 200)` con
**un `PagingSource` nuevo por llamada al factory**, `cachedIn(viewModelScope)`. Debounce de
búsqueda 250 ms (`onQueryChange` `:205`); mientras corre, `totalCount = null` para no mostrar
“0 resultados” con el total anterior.

## 5. Interfaz

`EanSearchContent` `:328`:

- Barra de búsqueda con botón de escaneo y acceso a “Modo Impresión de Flejes” (`EanSearchBar` `:740`).
- Chips de marca/caja (`EanFilterChips` `:908`) solo si hay más de una marca o alguna caja;
  tocar el chip activo lo quita. Estados vacíos diferenciados: catálogo vacío, filtro sin
  coincidencias y búsqueda sin resultados (`:368-384`).
- Filas `buildEanRows()` `ui/screens/EanSearchRows.kt:22`: inserta encabezados de marca **solo**
  al navegar el catálogo; con texto de búsqueda la lista va plana en el orden del ranking.
- Tarjeta `ui/screens/EanProductCard.kt:103`: descripción con highlight, código de barras
  (`EanBarcodeView`, clicable → zoom), chips EAN/SKU/CAJA/Prov, marca y estado, categorías.
- Zoom `EanBarcodeZoom` `:1102`: modal con el código grande, copiar EAN y compartir imagen.
- Modo Flejes `EanFlejesDialog` `:1198`: muestra `img_flejes_barcode.png` con brillo al máximo
  (EAN-13 `6969696969692`, JUMBO fuera de las barras: texto/logo sobre las barras lo ilegibiliza).
- Compartir: lista visible como texto (`buildEanListShareText` `util/EanShareText.kt:17`, máximo
  200 ítems), o selección en lotes con checkbox → imagen (`ShareImageGenerator.generateForEanProducts`)
  o copiar EANs (`:528-660`). Historial de búsquedas en `PreferencesRepository`.
- Estado del catálogo en el pie: `Catálogo v<versión> · <N> productos` (`catalogMeta`).

## 6. Cruce con Stock

`StockViewModel.eanParaSku(sku)` `:847`: emite `Cargando`, llama `eanSyncer.ensure()` y observa
`eanPorSkuFlow` (Flow): si el catálogo se reimporta, el detalle abierto recibe el código nuevo solo.
El stock **no** depende de haber abierto Cód. EAN. Si el SKU no está catalogado, no hay código y
la imagen compartida va solo con SKU + stock: nunca inferir un código a partir del SKU.

## 7. Pruebas relacionadas

- `app/src/test/.../services/EanCodigosParserTest.kt`, `services/EanDedupeTest.kt`,
  `viewmodel/EanSearchViewModelTest.kt`, `ui/screens/EanSearchRowsTest.kt`, `util/EanShareTextTest.kt`,
  `data/local/EanRankedSearchDeviceTest.kt` (androidTest).
- Al tocar un asset/marca: assert de `brandFromFilename("ean_<slug>.xlsx")` y test de importación
  del asset; subir `EAN_DATA_VERSION`; recuento final cuadrado con lo que muestra la app
  (filas − EAN repetidos − marcas excluidas).

## 8. Invariantes a respetar

- Agregar products = nuevo `ean*.xlsx` + (si no trae Marca) entrada en `EAN_FILE_BRANDS` +
  subir `EAN_DATA_VERSION` + test. Sin la subida de versión no llega a instalados.
- Bajas de marca: nunca editar el Excel fuente; `EXCLUDED_BRANDS` (EAN) + `MARCAS_EXCLUIDAS`
  (stock) + generador + manual, y subir `EAN_DATA_VERSION`.
- No reintroducir FTS5: el catálogo ronda los cientos de productos y el índice solo encarecía
  cada importación.
- No perder ceros iniciales de códigos (texto, no número) y validar el dígito verificador EAN-13.
- Nada de `Regex(...)` dentro de funciones por ítem/fila/tecla: declararlos `private val`.
