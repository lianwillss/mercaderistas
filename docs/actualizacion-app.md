# Cómo funciona la actualización de la app (GitHub Releases)

Mercaderistas se actualiza desde los releases públicos de GitHub
(`lianwillss/mercaderistas`). Todo el flujo vive **dentro de la app**:
no hay notificación de actualización ni diálogo en paralelo. El único
estado visible es `UpdateViewModel.card` (`StateFlow`), que consume la
tarjeta de actualización en pantalla.

## Resumen de fases

```
Disponible → Descargando (%) → Lista para instalar → Instalando → Error
```

- **Disponible**: se detectó una versión nueva en GitHub y pasó todas las
  verificaciones de integridad.
- **Descargando (%)**: el APK se baja en el proceso de la app (sin
  WorkManager, sin notificación), con porcentaje, velocidad y tiempo
  estimado. Es cancelable desde la tarjeta.
- **Lista para instalar**: el APK ya está validado; espera a que el
  usuario toque "Instalar" y a que la app pase a segundo plano o se
  confirme la instalación de Android.
- **Instalando**: sesión de `PackageInstaller` abierta esperando la
  confirmación estándar del sistema.
- **Error**: mensaje en español con opción de reintentar (o ocultar, si el
  error es permanente del release).

---

## 1. Dónde se consulta la nueva versión

Fuente única:

```
https://api.github.com/repos/lianwillss/mercaderistas/releases/latest
```

- Consulta HTTPS con `User-Agent: Mercaderistas-Android`, timeouts de
  15 s de conexión y 30 s de lectura.
- Lee `tag_name` (ej. `v12.52`) y el array de `assets` del release.
- Selecciona el APK: prefiere el de la ABI del teléfono
  (`app-arm64-v8a-release.apk` o `app-armeabi-v7a-release.apk`, ~50 MB)
  y cae a `app-universal-release.apk` (~99 MB) si el release no trae el
  específico.
- Obtiene el SHA-256 de `SHA256SUMS.txt` (máx. 64 KiB, publicado por el
  CI); si falta, usa el `digest` que publica GitHub en el asset.
- **Sin tamaño ni SHA-256 publicados, el release no se ofrece.**

Código: `services/UpdateChecker.kt`, `services/ApkVerification.kt`.

---

## 2. Comparación de versiones (nunca por texto)

Está en `services/AppVersion.kt`:

1. El tag se parsea por segmentos numéricos: `v12.52` → `[12, 52]`.
   Nunca se compara como texto ni como decimal (así `12.51 > 12.9` porque
   51 > 9).
2. Se compara con `PackageInfo.versionName` de la app instalada
   (`InstalledVersion.read`, leído de `PackageManager`; cae a
   `BuildConfig` solo si el sistema no responde).
3. **Versión idéntica a la instalada → no hay actualización**, aunque el
   `versionCode` difiera. Ese tag nunca se vuelve a ofrecer.
4. El `versionCode` del release se deriva del tag:
   `major * 1000 + minor`, y debe ser **estrictamente mayor** que el
   instalado.
5. Un tag ya rechazado (por traer un APK que no es más nuevo) se omite
   (`rejected_update_tag`).

Esto rompe el bucle del incidente **v12.51** (ver §9).

---

## 3. Integridad antes de descargar y antes de instalar

`ApkDownloader.validateApk` comprueba, en orden:

1. **Tamaño real** del asset publicado por GitHub (`assets[].size`). Sin
   tamaño no se ofrece instalar (no hay forma de detectar un archivo
   truncado).
2. **SHA-256** del CI (`SHA256SUMS.txt`, fallback `assets[].digest`).
3. **Paquete**: `com.rutamercaderistas`, igual al de la app instalada.
4. **Versión anunciada**: el APK debe tener exactamente el `versionName`
   y el `versionCode` del release (evita el caso v12.51 compilado como
   12.50).
5. **Más nuevo que lo instalado**: nombre y `versionCode` mayores.
6. **Misma firma**: SHA-256 de los certificados del APK igual a la de la
   app instalada (no se acepta firma distinta).

Si falla, se borra el archivo y el tag se guarda en
`rejected_update_tag`: **no se vuelve a ofrecer ese release**, con lo que
se evita descargar en bucle.

---

## 4. Descarga del APK

- Ubicación: `filesDir/updates/update.apk.part` → `update.apk` al terminar.
- Es **reanudable** con `Range` mientras la identidad del `.part` coincida
  (guardada en `update.apk.part.meta`: URL, tamaño, SHA-256, versión).
  Si el release cambió, el `.part` viejo se descarta y se empieza de cero.
