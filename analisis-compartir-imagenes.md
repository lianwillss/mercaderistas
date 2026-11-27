# Análisis: compartir imágenes de los productos EAN seleccionados

Proyecto: `~/AndroidStudioProjects/mercaderistas` (paquete `com.rutamercaderistas`).
 Rama revisada: `main`, working tree limpio. No se modificó ningún archivo.

---

## 1. ¿Existe el módulo "Código EAN"?

Sí. Pantalla **Buscar EAN / Código de Barras**. Archivos que la conforman:

| Rol | Archivo |
|---|---|
| Pantalla principal (Compose) | `app/src/main/java/com/rutamercaderistas/ui/screens/EanSearchScreen.kt` (1179 líneas) |
| Tarjeta de producto + dibujo del barcode | `app/src/main/java/com/rutamercaderistas/ui/screens/EanProductCard.kt` |
| Filas (encabezados de marca) | `app/src/main/java/com/rutamercaderistas/ui/screens/EanSearchRows.kt` |
| ViewModel | `app/src/main/java/com/rutamercaderistas/viewmodel/EanSearchViewModel.kt` |
| Texto para compartir lista | `app/src/main/java/com/rutamercaderistas/util/EanShareText.kt` |
| Generador de imagen individual | `app/src/main/java/com/rutamercaderistas/util/ShareImageGenerator.kt` |
| Datos | `data/local/EanProductEntity.kt`, `EanProductDao.kt`, `EanRankedSearch.kt`, `EanSkuLookup.kt` |
| Sync/parser del catálogo | `services/EanCatalogSyncer.kt`, `services/EanExcelParser.kt` |

Claves: `EanSearchScreen` (entrada), `EanSearchContent` (contenido Ready), `EanBarcodeZoom`
(diálogo), `EanProductCard` / `EanBarcodeView`, `ShareImageGenerator.generateForEanProduct`,
`buildEanListShareText`.

Precisión sobre el diálogo: **se abre al tocar el código de barras**, no sobre cualquier parte
de la tarjeta. En `EanProductCard.kt:161-172` el `clickable` está puesto sobre `EanBarcodeView`;
la `Card` no tiene `clickable` propio. `onBarcodeClick` sube hasta `zoomProduct`
(`EanSearchScreen.kt:223`) y se renderiza en `EanSearchScreen.kt:229-234`.

---

## 2. "Compartir seleccionados (N)": dónde está y qué hace hoy

`EanSearchScreen.kt:531-573`. El `onClick` (líneas 536-572) arma **texto** y lo comparte con
`ACTION_SEND` / `text/plain`:

```kotlin
AppButton(
    text = stringResource(
        R.string.ean_compartir_seleccionados,
        selectedEans.size,
    ),
    onClick = {
        val selectedProducts = selectedEans
            .mapNotNull { ean ->
                rows.filterIsInstance<EanResultRow.Product>()
                    .firstOrNull { it.product.eanPrincipal == ean }
                    ?.product
            }
        val q = state.query.trim()
        val title = if (q.isBlank()) {
            context.getString(
                R.string.ean_share_catalog_title,
                selectedProducts.size,
            )
        } else {
            context.getString(
                R.string.ean_share_list_title,
                q,
                selectedProducts.size,
            )
        }
        val text = buildEanListShareText(
            title = title,
            products = selectedProducts,
        ) { n ->
            context.getString(R.string.ean_share_more, n)
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(
            Intent.createChooser(
                intent,
                context.getString(R.string.compartir),
            ),
        )
    },
)
```

Hoy: comprime los EAN/SKU en texto vía `buildEanListShareText` (`util/EanShareText.kt`) y los
manda por `EXTRA_TEXT`. No toca imágenes ni `FileProvider`.

Nota: el **botón "Compartir" del modo normal** (sin selección), `EanSearchScreen.kt:470-501`,
hace exactamente lo mismo con todos los productos visibles. El pedido apunta al de selección,
pero conviene saber que hay dos sitios con el mismo patrón de texto.

---

## 3. "Compartir imagen" del diálogo: dónde está y cómo genera la imagen

`EanSearchScreen.kt:1086-1115`:

```kotlin
AppButton(
    text = stringResource(R.string.ean_share_image),
    onClick = {
        try {
            val file = ShareImageGenerator.generateForEanProduct(
                context = context,
                productName = product.descripcionProducto,
                ean = product.eanPrincipal,
            )
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(
                    shareIntent,
                    context.getString(R.string.ean_share_image),
                ),
            )
        } catch (_: Exception) {
            Toast.makeText(context, R.string.ean_share_error, Toast.LENGTH_SHORT).show()
        }
    },
)
```

