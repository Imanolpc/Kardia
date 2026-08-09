# Kardia: Spaced Repetition Flashcards Generator (Local AI MVP)

Kardia es un MVP de aplicación nativa para Android diseñada para la generación privada, ilimitada y gratuita ($0 de costo operativo de backend) de tarjetas de memoria (flashcards) de repetición espaciada compatibles con Anki.

El sistema ejecuta modelos de lenguaje pequeños (SLMs) de forma 100% local en el dispositivo del usuario, garantizando privacidad absoluta por diseño (*Privacy by Design*) y alineación estricta con la LOPD (GDPR).

---

## Arquitectura de Módulos Nucleares

### Módulo 1: Motor de Inferencia Local con LiteRT-LM
* **Dependencia Oficial:** `com.google.mediapipe:tasks-genai:0.10.27` (LiteRT-LM SDK moderno para Android).
* **Modelo Utilizado:** Gemma-3 1B IT cuantizado a 4 bits (`.task`).
* **Descarga Over-The-Air (OTA):** Gestión asíncrona mediante OkHttp que descarga el modelo hacia `context.filesDir.absolutePath + "/llm/gemma-3-1b-it-q4.task"` si no se encuentra en el almacenamiento interno del usuario.
* **Optimización de Hardware:**
  * **Hilos Dedicados:** La instanciación y ejecución del modelo se realiza en `Dispatchers.IO` y `Dispatchers.Default` para evitar excepciones de *Application Not Responding* (ANR) en el hilo principal de la interfaz.
  * **Hiperparámetros de Eficiencia:** `temperature = 0.4`, `topK = 40` y límite estricto de contexto a `maxTokens = 2048` para equilibrar la coherencia semántica y conservar la batería física.

### Módulo 2: Compilador Offline de Archivos `.apkg`
El compilador realiza la creación de mazos compatibles con Anki de forma 100% nativa en Kotlin, eliminando la necesidad de empaquetar intérpretes de Python (como Chaquopy) o de llamar a servidores externos.
* **Base de Datos SQLite (`collection.anki2`):** Instanciación dinámica del esquema relacional de Anki que inicializa y puebla las tablas obligatorias: `cards`, `notes`, `col`, `graves` y `revlog`, además de sus índices estandarizados.
* **Control y Prevención de Duplicados:**
  * **GUID Estable:** Generación de `notes.guid` aplicando hashing criptográfico SHA-256 al texto frontal de la tarjeta, codificado en un formato Base64 abreviado de 10 caracteres (`Base64.NO_PADDING or Base64.NO_WRAP`). Esto evita la duplicación caótica de tarjetas al re-importar actualizaciones de mazos.
  * **Note Type Estático:** Conserva el identificador de Note Type (`mid`) y los templates HTML de visualización (`qfmt` para pregunta, `afmt` para respuesta) fijos en la tabla `col` para evitar que Anki cree copias repetidas de los tipos de tarjetas.
  * **Suma de Verificación (`csum`):** Implementación precisa del algoritmo de Anki que extrae los primeros 4 bytes del hash SHA-1 de la pregunta del anverso y los convierte en un entero de 32 bits.
* **Empaquetado APKG:** Compresión ZIP que encapsula `collection.anki2` junto a un diccionario plano de recursos multimedia (`media`) vacío, renombrado finalmente con la extensión `.apkg`.

### Módulo 3: Conexión Defensiva con AnkiDroid
Permite la inyección instantánea y directa de tarjetas mediante la API de `ContentProvider` si el usuario cuenta con AnkiDroid instalado en su terminal.
* **Configuración del Manifiesto:** Bloque `<queries>` explícito para otorgar visibilidad al paquete `com.ichi2.anki` (obligatorio en Android 11+ / API 30+) y declaración de uso del permiso `com.ichi2.anki.permission.READ_WRITE_DATABASE`.
* **Estrategia Defensiva de Control de Excepciones:**
  1. **Recuperación ante Arranque en Frío:** Captura de la excepción `IllegalArgumentException` ("Must set a non-null context...") que ocurre si el proveedor se consulta tras iniciar el dispositivo y AnkiDroid no ha cargado su contexto. El sistema lo mitiga reintentando instanciar el cliente `AddContentApi` de forma segura tras un breve retraso.
  2. **Resolución de Concurrencia de SQLite:** Captura de colisiones concurrentes mediante el monitoreo de la excepción `BackendInvalidInputException` ("card was modified") u otras relacionadas a bloqueos de base de datos. Se soluciona implementando una lógica de reintentos automatizada con retroceso exponencial (Backoff) de `500ms -> 1000ms`.

### Módulo 4: UI de Inferencia de IA Local y Prevención de LMK
* **Jetpack Compose Screens:**
  * `IdleGeneratorLayout`: Panel de entrada premium con soporte de temas HSL oscuros/claros, campos de personalización del mazo y cuadro de transcripción/apuntes.
  * `GeneratingLayout`: Pantalla de carga reactiva con un `LinearProgressIndicator` que describe los sub-procesos concurrentes en ejecución (ej: "IA local redactando tarjetas...", "Compilando base de datos SQLite (.apkg)...").
  * `FlashcardDraftEditorScreen` (Control de Calidad): Workflow de edición interactiva que permite al usuario revisar, modificar o eliminar las preguntas/respuestas generadas por el SLM antes de guardarlas, neutralizando posibles alucinaciones de la IA local.
* **Prevención del Low Memory Killer (LMK):**
  * Banner visual de advertencia de alta prioridad que alerta al usuario de que no debe minimizar la aplicación ni apagar la pantalla durante el procesamiento.
  * **Justificación de Hardware:** Gemma-3 consume un promedio de 2 GB de RAM en uso activo. Si la aplicación pasa a segundo plano, el LMK de Android detectará que un proceso inactivo está reteniendo gran cantidad de memoria y lo matará inmediatamente, perdiendo el estado temporal y corrompiendo la compilación.
* **Monetización Sostenible:** Contenedor nativo reservado para banners publicitarios (ej. AdMob) integrado en la parte inferior de la pantalla de carga para monetizar el tiempo de CPU local que el usuario observa obligatoriamente.

---

## Cómo Iniciar y Compilar el Proyecto

1. Clona este repositorio o abre la carpeta en **Android Studio (versión Jellyfish o superior)**.
2. Asegúrate de tener configurado el JDK 17 en tus variables de entorno y en los ajustes de compilación de Android Studio (`Settings > Build, Execution, Deployment > Build Tools > Gradle`).
3. El proyecto resolverá de forma automática las dependencias declaradas en [`app/build.gradle.kts`](file:///c:/Users/imano/Proyectos%20Party/Kardia/app/build.gradle.kts).
4. Ejecuta la aplicación en un dispositivo físico con al menos 4 GB de RAM física y Android 8.0 (API 26) o superior. Se recomienda un dispositivo con NPU dedicada para acelerar la velocidad de inferencia de LiteRT-LM.
5. Copia y pega un fragmento de apuntes, introduce el nombre de tu mazo y pulsa **Generar**. Si es la primera ejecución, se descargará el modelo de forma transparente desde el servidor CDN predeterminado de Hugging Face.
