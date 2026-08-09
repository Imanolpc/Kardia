package com.kardia.app.core.ai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class LocalLLMManager(private val context: Context) {

    private var llmInference: LlmInference? = null

    // Ruta de almacenamiento obligatoria
    private val modelDirectory = File(context.filesDir, "llm")
    val modelFile = File(modelDirectory, "gemma-3-1b-it-q4.task")

    /**
     * Verifica si el archivo del modelo ya existe de forma local.
     */
    fun isModelDownloaded(): Boolean {
        return modelFile.exists() && modelFile.length() > 0
    }

    /**
     * Descarga el modelo Gemma-3 Over-The-Air (OTA) de forma asíncrona.
     * Emite el progreso de la descarga en valores decimales (0.0f a 1.0f).
     */
    fun downloadModel(url: String): Flow<DownloadStatus> = callbackFlow {
        withContext(Dispatchers.IO) {
            try {
                if (!modelDirectory.exists()) {
                    modelDirectory.mkdirs()
                }

                // Usamos un archivo temporal para evitar archivos corruptos a medio descargar
                val tempFile = File(modelDirectory, "gemma-3-1b-it-q4.task.tmp")
                if (tempFile.exists()) tempFile.delete()

                val client = OkHttpClient()
                val request = Request.Builder().url(url).build()

                try.trySend(DownloadStatus.Progress(0.0f, "Iniciando descarga..."))

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        try.trySend(DownloadStatus.Error(Exception("Error de servidor: ${response.code}")))
                        close()
                        return@use
                    }

                    val body = response.body
                    if (body == null) {
                        try.trySend(DownloadStatus.Error(Exception("Cuerpo de respuesta vacío")))
                        close()
                        return@use
                    }

                    val totalBytes = body.contentLength()
                    var bytesCopied = 0L
                    val buffer = ByteArray(8 * 1024)

                    body.byteStream().use { inputStream ->
                        FileOutputStream(tempFile).use { outputStream ->
                            var bytesRead = inputStream.read(buffer)
                            while (bytesRead != -1) {
                                outputStream.write(buffer, 0, bytesRead)
                                bytesCopied += bytesRead
                                
                                val progress = if (totalBytes > 0) bytesCopied.toFloat() / totalBytes else 0.0f
                                try.trySend(
                                    DownloadStatus.Progress(
                                        progress = progress,
                                        message = "Descargando modelo: ${(progress * 100).toInt()}%"
                                    )
                                )
                                bytesRead = inputStream.read(buffer)
                            }
                        }
                    }

                    // Renombrar el archivo temporal al nombre definitivo una vez completado sin errores
                    if (tempFile.renameTo(modelFile)) {
                        try.trySend(DownloadStatus.Success(modelFile.absolutePath))
                    } else {
                        try.trySend(DownloadStatus.Error(Exception("Error al renombrar el archivo temporal")))
                    }
                    close()
                }
            } catch (e: Exception) {
                try.trySend(DownloadStatus.Error(e))
                close()
            }
        }
        awaitClose { /* No-op */ }
    }

    /**
     * Inicializa el LlmInference utilizando corrutinas en Dispatchers.IO
     * para evitar ANRs en el hilo principal de la UI.
     */
    suspend fun initializeModel(): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            if (!isModelDownloaded()) {
                return@withContext Result.failure(Exception("El modelo no está descargado."))
            }

            // Liberar instancia previa si existe
            llmInference?.close()

            // Configurar LiteRT-LM según especificaciones técnicas de 2026
            val options = LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setTemperature(0.4f)   // 0.4 para balancear precisión y creatividad
                .setMaxTopK(40)         // topK = 40
                .setMaxTokens(2048)     // Contexto limitado para batería y RAM
                .build()

            llmInference = LlmInference.createFromOptions(context, options)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Genera respuestas en streaming asíncrono desde el modelo.
     */
    fun generateFlashcardsStream(prompt: String): Flow<InferenceStatus> = callbackFlow {
        val inference = llmInference
        if (inference == null) {
            trySend(InferenceStatus.Error(Exception("Modelo de IA no inicializado")))
            close()
            return@callbackFlow
        }

        trySend(InferenceStatus.Starting)

        try {
            // Inferencia asíncrona por flujo de tokens
            inference.generateResponseAsync(prompt) { partialResult, done ->
                if (partialResult != null) {
                    trySend(InferenceStatus.Token(partialResult))
                }
                if (done) {
                    trySend(InferenceStatus.Completed)
                    close()
                }
            }
        } catch (e: Exception) {
            trySend(InferenceStatus.Error(e))
            close()
        }

        awaitClose { /* No-op */ }
    }

    /**
     * Cierra la instancia del modelo para liberar RAM física del dispositivo.
     */
    fun close() {
        llmInference?.close()
        llmInference = null
    }

    sealed interface DownloadStatus {
        data class Progress(val progress: Float, val message: String) : DownloadStatus
        data class Success(val path: String) : DownloadStatus
        data class Error(val exception: Throwable) : DownloadStatus
    }

    sealed interface InferenceStatus {
        object Starting : InferenceStatus
        data class Token(val text: String) : InferenceStatus
        object Completed : InferenceStatus
        data class Error(val exception: Throwable) : InferenceStatus
    }
}