Generación: `util/ShareImageGenerator.kt:19-69` (`object ShareImageGenerator`). Crea un
`Bitmap` 1080×900, dibuja fondo, tarjeta, título (2 líneas), el barcode (960×300) y los dígitos,
y escribe un PNG en `context.cacheDir/share_images/ean_<timestamp>.png`:

```kotlin
fun generateForEanProduct(
    context: Context,
    productName: String,
    ean: String,
): File {
    val width = 1080
    val height = 900
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val digits = ean.filter(Char::isDigit)
    val barcode = BarcodeEncoder().encodeBitmap(
        digits,
        if (digits.length == 13 && digits[0] != '0') BarcodeFormat.EAN_13 else if (digits.length == 12) BarcodeFormat.EAN_13 else BarcodeFormat.CODE_128,
        960,
        300,
    )
    // ... dibujado ...
    val dir = File(context.cacheDir, "share_images").also { it.mkdirs() }
    val file = File(dir, "ean_${System.currentTimeMillis()}.png")
    file.outputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    }
    barcode.recycle()
    bitmap.recycle()
    return file
}
```

Ojo: el nombre usa `System.currentTimeMillis()`; generadas varias en el mismo milisegundo se
pisan entre sí (ver riesgos).

---

## 4. Cómo se dibuja el código de barras

Librerías: **ZXing core** + **journeyapps zxing-android-embedded** (`app/build.gradle:191-192`,
`libs.zxing.core`, `libs.zxing.android.embedded`). El escáner es `com.journeyapps.barcodescanner.CaptureActivity`
(`AndroidManifest.xml:45-49`).

- En lista/diálogo: `EanBarcodeView` (`EanProductCard.kt:369-423`) codifica con
  `BarcodeEncoder().encodeBitmap(...)` (800×280), con caché `LruCache<String, Bitmap>(64)`
  (`EanProductCard.kt:82`) y `Dispatchers.Default`.
- Elección de formato (`EanProductCard.kt:385`): EAN-13 si son 13 dígitos y no empieza en 0;
  si son 12 dígitos también EAN-13 (el 0 inicial lo agrega el encoder); si es alfanumérico o
  empieza en 0 → `CODE_128`.
- En la imagen compartida (`ShareImageGenerator.kt:30-35`) la lógica es similar pero con un
  matiz distinto: para 12 dígitos usa `EAN_13` con `digits` (sin el 0), y el bitmap va a 960×300.
  No es exactamente la misma regla que `EanBarcodeView`; si se reutiliza, definir una sola fuente
  de verdad para el formato.

No hay dibujo propio de barras: todo pasa por ZXing.

---

## 5. FileProvider configurado

