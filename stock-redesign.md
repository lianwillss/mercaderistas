# Rediseño del módulo STOCK (instrucciones para el agente de código)

Lee este archivo completo y ejecútalo **fase por fase**. Al terminar cada fase, detente para que yo la pruebe.

## Contexto

App Android (Kotlin + Jetpack Compose, Room), paquete `com.rutamercaderistas`.

- Sigue `AGENTS.md` y `docs/modulo-cod-ean.md` como reglas.
- Este trabajo se suma al de "escaneo EAN -> stock". Si ya existe (`ScanOutcome`, local activo global, `resolveActiveLocal`, normalización de EAN), **reutilízalo** y no lo dupliques.
- Skills: usa `compose-expert` solo para buenas prácticas de UI. Al cerrar cada fase corre `jetpack-compose-audit` y resume los hallazgos. Si un skill contradice `AGENTS.md` o este archivo, mandan `AGENTS.md` y este archivo. No refactorices arquitectura ni módulos.

## Objetivo

Rediseñar la pantalla Stock según el boceto aprobado, **sin romper nada de lo que funciona hoy**. Es un cambio de presentación e interacción: no se tocan queries, modelos de datos ni la semántica de filtros, salvo las adiciones listadas aquí.

## Regla de oro: cero regresiones

### Fase 0 (antes de escribir código de UI)

1. Haz un inventario de todo lo que hace hoy el módulo Stock leyendo `StockScreen`, `StockViewModel`, el repositorio de stock y los DAOs. Como mínimo verifica y lista:
   - Filtros **Local, Ruta y Marca** (dropdowns). Documenta si se combinan entre sí y con qué semántica (AND/OR). Esa semántica **no cambia**.
   - Búsqueda Producto/SKU y su comportamiento actual.
   - Modos "Por local" y "Por marca".
   - Pestañas **Estado** y **Cobertura** y lo que muestra cada una (incluida la barra de progreso de cada fila).
   - Tarjetas resumen (Quiebres, Negativos, Disponible) y cómo se calculan.
   - Orden actual (Producto A-Z y otros).
   - Compartir (texto/imagen) y botón Limpiar.
   - Encabezado del local ("N productos · datos al dd-mm-aaaa"), etiqueta DEMO, "Actualizado ayer", "204 locales".
   - Detalle del producto, tránsito, fecha por fila, `eanParaSku` / `EanCatalogSyncer`, `MARCAS_EXCLUIDAS`.
2. Escribe **tests de caracterización** del ViewModel para las combinaciones de filtros existentes (Local, Ruta + Marca, Local + búsqueda, etc.). Deben pasar antes y después de cada fase.
3. Si algo del diseño exige cambiar queries, modelos o la semántica de filtros, **detente y pregúntame**. No lo decidas solo.

## Diseño objetivo

Mantén el tema actual (gradiente morado del encabezado, fondo claro, tarjetas blancas, colores de estado). Usa los tokens del tema existente; solo si no existen, usa:

| Uso | Color |
| --- | --- |
| Gradiente del encabezado | `#3F2A8C` -> `#6D4FD6` |
| Acento | `#5B3FD1` |
| Fondo | `#F4F5FB` |
| Negativo | `#B3261E` |
| Quiebre | `#A84A12` |
| Disponible | `#1E6B34` |

De arriba hacia abajo:

1. **Encabezado**: "Stock" + "Actualizado ayer" (y la etiqueta DEMO / "Datos de ejemplo" si existe). Dentro del encabezado, **barra de búsqueda fija (sticky)** con placeholder "Producto, SKU o EAN" y a su derecha un botón de cámara (48dp) que lanza el escáner del módulo Cód. EAN.
   - La búsqueda acepta SKU, nombre o EAN (escrito o escaneado). Un EAN se normaliza (reusa la lógica del módulo EAN, incluido el dígito verificador) y se resuelve a SKU con el catálogo.
   - Tras escanear un único producto, filtra la lista a ese SKU y muestra el panel de stock del flujo de escaneo ya definido.
   - Nunca inferir un código EAN a partir de un SKU.
2. **Fila de alcance**: control segmentado **Local | Ruta** + su selector desplegable + botón **Filtros** con badge del número de filtros activos.
   - El segmentado solo cambia qué selector se muestra y cuál es el alcance principal. El estado de filtros y las queries siguen siendo los actuales. Si hoy Local y Ruta pueden combinarse, esa capacidad se conserva (por ejemplo, el otro selector queda accesible dentro de Filtros).
   - **Filtros** abre un bottom sheet con Marca y "Agrupar por: Local / Marca" (lo que hoy son los chips Por local / Por marca), con botones Aplicar y Limpiar.
   - **Ruta siempre a un toque**: el flujo "elegir ruta -> ver todos los negativos de la ruta" es el más usado y debe funcionar igual de rápido o más.
