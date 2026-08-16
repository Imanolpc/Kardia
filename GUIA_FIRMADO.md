# Guía de Compilación, Ofuscación y Firma de Release - Kardia

Este documento detalla la arquitectura de compilación de producción para el proyecto Kardia y sirve de referencia para que otros desarrolladores o agentes de IA puedan compilar y firmar versiones estables para Google Play Store.

---

## 1. Cambios Estructurales en la App
Para cumplir con los estándares de calidad de la Play Store y el registro inicial en el panel:
* **Package Name / Application ID:** Modificado globalmente a `imanolpc.kardia`. Toda la estructura física de directorios de Kotlin se trasladó a `app/src/main/java/imanolpc/kardia/`.
* **Target SDK:** Actualizado a **35** (Android 15) en `app/build.gradle.kts` para cumplir con las directivas de publicación vigentes.
* **Minificación y Optimización (R8/Proguard):** Habilitada en release (`isMinifyEnabled = true`, `isShrinkResources = true`).
* **Reglas Proguard:** Definidas en `app/proguard-rules.pro` para proteger clases clave contra la ofuscación agresiva (evita crasheos por reflexión en `MediaPipe Tasks GenAI`, `PDFBox-Android` y `AnkiDroid API`).
* **Políticas de Lint:** Configurado el bloque `lint` en Gradle con `abortOnError = false` para evitar que lints externos no compatibles bloqueen el empaquetado de producción.

---

## 2. Gestión Segura de Secretos y Firma
La firma de la aplicación se gestiona de forma descentralizada y segura para evitar fugas de credenciales en repositorios públicos:
* El archivo `.gitignore` excluye de forma estricta los archivos `*.jks`, `*.keystore` y `keystore.properties`.
* Gradle detecta automáticamente si el archivo `keystore.properties` existe en la raíz del proyecto. Si existe, aplica la firma de release; si no, continúa sin firmar.

### Datos de la Firma de Release Local
Para este entorno, se ha generado el archivo de firma y propiedades local:
* **Keystore:** `kardia-release-key.jks` (Almacenado en la raíz del proyecto, ignorado por Git).
* **Contraseña del almacén (Store Password):** `KardiaReleaseKey2026Password`
* **Alias de la clave (Key Alias):** `kardia-alias`
* **Contraseña de la clave (Key Password):** `KardiaReleaseKey2026Password`

---

## 3. Cómo Firmar y Compilar a partir de ahora

### Paso 1: Configurar el entorno en una nueva máquina
Si clonas el proyecto en otra máquina o un nuevo agente empieza a trabajar en él:
1. Coloca el archivo Keystore de producción (`.jks`) en la raíz del proyecto.
2. Crea un archivo `keystore.properties` en la raíz del proyecto con la siguiente estructura:
   ```properties
   storeFile=c:\\Users\\imano\\Proyectos Party\\Kardia\\kardia-release-key.jks
   storePassword=KardiaReleaseKey2026Password
   keyAlias=kardia-alias
   keyPassword=KardiaReleaseKey2026Password
   ```
   *(Nota: Asegura duplicar las barras inversas `\\` en la ruta de Windows para evitar errores de escape).*

### Paso 2: Ejecutar la Compilación del Bundle (AAB)
Usa el motor de Gradle para compilar y firmar el Android App Bundle (.aab) definitivo para Google Play Console. Asegúrate de configurar la variable `JAVA_HOME` apuntando al JDK de Android Studio (por ejemplo, `Android Studio1\jbr`):

**En PowerShell (Windows):**
```powershell
[System.Environment]::SetEnvironmentVariable('JAVA_HOME', 'C:\Program Files\Android\Android Studio1\jbr', 'Process'); .\gradlew.bat :app:bundleRelease
```

**En Linux / Mac (Ajustando la ruta de Java y de la clave en properties):**
```bash
export JAVA_HOME="/path/to/android-studio/jbr"
./gradlew :app:bundleRelease
```

### Paso 3: Obtener el archivo firmado
El instalador final se generará en la ruta:
`app/build/outputs/bundle/release/app-release.aab`

Este archivo está listo para ser cargado en Google Play Console bajo el paquete `imanolpc.kardia`.