Sí. `app/src/main/AndroidManifest.xml:65-73`:

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths"/>
</provider>
```

`app/src/main/res/xml/file_paths.xml` incluye la carpeta de share images:

```xml
<cache-path name="share_images" path="share_images/"/>
```

`(línea 9)` — coincide con `File(context.cacheDir, "share_images")` que usa
`ShareImageGenerator`. Por tanto **no hay que tocar manifiesto ni paths** para compartir N
imágenes desde ese directorio.

---

## 6. ¿Compose o vistas XML?

**Jetpack Compose** en toda la pantalla: `EanSearchScreen.kt`, `EanProductCard.kt`,
`EanSearchRows.kt` y `StockItemZoom.kt`. No hay layout XML de EAN (no existe
`res/layout/*ean*`). El proyecto sí tiene `res/layout` para otras pantallas, pero esta es 100%
Compose. Diálogo: `IosModal` + `AlertDialog`/`Dialog` de Compose, no `Dialog` de vista.

---

## 7. Plan para que "Compartir seleccionados" comparta imágenes (reuso de lo existente)

1. **Generador múltiple**: añadir en `ShareImageGenerator` una función
   `generateForEanProducts(context, products): List<File>` que recorra los productos y reutilice
   la lógica actual de dibujo. Devuelve una foto por producto, en el mismo orden.
2. **Nombres sin colisión**: usar un sello común + índice (`sello = System.currentTimeMillis()`,
   `"ean_" + sello + "_" + indice + ".png"`), siguiendo el patrón ya probado de
   `StockShareImage.kt:109,368`. Borrar los `ean_*.png` previos del directorio antes de generar
   (hoy se acumulan).
3. **Fuera del hilo principal**: mover la generación a `withContext(Dispatchers.IO)` dentro de un
   `rememberCoroutineScope()` (patrón de `StockScreen.kt:262-273`), con `runCatching` y toast
   `ean_share_error` si falla o la lista queda vacía.
4. **Resolver los productos**: conservar el mapeo actual de `selectedEans` → `EanProductEntity`,
   pero evaluar hacerlo contra una búsqueda por EAN en vez de solo las `rows` visibles (ver riesgos).
5. **URIs por FileProvider**: mapear cada `File` con `FileProvider.getUriForFile(context,
   "${context.packageName}.fileprovider", file)` (authority ya usado y path ya declarado).
6. **Intent**: replicar la rama de `StockScreen.kt:289-308`:
   - 1 elemento → `ACTION_SEND` + `EXTRA_STREAM`.
   - N elementos → `ACTION_SEND_MULTIPLE` + `putParcelableArrayListExtra(EXTRA_STREAM, ...)`,
     más `clipData` con todos los URIs (necesario para el grant en varios targets).
   - `type = "image/png"`, `FLAG_GRANT_READ_URI_PERMISSION`, `Intent.createChooser(...)`.
7. **Memoria**: generar, comprimir y `recycle()` cada bitmap uno por uno; nunca retener los N
   bitmaps (cada uno son ~3.7 MB en 1080×900 ARGB_8888). Solo quedarse con los `File`.
8. **Pruebas**: extraer una función pura (particionado/orden/nombres) testeable y ampliar
   `EanShareTextTest` si corresponde; verificación manual en AVD con 1, pocos y muchos productos
   (y confirmar que eligen bien la app destino).
9. **Opcional**: límite de cantidad por share y limpieza de `share_images` al cerrar la pantalla.

---

## 8. Dudas y riesgos antes de empezar

- **Colisión de nombres**: `ean_${System.currentTimeMillis()}` puede duplicarse al generar varias
  en el mismo milisegundo → se comparten menos imágenes que seleccionadas. Hay que cambiar el
  nombre (requiere tocar `ShareImageGenerator`).
- **`ACTION_SEND_MULTIPLE` y el límite del Binder**: el intent se envía en una transacción de
  ~1 MB. Muchas imágenes PNG grandes (1080×900) pueden lanzar `TransactionTooLargeException` o ser
  rechazadas por WhatsApp/Gmail. Conviene acotar N o achicar la imagen; **no está medido todavía**.
- **Trabajo en hilo principal**: hoy el diálogo genera la imagen directo en el `onClick`. Con N
  productos conviene `Dispatchers.IO`; si no, jank/ANR probable.
- **Productos seleccionados que no están en `rows`**: el mapeo de `selectedEans` se hace contra las
  filas actualmente cargadas en el snapshot paginado (`EanSearchScreen.kt:537-542`). Un producto
  seleccionado en otra página podría quedar afuera y su imagen no llegar, sin aviso. Es un
  comportamiento preexistente (también afecta al texto), pero con imágenes el silencio es peor.
- **Duplicados por EAN repetido**: `selectedEans` es un `Set<String>` de `eanPrincipal`; dos
  productos con el mismo EAN colapsan en uno y el `firstOrNull` devuelve uno solo. Para imágenes
  la cuenta puede diferir de la de texto.
- **Sin limpieza de caché**: `share_images/` crece sin borrado; si se comparte seguido, se acumula.
- **Formato de barcode inconsistente** entre `EanBarcodeView` y `ShareImageGenerator` (caso de 12
  dígitos): si se unifica, verificar que el compartido siga escaneando igual.
- **Alcance del pedido**: asumo "una imagen por producto" reusando la estética actual de
  `generateForEanProduct`. Si preferís **una hoja combinada** (varias tarjetas en una imagen, como
  hace `StockShareImage.generate` por local), el plan cambia (un solo archivo y `ACTION_SEND`).
- **No verificado en dispositivo**: todo lo anterior es lectura de código; no ejecuté la app ni
  probé el share múltiple. Faltaría confirmar en el AVD el comportamiento real del chooser con N
  imágenes.

Lo que **no** encontré: no hay función multi-imagen para EAN hoy, ni strings específicos de
"compartir N imágenes"; `ean_share_error` sí existe y sirve de fallback.