3. **Pestañas Estado | Cobertura**: se mantienen, como control segmentado pequeño justo encima de las tarjetas resumen. El boceto no las dibujó, pero **no se eliminan**.
4. **Tarjetas resumen tocables**: Quiebres, Negativos, Disponible (o los equivalentes de la pestaña Cobertura). Tocar una la activa como filtro (anillo de selección + check + semantics `selected`); tocar de nuevo la desactiva. Los contadores reflejan el alcance actual (local o ruta completa) y el resto de filtros.
5. **Fila de orden**: chip de orden con **"Prioridad"** como nuevo valor por defecto (negativos, luego quiebres, luego disponibles; dentro de cada grupo, producto A-Z). Conserva Producto A-Z y los demás órdenes existentes como opciones. **"Limpiar"** pasa a ser un botón de texto que solo aparece si hay algún filtro activo. "Compartir" a la derecha, con el mismo comportamiento actual.
6. **Lista**: cuando el alcance es Ruta (o hay más de un local en el resultado), agrupa por local con `stickyHeader`: pastilla morada "CÓDIGO · NOMBRE DEL LOCAL" + conteo de negativos/quiebres del local. En alcance Local, mantén el encabezado actual del local. Filas con borde izquierdo de 4dp del color del estado, nombre, SKU, stock grande a la derecha y "unid.". No quites información existente (tránsito, fecha, barra de progreso/cobertura) salvo lo indicado en la Fase 4.
7. **Local activo global**: el local activo es un estado compartido con el módulo EAN. Cambiarlo en Stock lo cambia en EAN y viceversa.

## Fases

Detente al terminar cada una.

- **Fase 0**: inventario + tests de caracterización + checklist de regresión.
- **Fase 1**: barra de búsqueda sticky + botón de escaneo + búsqueda por EAN.
- **Fase 2**: fila de alcance Local/Ruta + botón Filtros con bottom sheet y badge. Conserva el estado de filtros al volver a la pantalla y al rotar, y agrega pull-to-refresh.
- **Fase 3**: tarjetas tocables + orden por Prioridad + Limpiar condicional + agrupación por local con encabezados fijos y conteos.
- **Fase 4 (opcional, solo si las anteriores quedaron estables)**: limpieza de filas.
  - Chip de estado solo en excepciones (Quiebre/Negativo), siempre con texto además del color.
  - Tránsito con ícono propio.
  - Un solo botón de compartir.
  - Fecha solo en el encabezado del local.
  - Etiqueta explícita en la barra de progreso, o retirarla si no aporta.

## Reglas técnicas (de AGENTS.md)

- `Regex` como `private val`, nunca dentro de funciones por ítem/fila/tecla.
- No reintroducir FTS5. No perder ceros iniciales (texto, no número).
- No condicionar el `NavHost` a la visibilidad de la barra inferior.
- No subir `EAN_DATA_VERSION` (no se tocan assets). No tocar `MARCAS_EXCLUIDAS` ni `EXCLUDED_BRANDS`.
- Rendimiento: `LazyColumn` con `key` estable por fila, `derivedStateOf` para contadores y filtros, nada de cálculos pesados en composición. Hay hasta 204 locales y cientos de productos por local.
- Accesibilidad: áreas táctiles >= 48dp, `contentDescription` en íconos, el estado nunca se comunica solo con color, contraste >= 4.5:1 en texto.
- Compatible con los modos claro y oscuro del tema actual.

## Pruebas

- Tests de caracterización de la Fase 0 pasando antes y después de cada fase.
- Tests del ViewModel: activar/desactivar tarjeta, orden por Prioridad, Limpiar visible solo con filtros activos, conteos por local en alcance Ruta, búsqueda por EAN (UPC-A de 12 dígitos, ceros iniciales, dígito inválido, EAN no encontrado).
- Previews de Compose de los estados: vacío, cargando, con filtros, alcance Ruta, error.

## Entrega

Al terminar cada fase, lista:

1. Archivos modificados.
2. Qué probar en el teléfono.
3. Cualquier riesgo de regresión detectado.

Si encuentras una función existente que el boceto no contempla, **consérvala y pregúntame dónde ubicarla**.
