# Reglas de ofuscación R8 / Proguard para Kardia

# --- MediaPipe Tasks GenAI & LiteRT (TensorFlow Lite) ---
# MediaPipe utiliza JNI (Java Native Interface) y reflexión extensivamente para interactuar
# con las bibliotecas de C++ que ejecutan localmente el modelo Gemma-3.
-keep class com.google.mediapipe.tasks.** { *; }
-keep class com.google.mediapipe.tasks.genai.llminference.** { *; }
-dontwarn com.google.mediapipe.**

# --- PDFBox Android ---
# La biblioteca de extracción de PDFBox utiliza reflexión interna para leer recursos
# de fuentes tipográficas, metadatos y codificaciones de texto.
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**

# --- AnkiDroid API ---
# La API que conecta Kardia con la base de datos de AnkiDroid mediante ContentProvider.
-keep class com.ichi2.anki.api.** { *; }
-dontwarn com.ichi2.anki.api.**

# --- OkHttp y Okio ---
# Usados para la descarga OTA del modelo Gemma. Evita warnings molestos de compilación.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**

# Reglas generales para conservar nombres de métodos nativos
-keepclasseswithmembernames class * {
    native <methods>;
}

# Conservar atributos útiles para debugging en caso de crasheo
-keepattributes SourceFile,LineNumberTable,Signature,*Annotation*