- Se verifica espacio libre: aproximadamente **2× el tamaño del APK +
  20 MB de margen**, porque Android duplica el APK al instalarlo.
- Progreso en la tarjeta: porcentaje, velocidad (MB/s) y ETA
  (segundos/minutos/horas).
- **Cancelable desde la tarjeta**: al cancelar se borra el `.part` y la
  tarjeta vuelve a "Disponible".
- No usa WorkManager ni notificaciones: el progreso solo se ve dentro de
  la app.

Código: `services/ApkDownloader.kt` (`download`, `validateApk`).

---

## 5. Instalación (solo acción del usuario)

La descarga es automática; la instalación **nunca arranca sola**:

- Al 100 % el APK queda verificado y la tarjeta muestra **"Lista para
  instalar"**. La instalación se inicia **solo** tocando **"Instalar"** o
  **"Reintentar"**, con la app en primer plano.
- Usa **`PackageInstaller` + sesión** (`commit` con un `PendingIntent` de
  `UpdateInstallReceiver`), no un `Intent ACTION_VIEW` genérico.
- La confirmación es la **estándar del sistema** (`USER_ACTION_REQUIRED`
  por defecto). No se usa `USER_ACTION_NOT_REQUIRED`: MIUI/HyperOS y
  Play Protect no lo respetan y dejan notificaciones pendientes.
- `UpdateInstallReceiver` maneja:
  - `STATUS_PENDING_USER_ACTION`: abre el intent de confirmación que
    devuelve el sistema.
  - `STATUS_SUCCESS`: instalación completada.
  - otros estados: error traducido al español
    (`UpdateInstallErrors.kt`).
- `UpdateInstallBus` conecta el receiver con el ViewModel y lleva el ID de
  la sesión: un resultado atrasado de una sesión abandonada se descarta.
- El estado visible (`UpdateState`) se persiste en DataStore
  (`update_state_json`): sobrevive a la muerte del proceso durante la
  instalación.

Código: `services/UpdateInstallReceiver.kt`,
`services/UpdateInstallBus.kt`, `viewmodel/UpdateViewModel.kt` (`installApk`).

### Sesiones pendientes al arrancar

- El ID de sesión (`install_session_id`) se persiste. Al iniciar, si hay
  una sesión activa de PackageInstaller, la tarjeta muestra
  "Instalación pendiente" con Reintentar/Cancelar y **no se crea una
  segunda sesión**.
- `restoreState` lee `update_state_json`; si no existe (instalación previa a
  v12.73), reconstruye desde `pending_update` + `install_session_id`, así
  que tampoco borra el APK ni re-descarga.
- `installApk` abandona sesiones viejas antes de crear una nueva.
- Un **arranque en frío nunca instala solo**: sólo reconstruye el estado
  guardado.

---

## 6. Cuándo se consulta

| Momento | Qué hace |
|---|---|
| **Al abrir la app** (`MainActivity` → `checkForUpdateOnLaunch`) | Consulta forzada a GitHub; si hay versión nueva, **descarga automáticamente dentro de la app**. |
| **Al volver a la app** (`checkForUpdateIfStale`) | Consulta con throttle de **15 min** (`UPDATE_CHECK_MIN_INTERVAL_MS`) para no golpear GitHub. |
| **Worker periódico** (`UpdateCheckWorker`, cada 4 h) | Chequeo silencioso en segundo plano: solo deja el pendiente persistido para que la próxima apertura muestre la tarjeta. No descarga ni notifica. |
| **Manual** | Desde la tarjeta de actualización. |

---

## 7. Después de instalar

1. Al completarse la instalación, **el sistema mata el proceso** de la app.
2. `UpdatePackageReplacedReceiver` recibe `ACTION_MY_PACKAGE_REPLACED` e
   intenta reabrir `MainActivity` (best-effort: Android 10+ puede bloquear
   el arranque desde background; en MIUI/HyperOS depende de Autostart).
   **No hay notificaciones**: si el intento falla, la próxima apertura
   manual sigue mostrando el aviso.
3. En el siguiente arranque se compara `versionCode`; si cambió, se limpia
   el pendiente y se muestra **"Actualizado a X" una sola vez**
   (`pending_upgrade_notice` en `MercaderistasApp`; `MainActivity` lo
   muestra y, solo ese arranque, restaura `last_main_tab`).
4. Si la instalación falla, la tarjeta pasa a Error y el usuario puede
   reintentar o posponer (`suppressUntilTomorrow`, 24 h).

