package imanolpc.kardia.core.ai

import android.content.Context
import android.net.Uri
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

class LocalLLMManager(private val context: Context) {

    private var activeLlmInference: LlmInference? = null
    val modelDirectory = File(context.filesDir, "llm")

    init {
        if (!modelDirectory.exists()) {
            modelDirectory.mkdirs()
        }
    }

    /**
     * Retorna el archivo File correspondiente a un modelo.
     */
    fun getModelFile(filename: String): File {
        return File(modelDirectory, filename)
    }

    /**
     * Verifica si un modelo específico está descargado y tiene tamaño válido.
     */
    fun isModelDownloaded(model: AIModelInfo): Boolean {
        val file = getModelFile(model.filename)
        return file.exists() && file.length() > 0
    }

    /**
     * Verifica si existe al menos un modelo disponible en el dispositivo.
     */
    fun hasAnyModelDownloaded(): Boolean {
        val files = modelDirectory.listFiles { file ->
            file.isFile && (file.name.endsWith(".bin") || file.name.endsWith(".task")) && file.length() > 0
        }
        return !files.isNullOrEmpty()
    }

    /**
     * Elimina un archivo de modelo para liberar almacenamiento.
     */
    fun deleteModel(model: AIModelInfo): Boolean {
        close()
        val file = getModelFile(model.filename)
        return if (file.exists()) {
            file.delete()
        } else false
    }

    /**
     * Descarga un modelo Over-The-Air (OTA) emitiendo el progreso en tiempo real.
     */
    fun downloadModel(model: AIModelInfo): Flow<DownloadStatus> = callbackFlow {
        withContext(Dispatchers.IO) {
            try {
                val finalFile = getModelFile(model.filename)
                val tempFile = File(modelDirectory, "${model.filename}.tmp")
                if (tempFile.exists()) tempFile.delete()

                val client = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build()

                val request = Request.Builder().url(model.downloadUrl).build()

                trySend(DownloadStatus.Progress(0.0f, "Conectando con el servidor..."))

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        trySend(DownloadStatus.Error(Exception("Error en descarga: HTTP ${response.code}")))
                        close()
                        return@use
                    }

                    val body = response.body
                    if (body == null) {
                        trySend(DownloadStatus.Error(Exception("Respuesta vacía del servidor")))
                        close()
                        return@use
                    }

                    val totalBytes = if (body.contentLength() > 0) body.contentLength() else model.sizeBytes
                    var bytesCopied = 0L
                    val buffer = ByteArray(32 * 1024) // Buffer optimizado de 32KB

                    body.byteStream().use { inputStream ->
                        FileOutputStream(tempFile).use { outputStream ->
                            var bytesRead = inputStream.read(buffer)
                            var lastProgressReportTime = System.currentTimeMillis()

                            while (bytesRead != -1) {
                                outputStream.write(buffer, 0, bytesRead)
                                bytesCopied += bytesRead

                                val currentTime = System.currentTimeMillis()
                                if (currentTime - lastProgressReportTime > 150) { // Limitar emisiones a ~6 fps para no saturar UI
                                    val progress = if (totalBytes > 0) bytesCopied.toFloat() / totalBytes else 0.0f
                                    val mbCopied = bytesCopied / (1024 * 1024)
                                    val mbTotal = totalBytes / (1024 * 1024)
                                    trySend(
                                        DownloadStatus.Progress(
                                            progress = progress.coerceIn(0f, 1f),
                                            message = "Descargando ${model.name}: $mbCopied MB / $mbTotal MB (${(progress * 100).toInt()}%)"
                                        )
                                    )
                                    lastProgressReportTime = currentTime
                                }
                                bytesRead = inputStream.read(buffer)
                            }
                        }
                    }

                    // Renombrar archivo temporal al definitivo
                    if (tempFile.renameTo(finalFile)) {
                        trySend(DownloadStatus.Success(finalFile.absolutePath))
                    } else {
                        trySend(DownloadStatus.Error(Exception("No se pudo guardar el archivo final.")))
                    }
                    close()
                }
            } catch (e: Exception) {
                trySend(DownloadStatus.Error(e))
                close()
            }
        }
        awaitClose { /* No-op */ }
    }

    /**
     * Importa un modelo local (.bin o .task) seleccionado por el usuario desde el almacenamiento
     * copiándolo a la carpeta privada de la app.
     */
    fun importModelFromUri(uri: Uri, targetFilename: String): Flow<DownloadStatus> = callbackFlow {
        withContext(Dispatchers.IO) {
            try {
                val finalFile = getModelFile(targetFilename)
                val tempFile = File(modelDirectory, "$targetFilename.tmp")
                if (tempFile.exists()) tempFile.delete()

                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    trySend(DownloadStatus.Error(Exception("No se pudo abrir el archivo seleccionado")))
                    close()
                    return@withContext
                }

                val totalBytes = context.contentResolver.openFileDescriptor(uri, "r")?.statSize ?: -1L
                var bytesCopied = 0L
                val buffer = ByteArray(64 * 1024)

                trySend(DownloadStatus.Progress(0.0f, "Importando modelo local..."))

                inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        var bytesRead = input.read(buffer)
                        var lastProgressReportTime = System.currentTimeMillis()

                        while (bytesRead != -1) {
                            output.write(buffer, 0, bytesRead)
                            bytesCopied += bytesRead

                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastProgressReportTime > 150) {
                                val progress = if (totalBytes > 0) bytesCopied.toFloat() / totalBytes else 0.5f
                                val mbCopied = bytesCopied / (1024 * 1024)
                                trySend(
                                    DownloadStatus.Progress(
                                        progress = progress.coerceIn(0f, 1f),
                                        message = "Copiando archivo local: $mbCopied MB..."
                                    )
                                )
                                lastProgressReportTime = currentTime
                            }
                            bytesRead = input.read(buffer)
                        }
                    }
                }

                if (tempFile.renameTo(finalFile)) {
                    trySend(DownloadStatus.Success(finalFile.absolutePath))
                } else {
                    trySend(DownloadStatus.Error(Exception("Error al importar el archivo final")))
                }
                close()
            } catch (e: Exception) {
                trySend(DownloadStatus.Error(e))
                close()
            }
        }
        awaitClose { /* No-op */ }
    }

    /**
     * Inicializa LlmInference bajo demanda. Solo debe llamarse justo antes de generar.
     */
    suspend fun initializeModel(model: AIModelInfo): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val file = getModelFile(model.filename)
            if (!file.exists() || file.length() == 0L) {
                return@withContext Result.failure(Exception("El modelo '${model.name}' no está descargado."))
            }

            // Cerrar cualquier instancia previa para no duplicar uso de RAM
            close()

            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(file.absolutePath)
                .setMaxTopK(40)
                .setMaxTokens(2048)
                .build()

            activeLlmInference = LlmInference.createFromOptions(context, options)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Genera respuestas en streaming asíncrono desde el modelo actualmente activo.
     */
    fun generateFlashcardsStream(prompt: String): Flow<InferenceStatus> = callbackFlow {
        val inference = activeLlmInference
        if (inference == null) {
            trySend(InferenceStatus.Error(Exception("Modelo de IA no inicializado en memoria")))
            close()
            return@callbackFlow
        }

        trySend(InferenceStatus.Starting)

        try {
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
     * Cierra y destruye la instancia del modelo en memoria nativa
     * y fuerza la recolección de basura para devolver la RAM a Android.
     */
    fun close() {
        try {
            activeLlmInference?.close()
        } catch (ignored: Exception) {
        } finally {
            activeLlmInference = null
            System.gc()
        }
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
