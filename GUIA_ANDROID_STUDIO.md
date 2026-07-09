# 📱 RutaMercaderistas – Guía Android Studio

## 1. Instalar Android Studio
Descarga gratis desde: https://developer.android.com/studio
Instala normalmente en Mac y ábrelo.

---

## 2. Crear el proyecto

1. Abre Android Studio → **New Project**
2. Selecciona **Empty Activity**
3. Configura así:
   - **Name:** RutaMercaderistas
   - **Package name:** com.rutamercaderistas
   - **Save location:** donde quieras
   - **Language:** Kotlin
   - **Minimum SDK:** API 26 (Android 8.0)
4. Clic en **Finish**

---

## 3. Reemplazar archivos

Copia los archivos de esta carpeta en tu proyecto. La estructura es:

```
app/
├── build.gradle           ← reemplazar
├── src/main/
│   ├── AndroidManifest.xml  ← reemplazar
│   ├── java/com/rutamercaderistas/
│   │   ├── MainActivity.kt         ← reemplazar
│   │   ├── models/Models.kt        ← nuevo
│   │   ├── services/ExcelService.kt ← nuevo
│   │   ├── adapters/StoreAdapter.kt ← nuevo
│   │   ├── adapters/DayPagerAdapter.kt ← nuevo
│   │   └── fragments/DayFragment.kt ← nuevo
│   └── res/
│       ├── layout/activity_main.xml   ← reemplazar
│       ├── layout/fragment_day.xml    ← nuevo
│       ├── layout/item_store.xml      ← nuevo
│       ├── layout/item_client.xml     ← nuevo
│       ├── values/colors.xml          ← reemplazar
│       ├── values/strings.xml         ← reemplazar
│       ├── values/themes.xml          ← reemplazar
│       └── drawable/ (los 3 archivos bg_badge_*.xml) ← nuevos
```

Para crear carpetas nuevas en Android Studio:
- Clic derecho en `java/com/rutamercaderistas` → New → Package
- Escribe el nombre (models, services, adapters, fragments)

Para crear archivos Kotlin:
- Clic derecho en la carpeta → New → Kotlin Class/File
- Escribe el nombre y pega el código

---

## 4. Sincronizar el proyecto

Después de reemplazar `build.gradle`, Android Studio te mostrará un banner:
**"Gradle files have changed"** → Clic en **Sync Now**

Esto descargará Apache POI y las demás librerías (necesita internet, puede tardar unos minutos).

---

## 5. Ejecutar en tu celular Android

### Activar modo desarrollador en tu celular:
1. Ajustes → Acerca del teléfono
2. Toca **"Número de compilación"** 7 veces seguidas
3. Vuelve a Ajustes → Opciones de desarrollador
4. Activa **"Depuración USB"**

### Conectar y ejecutar:
1. Conecta el celular al Mac con cable USB
2. Acepta el permiso de depuración en el celular
3. En Android Studio, selecciona tu dispositivo en el menú superior
4. Clic en ▶ **Run** (o Shift+F10)

---

## 6. Usar la app

1. Abre la app en tu celular
2. Toca **"Cargar archivo Excel"**
3. Busca y selecciona tu archivo `.xlsx` de ruta
4. La app mostrará los tabs LUN / MAR / MIE / JUE / VIE / SAB
5. Toca cada día para ver los locales a visitar con sus marcas
6. Las marcas ⭐ prioritarias (CASO Y CIA, CUK, SUK) aparecen resaltadas en amarillo

---

## ¿Dónde poner el Excel en el celular?
Puedes enviarlo por WhatsApp, correo, Google Drive o pasarlo por cable.
La app puede abrir Excel desde cualquier ubicación del celular.

---

## Personalizar marcas prioritarias
En el archivo `Models.kt`, busca esta línea y agrega o quita marcas:
```kotlin
val MARCAS_PRIORITARIAS = setOf("CASO Y CIA", "CUK", "SUK")
```