---

## 8. Incidentes conocidos

### v12.51: bucle de "actualización disponible"

El tag `v12.51` se publicó compilado como `versionCode 12050` /
`versionName 12.50`, igual que lo que ya tenían instalado los usuarios.
La app seguía ofreciendo "12.51" y rechazaba el APK por no ser más nuevo:
bucle infinito.

**Corrección aplicada:**
- El CI (`.github/workflows/release.yml`) ahora falla si el tag no
  coincide con `versionName` y `versionCode` — tanto en `app/build.gradle`
  como en el APK real (verificado con `aapt2`).
- `v12.52` (12052) corrigió el flujo; a partir de ahí cada release sube el
  minor (`v12.53`, `v12.54`, …).

**Antes de diagnosticar un bucle, confirmar `versionCode` y `versionName`**
instalados (`InstalledVersion.read`); no asumir versiones.

---

## 9. Release y versionado

- La versión oficial está en `app/build.gradle`: `versionCode` /
  `versionName`. Cada release debe subir ambos y usar un tag `v*`
  (sin parche).
- `./gradlew :app:bumpVersion` sube el minor y valida
  `versionCode == major * 1000 + minor` (falla si hay mismatch o si es
  `X.Y.Z`). **No commitea ni taguea.**
- `.github/workflows/release.yml` corre solo al pushear un tag `v*`:
  1. Verifica que el tag coincida con `versionName` y `versionCode` del
     Gradle.
  2. Compila los APK de release (arm64-v8a, armeabi-v7a, universal).
  3. Verifica que el **APK real** coincida con el tag (`aapt2 dump badging`).
  4. Genera `SHA256SUMS.txt` (la app verifica ese hash antes de instalar).
  5. Publica el release con los 3 APK + `SHA256SUMS.txt`
     (`softprops/action-gh-release`, `overwrite_files`).
- Firma local: `keystore.properties` (gitignored). CI:
  `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`,
  `KEYSTORE_PATH`.

Regla del repo: **no hacer commit, tag ni push salvo petición explícita**
del usuario, y incluir todos los cambios de código de la sesión antes de
versionar.

---

## 10. Qué no hacer (invariantes del actualizador)

- No reintroducir `USER_ACTION_NOT_REQUIRED`.
- No auto-instalar al terminar la descarga: el instalador se abre sólo desde
  "Instalar"/"Reintentar" (la v12.72 lo había reintroducido y la app parecía
  "salirse" sola).
- No agregar notificaciones de actualización: el relanzamiento posterior a
  instalar es best-effort vía `MY_PACKAGE_REPLACED`.
- No reintroducir gate por ETag/HEAD (`headForETag` se eliminó: causaba
  falsos "sin cambios").
- No instalar con `Intent.ACTION_VIEW` genérico.
- No agregar notificaciones ni diálogos paralelos: la tarjeta de
  `UpdateViewModel.card` es la única superficie de actualización.
- No borrar un pendiente cuando la consulta a GitHub falla con error.

---

## Arquitectura de archivos (referencia rápida)

| Archivo | Responsabilidad |
|---|---|
| `services/UpdateChecker.kt` | Consulta a GitHub, selección de asset por ABI, comparación de versiones, rechazo de tags. |
| `services/AppVersion.kt` | Parseo numérico de versiones, `versionCode = major*1000+minor`, comparación de APK vs instalado. |
| `services/ApkDownloader.kt` | Descarga reanudable, validación (tamaño, SHA, paquete, versión, firma), `PackageInstaller`, sesiones. |
| `services/UpdateInstallReceiver.kt` | Resultado de la sesión de instalación (`STATUS_PENDING_USER_ACTION`, éxito, errores). |
| `services/UpdatePackageReplacedReceiver.kt` | Tras actualizar, intenta reabrir la app (`MY_PACKAGE_REPLACED`, sin notificaciones). |
| `services/UpdateInstallBus.kt` | Puente del receiver al ViewModel. |
| `viewmodel/UpdateViewModel.kt` | Único estado visible (`card`), descarga, install, sesiones pendientes, supresión. |
| `worker/UpdateCheckWorker.kt` | Chequeo silencioso periódico (4 h). |
| `ui/components/UpdateCard.kt` | Tarjeta de actualización en pantalla. |
| `app/build.gradle` | `versionCode`/`versionName`, tarea `bumpVersion`. |
| `.github/workflows/release.yml` | CI de releases: validación de tag, compilación, `SHA256SUMS.txt`, publicación. |


